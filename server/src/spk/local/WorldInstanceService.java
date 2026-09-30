package spk.local;

import java.util.*;

/**
 * Protocol-independent isolated world-instance lifecycle.
 *
 * Map allocation, region identity, encounter rules and persistence are
 * deliberately external to this first semantic foundation.
 */
final class WorldInstanceService {
    static final class StructuralHold {
        private final WorldInstanceService owner;
        private final WorldInstanceId instanceId;

        private StructuralHold(
            WorldInstanceService owner,
            WorldInstanceId instanceId
        ){
            this.owner=owner;
            this.instanceId=instanceId;
        }
    }

    enum Lifecycle {
        CREATED,
        ACTIVE,
        CLOSING,
        CLOSED
    }

    static final class Snapshot {
        final WorldInstanceId id;
        final String ownerRef;
        final Lifecycle lifecycle;
        final List<String> participants;
        final String sourceAuthority;

        Snapshot(Instance value){
            this.id=value.id;
            this.ownerRef=value.ownerRef;
            this.lifecycle=value.lifecycle;
            this.participants=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        value.participants
                    )
                );
            this.sourceAuthority=value.sourceAuthority;
        }

        boolean participant(String participantRef){
            return participants.contains(
                PartyService.requireRef(
                    participantRef
                )
            );
        }

        boolean terminal(){
            return lifecycle==Lifecycle.CLOSED;
        }
    }

    private static final class Instance {
        final WorldInstanceId id;
        final String ownerRef;
        final String sourceAuthority;
        final LinkedHashSet<String> participants=
            new LinkedHashSet<>();
        final Set<StructuralHold> structuralHolds=
            Collections.newSetFromMap(
                new IdentityHashMap<
                    StructuralHold,
                    Boolean
                >()
            );

        Lifecycle lifecycle=Lifecycle.CREATED;

        Instance(
            WorldInstanceId id,
            String ownerRef,
            String sourceAuthority
        ){
            this.id=id;
            this.ownerRef=ownerRef;
            this.sourceAuthority=sourceAuthority;
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final LinkedHashMap<WorldInstanceId,Instance>
        instances=
            new LinkedHashMap<>();

    synchronized void withMatchCompositionOwnership(
        MatchSessionService.MatchInstanceCompositionAction action
    )throws Exception{
        Objects.requireNonNull(
            action,
            "action"
        );
        action.run();
    }

    synchronized StructuralHold acquireStructuralHold(
        WorldInstanceId instanceId
    ){
        Instance instance=require(instanceId);

        if(instance.lifecycle!=Lifecycle.ACTIVE)
            throw invalid(
                instance,
                "acquireStructuralHold"
            );

        StructuralHold hold=
            new StructuralHold(
                this,
                instance.id
            );

        if(!instance.structuralHolds.add(hold))
            throw new IllegalStateException(
                "duplicate WorldInstance structural hold identity instance="+
                instance.id
            );

        return hold;
    }

    synchronized void releaseStructuralHold(
        StructuralHold hold
    ){
        StructuralHold checked=
            Objects.requireNonNull(
                hold,
                "hold"
            );

        if(checked.owner!=this)
            throw new IllegalArgumentException(
                "structural hold belongs to another WorldInstanceService"
            );

        Instance instance=require(
            checked.instanceId
        );

        if(!instance.structuralHolds.remove(
                checked))
            throw new IllegalStateException(
                "structural hold already released or unknown instance="+
                checked.instanceId
            );
    }

    synchronized int structuralHoldCount(
        WorldInstanceId instanceId
    ){
        return require(instanceId)
            .structuralHolds.size();
    }

    synchronized Snapshot create(
        WorldInstanceId id,
        String ownerRef,
        String sourceAuthority
    ){
        WorldInstanceId key=
            Objects.requireNonNull(id,"id");

        if(instances.containsKey(key))
            throw new IllegalStateException(
                "duplicate world instance id="+key
            );

        Instance instance=
            new Instance(
                key,
                PartyService.requireRef(ownerRef),
                requireAuthority(sourceAuthority)
            );

        instances.put(key,instance);
        return instance.snapshot();
    }

    synchronized Snapshot attach(
        WorldInstanceId id,
        String participantRef
    ){
        Instance instance=require(id);
        String participant=
            PartyService.requireRef(
                participantRef
            );

        if(instance.lifecycle!=Lifecycle.CREATED&&
           instance.lifecycle!=Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "cannot attach in "+
                instance.lifecycle
            );

        requireNoStructuralHolds(
            instance,
            "attach"
        );

        if(!instance.participants.add(participant))
            throw new IllegalStateException(
                "participant already attached "+
                participant
            );

        return instance.snapshot();
    }

    synchronized Snapshot detach(
        WorldInstanceId id,
        String participantRef
    ){
        Instance instance=require(id);
        String participant=
            PartyService.requireRef(
                participantRef
            );

        if(instance.lifecycle==Lifecycle.CLOSED)
            throw new IllegalStateException(
                "cannot detach from closed instance"
            );

        requireNoStructuralHolds(
            instance,
            "detach"
        );

        if(!instance.participants.remove(participant))
            throw new IllegalStateException(
                "participant not attached "+
                participant
            );

        return instance.snapshot();
    }

    synchronized Snapshot activate(WorldInstanceId id){
        Instance instance=require(id);

        if(instance.lifecycle!=Lifecycle.CREATED)
            throw invalid(instance,"activate");

        instance.lifecycle=Lifecycle.ACTIVE;
        return instance.snapshot();
    }

    synchronized Snapshot beginClosing(WorldInstanceId id){
        Instance instance=require(id);

        requireNoStructuralHolds(
            instance,
            "beginClosing"
        );

        if(instance.lifecycle==Lifecycle.CLOSING)
            return instance.snapshot();

        if(instance.lifecycle!=Lifecycle.ACTIVE)
            throw invalid(instance,"beginClosing");

        instance.lifecycle=Lifecycle.CLOSING;
        return instance.snapshot();
    }

    synchronized Snapshot close(WorldInstanceId id){
        Instance instance=require(id);

        requireNoStructuralHolds(
            instance,
            "close"
        );

        if(instance.lifecycle==Lifecycle.CLOSED)
            return instance.snapshot();

        if(instance.lifecycle!=Lifecycle.CLOSING)
            throw invalid(instance,"close");

        if(!instance.participants.isEmpty())
            throw new IllegalStateException(
                "cannot close instance with attached participants "+
                instance.participants.size()
            );

        instance.lifecycle=Lifecycle.CLOSED;
        return instance.snapshot();
    }

    synchronized Snapshot get(WorldInstanceId id){
        Instance instance=instances.get(
            Objects.requireNonNull(id,"id")
        );

        return instance==null
            ?null
            :instance.snapshot();
    }

    synchronized int size(){
        return instances.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Instance> ordered=
            new ArrayList<>(
                instances.values()
            );

        ordered.sort(
            Comparator.comparing(value->value.id)
        );

        ArrayList<Snapshot> out=new ArrayList<>();

        for(Instance instance:ordered)
            out.add(instance.snapshot());

        return Collections.unmodifiableList(out);
    }

    private static void requireNoStructuralHolds(
        Instance instance,
        String operation
    ){
        if(!instance.structuralHolds.isEmpty())
            throw new IllegalStateException(
                operation+
                " blocked by WorldInstance structural holds instance="+
                instance.id+
                " holds="+
                instance.structuralHolds
            );
    }

    private Instance require(WorldInstanceId id){
        WorldInstanceId key=
            Objects.requireNonNull(id,"id");
        Instance instance=instances.get(key);

        if(instance==null)
            throw new IllegalArgumentException(
                "unknown world instance id="+key
            );

        return instance;
    }

    private static IllegalStateException invalid(
        Instance instance,
        String operation
    ){
        return new IllegalStateException(
            operation+
            " invalid from "+
            instance.lifecycle+
            " for "+
            instance.id
        );
    }

    private static String requireAuthority(String value){
        if(value==null)
            throw new NullPointerException(
                "sourceAuthority"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "sourceAuthority blank"
            );

        return clean;
    }
}
