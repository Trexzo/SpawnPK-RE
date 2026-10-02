package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public final class RemotePetMirrorMaskRetryTest {
    private static final int QUEUE_CAPACITY=1024;
    private static final int[] SOURCE_SEED=
        new int[]{701,702,703,704};
    private static final int[] VIEWER_SEED=
        new int[]{705,706,707,708};

    public static void main(String[] args)throws Exception{
        assertPostAddMaskFailureDoesNotDuplicateMirror();

        System.out.println(
            "REMOTE_PET_MIRROR_MASK_RETRY_PASS "+
            "addCommittedOnce=true "+
            "failedMaskDeferred=true "+
            "stableSceneOnRetry=true "+
            "noDuplicateMirror=true "+
            "deferredMaskRetried=true "+
            "deferredMaskBlocksLaterProjection=true "+
            "fifoDrainsBeforeMiniProjection=true "+
            "nativeMaskOrderedAfterDeferredInteraction=true "+
            "queueBackedRetry=true "+
            "retryCipherRewound=true "+
            "eventualWireMatchesCleanReference=true"
        );
    }

    private static void assertPostAddMaskFailureDoesNotDuplicateMirror()
        throws Exception
    {
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
                "mirror-mask-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "mirror-mask-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    SOURCE_SEED.clone()
                )
            );

        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );

        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
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

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        SharedNpcWorldRelay.register(
            sourceWriter,
            world,
            source,
            sourceNpcs,
            source.movement()
        );

        SharedNpcWorldRelay.register(
            viewerWriter,
            world,
            viewer,
            viewerNpcs,
            viewer.movement()
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

            String spawn=
                sourceNpcs.spawnPet(
                    pet,
                    source.movement(),
                    sourceWriter
                );

            if(spawn==null||
               !spawn.startsWith(
                    "PET_SPAWN_OK"
               ))
                throw new AssertionError(
                    "source pet setup failed: "+
                    spawn
                );

            String nativeState=
                sourceNpcs.setPetNativeState(
                    1,
                    sourceWriter
                );

            if(nativeState==null||
               !nativeState.startsWith(
                    "PET_NATIVE_STATE_OK"
               ))
                throw new AssertionError(
                    "source native-state setup failed: "+
                    nativeState
                );

            MiniPetDefinitionRepository.Def mini=
                MiniPetDefinitionRepository.get(
                    22088
                );

            if(mini==null)
                throw new AssertionError(
                    "missing mini pet 22088"
                );

            String miniSpawn=
                sourceNpcs.spawnOrReplaceMiniPet(
                    mini,
                    source.movement(),
                    sourceWriter
                );

            if(miniSpawn==null||
               !miniSpawn.startsWith(
                    "MINIPET_SPAWN_OK"
               ))
                throw new AssertionError(
                    "source mini-pet setup failed: "+
                    miniSpawn
                );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            int playerIndex=
                Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    source
                );

            if(playerIndex<0)
                throw new AssertionError(
                    "viewer did not establish source visibility"
                );

            PacketSizes sizes=
                measureMainPacketSizes(
                    pet.npcId,
                    viewer.movement(),
                    32768+playerIndex
                );

            OutboundPacketQueue.BatchReservation
                pressure=
                    reserveFirstMaskFailure(
                        viewerQueue,
                        sizes
                    );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerQueue.queuedPackets()!=1)
                throw new AssertionError(
                    "failing sync should admit only main mirror add queued="+
                    viewerQueue.queuedPackets()
                );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "deferred main mask allowed later mini/canonical projection size="+
                    viewerNpcs.snapshot().size()
                );

            NpcEntity first=
                viewerNpcs.snapshot().get(0);
            int scene=
                first.sceneIndex;

            pressure.release();

            ByteArrayOutputStream actual=
                new ByteArrayOutputStream();

            viewerQueue.drainTo(
                actual,
                Integer.MAX_VALUE
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=2)
                throw new AssertionError(
                    "retry did not drain deferred main mask before resuming mini projection size="+
                    viewerNpcs.snapshot().size()
                );

            NpcEntity after=
                null;

            for(NpcEntity npc:
                    viewerNpcs.snapshot())
                if(npc.sceneIndex==scene){
                    after=npc;
                    break;
                }

            if(after==null)
                throw new AssertionError(
                    "mask retry changed or lost stable main mirror scene="+
                    scene
                );

            if(viewerQueue.queuedPackets()!=5)
                throw new AssertionError(
                    "retry packet count mismatch expected queued-main-mask/queued-native-mask/mini-add/mini-masks packets="+
                    viewerQueue.queuedPackets()
                );

            viewerQueue.drainTo(
                actual,
                Integer.MAX_VALUE
            );

            byte[] expected=
                cleanReferenceBytes(
                    pet.npcId,
                    sourceNpcs.pet(),
                    mini.npcId,
                    sourceNpcs.miniPet(),
                    viewer.movement(),
                    32768+playerIndex
                );

            if(!Arrays.equals(
                    actual.toByteArray(),
                    expected))
                throw new AssertionError(
                    "eventual retry bytes differ from clean same-seed projection actual="+
                    actual.size()+
                    " expected="+
                    expected.length
                );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=2)
                throw new AssertionError(
                    "steady-state sync changed main+mini mirror cardinality"
                );

            if(viewerQueue.queuedPackets()!=0)
                throw new AssertionError(
                    "completed deferred mask emitted again queued="+
                    viewerQueue.queuedPackets()
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

    private static OutboundPacketQueue.BatchReservation
        reserveFirstMaskFailure(
            OutboundPacketQueue queue,
            PacketSizes sizes
        )throws Exception
    {
        int reserve=
            QUEUE_CAPACITY-
            sizes.addBytes-
            sizes.interactionMaskBytes+
            1;

        if(reserve<0)
            throw new AssertionError(
                "fixture packets exceed queue capacity add="+
                sizes.addBytes+
                " mask="+
                sizes.interactionMaskBytes
            );

        return OutboundPacketQueue.reserveBatch(
            queue,
            reserve
        );
    }

    private static PacketSizes measureMainPacketSizes(
        int npcId,
        MovementState movement,
        int interactionTarget
    )throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );
        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        NpcEntity mirror=
            npcs.spawnMirroredNpc(
                npcId,
                movement.x(),
                movement.y(),
                null,
                movement,
                writer
            );

        int addBytes=
            queue.queuedBytes();

        npcs.sendMaskLocal(
            mirror,
            NpcSyncEncoder.Mask.interactionTarget(
                interactionTarget
            ),
            writer
        );

        return new PacketSizes(
            addBytes,
            queue.queuedBytes()-addBytes
        );
    }

    private static byte[] cleanReferenceBytes(
        int mainNpcId,
        NpcEntity sourceMain,
        int miniNpcId,
        NpcEntity sourceMini,
        MovementState movement,
        int playerTarget
    )throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );
        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        NpcEntity main=
            npcs.spawnMirroredNpc(
                mainNpcId,
                sourceMain.x,
                sourceMain.y,
                null,
                movement,
                writer
            );

        npcs.sendMaskLocal(
            main,
            NpcSyncEncoder.Mask.interactionTarget(
                playerTarget
            ),
            writer
        );

        npcs.sendMaskLocal(
            main,
            NpcSyncEncoder.Mask.forceText(
                "1"
            ),
            writer
        );

        NpcEntity mini=
            npcs.spawnMirroredNpc(
                miniNpcId,
                sourceMini.x,
                sourceMini.y,
                null,
                movement,
                writer
            );

        npcs.sendMaskLocal(
            mini,
            NpcSyncEncoder.Mask.interactionTarget(
                main.sceneIndex
            ),
            writer
        );

        npcs.sendMaskLocal(
            mini,
            NpcSyncEncoder.Mask.interactionTarget(
                main.sceneIndex
            ),
            writer
        );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        queue.drainTo(
            out,
            Integer.MAX_VALUE
        );

        return out.toByteArray();
    }

    private static final class PacketSizes {
        final int addBytes;
        final int interactionMaskBytes;

        PacketSizes(
            int addBytes,
            int interactionMaskBytes
        ){
            this.addBytes=addBytes;
            this.interactionMaskBytes=
                interactionMaskBytes;
        }
    }

    private RemotePetMirrorMaskRetryTest(){}
}
