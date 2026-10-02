package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemotePetMirrorMaskRetryTest {
    private static final int QUEUE_CAPACITY=1024;
    private static final int[] SOURCE_SEED=
        new int[]{701,702,703,704};
    private static final int[] VIEWER_SEED=
        new int[]{705,706,707,708};

    public static void main(String[] args)throws Exception{
        assertPostAddMaskFailureDoesNotDuplicateMirror();
        assertDeferredMasksPreserveFifoOrder();
        assertDeferredMaskBlocksLaterProjection();
        assertDirectTransportFailureFailsClosed();
        assertTerminalQueueIsNotRetryable();
        assertMirrorAddMoveRemoveRetractable();
        assertRelayRemovalTrackSurvivesRetraction();

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
            "queueBackedRetry=true "+
            "repeatedRetryRetracted=true "+
            "deferredMaskBlocksLaterProjection=true "+
            "nativeMaskPreserved=true "+
            "miniProjectionWaitsForDrain=true "+
            "directTransportFailClosed=true "+
            "terminalMirrorRuntimeRetired=true "+
            "terminalMirrorWriterLatched=true "+
            "terminalMirrorSentinelRetained=true "+
            "terminalQueueNotRetryable=true "+
            "mirrorAddRetryRetracted=true "+
            "mirrorMoveRetryRetracted=true "+
            "mirrorRemoveRetryRetracted=true "+
            "relayRemovalTrackRetryPreserved=true"
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

            /*
             * Retry once while the same reservation still blocks transport.
             * The FIFO head must remain pending and the writer must rewind
             * ISAAC/pending state again without emitting another packet.
             */
            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerQueue.queuedPackets()!=1||
               fixture.viewerNpcs.snapshot().size()!=1||
               fixture.viewerNpcs.snapshot().get(0).sceneIndex!=scene)
                throw new AssertionError(
                    "second retractable retry changed transport or mirror state packets="+
                    fixture.viewerQueue.queuedPackets()+
                    " size="+
                    fixture.viewerNpcs.snapshot().size()
                );

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

    private static void assertDeferredMaskBlocksLaterProjection()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "mirror-projection-barrier"
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

            String nativeState=
                fixture.sourceNpcs.setPetNativeState(
                    1,
                    fixture.sourceWriter
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
                fixture.sourceNpcs.spawnOrReplaceMiniPet(
                    mini,
                    fixture.source.movement(),
                    fixture.sourceWriter
                );

            if(miniSpawn==null||
               !miniSpawn.startsWith(
                    "MINIPET_SPAWN_OK"
               ))
                throw new AssertionError(
                    "source mini-pet setup failed: "+
                    miniSpawn
                );

            int playerIndex=
                fixture.establishVisibility();

            PacketSizes sizes=
                measurePacketSizes(
                    pet.npcId,
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

            if(fixture.viewerQueue.queuedPackets()!=1)
                throw new AssertionError(
                    "deferred main mask allowed later packet65 projection queued="+
                    fixture.viewerQueue.queuedPackets()
                );

            if(fixture.viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "deferred main mask allowed mini projection size="+
                    fixture.viewerNpcs.snapshot().size()
                );

            int mainScene=
                fixture.viewerNpcs.snapshot().get(0)
                    .sceneIndex;

            pressure.release();

            ByteArrayOutputStream drained=
                new ByteArrayOutputStream();

            fixture.viewerQueue.drainTo(
                drained,
                Integer.MAX_VALUE
            );

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerNpcs.snapshot().size()!=2)
                throw new AssertionError(
                    "projection did not resume with main+mini after FIFO drain size="+
                    fixture.viewerNpcs.snapshot().size()
                );

            boolean retainedMain=false;

            for(NpcEntity npc:
                    fixture.viewerNpcs.snapshot())
                if(npc.sceneIndex==mainScene)
                    retainedMain=true;

            if(!retainedMain)
                throw new AssertionError(
                    "projection barrier changed authoritative main mirror scene"
                );

            if(fixture.viewerQueue.queuedPackets()!=5)
                throw new AssertionError(
                    "ordered retry did not preserve main interaction/native then mini projection packets="+
                    fixture.viewerQueue.queuedPackets()
                );

            fixture.viewerQueue.drainTo(
                drained,
                Integer.MAX_VALUE
            );

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerQueue.queuedPackets()!=0||
               fixture.viewerNpcs.snapshot().size()!=2)
                throw new AssertionError(
                    "steady projection repeated work after FIFO drain packets="+
                    fixture.viewerQueue.queuedPackets()+
                    " size="+
                    fixture.viewerNpcs.snapshot().size()
                );
        }finally{
            fixture.close();
        }
    }

    private static void assertDirectTransportFailureFailsClosed()
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
                "mirror-direct-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "mirror-direct-viewer"
            );

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(
                    SOURCE_SEED.clone()
                )
            );

        FailNthWriteOutputStream viewerOut=
            new FailNthWriteOutputStream(
                2
            );

        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerOut,
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

        TradeService.register(
            world,
            source,
            sourceGeneration,
            source.bank(),
            sourceWriter,
            ()->{}
        );
        TradeService.register(
            world,
            viewer,
            viewerGeneration,
            viewer.bank(),
            viewerWriter,
            ()->{}
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
                    "direct fixture pet setup failed: "+
                    spawn
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
                    "direct viewer did not establish source visibility"
                );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerOut.attempts()!=2||
               viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "direct failure fixture did not stop after add+failed mask attempts="+
                    viewerOut.attempts()+
                    " size="+
                    viewerNpcs.snapshot().size()
                );

            if(!viewerWriter.terminal())
                throw new AssertionError(
                    "terminal mirror failure did not latch exact writer"
                );

            if(Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    source
                )!=-1)
                throw new AssertionError(
                    "terminal mirror failure retained Player81 authority"
                );

            String terminalTradeStart=
                TradeService.start(
                    world,
                    source,
                    viewer
                );

            if(terminalTradeStart==null||
               !terminalTradeStart.contains(
                    "TRADE_UI_REJECTED_CONTEXT_MISSING"
                ))
                throw new AssertionError(
                    "terminal mirror failure retained Trade context authority result="+
                    terminalTradeStart
                );

            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            boolean sentinelRetained=false;

            try{
                SharedNpcWorldRelay.preflightRegistration(
                    viewerWriter,
                    world,
                    viewer
                );
            }catch(SharedNpcWorldRelay.TerminalRegistrationException expected){
                sentinelRetained=true;
            }

            if(!sentinelRetained)
                throw new AssertionError(
                    "terminal mirror SharedNpc sentinel was resurrectable"
                );

            viewerOut.disableFailure();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerOut.attempts()!=2||
               viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "non-retractable direct failure created retry debt or duplicate mirror attempts="+
                    viewerOut.attempts()+
                    " size="+
                    viewerNpcs.snapshot().size()
                );
        }finally{
            TradeService.unregister(
                source,
                sourceWriter
            );
            TradeService.unregister(
                viewer,
                viewerWriter
            );
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

    private static void assertMirrorAddMoveRemoveRetractable()
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

        OutboundPacketQueue.BatchReservation addPressure=
            OutboundPacketQueue.reserveBatch(
                retryQueue,
                QUEUE_CAPACITY
            );

        NpcEntity rejectedAdd=
            retryNpcs.spawnMirroredNpc(
                6650,
                movement.x(),
                movement.y(),
                null,
                movement,
                retryWriter
            );

        if(rejectedAdd!=null||
           retryQueue.queuedBytes()!=0||
           !retryNpcs.snapshot().isEmpty())
            throw new AssertionError(
                "retracted mirror add committed bytes/state"
            );

        addPressure.release();

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

        ByteArrayOutputStream retryAdd=
            new ByteArrayOutputStream();
        ByteArrayOutputStream cleanAdd=
            new ByteArrayOutputStream();

        retryQueue.drainTo(
            retryAdd,
            Integer.MAX_VALUE
        );
        cleanQueue.drainTo(
            cleanAdd,
            Integer.MAX_VALUE
        );

        if(retryNpc==null||
           cleanNpc==null||
           !Arrays.equals(
                retryAdd.toByteArray(),
                cleanAdd.toByteArray()))
            throw new AssertionError(
                "mirror add retry diverged from clean same-seed publication"
            );

        int nextX=movement.x()+1;
        int nextY=movement.y();
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

        ServerPacketWriter.RecoverablePacketResult moveResult=
            retryNpcs.moveMirroredNpcRetractable(
                retryNpc,
                direction,
                -1,
                nextX,
                nextY,
                retryWriter
            );

        if(moveResult!=
                ServerPacketWriter
                    .RecoverablePacketResult
                    .RETRACTED_RETRYABLE||
           retryNpc.x!=movement.x()||
           retryNpc.y!=movement.y()||
           retryQueue.queuedBytes()!=0)
            throw new AssertionError(
                "retracted mirror movement committed state/bytes"
            );

        movePressure.release();

        if(retryNpcs.moveMirroredNpcRetractable(
                retryNpc,
                direction,
                -1,
                nextX,
                nextY,
                retryWriter
            )!=
                ServerPacketWriter
                    .RecoverablePacketResult
                    .COMMITTED||
           cleanNpcs.moveMirroredNpcRetractable(
                cleanNpc,
                direction,
                -1,
                nextX,
                nextY,
                cleanWriter
            )!=
                ServerPacketWriter
                    .RecoverablePacketResult
                    .COMMITTED)
            throw new AssertionError(
                "mirror movement retry did not commit"
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

        ServerPacketWriter.RecoverablePacketResult removeResult=
            retryNpcs.removeMirroredNpcRetractable(
                retryNpc.sceneIndex,
                retryWriter
            );

        if(removeResult!=
                ServerPacketWriter
                    .RecoverablePacketResult
                    .RETRACTED_RETRYABLE||
           retryNpcs.snapshot().size()!=1||
           retryQueue.queuedBytes()!=0)
            throw new AssertionError(
                "retracted mirror removal committed state/bytes"
            );

        removePressure.release();

        if(retryNpcs.removeMirroredNpcRetractable(
                retryNpc.sceneIndex,
                retryWriter
            )!=
                ServerPacketWriter
                    .RecoverablePacketResult
                    .COMMITTED||
           cleanNpcs.removeMirroredNpcRetractable(
                cleanNpc.sceneIndex,
                cleanWriter
            )!=
                ServerPacketWriter
                    .RecoverablePacketResult
                    .COMMITTED)
            throw new AssertionError(
                "mirror removal retry did not commit"
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

    private static void assertRelayRemovalTrackSurvivesRetraction()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "mirror-remove-retry"
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
            fixture.establishVisibility();

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "removal retry fixture did not create one mirror"
                );

            fixture.viewerQueue.drainTo(
                new ByteArrayOutputStream(),
                Integer.MAX_VALUE
            );

            SharedNpcWorldRelay.unregister(
                fixture.sourceWriter
            );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    fixture.viewerQueue,
                    QUEUE_CAPACITY
                );

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(fixture.viewerNpcs.snapshot().size()!=1||
               fixture.viewerQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "retracted relay removal lost mirror state or emitted bytes"
                );

            pressure.release();

            SharedNpcWorldRelay.syncRemotePets(
                fixture.viewerWriter
            );

            if(!fixture.viewerNpcs.snapshot().isEmpty()||
               fixture.viewerQueue.queuedPackets()!=1)
                throw new AssertionError(
                    "relay removal retry did not retire stale mirror exactly once"
                );
        }finally{
            fixture.close();
        }
    }

    private static void assertTerminalQueueIsNotRetryable()
        throws Exception
    {
        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );

        boolean poisoned=false;

        try{
            queue.offer(
                new byte[QUEUE_CAPACITY+1]
            );
        }catch(IOException expected){
            poisoned=true;
        }

        if(!poisoned||!queue.overflowed())
            throw new AssertionError(
                "terminal queue fixture did not poison queue"
            );

        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    VIEWER_SEED.clone()
                )
            );

        boolean terminal=false;

        try{
            writer.publishRecoverablePacket(
                ()->writer.varShort(
                    65,
                    new byte[]{1,2,3}
                )
            );
        }catch(IOException expected){
            terminal=true;
        }

        if(!terminal)
            throw new AssertionError(
                "already-overflowed queue was classified retractable"
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

    private static final class FailNthWriteOutputStream
        extends OutputStream {

        private final ByteArrayOutputStream delegate=
            new ByteArrayOutputStream();
        private final int failAt;
        private int attempts;
        private final AtomicBoolean failureEnabled=
            new AtomicBoolean(true);

        FailNthWriteOutputStream(
            int failAt
        ){
            this.failAt=failAt;
        }

        synchronized int attempts(){
            return attempts;
        }

        void disableFailure(){
            failureEnabled.set(false);
        }

        @Override public void write(
            int value
        )throws IOException{
            write(
                new byte[]{(byte)value},
                0,
                1
            );
        }

        @Override public synchronized void write(
            byte[] bytes,
            int offset,
            int length
        )throws IOException{
            attempts++;

            if(failureEnabled.get()&&
               attempts==failAt)
                throw new IOException(
                    "EXPECTED_NON_RETRACTABLE_DIRECT_FAILURE"
                );

            delegate.write(
                bytes,
                offset,
                length
            );
        }
    }

    private RemotePetMirrorMaskRetryTest(){}
}
