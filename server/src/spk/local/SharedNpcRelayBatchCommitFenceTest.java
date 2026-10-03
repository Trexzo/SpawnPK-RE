package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class SharedNpcRelayBatchCommitFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "relay-batch-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "relay-batch-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue(1024);
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue(4096);
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
            NpcEntity sourceTarget=
                shared[0];

            drain(
                sourceQueue
            );
            drain(
                viewerQueue
            );

            NpcSyncEncoder.Mask mask=
                NpcSyncEncoder.Mask.forceText(
                    "relay-batch"
                );

            SharedNpcWorldRelay.beginSourceMaskBatch(
                sourceWriter
            );
            sourceWriter.beginBatch();

            sourceNpcs.sendMask(
                sourceTarget,
                mask,
                sourceWriter
            );

            if(world.npcPresentationEvents()
                    .size()!=0||
               sourceQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "relay/source bytes committed before outer source settlement"
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    sourceQueue,
                    1024
                );

            boolean sourceCommitFailed=false;
            try{
                LocalSession.endWorldTickBatch(
                    sourceWriter
                );
            }catch(IOException expected){
                sourceCommitFailed=true;
            }finally{
                pressure.release();
            }

            SharedNpcWorldRelay.abortSourceMaskBatch(
                sourceWriter
            );

            if(!sourceCommitFailed||
               sourceQueue.queuedBytes()!=0||
               world.npcPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "aborted source batch leaked relay or source bytes"
                );

            SharedNpcWorldRelay.beginSourceMaskBatch(
                sourceWriter
            );
            sourceWriter.beginBatch();

            sourceNpcs.sendMask(
                sourceTarget,
                mask,
                sourceWriter
            );

            LocalSession.endWorldTickBatch(
                sourceWriter
            );

            if(sourceQueue.queuedBytes()<=0||
               world.npcPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "source bytes/relay staging settlement order wrong"
                );

            SharedNpcWorldRelay.commitSourceMaskBatch(
                sourceWriter
            );

            if(world.npcPresentationEvents()
                    .size()!=1)
                throw new AssertionError(
                    "committed relay did not enqueue exactly once"
                );

            int viewerBefore=
                viewerQueue.queuedBytes();

            viewerWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            if(viewerQueue.queuedBytes()<=
                    viewerBefore)
                throw new AssertionError(
                    "committed relay was not eventually delivered"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        viewerGeneration,
                        System.currentTimeMillis()
                    ).isEmpty())
                throw new AssertionError(
                    "committed relay remained pending after delivery"
                );

            drain(
                sourceQueue
            );

            SharedNpcWorldRelay.beginSourceMaskBatch(
                sourceWriter
            );
            sourceWriter.beginBatch();
            sourceNpcs.sendMask(
                sourceTarget,
                NpcSyncEncoder.Mask.forceText(
                    "stale-source"
                ),
                sourceWriter
            );
            LocalSession.endWorldTickBatch(
                sourceWriter
            );

            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.commitSourceMaskBatch(
                sourceWriter
            );

            if(world.npcPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "stale source context admitted staged relay"
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
                    sourceGeneration
                );
            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewerGeneration
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
            "no shared HOME target"
        );
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        queue.drainTo(
            new ByteArrayOutputStream(),
            Integer.MAX_VALUE
        );
    }

    private SharedNpcRelayBatchCommitFenceTest(){}
}
