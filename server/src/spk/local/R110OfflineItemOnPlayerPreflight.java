package spk.local;

import java.util.Objects;

/**
 * R110 OFFLINE TEST ONLY: a non-mutating snapshot preflight for opcode 14.
 *
 * This does NOT enable LocalGenericInteractionHandler.ITEM_ON_PLAYER.
 * The incoming player index is viewer-local and MUST be resolved through
 * the real generation-fenced Player81WorldSync.Context, not treated as
 * an EntityId. Selected item and slot are untrusted packet claims and
 * must match the actual authoritative source inventory slot.
 *
 * Result is diagnostic-only and cannot authorize deferred execution.
 * No item-on-player semantic effect is defined by this fixture.
 */
final class R110OfflineItemOnPlayerPreflight {
    static final class Review {
        final EntityId sourceId;
        final long sourceGeneration;
        final EntityId targetId;
        final long targetGeneration;
        final int selectedItem;
        final int selectedSlot;
        final int viewerIndex;
        final int widget;

        private Review(WorldPlayer source,long sg,WorldPlayer target,
                long tg,GenericInteractionEvent event){
            sourceId=source.id();
            sourceGeneration=sg;
            targetId=target.id();
            targetGeneration=tg;
            selectedItem=event.selectedItemId;
            selectedSlot=event.selectedSlot;
            viewerIndex=event.targetId;
            widget=event.selectedWidget;
        }
    }

    static Review inspectForTest(World world,WorldPlayer source,
            long sourceGeneration,Player81WorldSync.Context viewer,
            GenericInteractionEvent event)throws Exception{
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(source,"source");
        Objects.requireNonNull(viewer,"viewer");
        Objects.requireNonNull(event,"event");
        if(!world.pulse().inExecutionContext())
            throw refused("not World execution context");

        if(event.family!=GenericInteractionEvent.Family.ITEM_ON_PLAYER||
                event.opcode!=14)
            throw refused("not exact opcode 14 item-on-player");
        if(event.selectedWidget!=BankState.NORMAL_INVENTORY_CONTAINER)
            throw refused("not known selected inventory widget");
        if(event.selectedSlot<0||
                event.selectedSlot>=BankState.INVENTORY_CAPACITY)
            throw refused("selected inventory slot outside 0..27");
        if(event.selectedItemId<=0)
            throw refused("invalid selected item ID");
        if(viewer.owner!=source||
                viewer.ownerGeneration!=sourceGeneration||
                !viewer.ownerCurrent())
            throw refused("stale viewer source generation");
        if(event.targetId<0||
                event.targetId==NpcRegistry.LOCAL_PLAYER_INDEX)
            throw refused("invalid or self viewer-local target index");

        WorldPlayer target=viewer.resolveVisible(event.targetId);
        if(target==null||target==source)
            throw refused("target not currently visible");
        long targetGeneration=target.generation();
        final Review[] record=new Review[1];

        boolean pair=world.withOfflineDualPlayerMutationOwnershipForTest(
            source,sourceGeneration,target,targetGeneration,()->{
                // All user-supplied claims are revalidated while both
                // World actors are locked; no state mutation is permitted.
                if(!viewer.ownerCurrent()||
                        viewer.resolveVisible(event.targetId)!=target)
                    throw refused("viewer mapping changed");
                BankState.InventorySlotSnapshot snapshot=
                    source.bank().inventorySlotSnapshot(
                        event.selectedSlot);
                if(!snapshot.occupied||snapshot.quantity<=0||
                        snapshot.itemId!=event.selectedItemId)
                    throw refused("selected item/slot provenance mismatch");
                record[0]=new Review(source,sourceGeneration,
                    target,targetGeneration,event);
            });
        if(!pair||record[0]==null)
            throw refused("stale source or target ownership");
        return record[0];
    }

    private static IllegalStateException refused(String reason){
        return new IllegalStateException(
            "R110_ITEM_ON_PLAYER_PREFLIGHT_DENIED "+reason);
    }

    private R110OfflineItemOnPlayerPreflight(){}
}
