package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Map;

public final class Player81BatchAbortAtomicityTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        world.registerPlayer(
            source,
            "player81-batch-source"
        );
        world.registerPlayer(
            viewer,
            "player81-batch-viewer"
        );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue();
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    new int[]{5,6,7,8}
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
            new MovementState();
        MovementState viewerMovement=
            new MovementState();

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
            NpcEntity[] sharedHome=
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
                sharedHome[0];

            sourceWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
            viewerWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            sourceWriter.varShort(
                81,
                CombatSync.player81AnimationAndInteraction(
                    15552,
                    sourceTarget.sceneIndex
                )
            );

            int packetsBeforeMask=
                viewerQueue.queuedPackets();

            sourceNpcs.sendMask(
                sourceTarget,
                NpcSyncEncoder.Mask.singleHit(
                    100,
                    6,
                    255,
                    255
                ),
                sourceWriter
            );

            if(viewerQueue.queuedPackets()!=
                    packetsBeforeMask)
                throw new AssertionError(
                    "NPC mask overtook source Player81 barrier"
                );

            long sequenceBeforeAbort=
                sequence(world);
            int packetsBeforeAbort=
                viewerQueue.queuedPackets();

            viewerWriter.beginBatch();
            viewerWriter.varShort(
                81,
                BootstrapPackets.player81WalkStep(4)
            );

            if(sequence(world)!=
                    sequenceBeforeAbort)
                throw new AssertionError(
                    "staged Player81 mutated shared sequence before commit"
                );
            if(viewerQueue.queuedPackets()!=
                    packetsBeforeAbort)
                throw new AssertionError(
                    "staged Player81 emitted before commit"
                );

            viewerWriter.abortBatch();

            if(sequence(world)!=
                    sequenceBeforeAbort)
                throw new AssertionError(
                    "aborted Player81 retained shared publication state"
                );
            if(viewerQueue.queuedPackets()!=
                    packetsBeforeAbort)
                throw new AssertionError(
                    "aborted Player81 leaked transport bytes"
                );

            viewerWriter.beginBatch();
            viewerWriter.varShort(
                81,
                BootstrapPackets.player81WalkStep(4)
            );

            if(sequence(world)!=
                    sequenceBeforeAbort)
                throw new AssertionError(
                    "retry staged Player81 committed before batch admission"
                );

            viewerWriter.endBatch();

            if(sequence(world)<=
                    sequenceBeforeAbort)
                throw new AssertionError(
                    "successful Player81 batch did not commit publication state"
                );

            int packetDelta=
                viewerQueue.queuedPackets()-
                packetsBeforeAbort;

            if(packetDelta<2)
                throw new AssertionError(
                    "pending NPC mask barrier was lost across abort packetDelta="+
                    packetDelta
                );

            testOwnershipCommitBarrier();
            testStalePreparationFailClosed();
            testUnbatchedPublicationAtomicity();

            System.out.println(
                "PLAYER81_BATCH_ABORT_ATOMICITY_PASS "+
                "precommitStateStable=true "+
                "abortStateStable=true "+
                "zeroAbortBytes=true "+
                "retryCommittedOnce=true "+
                "relayBarrierRetained=true "+
                "transportSemanticOwnershipAtomic=true "+
                "stalePrepareFailClosed=true "+
                "noContextLocalOnlyPreserved=true "+
                "unbatchedQueueFailureAtomic=true "+
                "unbatchedRetryCommitsOnce=true "+
                "unbatchedDirectOutputFailClosed=true"
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
                    source
                );
            if(viewer.registered())
                world.unregisterPlayer(
                    viewer
                );

            world.close();
        }
    }

    private static void testUnbatchedPublicationAtomicity()
        throws Exception
    {
        World world=
            World.isolatedForTest(604L);
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "player81-unbatched"
        );

        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{25,26,27,28}
                )
            );

        Player81WorldSync.register(
            writer,
            world,
            player,
            new DevAuthorityWorkbench()
        );

        try{
            queue.offer(
                new byte[1024]
            );

            long sequenceBefore=
                sequence(world);

            boolean failed=false;
            try{
                writer.varShort(
                    81,
                    BootstrapPackets.player81WalkStep(4)
                );
            }catch(java.io.IOException expected){
                failed=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "reservation unavailable"
                    );
            }

            if(!failed)
                throw new AssertionError(
                    "full queue did not reject unbatched packet81"
                );

            if(queue.queuedBytes()!=1024)
                throw new AssertionError(
                    "failed unbatched packet81 changed queue bytes"
                );

            if(sequence(world)!=
                    sequenceBefore)
                throw new AssertionError(
                    "failed unbatched packet81 committed semantic sequence"
                );

            drain(queue);

            writer.varShort(
                81,
                BootstrapPackets.player81WalkStep(4)
            );

            if(queue.queuedBytes()==0)
                throw new AssertionError(
                    "unbatched packet81 retry emitted no bytes"
                );

            if(sequence(world)<=
                    sequenceBefore)
                throw new AssertionError(
                    "unbatched packet81 retry did not commit semantic state"
                );
        }finally{
            Player81WorldSync.unregister(
                writer
            );
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }

        World directWorld=
            World.isolatedForTest(605L);
        WorldPlayer directPlayer=
            new WorldPlayer();
        directWorld.registerPlayer(
            directPlayer,
            "player81-direct-output"
        );

        ByteArrayOutputStream directBytes=
            new ByteArrayOutputStream();
        ServerPacketWriter directWriter=
            new ServerPacketWriter(
                directBytes,
                new IsaacCipher(
                    new int[]{29,30,31,32}
                )
            );

        Player81WorldSync.register(
            directWriter,
            directWorld,
            directPlayer,
            new DevAuthorityWorkbench()
        );

        try{
            long sequenceBefore=
                sequence(directWorld);

            boolean rejected=false;
            try{
                directWriter.varShort(
                    81,
                    BootstrapPackets.player81WalkStep(4)
                );
            }catch(java.io.IOException expected){
                rejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "requires queue-backed writer"
                    );
            }

            if(!rejected||
               directBytes.size()!=0||
               sequence(directWorld)!=sequenceBefore)
                throw new AssertionError(
                    "active-context direct OutputStream packet81 did not fail closed"
                );
        }finally{
            Player81WorldSync.unregister(
                directWriter
            );
            if(directPlayer.registered())
                directWorld.unregisterPlayer(
                    directPlayer
                );
            directWorld.close();
        }

        OutboundPacketQueue localQueue=
            new OutboundPacketQueue();
        ServerPacketWriter localWriter=
            new ServerPacketWriter(
                localQueue,
                new IsaacCipher(
                    new int[]{33,34,35,36}
                )
            );

        localWriter.varShort(
            81,
            BootstrapPackets.player81Idle()
        );

        if(localQueue.queuedBytes()==0)
            throw new AssertionError(
                "unregistered unbatched local-only packet81 compatibility was lost"
            );
    }

    private static void testStalePreparationFailClosed()
        throws Exception
    {
        World staleWorld=
            World.isolatedForTest(603L);
        WorldPlayer stalePlayer=
            new WorldPlayer();

        staleWorld.registerPlayer(
            stalePlayer,
            "player81-stale-prepare"
        );

        OutboundPacketQueue staleQueue=
            new OutboundPacketQueue();
        ServerPacketWriter staleWriter=
            new ServerPacketWriter(
                staleQueue,
                new IsaacCipher(
                    new int[]{17,18,19,20}
                )
            );

        Player81WorldSync.register(
            staleWriter,
            staleWorld,
            stalePlayer,
            new DevAuthorityWorkbench()
        );

        try{
            staleWriter.beginBatch();

            if(!staleWorld.unregisterPlayer(
                    stalePlayer))
                throw new AssertionError(
                    "failed to invalidate owner before Player81 preparation"
                );

            boolean rejected=false;
            try{
                staleWriter.varShort(
                    81,
                    BootstrapPackets.player81Idle()
                );
            }catch(java.io.IOException expected){
                rejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "semantic preparation rejected stale owner"
                    );
            }

            if(!rejected)
                throw new AssertionError(
                    "stale Player81 preparation did not fail closed"
                );

            if(staleQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "stale Player81 preparation emitted bytes"
                );

            staleWriter.abortBatch();

            if(staleQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "stale Player81 abort leaked bytes"
                );
        }finally{
            Player81WorldSync.unregister(
                staleWriter
            );
            if(stalePlayer.registered())
                staleWorld.unregisterPlayer(
                    stalePlayer
                );
            staleWorld.close();
        }

        OutboundPacketQueue localQueue=
            new OutboundPacketQueue();
        ServerPacketWriter localWriter=
            new ServerPacketWriter(
                localQueue,
                new IsaacCipher(
                    new int[]{21,22,23,24}
                )
            );

        localWriter.beginBatch();
        localWriter.varShort(
            81,
            BootstrapPackets.player81Idle()
        );
        localWriter.endBatch();

        if(localQueue.queuedBytes()==0)
            throw new AssertionError(
                "unregistered local-only packet81 compatibility was lost"
            );
    }

    private static void testOwnershipCommitBarrier()
        throws Exception
    {
        World staleWorld=
            World.isolatedForTest(601L);
        WorldPlayer stalePlayer=
            new WorldPlayer();

        staleWorld.registerPlayer(
            stalePlayer,
            "player81-stale-owner"
        );

        OutboundPacketQueue staleQueue=
            new OutboundPacketQueue();
        ServerPacketWriter staleWriter=
            new ServerPacketWriter(
                staleQueue,
                new IsaacCipher(
                    new int[]{9,10,11,12}
                )
            );

        Player81WorldSync.register(
            staleWriter,
            staleWorld,
            stalePlayer,
            new DevAuthorityWorkbench()
        );

        try{
            long sequenceBefore=
                sequence(staleWorld);

            staleWriter.beginBatch();
            staleWriter.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            Player81WorldSync.PreparedBatch prepared=
                preparedBatch(staleWriter);

            if(prepared==null||
               prepared.completed)
                throw new AssertionError(
                    "stale-owner fixture did not stage Player81"
                );

            if(!staleWorld.unregisterPlayer(
                    stalePlayer))
                throw new AssertionError(
                    "failed to invalidate staged Player81 owner"
                );

            boolean staleRejected=false;
            try{
                staleWriter.endBatch();
            }catch(java.io.IOException expected){
                staleRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "owner stale"
                    );
            }

            if(!staleRejected)
                throw new AssertionError(
                    "stale staged Player81 commit was not rejected"
                );

            if(staleQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "stale staged Player81 admitted transport bytes"
                );

            if(prepared.completed)
                throw new AssertionError(
                    "stale staged Player81 committed semantic state"
                );

            if(sequence(staleWorld)!=
                    sequenceBefore)
                throw new AssertionError(
                    "stale staged Player81 advanced shared sequence"
                );

            staleWriter.abortBatch();
        }finally{
            Player81WorldSync.unregister(
                staleWriter
            );
            if(stalePlayer.registered())
                staleWorld.unregisterPlayer(
                    stalePlayer
                );
            staleWorld.close();
        }

        World freshWorld=
            World.isolatedForTest(602L);
        WorldPlayer freshPlayer=
            new WorldPlayer();
        freshWorld.registerPlayer(
            freshPlayer,
            "player81-fresh-owner"
        );

        OutboundPacketQueue freshQueue=
            new OutboundPacketQueue();
        ServerPacketWriter freshWriter=
            new ServerPacketWriter(
                freshQueue,
                new IsaacCipher(
                    new int[]{13,14,15,16}
                )
            );

        Player81WorldSync.register(
            freshWriter,
            freshWorld,
            freshPlayer,
            new DevAuthorityWorkbench()
        );

        try{
            long sequenceBefore=
                sequence(freshWorld);

            freshWriter.beginBatch();
            freshWriter.varShort(
                81,
                BootstrapPackets.player81WalkStep(4)
            );

            Player81WorldSync.PreparedBatch prepared=
                preparedBatch(freshWriter);

            freshWriter.endBatch();

            if(freshQueue.queuedBytes()==0)
                throw new AssertionError(
                    "fresh joint commit admitted no transport bytes"
                );

            if(prepared==null||
               !prepared.completed)
                throw new AssertionError(
                    "fresh joint commit did not commit semantic state"
                );

            if(sequence(freshWorld)<=
                    sequenceBefore)
                throw new AssertionError(
                    "fresh joint commit did not advance shared sequence"
                );
        }finally{
            Player81WorldSync.unregister(
                freshWriter
            );
            if(freshPlayer.registered())
                freshWorld.unregisterPlayer(
                    freshPlayer
                );
            freshWorld.close();
        }
    }

    private static Player81WorldSync.PreparedBatch preparedBatch(
        ServerPacketWriter writer
    )throws Exception{
        Field field=
            ServerPacketWriter.class
                .getDeclaredField(
                    "batchPlayer81"
                );
        field.setAccessible(true);
        return (Player81WorldSync.PreparedBatch)
            field.get(writer);
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
                second.canonical(id);

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

    private static long sequence(
        World world
    )throws Exception{
        Object state=
            worldState(world);

        if(state==null)
            return 0L;

        Field field=
            state.getClass()
                .getDeclaredField(
                    "sequence"
                );
        field.setAccessible(true);
        return field.getLong(state);
    }

    private static Object worldState(
        World world
    )throws Exception{
        Field field=
            Player81WorldSync.class
                .getDeclaredField(
                    "BY_WORLD"
                );
        field.setAccessible(true);

        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            Map<World,?> states=
                (Map<World,?>)
                    field.get(null);
            return states.get(world);
        }
    }

    private Player81BatchAbortAtomicityTest(){}
}
