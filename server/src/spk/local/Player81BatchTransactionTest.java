package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Map;

public final class Player81BatchTransactionTest {
    public static void main(String[] args)throws Exception{
        stagedWorldStateAbortsAndCommits();
        relayBarrierSurvivesAbort();

        System.out.println(
            "PLAYER81_BATCH_TRANSACTION_PASS "+
            "stagedWorldStateInvisible=true "+
            "abortRestoresWorldState=true "+
            "commitAppliesExactlyOnce=true "+
            "viewerTrackDeferred=true "+
            "relayBarrierAbortPreserved=true "+
            "relayFlushAfterCommit=true"
        );
    }

    private static void stagedWorldStateAbortsAndCommits()
        throws Exception
    {
        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        world.registerPlayer(a,"batch-a");
        world.registerPlayer(b,"batch-b");

        OutboundPacketQueue qa=new OutboundPacketQueue();
        OutboundPacketQueue qb=new OutboundPacketQueue();
        ServerPacketWriter wa=
            new ServerPacketWriter(
                qa,
                new IsaacCipher(new int[]{1,2,3,4})
            );
        ServerPacketWriter wb=
            new ServerPacketWriter(
                qb,
                new IsaacCipher(new int[]{5,6,7,8})
            );

        Player81WorldSync.register(
            wa,
            world,
            a,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.register(
            wb,
            world,
            b,
            new DevAuthorityWorkbench()
        );

        try{
            long before=sequence(world);

            wa.beginBatch();
            wa.varShort(
                81,
                BootstrapPackets.player81WalkStep(4)
            );

            if(sequence(world)!=before)
                throw new AssertionError(
                    "batched packet81 published WorldState before commit"
                );

            if(Player81WorldSync.clientIndexFor(wa,b)!=-1)
                throw new AssertionError(
                    "batched packet81 published viewer Track before commit"
                );

            if(qa.queuedBytes()!=0)
                throw new AssertionError(
                    "batched packet81 emitted bytes before commit"
                );

            wa.abortBatch();

            if(sequence(world)!=before||
               Player81WorldSync.clientIndexFor(wa,b)!=-1||
               qa.queuedBytes()!=0)
                throw new AssertionError(
                    "packet81 abort did not preserve exact preimage"
                );

            wa.beginBatch();
            wa.varShort(
                81,
                BootstrapPackets.player81WalkStep(4)
            );
            wa.endBatch();

            if(sequence(world)<=before)
                throw new AssertionError(
                    "successful packet81 batch did not commit WorldState"
                );

            if(Player81WorldSync.clientIndexFor(wa,b)<0)
                throw new AssertionError(
                    "successful packet81 batch did not commit viewer Track"
                );

            if(qa.queuedBytes()==0)
                throw new AssertionError(
                    "successful packet81 batch emitted no bytes"
                );
        }finally{
            Player81WorldSync.unregister(wa);
            Player81WorldSync.unregister(wb);
            if(a.registered())world.unregisterPlayer(a);
            if(b.registered())world.unregisterPlayer(b);
            world.close();
        }
    }

    private static void relayBarrierSurvivesAbort()
        throws Exception
    {
        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        world.registerPlayer(a,"relay-a");
        world.registerPlayer(b,"relay-b");

        OutboundPacketQueue qa=new OutboundPacketQueue();
        OutboundPacketQueue qb=new OutboundPacketQueue();
        ServerPacketWriter wa=
            new ServerPacketWriter(
                qa,
                new IsaacCipher(new int[]{11,12,13,14})
            );
        ServerPacketWriter wb=
            new ServerPacketWriter(
                qb,
                new IsaacCipher(new int[]{15,16,17,18})
            );

        Player81WorldSync.register(
            wa,
            world,
            a,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.register(
            wb,
            world,
            b,
            new DevAuthorityWorkbench()
        );

        NpcRegistry na=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry nb=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        MovementState ma=new MovementState();
        MovementState mb=new MovementState();

        SharedNpcWorldRelay.register(
            wa,
            world,
            a,
            na,
            ma
        );
        SharedNpcWorldRelay.register(
            wb,
            world,
            b,
            nb,
            mb
        );

        try{
            NpcEntity[] shared=
                bootstrapSharedHome(
                    world,
                    na,
                    nb,
                    ma,
                    mb,
                    wa,
                    wb
                );
            NpcEntity sourceNpc=shared[0];

            drain(qa);
            drain(qb);

            wa.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
            wb.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
            drain(qa);
            drain(qb);

            wa.varShort(
                81,
                CombatSync.player81AnimationAndInteraction(
                    15552,
                    sourceNpc.sceneIndex
                )
            );
            drain(qa);

            na.sendMask(
                sourceNpc,
                NpcSyncEncoder.Mask.singleHit(
                    100,
                    6,
                    255,
                    255
                ),
                wa
            );

            if(qb.queuedBytes()!=0)
                throw new AssertionError(
                    "relay mask overtook Player81 barrier fixture"
                );

            long consumedBefore=
                Player81WorldSync.consumedEventSequence(
                    wb,
                    a.id()
                );

            wb.beginBatch();
            wb.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            if(qb.queuedBytes()!=0)
                throw new AssertionError(
                    "staged Player81 unexpectedly flushed relay"
                );

            long consumedStaged=
                Player81WorldSync.consumedEventSequence(
                    wb,
                    a.id()
                );

            if(consumedStaged!=consumedBefore)
                throw new AssertionError(
                    "staged Player81 consumed relay barrier before commit"
                );

            wb.abortBatch();

            if(qb.queuedBytes()!=0||
               Player81WorldSync.consumedEventSequence(
                   wb,
                   a.id()
               )!=consumedBefore)
                throw new AssertionError(
                    "aborted Player81 lost relay barrier"
                );

            wb.beginBatch();
            wb.varShort(
                81,
                BootstrapPackets.player81Idle()
            );
            wb.endBatch();

            if(qb.queuedBytes()==0)
                throw new AssertionError(
                    "committed Player81 did not flush barrier/relay bytes"
                );

            if(Player81WorldSync.consumedEventSequence(
                    wb,
                    a.id()
                )<=consumedBefore)
                throw new AssertionError(
                    "committed Player81 did not advance consumed event"
                );
        }finally{
            SharedNpcWorldRelay.unregister(wa);
            SharedNpcWorldRelay.unregister(wb);
            Player81WorldSync.unregister(wa);
            Player81WorldSync.unregister(wb);
            if(a.registered())world.unregisterPlayer(a);
            if(b.registered())world.unregisterPlayer(b);
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

        for(NpcEntity candidate:first.snapshot()){
            EntityId id=candidate.canonicalId();
            if(id==null)continue;
            NpcEntity other=second.canonical(id);
            if(other!=null&&
               other.definitionId==
                    candidate.definitionId)
                return new NpcEntity[]{
                    candidate,
                    other
                };
        }

        throw new AssertionError(
            "no shared canonical HOME NPC visible"
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

    private static long sequence(
        World world
    )throws Exception{
        Object state=worldState(world);
        if(state==null)return 0L;

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

    private Player81BatchTransactionTest(){}
}
