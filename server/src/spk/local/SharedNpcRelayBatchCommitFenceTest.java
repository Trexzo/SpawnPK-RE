package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class SharedNpcRelayBatchCommitFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        world.registerPlayer(
            source,
            "relay-batch-source"
        );
        world.registerPlayer(
            viewer,
            "relay-batch-viewer"
        );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue(
                1024
            );
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue(
                1024
            );
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{901,902,903,904}
                )
            );
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    new int[]{911,912,913,914}
                )
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.register(
            viewerWriter,
            world,
            viewer,
            new DevAuthorityWorkbench()
        );

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        MovementState sourceMovement=
            source.movement();
        MovementState viewerMovement=
            viewer.movement();

        SharedNpcWorldRelay.register(
            sourceWriter,
            world,
            source,
            sourceNpcs,
            sourceMovement
        );
        SharedNpcWorldRelay.register(
            viewerWriter,
            world,
            viewer,
            viewerNpcs,
            viewerMovement
        );

        try{
            NpcEntity[] shared=
                bootstrapSharedHome(
                    world,
                    sourceNpcs,
                    viewerNpcs,
                    sourceMovement,
                    viewerMovement,
                    sourceWriter,
                    viewerWriter
                );
            NpcEntity sourceNpc=
                shared[0];

            drain(sourceQueue);
            drain(viewerQueue);

            world.npcPresentationEvents()
                .removeSource(
                    source.id(),
                    System.currentTimeMillis()
                );

            require(
                world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewer.generation(),
                        System.currentTimeMillis()
                    )
                    .isEmpty(),
                "relay fixture did not start empty"
            );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    sourceQueue,
                    1024
                );

            require(
                SharedNpcWorldRelay
                    .beginSourceMaskBatch(
                        sourceWriter
                    ),
                "source relay batch did not begin"
            );

            sourceWriter.beginBatch();
            sourceNpcs.sendMask(
                sourceNpc,
                NpcSyncEncoder.Mask.singleHit(
                    10,
                    1,
                    245,
                    255
                ),
                sourceWriter
            );

            require(
                world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewer.generation(),
                        System.currentTimeMillis()
                    )
                    .isEmpty(),
                "remote relay escaped before source packet commit"
            );

            boolean failed=false;

            try{
                LocalSession.endWorldTickBatch(
                    sourceWriter
                );
            }catch(IOException expected){
                failed=true;
            }finally{
                pressure.release();
            }

            require(
                failed,
                "source packet batch admission unexpectedly succeeded"
            );

            require(
                SharedNpcWorldRelay
                    .abortSourceMaskBatch(
                        sourceWriter
                    )==1,
                "aborted source relay batch did not discard one mask"
            );

            require(
                sourceQueue.queuedBytes()==0,
                "aborted source packet batch leaked bytes"
            );
            require(
                world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewer.generation(),
                        System.currentTimeMillis()
                    )
                    .isEmpty(),
                "aborted source packet batch leaked remote relay event"
            );

            require(
                SharedNpcWorldRelay
                    .beginSourceMaskBatch(
                        sourceWriter
                    ),
                "retry source relay batch did not begin"
            );

            sourceWriter.beginBatch();
            sourceNpcs.sendMask(
                sourceNpc,
                NpcSyncEncoder.Mask.singleHit(
                    10,
                    1,
                    245,
                    255
                ),
                sourceWriter
            );

            LocalSession.endWorldTickBatch(
                sourceWriter
            );

            require(
                sourceQueue.queuedBytes()>0,
                "committed source retry produced no packet bytes"
            );

            require(
                world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewer.generation(),
                        System.currentTimeMillis()
                    )
                    .isEmpty(),
                "remote relay committed before source settlement hook"
            );

            require(
                SharedNpcWorldRelay
                    .commitSourceMaskBatch(
                        sourceWriter
                    )==1,
                "source relay retry did not enqueue exactly one event"
            );

            require(
                world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewer.generation(),
                        System.currentTimeMillis()
                    )
                    .size()==1,
                "committed source relay did not produce one remote event"
            );

            int viewerBytesBefore=
                viewerQueue.queuedBytes();

            SharedNpcWorldRelay.flushAfterPlayer81(
                viewerWriter
            );

            require(
                viewerQueue.queuedBytes()>
                    viewerBytesBefore,
                "committed remote relay did not eventually publish"
            );
            require(
                world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewer.generation(),
                        System.currentTimeMillis()
                    )
                    .isEmpty(),
                "committed remote relay was not delivered exactly once"
            );

            drain(sourceQueue);
            drain(viewerQueue);

            require(
                SharedNpcWorldRelay
                    .beginSourceMaskBatch(
                        sourceWriter
                    ),
                "stale-source relay batch did not begin"
            );

            sourceWriter.beginBatch();
            sourceNpcs.sendMask(
                sourceNpc,
                NpcSyncEncoder.Mask.gfx(
                    100,
                    0,
                    0
                ),
                sourceWriter
            );
            LocalSession.endWorldTickBatch(
                sourceWriter
            );

            long oldGeneration=
                source.generation();

            require(
                world.unregisterPlayer(
                    source,
                    oldGeneration
                ),
                "could not retire source generation"
            );

            require(
                SharedNpcWorldRelay
                    .commitSourceMaskBatch(
                        sourceWriter
                    )==0,
                "stale source relay was committed"
            );

            require(
                world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewer.generation(),
                        System.currentTimeMillis()
                    )
                    .isEmpty(),
                "stale source relay event leaked remotely"
            );

            System.out.println(
                "SHARED_NPC_RELAY_BATCH_COMMIT_FENCE_PASS "+
                "abortDropsRelay=true "+
                "retryEnqueuesOnce=true "+
                "commitAfterSourceBytes=true "+
                "staleSourceRejected=true"
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.unregister(
                viewerWriter
            );
            Player81WorldSync.unregister(
                sourceWriter
            );
            Player81WorldSync.unregister(
                viewerWriter
            );

            if(source.registered())
                world.unregisterPlayer(
                    source,
                    source.generation()
                );
            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewer.generation()
                );

            world.close();
        }
    }

    private static NpcEntity[] bootstrapSharedHome(
        World world,
        NpcRegistry first,
        NpcRegistry second,
        MovementState firstMovement,
        MovementState secondMovement,
        ServerPacketWriter firstWriter,
        ServerPacketWriter secondWriter
    )throws Exception{
        HomeWorldRuntimePlan firstHome=
            new HomeWorldRuntimePlan(
                world.homeNpcs()
            );
        HomeWorldRuntimePlan secondHome=
            new HomeWorldRuntimePlan(
                world.homeNpcs()
            );

        first.bootstrapHome(
            firstWriter,
            firstMovement,
            new PetState(),
            firstHome
        );
        second.bootstrapHome(
            secondWriter,
            secondMovement,
            new PetState(),
            secondHome
        );

        for(NpcEntity candidate:
                first.snapshot()){
            EntityId id=
                candidate.canonicalId();

            if(id==null)
                continue;

            NpcEntity other=
                second.canonical(
                    id
                );

            if(other!=null&&
               other.definitionId==
                    candidate.definitionId)
                return new NpcEntity[]{
                    candidate,
                    other
                };
        }

        throw new AssertionError(
            "no shared canonical HOME NPC visible to both viewers"
        );
    }

    private static byte[] drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        queue.drainTo(
            out,
            1<<20
        );

        return out.toByteArray();
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private SharedNpcRelayBatchCommitFenceTest(){}
}
