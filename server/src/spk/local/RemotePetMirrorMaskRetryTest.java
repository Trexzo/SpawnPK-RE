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
        assertDeferredMasksPreserveFifoOrder();

        System.out.println(
            "REMOTE_PET_MIRROR_MASK_RETRY_PASS "+
            "addCommittedOnce=true "+
            "failedMaskDeferred=true "+
            "stableSceneOnRetry=true "+
            "noDuplicateMirror=true "+
            "deferredMaskRetried=true "+
            "laterMaskQueuedBehindFailure=true "+
            "deferredMaskFifo=true "+
            "retryCipherRewound=true "+
            "queueBackedRetry=true"
        );
    }

    private static void assertPostAddMaskFailureDoesNotDuplicateMirror()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "mirror-mask"
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

            fixture.spawnSourcePet(
                pet
            );

            int playerIndex=
                fixture.establishVisibility();

            PacketSizes sizes=
                measurePacketSizes(
                    pet.npcId,
                    fixture.viewer.movement(),
                    32768+playerIndex,
                    false
                );

            OutboundPacketQueue.BatchReservation
                pressure=
                    reserveFirstMaskFailure(
                        fixture.viewerQueue,
                        sizes
                    );

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerQueue.queuedPackets()!=1)
                throw new AssertionError(
                    "failing sync should queue only mirrored add packets="+
                    fixture.viewerQueue.queuedPackets()
                );

            if(fixture.viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "post-add mask failure did not leave exactly one authoritative mirror size="+
                    fixture.viewerNpcs.snapshot().size()
                );

            NpcEntity first=
                fixture.viewerNpcs.snapshot().get(0);
            int scene=
                first.sceneIndex;

            pressure.release();

            ByteArrayOutputStream actual=
                new ByteArrayOutputStream();

            fixture.viewerQueue.drainTo(
                actual,
                Integer.MAX_VALUE
            );

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "mask retry spawned duplicate mirror size="+
                    fixture.viewerNpcs.snapshot().size()
                );

            if(fixture.viewerNpcs.snapshot().get(0)
                    .sceneIndex!=scene)
                throw new AssertionError(
                    "mask retry changed mirror scene"
                );

            if(fixture.viewerQueue.queuedPackets()!=1)
                throw new AssertionError(
                    "deferred interaction mask was not retried exactly once queued="+
                    fixture.viewerQueue.queuedPackets()
                );

            fixture.viewerQueue.drainTo(
                actual,
                Integer.MAX_VALUE
            );

            byte[] expected=
                referenceMirrorBytes(
                    pet.npcId,
                    fixture.viewer.movement(),
                    32768+playerIndex,
                    false
                );

            if(!Arrays.equals(
                    actual.toByteArray(),
                    expected))
                throw new AssertionError(
                    "retried mirror wire bytes/cipher diverged from clean publication actual="+
                    actual.size()+
                    " expected="+
                    expected.length
                );

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerQueue.queuedPackets()!=0)
                throw new AssertionError(
                    "completed deferred mask emitted again"
                );
        }finally{
            fixture.close();
        }
    }

    private static void assertDeferredMasksPreserveFifoOrder()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "mirror-mask-fifo"
            );

        try{
            PetDefinitionRepository.Def base=
                PetDefinitionRepository.get(
                    24019
                );

            if(base==null)
                throw new AssertionError(
                    "missing pet 24019 template"
                );

            PetDefinitionRepository.Def nativePet=
                new PetDefinitionRepository.Def(
                    base.itemId,
                    6650,
                    base.itemName,
                    "FIFO native-state fixture",
                    base.standAnim,
                    base.walkAnim,
                    base.turn180Anim,
                    base.turn90CWAnim,
                    base.turn90CCWAnim,
                    base.size,
                    base.models,
                    "TEST_NATIVE_STATE_FIFO"
                );

            fixture.spawnSourcePet(
                nativePet
            );

            String nativeState=
                fixture.sourceNpcs.setPetNativeState(
                    2,
                    fixture.sourceWriter
                );

            if(nativeState==null||
               !nativeState.startsWith(
                    "PET_NATIVE_STATE_OK"
               ))
                throw new AssertionError(
                    "native-state fixture setup failed: "+
                    nativeState
                );

            int playerIndex=
                fixture.establishVisibility();

            PacketSizes sizes=
                measurePacketSizes(
                    nativePet.npcId,
                    fixture.viewer.movement(),
                    32768+playerIndex,
                    true
                );

            OutboundPacketQueue.BatchReservation
                pressure=
                    reserveFirstMaskFailure(
                        fixture.viewerQueue,
                        sizes
                    );

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            /*
             * Only the add may enter the queue. The first interaction mask
             * fails transactionally; the later native-state mask must enqueue
             * semantically behind it rather than touching transport.
             */
            if(fixture.viewerQueue.queuedPackets()!=1)
                throw new AssertionError(
                    "later mirror mask overtook deferred FIFO queuedPackets="+
                    fixture.viewerQueue.queuedPackets()
                );

            if(fixture.viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "FIFO failure did not preserve exactly one mirror size="+
                    fixture.viewerNpcs.snapshot().size()
                );

            int scene=
                fixture.viewerNpcs.snapshot().get(0)
                    .sceneIndex;

            pressure.release();

            ByteArrayOutputStream actual=
                new ByteArrayOutputStream();

            fixture.viewerQueue.drainTo(
                actual,
                Integer.MAX_VALUE
            );

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerQueue.queuedPackets()!=2)
                throw new AssertionError(
                    "retry did not drain both deferred masks FIFO packets="+
                    fixture.viewerQueue.queuedPackets()
                );

            fixture.viewerQueue.drainTo(
                actual,
                Integer.MAX_VALUE
            );

            if(fixture.viewerNpcs.snapshot().size()!=1||
               fixture.viewerNpcs.snapshot().get(0)
                    .sceneIndex!=scene)
                throw new AssertionError(
                    "FIFO retry changed authoritative mirror scene"
                );

            byte[] expected=
                referenceMirrorBytes(
                    nativePet.npcId,
                    fixture.viewer.movement(),
                    32768+playerIndex,
                    true
                );

            if(!Arrays.equals(
                    actual.toByteArray(),
                    expected))
                throw new AssertionError(
                    "FIFO retry wire order/cipher differs from clean publication actual="+
                    actual.size()+
                    " expected="+
                    expected.length
                );

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerQueue.queuedPackets()!=0)
                throw new AssertionError(
                    "drained FIFO emitted duplicate mask work"
                );
        }finally{
            fixture.close();
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

    private static PacketSizes measurePacketSizes(
        int npcId,
        MovementState movement,
        int interactionTarget,
        boolean nativeState
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

        int interactionBytes=
            queue.queuedBytes()-
            addBytes;

        if(nativeState)
            npcs.sendMaskLocal(
                mirror,
                NpcSyncEncoder.Mask.forceText(
                    "2"
                ),
                writer
            );

        return new PacketSizes(
            addBytes,
            interactionBytes
        );
    }

    private static byte[] referenceMirrorBytes(
        int npcId,
        MovementState movement,
        int interactionTarget,
        boolean nativeState
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

        npcs.sendMaskLocal(
            mirror,
            NpcSyncEncoder.Mask.interactionTarget(
                interactionTarget
            ),
            writer
        );

        if(nativeState)
            npcs.sendMaskLocal(
                mirror,
                NpcSyncEncoder.Mask.forceText(
                    "2"
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

    private static final class Fixture {
        final World world;
        final WorldPlayer source=
            new WorldPlayer();
        final WorldPlayer viewer=
            new WorldPlayer();
        final long sourceGeneration;
        final long viewerGeneration;
        final OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        final OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        final ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    SOURCE_SEED.clone()
                )
            );
        final ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );
        final Player81WorldSync.Context viewerSync;
        final NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        final NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        Fixture(
            String prefix
        )throws Exception{
            world=
                World.isolatedForTest(
                    600L
                );

            sourceGeneration=
                world.registerPlayer(
                    source,
                    prefix+"-source"
                );
            viewerGeneration=
                world.registerPlayer(
                    viewer,
                    prefix+"-viewer"
                );

            Player81WorldSync.register(
                sourceWriter,
                world,
                source,
                new DevAuthorityWorkbench()
            );

            viewerSync=
                Player81WorldSync.register(
                    viewerWriter,
                    world,
                    viewer,
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
        }

        void spawnSourcePet(
            PetDefinitionRepository.Def pet
        )throws Exception{
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
        }

        int establishVisibility()
            throws Exception
        {
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

            return playerIndex;
        }

        void close(){
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

    private RemotePetMirrorMaskRetryTest(){}
}
