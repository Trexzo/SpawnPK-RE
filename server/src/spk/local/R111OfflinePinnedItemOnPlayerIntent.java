package spk.local;

import java.util.Objects;

/**
 * R111 OFFLINE TEST ONLY. Immutable packet-intent identity captured on the
 * World actor execution context and rechecked when a queued command runs.
 *
 * This is read-only diagnostics: neither capture nor recheck authorizes,
 * consumes or dispatches an item-on-player action. In particular, matching
 * item ID + quantity is NOT proof against consume-and-reinsert (ABA).
 */
final class R111OfflinePinnedItemOnPlayerIntent {
    private final World world;
    private final EntityId sourceId;
    private final long sourceGeneration;
    private final EntityId targetId;
    private final long targetGeneration;
    private final GenericInteractionEvent event;
    private final int selectedQuantity;

    private R111OfflinePinnedItemOnPlayerIntent(
            World world,R110OfflineItemOnPlayerPreflight.Review review,
            GenericInteractionEvent event){
        this.world=world;
        sourceId=review.sourceId;
        sourceGeneration=review.sourceGeneration;
        targetId=review.targetId;
        targetGeneration=review.targetGeneration;
        selectedQuantity=review.selectedQuantity;
        this.event=event;
    }

    static R111OfflinePinnedItemOnPlayerIntent captureForTest(
            World world,WorldPlayer source,long generation,
            Player81WorldSync.Context viewer,
            GenericInteractionEvent event)throws Exception{
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(source,"source");
        Objects.requireNonNull(viewer,"viewer");
        Objects.requireNonNull(event,"event");
        if(!Thread.holdsLock(source.mutationLock()))
            throw refused("capture requires source actor mutation lock");
        R110OfflineItemOnPlayerPreflight.Review read=
            R110OfflineItemOnPlayerPreflight.inspectForTest(
                world,source,generation,viewer,event);
        return new R111OfflinePinnedItemOnPlayerIntent(
            world,read,event);
    }

    R110OfflineItemOnPlayerPreflight.Review recheckForTest(
            World expectedWorld,WorldPlayer source,long generation,
            Player81WorldSync.Context viewer)throws Exception{
        if(world!=expectedWorld)
            throw refused("wrong World");
        if(source==null||!sourceId.equals(source.id())||
                generation!=sourceGeneration)
            throw refused("different source actor or generation");
        if(!Thread.holdsLock(source.mutationLock()))
            throw refused("recheck requires source actor mutation lock");

        // R110 re-resolves the *current* viewer index and verifies source
        // selected item, slot and target ownership under both actor locks.
        R110OfflineItemOnPlayerPreflight.Review current=
            R110OfflineItemOnPlayerPreflight.inspectForTest(
                world,source,generation,viewer,event);
        if(!targetId.equals(current.targetId)||
                targetGeneration!=current.targetGeneration)
            throw refused("viewer index rebound to different actor");
        if(current.selectedQuantity!=selectedQuantity)
            throw refused("selected item quantity changed while queued");
        return current;
    }

    EntityId pinnedTargetIdForTest(){return targetId;}
    long pinnedTargetGenerationForTest(){return targetGeneration;}
    int pinnedItemQuantityForTest(){return selectedQuantity;}

    private static IllegalStateException refused(String reason){
        return new IllegalStateException(
            "R111_QUEUED_ITEM_INTENT_DENIED "+reason);
    }

}
