package spk.local;

import java.io.IOException;

/**
 * Typed ground-item interaction coordinator.
 *
 * Packet decoding stays in ClientPacketProbe. This coordinator owns the current
 * Take semantic, deferred exact-tile arrival state, authoritative inventory/world
 * mutation, and scene removal publication.
 */
final class LocalGroundItemInteractionHandler {
    private final World world;
    private final BankState bank;
    private final MovementState movement;

    private GroundItemInteraction pendingTake;
    private long pendingTakeGroundId;
    private long pendingTakeDeadlineMs;

    LocalGroundItemInteractionHandler(
        World world,
        BankState bank,
        MovementState movement
    ){
        this.world=java.util.Objects.requireNonNull(world,"world");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
    }

    Result handle(
        GroundItemInteraction action,
        String username,
        SceneUpdatePublisher scenePublisher,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(action==null)return null;

        GroundItem ground=world.groundItems().findVisible(
            action.itemId,
            action.worldX,
            action.worldY,
            movement.plane(),
            username
        );

        if(ground==null){
            return Result.log(
                "V511_GROUND_ACTION "+action+
                " result=REJECTED_NOT_VISIBLE_OR_MISSING"
            );
        }

        String semantic=GroundItemActionRepository.action(
            action.itemId,action.option);

        if(semantic==null){
            return Result.log(
                "V511_GROUND_ACTION "+action+
                " result=EMPTY_ACTION_SLOT authority=EXACT_CLIENT_DEF"
            );
        }

        if(!"Take".equalsIgnoreCase(semantic)){
            return Result.log(
                "V511_GROUND_ACTION "+action+
                " action="+semantic+
                " result=DECODED_CONTENT_SEMANTIC_UNIMPLEMENTED"
            );
        }

        if(!bank.canAddInventoryAmount(ground.itemId,ground.amount)){
            return Result.log(
                "V511_GROUND_TAKE "+action+
                " result=REJECTED_INVENTORY_FULL amount="+ground.amount
            );
        }

        if(onTile(ground.tile.x,ground.tile.y)){
            return takeNow(
                ground,scenePublisher,serverPackets,
                "TAKE_ON_TILE_IMMEDIATE"
            );
        }

        pendingTake=action;
        pendingTakeGroundId=ground.id;
        pendingTakeDeadlineMs=System.currentTimeMillis()+10_000L;

        return Result.log(
            "V5122_GROUND_TAKE "+action+
            " result=DEFERRED_UNTIL_EXACT_TILE distance="+
            chebyshev(movement.x(),movement.y(),ground.tile.x,ground.tile.y)
        );
    }

    Result tick(
        long now,
        SceneUpdatePublisher scenePublisher,
        ServerPacketWriter serverPackets
    )throws IOException{
        GroundItemInteraction action=pendingTake;
        if(action==null)return null;

        GroundItem ground=
            world.groundItems().byId(
                pendingTakeGroundId
            );

        if(ground==null||
           ground.itemId!=action.itemId||
           ground.tile.x!=action.worldX||
           ground.tile.y!=action.worldY||
           ground.tile.plane!=movement.plane()||
           now>pendingTakeDeadlineMs){
            clearPendingTake();
            return Result.log(
                "V511_GROUND_TAKE "+action+
                " result=CANCELLED_MISSING_OR_TIMEOUT"
            );
        }

        if(!onTile(ground.tile.x,ground.tile.y)){
            if(movement.queued()==0){
                clearPendingTake();
                return Result.log(
                    "V5122_GROUND_TAKE "+action+
                    " result=CANCELLED_PATH_ENDED_NOT_ON_TILE"
                );
            }
            return null;
        }

        clearPendingTake();
        movement.clearQueuedPath();
        return takeNow(
            ground,scenePublisher,serverPackets,
            "TAKE_AFTER_EXACT_TILE_ARRIVAL"
        );
    }

    boolean hasPendingTake(){
        return pendingTake!=null;
    }

    long pendingTakeGroundId(){
        return pendingTakeGroundId;
    }

    private void clearPendingTake(){
        pendingTake=null;
        pendingTakeGroundId=0L;
        pendingTakeDeadlineMs=0L;
    }

    private Result takeNow(
        GroundItem ground,
        SceneUpdatePublisher scenePublisher,
        ServerPacketWriter serverPackets,
        String reason
    )throws IOException{
        BankState.PreparedInventoryMutation inventoryMutation=
            bank.prepareAddInventoryAmount(
                ground.itemId,
                ground.amount
            );

        if(!inventoryMutation.accepted()){
            return Result.log(
                "V511_GROUND_TAKE id="+ground.id+
                " result=REJECTED_INVENTORY_FULL"
            );
        }

        GroundItemRegistry.PreparedRemove groundMutation=
            world.groundItems().prepareRemove(
                ground.id
            );

        if(groundMutation.expected!=ground)
            return null;

        SceneCoordinateContext.Snapshot sceneBefore=
            scenePublisher.context().snapshot();

        serverPackets.beginBatch();
        boolean ended=false;

        try{
            bank.publishPreparedInventoryMutation(
                inventoryMutation,
                serverPackets
            );
            scenePublisher.groundRemove(
                ground
            );
            serverPackets.endBatch();
            ended=true;
        }catch(IOException failure){
            scenePublisher.context().restore(
                sceneBefore
            );
            if(!ended)
                try{serverPackets.abortBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            scenePublisher.context().restore(
                sceneBefore
            );
            if(!ended)
                try{serverPackets.abortBatch();}catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            scenePublisher.context().restore(
                sceneBefore
            );
            if(!ended)
                try{serverPackets.abortBatch();}catch(Throwable ignored){}
            throw failure;
        }

        int destination=
            bank.commitPreparedInventoryMutation(
                inventoryMutation
            );

        if(!world.groundItems().commitPreparedRemove(
                groundMutation
           ))
            throw new IllegalStateException(
                "prepared ground remove did not commit"
            );

        return new Result(
            "V511_GROUND_TAKE id="+ground.id+
            " item="+ground.itemId+
            " amount="+ground.amount+
            " dst="+destination+
            " world="+ground.tile+
            " result="+reason,
            "GROUND_TAKE"
        );
    }

    private boolean onTile(int x,int y){
        return movement.x()==x&&movement.y()==y;
    }

    private static int chebyshev(int x0,int y0,int x1,int y1){
        return Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
    }

    static final class Result {
        final String logText;
        final String saveReason;

        Result(String logText,String saveReason){
            this.logText=logText;
            this.saveReason=saveReason;
        }

        static Result log(String text){
            return new Result(text,null);
        }
    }
}