package spk.local;

import java.io.ByteArrayOutputStream;

public final class MakeoverNpcProximityLifecycleTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=
            new WorldPlayer();
        MovementState movement=
            player.movement();

        NpcRegistry npcs=
            new NpcRegistry();

        LocalMakeoverMageHandler handler=
            new LocalMakeoverMageHandler(
                player,
                player.equipment(),
                movement,
                npcs
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{11,22,33,44}
                )
            );

        int[] target=
            findRoutableTarget(
                movement
            );

        NpcEntity mage=
            npcs.spawnMirroredNpc(
                LocalMakeoverMageHandler.NPC_ID,
                target[0],
                target[1],
                null,
                movement,
                packets
            );

        int afterSpawn=wire.size();

        if(!handler.beginIfSupported(
                new NpcAction(
                    155,
                    mage.sceneIndex
                ),
                mage,
                packets,
                "[makeover-range-test] "
            ))
            throw new AssertionError(
                "far Make-over click not consumed"
            );

        if(handler.active()||
           !handler.pending())
            throw new AssertionError(
                "far Make-over click opened before arrival"
            );

        if(wire.size()!=afterSpawn)
            throw new AssertionError(
                "far Make-over click emitted dialogue before arrival"
            );

        for(int i=0;
            i<64&&!handler.active();
            i++){
            movement.advance();
            handler.tick(
                System.currentTimeMillis()+i,
                packets,
                "[makeover-range-test] "
            );
        }

        if(!handler.active()||
           handler.pending())
            throw new AssertionError(
                "Make-over dialogue did not open on authoritative arrival"
            );

        if(Math.max(
                Math.abs(
                    movement.x()-mage.x
                ),
                Math.abs(
                    movement.y()-mage.y
                ))>1)
            throw new AssertionError(
                "dialogue opened outside adjacency"
            );

        npcs.detachRegionViewPreservingFollowers(
            packets
        );

        int beforeTargetLossClose=
            wire.size();

        String targetLoss=
            handler.tick(
                System.currentTimeMillis(),
                packets,
                "[makeover-range-test] "
            );

        if(handler.active()||
           targetLoss==null||
           !targetLoss.contains(
               "ACTIVE_TARGET_LOST"
           ))
            throw new AssertionError(
                "active target loss did not close dialogue result="+
                targetLoss
            );

        if(wire.size()<=beforeTargetLossClose)
            throw new AssertionError(
                "target-loss close emitted no packet"
            );

        NpcEntity adjacent=
            npcs.spawnMirroredNpc(
                LocalMakeoverMageHandler.NPC_ID,
                movement.x()+1,
                movement.y(),
                null,
                movement,
                packets
            );

        if(!handler.beginIfSupported(
                new NpcAction(
                    155,
                    adjacent.sceneIndex
                ),
                adjacent,
                packets,
                "[makeover-range-test] "
            )||
           !handler.active())
            throw new AssertionError(
                "adjacent Make-over did not open"
            );

        if(!handler.cancelForManualMovement(
                packets,
                "[makeover-range-test] "
            )||
           handler.active()||
           handler.pending())
            throw new AssertionError(
                "manual movement cancellation failed"
            );

        WorldPlayer retryPlayer=
            new WorldPlayer();
        MovementState retryMovement=
            retryPlayer.movement();
        NpcRegistry retryNpcs=
            new NpcRegistry();
        LocalMakeoverMageHandler retryHandler=
            new LocalMakeoverMageHandler(
                retryPlayer,
                retryPlayer.equipment(),
                retryMovement,
                retryNpcs
            );
        OutboundPacketQueue retryQueue=
            new OutboundPacketQueue(
                1024
            );
        ServerPacketWriter retryPackets=
            new ServerPacketWriter(
                retryQueue,
                new IsaacCipher(
                    new int[]{55,66,77,88}
                )
            );
        int[] retryTarget=
            findRoutableTarget(
                retryMovement
            );
        NpcEntity retryMage=
            retryNpcs.spawnMirroredNpc(
                LocalMakeoverMageHandler.NPC_ID,
                retryTarget[0],
                retryTarget[1],
                null,
                retryMovement,
                retryPackets
            );

        drain(
            retryQueue
        );

        if(!retryHandler.beginIfSupported(
                new NpcAction(
                    155,
                    retryMage.sceneIndex
                ),
                retryMage,
                retryPackets,
                "[makeover-retry-test] "
            )||
           !retryHandler.pending()||
           retryHandler.active())
            throw new AssertionError(
                "Make-over retry fixture did not defer"
            );

        for(int i=0;
            i<64&&Math.max(
                Math.abs(
                    retryMovement.x()-retryMage.x
                ),
                Math.abs(
                    retryMovement.y()-retryMage.y
                ))>1;
            i++)
            retryMovement.advance();

        if(Math.max(
                Math.abs(
                    retryMovement.x()-retryMage.x
                ),
                Math.abs(
                    retryMovement.y()-retryMage.y
                ))>1)
            throw new AssertionError(
                "Make-over retry fixture did not reach adjacency"
            );

        OutboundPacketQueue.BatchReservation
            introPressure=
                OutboundPacketQueue.reserveBatch(
                    retryQueue,
                    1024
                );
        boolean introFailed=false;

        try{
            retryHandler.tick(
                System.currentTimeMillis(),
                retryPackets,
                "[makeover-retry-test] "
            );
        }catch(java.io.IOException expected){
            introFailed=true;
        }finally{
            introPressure.release();
        }

        if(!introFailed||
           !retryHandler.pending()||
           retryHandler.active()||
           retryQueue.queuedBytes()!=0)
            throw new AssertionError(
                "failed Make-over intro did not preserve exact pending preimage"
            );

        retryHandler.tick(
            System.currentTimeMillis(),
            retryPackets,
            "[makeover-retry-test] "
        );

        if(retryHandler.pending()||
           !retryHandler.active()||
           retryQueue.queuedBytes()<=0)
            throw new AssertionError(
                "same-writer Make-over intro retry did not commit"
            );

        drain(
            retryQueue
        );

        retryNpcs.detachRegionViewPreservingFollowers(
            retryPackets
        );
        drain(
            retryQueue
        );

        OutboundPacketQueue.BatchReservation
            closePressure=
                OutboundPacketQueue.reserveBatch(
                    retryQueue,
                    1024
                );
        boolean closeFailed=false;

        try{
            retryHandler.tick(
                System.currentTimeMillis(),
                retryPackets,
                "[makeover-retry-test] "
            );
        }catch(java.io.IOException expected){
            closeFailed=true;
        }finally{
            closePressure.release();
        }

        if(!closeFailed||
           !retryHandler.active()||
           retryQueue.queuedBytes()!=0)
            throw new AssertionError(
                "failed Make-over close cleared active semantic state"
            );

        String retryClose=
            retryHandler.tick(
                System.currentTimeMillis(),
                retryPackets,
                "[makeover-retry-test] "
            );

        if(retryHandler.active()||
           retryClose==null||
           !retryClose.contains(
               "ACTIVE_TARGET_LOST"
           )||
           retryQueue.queuedBytes()<=0)
            throw new AssertionError(
                "same-writer Make-over close retry did not commit result="+
                retryClose
            );

        System.out.println(
            "MAKEOVER_NPC_PROXIMITY_LIFECYCLE_PASS "+
            "farDeferred=true authoritativeArrival=true "+
            "exactTargetLossClose=true manualMovementClose=true "+
            "introAdmissionFailureRetainsPending=true "+
            "introSameWriterRetry=true "+
            "closeAdmissionFailureRetainsActive=true "+
            "closeSameWriterRetry=true"
        );
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        queue.drainTo(
            out,
            1<<20
        );
    }

    private static int[] findRoutableTarget(
        MovementState movement
    ){
        int startX=movement.x();
        int startY=movement.y();

        for(int radius=3;radius<=8;radius++){
            for(int dx=-radius;dx<=radius;dx++){
                for(int dy=-radius;dy<=radius;dy++){
                    if(Math.max(
                            Math.abs(dx),
                            Math.abs(dy)
                        )!=radius)
                        continue;

                    int targetX=startX+dx;
                    int targetY=startY+dy;

                    if(!MovementState.insideLoadedRegion(
                            targetX,
                            targetY
                        ))
                        continue;

                    RouteFinder.Result route=
                        RouteFinder.find(
                            RouteRequest
                                .interactionHomeRecovered(
                                    startX,
                                    startY,
                                    movement.plane(),
                                    targetX,
                                    targetY,
                                    1
                                )
                        );

                    if(route.path!=null&&
                       !route.path.isEmpty())
                        return new int[]{
                            targetX,
                            targetY
                        };
                }
            }
        }

        throw new AssertionError(
            "no routable Make-over target fixture"
        );
    }
}
