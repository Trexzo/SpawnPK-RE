package spk.local;

import java.util.*;

/**
 * Versioned semantic loadout registry and validation/apply-planning boundary.
 *
 * Applying a plan is deliberately external. The service only acknowledges that
 * authoritative mutation services applied the same current revision.
 */
final class LoadoutService {
    static final class ValidationResult {
        final boolean valid;
        final List<String> problems;

        private ValidationResult(
            boolean valid,
            List<String> problems
        ){
            this.valid=valid;
            this.problems=
                Collections.unmodifiableList(
                    new ArrayList<>(problems)
                );
        }

        static ValidationResult valid(){
            return new ValidationResult(
                true,
                Collections.emptyList()
            );
        }

        static ValidationResult invalid(
            String problem
        ){
            String checked=
                PlayerLoadout.requireText(
                    problem,
                    "problem"
                );

            return new ValidationResult(
                false,
                Collections.singletonList(
                    checked
                )
            );
        }

        static ValidationResult invalid(
            List<String> problems
        ){
            if(problems==null||problems.isEmpty())
                throw new IllegalArgumentException(
                    "problems empty"
                );

            ArrayList<String> checked=new ArrayList<>();

            for(String problem:problems)
                checked.add(
                    PlayerLoadout.requireText(
                        problem,
                        "problem"
                    )
                );

            return new ValidationResult(
                false,
                checked
            );
        }
    }

    interface LoadoutValidator {
        ValidationResult validate(PlayerLoadout loadout);
    }

    static final class Snapshot {
        final PlayerLoadout loadout;
        final PlayerLoadoutVersion lastAppliedVersion;

        Snapshot(Entry entry){
            this.loadout=entry.current;
            this.lastAppliedVersion=
                entry.lastAppliedVersion;
        }

        boolean hasAppliedVersion(){
            return lastAppliedVersion!=null;
        }
    }

    static final class LoadoutApplyPlan {
        final PlayerLoadout loadout;
        final ValidationResult validation;

        LoadoutApplyPlan(
            PlayerLoadout loadout,
            ValidationResult validation
        ){
            this.loadout=loadout;
            this.validation=validation;
        }
    }

    private static final class Entry {
        PlayerLoadout current;
        PlayerLoadoutVersion lastAppliedVersion;

        Entry(PlayerLoadout current){
            this.current=current;
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private static final class Key
        implements Comparable<Key> {
        final String ownerRef;
        final PlayerLoadoutId id;

        Key(String ownerRef,PlayerLoadoutId id){
            this.ownerRef=ownerRef;
            this.id=id;
        }

        @Override public int compareTo(Key other){
            int owner=
                ownerRef.compareTo(other.ownerRef);

            return owner!=0
                ?owner
                :id.compareTo(other.id);
        }

        @Override public boolean equals(Object other){
            return other instanceof Key&&
                ownerRef.equals(((Key)other).ownerRef)&&
                id.equals(((Key)other).id);
        }

        @Override public int hashCode(){
            return Objects.hash(ownerRef,id);
        }
    }

    private final LinkedHashMap<Key,Entry> entries=
        new LinkedHashMap<>();

    synchronized Snapshot create(PlayerLoadout loadout){
        PlayerLoadout checked=
            Objects.requireNonNull(loadout,"loadout");
        Key key=key(checked.ownerRef,checked.id);

        if(entries.containsKey(key))
            throw new IllegalStateException(
                "loadout already exists owner="+
                checked.ownerRef+
                " id="+checked.id
            );

        Entry entry=new Entry(checked);
        entries.put(key,entry);
        return entry.snapshot();
    }

    synchronized Snapshot replace(
        PlayerLoadout replacement,
        PlayerLoadoutVersion expectedCurrentVersion
    ){
        PlayerLoadout checked=
            Objects.requireNonNull(
                replacement,
                "replacement"
            );
        PlayerLoadoutVersion expected=
            Objects.requireNonNull(
                expectedCurrentVersion,
                "expectedCurrentVersion"
            );

        Entry entry=require(
            checked.ownerRef,
            checked.id
        );

        if(!entry.current.version.equals(expected))
            throw new IllegalStateException(
                "loadout version conflict expected="+
                expected+
                " actual="+entry.current.version
            );

        PlayerLoadoutVersion requiredNext=
            expected.next();

        if(!checked.version.equals(requiredNext))
            throw new IllegalStateException(
                "replacement version must be "+
                requiredNext+
                " actual="+checked.version
            );

        entry.current=checked;
        return entry.snapshot();
    }

    synchronized ValidationResult validate(
        String ownerRef,
        PlayerLoadoutId id,
        LoadoutValidator validator
    ){
        PlayerLoadout loadout=
            require(ownerRef,id).current;

        ValidationResult result=
            Objects.requireNonNull(
                Objects.requireNonNull(
                    validator,
                    "validator"
                ).validate(loadout),
                "validation result"
            );

        if(result.valid&&!result.problems.isEmpty())
            throw new IllegalStateException(
                "valid result has problems"
            );

        if(!result.valid&&result.problems.isEmpty())
            throw new IllegalStateException(
                "invalid result has no problems"
            );

        return result;
    }

    synchronized LoadoutApplyPlan planApply(
        String ownerRef,
        PlayerLoadoutId id,
        LoadoutValidator validator
    ){
        Entry entry=require(ownerRef,id);
        ValidationResult validation=
            validate(
                entry.current.ownerRef,
                entry.current.id,
                validator
            );

        if(!validation.valid)
            throw new IllegalStateException(
                "loadout validation failed "+
                validation.problems
            );

        return new LoadoutApplyPlan(
            entry.current,
            validation
        );
    }

    synchronized boolean acknowledgeApplied(
        LoadoutApplyPlan plan
    ){
        LoadoutApplyPlan checked=
            Objects.requireNonNull(plan,"plan");

        Entry entry=require(
            checked.loadout.ownerRef,
            checked.loadout.id
        );

        if(!checked.validation.valid)
            throw new IllegalStateException(
                "cannot acknowledge invalid plan"
            );

        if(!entry.current.version.equals(
                checked.loadout.version))
            throw new IllegalStateException(
                "stale apply plan version="+
                checked.loadout.version+
                " current="+
                entry.current.version
            );

        if(entry.lastAppliedVersion!=null&&
           entry.lastAppliedVersion.equals(
                checked.loadout.version))
            return false;

        entry.lastAppliedVersion=
            checked.loadout.version;
        return true;
    }

    synchronized Snapshot get(
        String ownerRef,
        PlayerLoadoutId id
    ){
        Entry entry=entries.get(key(ownerRef,id));
        return entry==null?null:entry.snapshot();
    }

    synchronized int size(){
        return entries.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Map.Entry<Key,Entry>> ordered=
            new ArrayList<>(entries.entrySet());

        ordered.sort(Map.Entry.comparingByKey());

        ArrayList<Snapshot> out=new ArrayList<>();

        for(Map.Entry<Key,Entry> value:ordered)
            out.add(value.getValue().snapshot());

        return Collections.unmodifiableList(out);
    }

    private Entry require(
        String ownerRef,
        PlayerLoadoutId id
    ){
        Key key=key(ownerRef,id);
        Entry entry=entries.get(key);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown loadout owner="+
                key.ownerRef+
                " id="+key.id
            );

        return entry;
    }

    private static Key key(
        String ownerRef,
        PlayerLoadoutId id
    ){
        return new Key(
            PlayerLoadout.requireText(
                ownerRef,
                "ownerRef"
            ),
            Objects.requireNonNull(id,"id")
        );
    }
}
