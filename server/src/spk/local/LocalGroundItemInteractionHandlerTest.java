package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalGroundItemInteractionHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            BankState bank=player.bank();
            MovementState movement=player.movement();

            LocalGroundItemInteractionHandler h=
                new LocalGroundItemInteractionHandler(world,bank,movement);

            ByteArrayOutputStream wire=new ByteArrayOutputStream();
            ServerPacketWriter w=new ServerPacketWriter(
                wire,new IsaacCipher(new int[]{1,2,3,4}));
            SceneUpdatePublisher scene=new SceneUpdatePublisher(
                w,
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                )
            );

            Tile here=new Tile(movement.x(),movement.y(),0);
            world.groundItems().add(4151,1,here,"opensrc",10L,true);

            GroundItemInteraction immediate=new GroundItemInteraction(
                236,3,4151,here.x,here.y);
            int beforeWire=wire.size();
            LocalGroundItemInteractionHandler.Result taken=
                h.handle(immediate,"opensrc",scene,w);

            if(taken==null||
               !taken.logText.contains("result=TAKE_ON_TILE_IMMEDIATE"))
                throw new AssertionError("immediate take route="+
                    (taken==null?"null":taken.logText));
            if(!"GROUND_TAKE".equals(taken.saveReason))
                throw new AssertionError("take save reason="+taken.saveReason);
            if(bank.inventoryCount(4151)!=1)
                throw new AssertionError("taken item not added to inventory");
            if(world.groundItems().find(4151,here.x,here.y,0)!=null)
                throw new AssertionError("taken item still in world");
            if(wire.size()<=beforeWire)
                throw new AssertionError("take emitted no inventory/scene packets");

            Tile far=new Tile(movement.x()+2,movement.y(),0);
            world.groundItems().add(995,25,far,"opensrc",11L,true);
            GroundItemInteraction deferred=new GroundItemInteraction(
                236,3,995,far.x,far.y);

            LocalGroundItemInteractionHandler.Result queued=
                h.handle(deferred,"opensrc",scene,w);
            if(queued==null||
               !queued.logText.contains("DEFERRED_UNTIL_EXACT_TILE"))
                throw new AssertionError("deferred route="+
                    (queued==null?"null":queued.logText));
            if(!h.hasPendingTake())
                throw new AssertionError("deferred state not owned by handler");

            LocalGroundItemInteractionHandler.Result cancelled=
                h.tick(System.currentTimeMillis(),scene,w);
            if(cancelled==null||
               !cancelled.logText.contains("CANCELLED_PATH_ENDED_NOT_ON_TILE"))
                throw new AssertionError("deferred cancellation="+
                    (cancelled==null?"null":cancelled.logText));
            if(h.hasPendingTake())
                throw new AssertionError("cancelled pending state not cleared");

            Tile studyTile=new Tile(movement.x()+1,movement.y(),0);
            world.groundItems().add(4653,1,studyTile,"opensrc",12L,true);
            GroundItemInteraction study=new GroundItemInteraction(
                236,3,4653,studyTile.x,studyTile.y);
            LocalGroundItemInteractionHandler.Result studyResult=
                h.handle(study,"opensrc",scene,w);
            if(studyResult==null||
               !studyResult.logText.contains(
                   "action=Study result=DECODED_CONTENT_SEMANTIC_UNIMPLEMENTED"))
                throw new AssertionError("non-Take semantic="+
                    (studyResult==null?"null":studyResult.logText));
            if(studyResult.saveReason!=null)
                throw new AssertionError("unimplemented semantic must not save");

            System.out.println(
                "LOCAL_GROUND_ITEM_HANDLER_PASS immediateTake=true deferredOwnership=true pathEndCancel=true nonTakeFailClosed=true");
        }finally{
            world.close();
        }
    }
}
