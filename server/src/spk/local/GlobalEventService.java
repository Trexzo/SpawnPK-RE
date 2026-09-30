package spk.local;

import java.util.*;

/**
 * Durable semantic global-event lifecycle above low-level tick scheduling.
 *
 * The service owns event identity and state. WorldEventQueue tasks may choose
 * when to call this service, but scheduler handles are never stored here and
 * never become event identity or lifecycle authority.
 */
final class GlobalEventService {
    static final long NO_TRANSITION_TICK=Long.MIN_VALUE;

    enum Lifecycle {
        SCHEDULED,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    enum ChangeCause {
        START_DEADLINE,
        PHASE_DEADLINE,
        END_DEADLINE,
        EXPLICIT_CANCEL,
        EXPLICIT_COMPLETE
    }

    static final class Snapshot {
        final WorldEventId id;
        final Lifecycle lifecycle;
        final long startTick;
        final long endTick;
        final String phaseKey;
        final long lastTransitionTick;
        final String sourceAuthority;

        Snapshot(Entry entry){
            this.id=entry.definition.id;
            this.lifecycle=entry.lifecycle;
            this.startTick=entry.definition.startTick;
            this.endTick=entry.definition.endTick;
            this.phaseKey=
                entry.phaseIndex<0
                    ?null
                    :entry.definition.phases
                        .get(entry.phaseIndex).key;
            this.lastTransitionTick=
                entry.lastTransitionTick;
            this.sourceAuthority=
                entry.definition.sourceAuthority;
        }

        boolean terminal(){
            return lifecycle==Lifecycle.COMPLETED||
                lifecycle==Lifecycle.CANCELLED;
        }

        boolean hasPhase(){
            return phaseKey!=null;
        }

        boolean hasTransitionTick(){
            return lastTransitionTick!=NO_TRANSITION_TICK;
        }

        @Override public String toString(){
            return "GlobalEventSnapshot{"+
                "id="+id+
                ",lifecycle="+lifecycle+
                ",startTick="+startTick+
                ",endTick="+endTick+
                ",phaseKey="+phaseKey+
                ",lastTransitionTick="+
                    (hasTransitionTick()
                        ?Long.toString(lastTransitionTick)
                        :"absent")+
                ",sourceAuthority="+sourceAuthority+
                "}";
        }
    }

    static final class Change {
        final Snapshot before;
        final Snapshot after;
        final ChangeCause cause;

        Change(
            Snapshot before,
            Snapshot after,
            ChangeCause cause
        ){
            this.before=before;
            this.after=after;
            this.cause=cause;
        }
    }

    static final class TickResult {
        final long worldTick;
        final List<Change> changes;

        TickResult(long worldTick,List<Change> changes){
            this.worldTick=worldTick;
            this.changes=immutableChanges(changes);
        }

        boolean changed(){
            return !changes.isEmpty();
        }
    }

    static final class MutationResult {
        final Snapshot snapshot;
        final List<Change> changes;

        MutationResult(Snapshot snapshot,List<Change> changes){
            this.snapshot=snapshot;
            this.changes=immutableChanges(changes);
        }

        boolean changed(){
            return !changes.isEmpty();
        }
    }

    private static final class Entry {
        final WorldEventDefinition definition;
        Lifecycle lifecycle=Lifecycle.SCHEDULED;
        int phaseIndex=-1;
        long lastTransitionTick=NO_TRANSITION_TICK;

