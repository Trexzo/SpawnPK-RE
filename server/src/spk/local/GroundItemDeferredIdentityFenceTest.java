package spk.local;

import java.io.ByteArrayOutputStream;

public final class GroundItemDeferredIdentityFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer player=
            new WorldPlayer();

        BankState bank=
            player.bank();
        MovementState movement=
            player.movement();

        LocalGroundItemInteractionHandler handler=
            new LocalGroundItemInteractionHandler(
                world,
                bank,
                movement
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        SceneUpdatePublisher scene=
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                )
            );

        try{
            Tile tile=
                new Tile(
                    movement.x()+1,
                    movement.y(),
                    0
                );

            GroundItem original=
                world.groundItems().add(
                    995,
                    25,
                    tile,
                    "opensrc",
                    10L,
                    false
                );

            GroundItemInteraction action=
                new GroundItemInteraction(
                    236,
                    3,
                    995,
                    tile.x,
                    tile.y
                );

            LocalGroundItemInteractionHandler.Result queued=
                handler.handle(
                    action,
                    "opensrc",
                    scene,
                    writer
                );

            if(queued==null||
               !queued.logText.contains(
                    "DEFERRED_UNTIL_EXACT_TILE"
               ))
                throw new AssertionError(
                    "deferred Take not accepted "+
                    (queued==null
                        ?"null"
                        :queued.logText)
                );

            if(!handler.hasPendingTake()||
               handler.pendingTakeGroundId()!=
                    original.id)
                throw new AssertionError(
                    "pending Take did not capture original identity"
                );

            if(!world.groundItems().remove(
                    original.id
                ))
                throw new AssertionError(
                    "original ground item removal failed"
                );

            GroundItem replacement=
                world.groundItems().add(
                    995,
                    25,
                    tile,
                    "different-owner",
                    11L,
                    false
                );

            if(replacement.id==original.id)
                throw new AssertionError(
                    "replacement reused original identity"
                );

            moveTo(
                movement,
                tile.x,
                tile.y
            );

            int inventoryBefore=
                bank.inventoryCount(995);

            LocalGroundItemInteractionHandler.Result stale=
                handler.tick(
                    System.currentTimeMillis(),
                    scene,
                    writer
                );

            if(stale==null||
               !stale.logText.contains(
                    "CANCELLED_MISSING_OR_TIMEOUT"
               ))
                throw new AssertionError(
                    "stale deferred Take was not cancelled "+
                    (stale==null
                        ?"null"
                        :stale.logText)
                );

            if(handler.hasPendingTake()||
               handler.pendingTakeGroundId()!=0L)
                throw new AssertionError(
                    "stale pending identity was not cleared"
                );

            if(bank.inventoryCount(995)!=
                    inventoryBefore)
                throw new AssertionError(
                    "stale Take mutated inventory"
                );

            if(world.groundItems().byId(
                    replacement.id
                )!=replacement)
                throw new AssertionError(
                    "stale Take consumed replacement item"
                );

            if(!world.groundItems().remove(
                    replacement.id
                ))
                throw new AssertionError(
                    "replacement cleanup failed"
                );

            GroundItem fresh=
                world.groundItems().add(
                    995,
                    25,
                    tile,
                    "opensrc",
                    12L,
                    false
                );

            LocalGroundItemInteractionHandler.Result freshTake=
                handler.handle(
                    action,
                    "opensrc",
                    scene,
                    writer
                );

            if(freshTake==null||
               !freshTake.logText.contains(
                    "TAKE_ON_TILE_IMMEDIATE"
               ))
                throw new AssertionError(
                    "fresh click did not take fresh item "+
                    (freshTake==null
                        ?"null"
                        :freshTake.logText)
                );

            if(world.groundItems().byId(
                    fresh.id
                )!=null)
                throw new AssertionError(
                    "fresh item remained after fresh Take"
                );

            if(bank.inventoryCount(995)!=
                    inventoryBefore+25)
                throw new AssertionError(
                    "fresh Take inventory mismatch"
                );

            System.out.println(
                "GROUND_ITEM_DEFERRED_IDENTITY_FENCE_PASS "+
                "originalIdentityCaptured=true "+
                "replacementIdentityDifferent=true "+
                "staleTakeCancelled=true "+
                "replacementOwnerItemPreserved=true "+
                "inventoryUnchangedOnStale=true "+
                "freshReplacementTakeWorks=true"
            );
        }finally{
            world.close();
        }
    }

    private static void moveTo(
        MovementState movement,
        int x,
        int y
    ){
        String accepted=
            movement.accept(
                new MovementRequest(
                    164,
                    false,
                    new int[]{x},
                    new int[]{y},
                    new byte[0]
                )
            );

        if(!accepted.startsWith(
                "ACCEPTED"))
            throw new AssertionError(
                "movement setup failed "+
                accepted
            );

        MovementState.Tick tick=
            movement.advance();

        if(tick==null||
           movement.x()!=x||
           movement.y()!=y)
            throw new AssertionError(
                "movement did not reach deferred tile"
            );
    }

    private GroundItemDeferredIdentityFenceTest(){}
}
