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

            Tile overlapTile=new Tile(movement.x(),movement.y(),0);
            GroundItem otherPrivate=
                world.groundItems().add(
                    995,7,overlapTile,"other",13L,false);
            GroundItem ownPrivate=
                world.groundItems().add(
                    995,11,overlapTile,"OpenSrc",13L,false);

            LocalGroundItemInteractionHandler.Result ownTaken=
                h.handle(
                    new GroundItemInteraction(
                        236,3,995,overlapTile.x,overlapTile.y
                    ),
                    "opensrc",
                    scene,
                    w
                );

            if(ownTaken==null||
               !ownTaken.logText.contains("TAKE_ON_TILE_IMMEDIATE"))
                throw new AssertionError(
                    "owner-aware take="+
                    (ownTaken==null?"null":ownTaken.logText)
                );
            if(world.groundItems().byId(ownPrivate.id)!=null)
                throw new AssertionError(
                    "own private stack remained after take"
                );
            if(world.groundItems().byId(otherPrivate.id)!=otherPrivate)
                throw new AssertionError(
                    "foreign private stack was removed"
                );

            GroundItem publicItem=
                world.groundItems().add(
                    995,3,overlapTile,null,14L,false);

            LocalGroundItemInteractionHandler.Result publicTaken=
                h.handle(
                    new GroundItemInteraction(
                        236,3,995,overlapTile.x,overlapTile.y
                    ),
                    "opensrc",
                    scene,
                    w
                );

            if(publicTaken==null||
               !publicTaken.logText.contains("TAKE_ON_TILE_IMMEDIATE"))
                throw new AssertionError(
                    "public fallback take="+
                    (publicTaken==null?"null":publicTaken.logText)
                );
            if(world.groundItems().byId(publicItem.id)!=null)
                throw new AssertionError(
                    "public fallback stack remained after take"
                );
            if(world.groundItems().byId(otherPrivate.id)!=otherPrivate)
                throw new AssertionError(
                    "foreign private stack changed during public fallback"
                );

            LocalGroundItemInteractionHandler.Result foreignOnly=
                h.handle(
                    new GroundItemInteraction(
                        236,3,995,overlapTile.x,overlapTile.y
                    ),
                    "opensrc",
                    scene,
                    w
                );

            if(foreignOnly==null||
               !foreignOnly.logText.contains(
                   "REJECTED_NOT_VISIBLE_OR_MISSING"))
                throw new AssertionError(
                    "foreign-only visibility="+
                    (foreignOnly==null?"null":foreignOnly.logText)
                );

            movement.enterTransientRegion(
                movement.x(),
                movement.y(),
                2,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            Tile planeTwo=
                new Tile(
                    movement.x(),
                    movement.y(),
                    2
                );
            Tile sameXyPlaneZero=
                new Tile(
                    movement.x(),
                    movement.y(),
                    0
                );

            GroundItem wrongPlane=
                world.groundItems().add(
                    4151,2,sameXyPlaneZero,"opensrc",15L,false);
            GroundItem planeTwoOwned=
                world.groundItems().add(
                    4151,4,planeTwo,"opensrc",15L,false);

            SceneUpdatePublisher scenePlaneTwo=
                new SceneUpdatePublisher(
                    w,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        2
                    )
                );

            LocalGroundItemInteractionHandler.Result planeTaken=
                h.handle(
                    new GroundItemInteraction(
                        236,3,4151,planeTwo.x,planeTwo.y
                    ),
                    "opensrc",
                    scenePlaneTwo,
                    w
                );

            if(planeTaken==null||
               !planeTaken.logText.contains("TAKE_ON_TILE_IMMEDIATE"))
                throw new AssertionError(
                    "nonzero-plane take="+
                    (planeTaken==null?"null":planeTaken.logText)
                );
            if(world.groundItems().byId(planeTwoOwned.id)!=null)
                throw new AssertionError(
                    "nonzero-plane stack remained"
                );
            if(world.groundItems().byId(wrongPlane.id)!=wrongPlane)
                throw new AssertionError(
                    "same x/y wrong-plane stack was selected"
                );

            movement.returnHome();

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

            testTakeTransactionAtomicity();
            testDeferredTakeFailureRetainsPending();

            System.out.println(
                "LOCAL_GROUND_ITEM_HANDLER_PASS immediateTake=true deferredOwnership=true pathEndCancel=true nonTakeFailClosed=true ownerAwareLookup=true currentPlane=true privatePreferred=true publicFallback=true takeTransactionAtomic=true takeSceneContextRollback=true deferredTransportFailureRetainsPending=true");
        }finally{
            world.close();
        }
    }

    private static void testTakeTransactionAtomicity()
        throws Exception
    {
        World world=
            World.isolatedForTest(52L);

        try{
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

            Tile tile=
                new Tile(
                    movement.x(),
                    movement.y(),
                    0
                );

            GroundItem ground=
                world.groundItems().add(
                    4151,
                    1,
                    tile,
                    "opensrc",
                    20L,
                    false
                );

            OutboundPacketQueue failedQueue=
                fullQueue();
            ServerPacketWriter failedWriter=
                queueWriter(
                    failedQueue,
                    new int[]{21,22,23,24}
                );
            SceneCoordinateContext failedContext=
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                );
            SceneUpdatePublisher failedScene=
                new SceneUpdatePublisher(
                    failedWriter,
                    failedContext
                );

            boolean failed=false;

            try{
                handler.handle(
                    new GroundItemInteraction(
                        236,
                        3,
                        4151,
                        tile.x,
                        tile.y
                    ),
                    "opensrc",
                    failedScene,
                    failedWriter
                );
            }catch(java.io.IOException expected){
                failed=true;
            }

            if(!failed||
               bank.inventoryCount(4151)!=0||
               world.groundItems().byId(ground.id)!=ground||
               failedContext.currentChunkX()!=-1||
               failedContext.currentChunkY()!=-1||
               failedQueue.queuedBytes()!=1024)
                throw new AssertionError(
                    "failed Take changed cross-domain preimage"
                );

            ServerPacketWriter healthy=
                new ServerPacketWriter(
                    new ByteArrayOutputStream(),
                    new IsaacCipher(
                        new int[]{25,26,27,28}
                    )
                );
            SceneUpdatePublisher healthyScene=
                new SceneUpdatePublisher(
                    healthy,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalGroundItemInteractionHandler.Result retry=
                handler.handle(
                    new GroundItemInteraction(
                        236,
                        3,
                        4151,
                        tile.x,
                        tile.y
                    ),
                    "opensrc",
                    healthyScene,
                    healthy
                );

            if(retry==null||
               !"GROUND_TAKE".equals(
                    retry.saveReason
               )||
               bank.inventoryCount(4151)!=1||
               world.groundItems().byId(ground.id)!=null)
                throw new AssertionError(
                    "Take retry did not commit exactly once"
                );
        }finally{
            world.close();
        }
    }

    private static void testDeferredTakeFailureRetainsPending()
        throws Exception
    {
        World world=
            World.isolatedForTest(53L);

        try{
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

            Tile target=
                new Tile(
                    movement.x()+1,
                    movement.y(),
                    0
                );

            GroundItem ground=
                world.groundItems().add(
                    4151,
                    1,
                    target,
                    "opensrc",
                    31L,
                    false
                );

            OutboundPacketQueue queue=
                fullQueue();
            ServerPacketWriter writer=
                queueWriter(
                    queue,
                    new int[]{31,32,33,34}
                );
            SceneCoordinateContext context=
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                );
            SceneUpdatePublisher scene=
                new SceneUpdatePublisher(
                    writer,
                    context
                );

            LocalGroundItemInteractionHandler.Result deferred=
                handler.handle(
                    new GroundItemInteraction(
                        236,
                        3,
                        4151,
                        target.x,
                        target.y
                    ),
                    "opensrc",
                    scene,
                    writer
                );

            if(deferred==null||
               !handler.hasPendingTake()||
               handler.pendingTakeGroundId()!=ground.id)
                throw new AssertionError(
                    "deferred failure fixture did not retain exact identity"
                );

            String accepted=
                movement.accept(
                    new MovementRequest(
                        164,
                        false,
                        new int[]{target.x},
                        new int[]{target.y},
                        new byte[0]
                    )
                );

            if(!accepted.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "deferred failure movement rejected: "+
                    accepted
                );

            movement.advance();

            boolean failed=false;

            try{
                handler.tick(
                    System.currentTimeMillis(),
                    scene,
                    writer
                );
            }catch(java.io.IOException expected){
                failed=true;
            }

            if(!failed||
               !handler.hasPendingTake()||
               handler.pendingTakeGroundId()!=ground.id||
               bank.inventoryCount(4151)!=0||
               world.groundItems().byId(ground.id)!=ground||
               context.currentChunkX()!=-1||
               context.currentChunkY()!=-1)
                throw new AssertionError(
                    "failed deferred Take did not preserve retry preimage"
                );

            ByteArrayOutputStream drain=
                new ByteArrayOutputStream();
            queue.drainTo(
                drain,
                1<<20
            );

            LocalGroundItemInteractionHandler.Result retry=
                handler.tick(
                    System.currentTimeMillis(),
                    scene,
                    writer
                );

            if(retry==null||
               !"GROUND_TAKE".equals(
                    retry.saveReason
               )||
               handler.hasPendingTake()||
               bank.inventoryCount(4151)!=1||
               world.groundItems().byId(ground.id)!=null||
               queue.queuedBytes()<=0)
                throw new AssertionError(
                    "deferred Take retry did not commit exactly once"
                );
        }finally{
            world.close();
        }
    }

    private static OutboundPacketQueue fullQueue()
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );
        return queue;
    }

    private static ServerPacketWriter queueWriter(
        OutboundPacketQueue queue,
        int[] seed
    ){
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(seed)
        );
    }

}