        Entry(WorldEventDefinition definition){
            this.definition=definition;
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final LinkedHashMap<WorldEventId,Entry> entries=
        new LinkedHashMap<>();

    private long lastObservedTick=-1L;

    interface EventCompositionAction {
        void run() throws Exception;
    }

    /**
     * Holds the GlobalEventService mutation monitor for one caller-owned
     * composition action. The event entry is required to exist before the
     * action begins, but mutable Entry state is never exposed.
     *
     * Service methods are synchronized/reentrant, so the action may call
     * get/tick/complete/cancel while retaining this ownership boundary.
     */
    synchronized void withEventCompositionOwnership(
        WorldEventId eventId,
        EventCompositionAction action
    )throws Exception{
        require(
            Objects.requireNonNull(
                eventId,
                "eventId"
            )
        );
        Objects.requireNonNull(
            action,
            "action"
        ).run();
    }

    synchronized Snapshot register(
        WorldEventDefinition definition
    ){
        Objects.requireNonNull(definition,"definition");

        if(entries.containsKey(definition.id))
            throw new IllegalStateException(
                "duplicate world event id="+definition.id
            );

        Entry entry=new Entry(definition);
        entries.put(definition.id,entry);
        return entry.snapshot();
    }

    synchronized Snapshot get(WorldEventId id){
        Entry entry=entries.get(
            Objects.requireNonNull(id,"id")
        );
        return entry==null?null:entry.snapshot();
    }

    synchronized Snapshot get(String id){
        return get(WorldEventId.of(id));
    }

    synchronized int size(){
        return entries.size();
    }

    synchronized long lastObservedTick(){
        return lastObservedTick;
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Entry> ordered=new ArrayList<>(
            entries.values()
        );

        ordered.sort(
            Comparator.comparing(
                entry->entry.definition.id
            )
        );

        ArrayList<Snapshot> out=new ArrayList<>();

        for(Entry entry:ordered)
            out.add(entry.snapshot());

        return Collections.unmodifiableList(out);
    }

    synchronized TickResult tick(long worldTick){
        observeTick(worldTick);

        ArrayList<Entry> ordered=new ArrayList<>(
            entries.values()
        );

        ordered.sort(
            Comparator.comparing(
                entry->entry.definition.id
            )
        );

        ArrayList<Change> changes=new ArrayList<>();

        for(Entry entry:ordered)
            advance(entry,worldTick,changes);

        return new TickResult(worldTick,changes);
    }

    synchronized MutationResult cancel(
        WorldEventId id,
        long worldTick
    ){
        observeTick(worldTick);
        Entry entry=require(id);

        if(entry.lifecycle==Lifecycle.CANCELLED)
            return new MutationResult(
                entry.snapshot(),
                Collections.emptyList()
            );

        if(entry.lifecycle==Lifecycle.COMPLETED)
            throw new IllegalStateException(
                "cannot cancel completed event "+id
            );

        ArrayList<Change> changes=new ArrayList<>();
        advance(entry,worldTick,changes);

        if(entry.lifecycle==Lifecycle.COMPLETED)
            throw new IllegalStateException(
                "cannot cancel event after end deadline "+id
            );

        Snapshot before=entry.snapshot();
        entry.lifecycle=Lifecycle.CANCELLED;
        entry.lastTransitionTick=worldTick;
        changes.add(
            new Change(
                before,
                entry.snapshot(),
                ChangeCause.EXPLICIT_CANCEL
            )
        );

        return new MutationResult(
            entry.snapshot(),
            changes
        );
    }

    synchronized MutationResult cancel(
        String id,
        long worldTick
    ){
        return cancel(
            WorldEventId.of(id),
            worldTick
        );
    }

    synchronized MutationResult complete(
        WorldEventId id,
        long worldTick
    ){
        observeTick(worldTick);
        Entry entry=require(id);

        if(entry.lifecycle==Lifecycle.COMPLETED)
            return new MutationResult(
                entry.snapshot(),
                Collections.emptyList()
            );

        if(entry.lifecycle==Lifecycle.CANCELLED)
            throw new IllegalStateException(
                "cannot complete cancelled event "+id
            );

        ArrayList<Change> changes=new ArrayList<>();
        advance(entry,worldTick,changes);

        if(entry.lifecycle==Lifecycle.COMPLETED)
            return new MutationResult(
                entry.snapshot(),
                changes
            );

        if(entry.lifecycle!=Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "cannot complete non-active event "+id
            );

        Snapshot before=entry.snapshot();
        entry.lifecycle=Lifecycle.COMPLETED;
        entry.lastTransitionTick=worldTick;
        changes.add(
            new Change(
                before,
                entry.snapshot(),
                ChangeCause.EXPLICIT_COMPLETE
            )
        );

        return new MutationResult(
            entry.snapshot(),
            changes
        );
    }

    synchronized MutationResult complete(
        String id,
        long worldTick
    ){
        return complete(
            WorldEventId.of(id),
            worldTick
        );
    }

    private Entry require(WorldEventId id){
        WorldEventId key=Objects.requireNonNull(id,"id");
        Entry entry=entries.get(key);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown world event id="+key
            );

        return entry;
    }

    private void observeTick(long worldTick){
        if(worldTick<0)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        if(worldTick<lastObservedTick)
            throw new IllegalArgumentException(
                "world tick moved backwards "+
                worldTick+" < "+lastObservedTick
            );

        lastObservedTick=worldTick;
    }

    private static void advance(
        Entry entry,
        long worldTick,
        List<Change> changes
    ){
        if(entry.lifecycle==Lifecycle.CANCELLED||
           entry.lifecycle==Lifecycle.COMPLETED)
            return;

        WorldEventDefinition definition=
            entry.definition;

        if(entry.lifecycle==Lifecycle.SCHEDULED&&
           worldTick>=definition.startTick){
            Snapshot before=entry.snapshot();

            entry.lifecycle=Lifecycle.ACTIVE;
            entry.phaseIndex=
                definition.phaseIndexAt(
                    definition.startTick
                );
            entry.lastTransitionTick=
                definition.startTick;

            changes.add(
                new Change(
                    before,
                    entry.snapshot(),
                    ChangeCause.START_DEADLINE
                )
            );
        }

        if(entry.lifecycle==Lifecycle.ACTIVE){
            int next=entry.phaseIndex+1;

            while(next<definition.phases.size()){
                WorldEventDefinition.PhaseDefinition phase=
                    definition.phases.get(next);

                if(phase.startsAtTick>worldTick)
                    break;

                Snapshot before=entry.snapshot();
                entry.phaseIndex=next;
                entry.lastTransitionTick=
                    phase.startsAtTick;

                changes.add(
                    new Change(
                        before,
                        entry.snapshot(),
                        ChangeCause.PHASE_DEADLINE
                    )
                );

                next++;
            }

            if(worldTick>=definition.endTick){
                Snapshot before=entry.snapshot();
                entry.lifecycle=Lifecycle.COMPLETED;
                entry.lastTransitionTick=
                    definition.endTick;

                changes.add(
                    new Change(
                        before,
                        entry.snapshot(),
                        ChangeCause.END_DEADLINE
                    )
                );
            }
        }
    }

    private static List<Change> immutableChanges(
        List<Change> changes
    ){
        return Collections.unmodifiableList(
            new ArrayList<>(changes)
        );
    }
}
