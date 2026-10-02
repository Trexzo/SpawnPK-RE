package spk.local;

import java.io.ByteArrayOutputStream;

public final class SharedNpcRebindCleanupRetractabilityTest {
    private static final int QUEUE_CAPACITY=1024;
    private static final int[] SOURCE_SEED=
        new int[]{1001,1002,1003,1004};
    private static final int[] VIEWER_SEED=
        new int[]{1005,1006,1007,1008};

    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(610L);
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "rebind-cleanup-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "rebind-cleanup-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    SOURCE_SEED.clone()
                )
            );
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.Context viewerSync=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                new DevAuthorityWorkbench()
            );

        if(!SharedNpcWorldRelay.register(
                sourceWriter,
                world,
                source,
                sourceNpcs,
                source.movement()
            )||
           !SharedNpcWorldRelay.register(
                viewerWriter,
                world,
                viewer,
                viewerNpcs,
                viewer.movement()
            ))
            throw new AssertionError(
                "initial relay registration rejected"
            );

        try{
            PetDefinitionRepository.Def pet=
                PetDefinitionRepository.get(
                    24019
                );

            if(pet==null)
                throw new AssertionError(
                    "missing pet 24019"
                );

            String spawned=
                sourceNpcs.spawnPet(
                    pet,
                    source.movement(),
                    sourceWriter
                );

            if(spawned==null||
               !spawned.startsWith(
                    "PET_SPAWN_OK"
               ))
                throw new AssertionError(
                    "source pet fixture failed: "+
                    spawned
                );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            if(Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    source
                )<0)
                throw new AssertionError(
                    "viewer did not establish source visibility"
                );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "fixture did not create exactly one remote mirror"
                );

            int oldScene=
                viewerNpcs.snapshot().get(0)
                    .sceneIndex;

            drain(
                viewerQueue
            );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    viewerQueue,
                    QUEUE_CAPACITY
                );

            boolean replacedUnderPressure=
                SharedNpcWorldRelay.register(
                    viewerWriter,
                    world,
                    viewer,
                    viewerNpcs,
                    viewer.movement()
                );

            if(replacedUnderPressure)
                throw new AssertionError(
                    "rebind committed despite retracted old-context cleanup"
                );

            if(viewerQueue.queuedBytes()!=0||
               viewerNpcs.snapshot().size()!=1||
               viewerNpcs.snapshot().get(0)
                    .sceneIndex!=oldScene)
                throw new AssertionError(
                    "failed rebind detached/orphaned old mirror state"
                );

            /*
             * The old Context must still own this writer. A normal sync while
             * pressure remains should therefore see the existing track and must
             * not allocate a duplicate mirror.
             */
            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerQueue.queuedBytes()!=0||
               viewerNpcs.snapshot().size()!=1||
               viewerNpcs.snapshot().get(0)
                    .sceneIndex!=oldScene)
                throw new AssertionError(
                    "old context lost authority after rejected rebind"
                );

            pressure.release();

            boolean replaced=
                SharedNpcWorldRelay.register(
                    viewerWriter,
                    world,
                    viewer,
                    viewerNpcs,
                    viewer.movement()
                );

            if(!replaced)
                throw new AssertionError(
                    "rebind retry did not commit after capacity returned"
                );

            if(!viewerNpcs.snapshot().isEmpty()||
               viewerQueue.queuedPackets()!=1)
                throw new AssertionError(
                    "rebind retry did not remove old mirror exactly once packets="+
                    viewerQueue.queuedPackets()+
                    " mirrors="+
                    viewerNpcs.snapshot().size()
                );

            drain(
                viewerQueue
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "fresh rebind context did not recreate exactly one mirror"
                );

            int newScene=
                viewerNpcs.snapshot().get(0)
                    .sceneIndex;

            if(newScene!=oldScene)
                throw new AssertionError(
                    "deterministic scene allocation changed old="+
                    oldScene+
                    " new="+newScene
                );

            drain(
                viewerQueue
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1||
               viewerQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "post-rebind steady sync duplicated mirror"
                );

            System.out.println(
                "SHARED_NPC_REBIND_CLEANUP_RETRACTABILITY_PASS "+
                "retractedCleanupRejectsReplacement=true "+
                "oldContextAuthorityPreserved=true "+
                "zeroRemovalBytesOnRetraction=true "+
                "retryCleanupCommitsOnce=true "+
                "freshContextInstallsAfterCleanup=true "+
                "noOrphanDuplicateMirror=true"
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

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream sink=
            new ByteArrayOutputStream();

        while(queue.queuedBytes()>0)
            queue.drainTo(
                sink,
                Integer.MAX_VALUE
            );
    }

    private SharedNpcRebindCleanupRetractabilityTest(){}
}
