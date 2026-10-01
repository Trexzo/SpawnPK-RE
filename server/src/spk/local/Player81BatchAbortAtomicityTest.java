package spk.local;

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

            System.out.println(
                "PLAYER81_BATCH_ABORT_ATOMICITY_PASS "+
                "precommitStateStable=true "+
                "abortStateStable=true "+
                "zeroAbortBytes=true "+
                "retryCommittedOnce=true "+
                "relayBarrierRetained=true"
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
