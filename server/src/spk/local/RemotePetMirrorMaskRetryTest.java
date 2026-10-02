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
        assertDirectWriterIsNotRetryable();
        assertMirrorAddPublicationRetractable();
        assertMirrorMoveRemovePublicationRetractable();
        assertRelayRemovalTrackSurvivesFailure();

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
            "eventualWireMatchesCleanReference=true "+
            "directWriterNotRetryable=true "+
            "partialDirectProgressFailClosed=true "+
            "mirrorAddRetryCipherRewound=true "+
            "mirrorAddStateCommitAfterTransport=true "+
            "mirrorAddDirectFailClosed=true "+
            "mirrorMoveRetryCipherRewound=true "+
            "mirrorRemoveRetryCipherRewound=true "+
            "relayRemovalTrackRetryPreserved=true"
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

    private static void assertMirrorAddPublicationRetractable()
        throws Exception
    {
        WorldPlayer viewer=
            new WorldPlayer();
        MovementState movement=
            viewer.movement();

        PacketSizes sizes=
            measureMainPacketSizes(
                6650,
                movement,
                32768
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
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

        int reserve=
            QUEUE_CAPACITY-
            sizes.addBytes+
            1;

        OutboundPacketQueue.BatchReservation pressure=
            OutboundPacketQueue.reserveBatch(
                queue,
                reserve
            );

        boolean rejected=false;

        try{
            npcs.spawnMirroredNpc(
                6650,
                movement.x(),
                movement.y(),
                null,
                movement,
                writer
            );
        }catch(ServerPacketWriter.RecoverablePublicationException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                "mirror add capacity rejection was not classified recoverable"
            );

        if(queue.queuedBytes()!=0||
           queue.queuedPackets()!=0)
            throw new AssertionError(
                "failed mirror add emitted bytes queued="+
                queue.queuedBytes()
            );

        if(!npcs.snapshot().isEmpty())
            throw new AssertionError(
                "failed mirror add committed viewer NPC state size="+
                npcs.snapshot().size()
            );

        pressure.release();

        NpcEntity retry=
            npcs.spawnMirroredNpc(
                6650,
                movement.x(),
                movement.y(),
                null,
                movement,
                writer
            );

        if(retry==null||
           npcs.snapshot().size()!=1||
           npcs.snapshot().get(0)!=retry)
            throw new AssertionError(
                "mirror add retry did not commit exactly one NPC"
            );

        ByteArrayOutputStream actual=
            new ByteArrayOutputStream();
        queue.drainTo(
            actual,
            Integer.MAX_VALUE
        );

        OutboundPacketQueue cleanQueue=
            new OutboundPacketQueue();
        ServerPacketWriter cleanWriter=
            new ServerPacketWriter(
                cleanQueue,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );
        NpcRegistry cleanNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        cleanNpcs.spawnMirroredNpc(
            6650,
            movement.x(),
            movement.y(),
            null,
            movement,
            cleanWriter
        );

        ByteArrayOutputStream expected=
            new ByteArrayOutputStream();
        cleanQueue.drainTo(
            expected,
            Integer.MAX_VALUE
        );

        if(!Arrays.equals(
                actual.toByteArray(),
                expected.toByteArray()))
            throw new AssertionError(
                "mirror add retry bytes differ from clean same-seed add actual="+
                actual.size()+
                " expected="+
                expected.size()
            );

        PrefixThenFailOutputStream directOut=
            new PrefixThenFailOutputStream();
        ServerPacketWriter directWriter=
            new ServerPacketWriter(
                directOut,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );
        NpcRegistry directNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        boolean directRejected=false;

        try{
            directNpcs.spawnMirroredNpc(
                6650,
                movement.x(),
                movement.y(),
                null,
                movement,
                directWriter
            );
        }catch(ServerPacketWriter.NonRetractablePublicationException expectedFailure){
            directRejected=true;
        }

        if(!directRejected||
           directOut.bytes.size()!=1||
           !directNpcs.snapshot().isEmpty())
            throw new AssertionError(
                "partial direct mirror add was not failed closed bytes="+
                directOut.bytes.size()+
                " mirrors="+
                directNpcs.snapshot().size()
            );
    }

    private static void assertMirrorMoveRemovePublicationRetractable()
        throws Exception
    {
        WorldPlayer viewer=
            new WorldPlayer();
        MovementState movement=
            viewer.movement();

        OutboundPacketQueue retryQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        OutboundPacketQueue cleanQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );

        ServerPacketWriter retryWriter=
            new ServerPacketWriter(
                retryQueue,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );
        ServerPacketWriter cleanWriter=
            new ServerPacketWriter(
                cleanQueue,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );

        NpcRegistry retryNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry cleanNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        NpcEntity retryNpc=
            retryNpcs.spawnMirroredNpc(
                6650,
                movement.x(),
                movement.y(),
                null,
                movement,
                retryWriter
            );
        NpcEntity cleanNpc=
            cleanNpcs.spawnMirroredNpc(
                6650,
                movement.x(),
                movement.y(),
                null,
                movement,
                cleanWriter
            );

        ByteArrayOutputStream retryInitial=
            new ByteArrayOutputStream();
        ByteArrayOutputStream cleanInitial=
            new ByteArrayOutputStream();

        retryQueue.drainTo(
            retryInitial,
            Integer.MAX_VALUE
        );
        cleanQueue.drainTo(
            cleanInitial,
            Integer.MAX_VALUE
        );

        if(!Arrays.equals(
                retryInitial.toByteArray(),
                cleanInitial.toByteArray()))
            throw new AssertionError(
                "movement fixture initial add mismatch"
            );

        int nextX=
            movement.x()+1;
        int nextY=
            movement.y();
        int direction=
            MovementState.direction(
                movement.x(),
                movement.y(),
                nextX,
                nextY
            );

        OutboundPacketQueue.BatchReservation movePressure=
            OutboundPacketQueue.reserveBatch(
                retryQueue,
                QUEUE_CAPACITY
            );

        boolean moveRejected=false;

        try{
            retryNpcs.moveMirroredNpcRetractable(
                retryNpc,
                direction,
                -1,
                nextX,
                nextY,
                retryWriter
            );
        }catch(ServerPacketWriter.RecoverablePublicationException expected){
            moveRejected=true;
        }

        if(!moveRejected||
           retryNpc.x!=movement.x()||
           retryNpc.y!=movement.y()||
           retryQueue.queuedBytes()!=0)
            throw new AssertionError(
                "failed mirror movement committed state/bytes"
            );

        movePressure.release();

        retryNpcs.moveMirroredNpcRetractable(
            retryNpc,
            direction,
            -1,
            nextX,
            nextY,
            retryWriter
        );
        cleanNpcs.moveMirroredNpcRetractable(
            cleanNpc,
            direction,
            -1,
            nextX,
            nextY,
            cleanWriter
        );

        ByteArrayOutputStream retryMove=
            new ByteArrayOutputStream();
        ByteArrayOutputStream cleanMove=
            new ByteArrayOutputStream();

        retryQueue.drainTo(
            retryMove,
            Integer.MAX_VALUE
        );
        cleanQueue.drainTo(
            cleanMove,
            Integer.MAX_VALUE
        );

        if(!Arrays.equals(
                retryMove.toByteArray(),
                cleanMove.toByteArray())||
           retryNpc.x!=nextX||
           retryNpc.y!=nextY)
            throw new AssertionError(
                "mirror movement retry diverged from clean same-seed publication"
            );

        OutboundPacketQueue.BatchReservation removePressure=
            OutboundPacketQueue.reserveBatch(
                retryQueue,
                QUEUE_CAPACITY
            );

        boolean removeRejected=false;

        try{
            retryNpcs.removeMirroredNpcRetractable(
                retryNpc.sceneIndex,
                retryWriter
            );
        }catch(ServerPacketWriter.RecoverablePublicationException expected){
            removeRejected=true;
        }

        if(!removeRejected||
           retryNpcs.snapshot().size()!=1||
           retryNpcs.snapshot().get(0)!=retryNpc||
           retryQueue.queuedBytes()!=0)
            throw new AssertionError(
                "failed mirror removal committed state/bytes"
            );

        removePressure.release();

        retryNpcs.removeMirroredNpcRetractable(
            retryNpc.sceneIndex,
            retryWriter
        );
        cleanNpcs.removeMirroredNpcRetractable(
            cleanNpc.sceneIndex,
            cleanWriter
        );

        ByteArrayOutputStream retryRemove=
            new ByteArrayOutputStream();
        ByteArrayOutputStream cleanRemove=
            new ByteArrayOutputStream();

        retryQueue.drainTo(
            retryRemove,
            Integer.MAX_VALUE
        );
        cleanQueue.drainTo(
            cleanRemove,
            Integer.MAX_VALUE
        );

        if(!Arrays.equals(
                retryRemove.toByteArray(),
                cleanRemove.toByteArray())||
           !retryNpcs.snapshot().isEmpty())
            throw new AssertionError(
                "mirror removal retry diverged from clean same-seed publication"
            );
    }

    private static void assertRelayRemovalTrackSurvivesFailure()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                608L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "mirror-remove-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "mirror-remove-viewer"
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

            sourceNpcs.spawnPet(
                pet,
                source.movement(),
                sourceWriter
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
                    "removal fixture did not establish source visibility"
                );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "removal fixture did not create one mirror"
                );

            ByteArrayOutputStream initial=
                new ByteArrayOutputStream();
            viewerQueue.drainTo(
                initial,
                Integer.MAX_VALUE
            );

            SharedNpcWorldRelay.unregister(
                sourceWriter
            );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    viewerQueue,
                    QUEUE_CAPACITY
                );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1||
               viewerQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "failed relay removal lost mirror state or emitted bytes"
                );

            pressure.release();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(!viewerNpcs.snapshot().isEmpty()||
               viewerQueue.queuedPackets()!=1)
                throw new AssertionError(
                    "relay removal retry did not retire stale mirror packets="+
                    viewerQueue.queuedPackets()+
                    " mirrors="+
                    viewerNpcs.snapshot().size()
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

    private static void assertDirectWriterIsNotRetryable()
        throws Exception
    {
        PrefixThenFailOutputStream out=
            new PrefixThenFailOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );
        final boolean[] entered={false};

        boolean classified=false;

        try{
            writer.publishRecoverablePacket(
                ()->{
                    entered[0]=true;
                    writer.varShort(
                        65,
                        new byte[]{1,2,3}
                    );
                }
            );
        }catch(ServerPacketWriter.NonRetractablePublicationException expected){
            classified=true;
        }

        if(!classified)
            throw new AssertionError(
                "partial direct writer failure was classified retryable"
            );

        if(!entered[0])
            throw new AssertionError(
                "direct publication callback was not exercised"
            );

        if(out.bytes.size()!=1)
            throw new AssertionError(
                "partial direct fixture did not emit exactly one prefix byte="+
                out.bytes.size()
            );
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

    private static final class PrefixThenFailOutputStream
        extends java.io.OutputStream
    {
        final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();

        @Override public void write(int value)
            throws java.io.IOException
        {
            bytes.write(value);
            throw new java.io.IOException(
                "EXPECTED_PARTIAL_DIRECT_FAILURE"
            );
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws java.io.IOException
        {
            if(length>0)
                bytes.write(data[offset]);

            throw new java.io.IOException(
                "EXPECTED_PARTIAL_DIRECT_FAILURE"
            );
        }
    }

    private RemotePetMirrorMaskRetryTest(){}
}
