package spk.local;

import java.util.*;

/**
 * Protocol-independent isolated world-instance lifecycle.
 *
 * Map allocation, region identity, encounter rules and persistence are
 * deliberately external to this first semantic foundation.
 */
final class WorldInstanceService {
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

        Lifecycle lifecycle=Lifecycle.CREATED;
        MatchSessionService.CompositionLease
            compositionLease;

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

    synchronized void requireCompositionLeaseAvailable(
        WorldInstanceId instanceId
    ){
        Instance instance=
            require(
                instanceId
            );

        if(instance.lifecycle!=Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "composition lease requires ACTIVE instance "+
                instance.id+
                " lifecycle="+
                instance.lifecycle
            );

        requireNoCompositionLease(
            instance,
            "acquireCompositionLease"
        );
    }

    synchronized void acquireCompositionLease(
        WorldInstanceId instanceId,
        MatchSessionService.CompositionLease lease
    ){
        Instance instance=
            require(
                instanceId
            );
        MatchSessionService.CompositionLease checked=
            Objects.requireNonNull(
                lease,
                "lease"
            );

        requireCompositionLeaseAvailable(
            instance.id
        );
        instance.compositionLease=
            checked;
    }

    synchronized void requireCompositionLease(
        WorldInstanceId instanceId,
        MatchSessionService.CompositionLease lease
    ){
        Instance instance=
            require(
                instanceId
            );
        MatchSessionService.CompositionLease checked=
            Objects.requireNonNull(
                lease,
                "lease"
            );

        if(instance.compositionLease!=checked)
            throw new IllegalStateException(
                "composition lease identity mismatch instance="+
                instance.id
            );
    }

    synchronized void releaseCompositionLease(
        WorldInstanceId instanceId,
        MatchSessionService.CompositionLease lease
    ){
        Instance instance=
            require(
                instanceId
            );

        requireCompositionLease(
            instance.id,
            lease
        );
        instance.compositionLease=
            null;
    }

    synchronized boolean compositionLeaseHeld(
        WorldInstanceId instanceId
    ){
        return require(
            instanceId
        ).compositionLease!=null;
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

        requireNoCompositionLease(
            instance,
            "attach"
        );

        if(instance.lifecycle!=Lifecycle.CREATED&&
           instance.lifecycle!=Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "cannot attach in "+
                instance.lifecycle
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
        requireNoCompositionLease(
            instance,
            "detach"
        );

        return detachEntry(
            instance,
            participantRef
        );
    }

    synchronized Snapshot detachOwned(
        WorldInstanceId id,
        String participantRef,
        MatchSessionService.CompositionLease lease
    ){
        Instance instance=require(id);
        requireCompositionLease(
            instance.id,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return detachEntry(
            instance,
            participantRef
        );
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
        requireNoCompositionLease(
            instance,
            "beginClosing"
        );

        return beginClosingEntry(
            instance
        );
    }

    synchronized Snapshot beginClosingOwned(
        WorldInstanceId id,
        MatchSessionService.CompositionLease lease
    ){
        Instance instance=require(id);
        requireCompositionLease(
            instance.id,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return beginClosingEntry(
            instance
        );
    }

    synchronized Snapshot close(WorldInstanceId id){
        Instance instance=require(id);
        requireNoCompositionLease(
            instance,
            "close"
        );

        return closeEntry(
            instance
        );
    }

    synchronized Snapshot closeOwned(
        WorldInstanceId id,
        MatchSessionService.CompositionLease lease
    ){
        Instance instance=require(id);
        requireCompositionLease(
            instance.id,
            Objects.requireNonNull(
                lease,
                "lease"
            )
        );

        return closeEntry(
            instance
        );
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

    private static Snapshot detachEntry(
        Instance instance,
        String participantRef
    ){
        String participant=
            PartyService.requireRef(
                participantRef
            );

        if(instance.lifecycle==Lifecycle.CLOSED)
            throw new IllegalStateException(
                "cannot detach from closed instance"
            );

        if(!instance.participants.remove(participant))
            throw new IllegalStateException(
                "participant not attached "+
                participant
            );

        return instance.snapshot();
    }

    private static Snapshot beginClosingEntry(
        Instance instance
    ){
        if(instance.lifecycle==Lifecycle.CLOSING)
            return instance.snapshot();

        if(instance.lifecycle!=Lifecycle.ACTIVE)
            throw invalid(
                instance,
                "beginClosing"
            );

        instance.lifecycle=
            Lifecycle.CLOSING;
        return instance.snapshot();
    }

    private static Snapshot closeEntry(
        Instance instance
    ){
        if(instance.lifecycle==Lifecycle.CLOSED)
            return instance.snapshot();

        if(instance.lifecycle!=Lifecycle.CLOSING)
            throw invalid(
                instance,
                "close"
            );

        if(!instance.participants.isEmpty())
            throw new IllegalStateException(
                "cannot close instance with attached participants "+
                instance.participants.size()
            );

        instance.lifecycle=
            Lifecycle.CLOSED;
        return instance.snapshot();
    }

    private static void requireNoCompositionLease(
        Instance instance,
        String operation
    ){
        if(instance.compositionLease!=null)
            throw new IllegalStateException(
                operation+
                " blocked by composition lease instance="+
                instance.id+
                " lease="+
                instance.compositionLease
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
