package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

public final class Player81GenerationOwnershipFenceTest {
    public static void main(String[] args)throws Exception{
        assertTerminalSkillPublicationLatchesWriter();
        assertQueuePressureSkillPublicationRetracts();

        World world=
            World.isolatedForTest(600L);

        WorldPlayer player=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                player,
                "player81-generation-owner"
            );

        ByteArrayOutputStream staleOut=
            new ByteArrayOutputStream();

        ServerPacketWriter staleWriter=
            new ServerPacketWriter(
                staleOut,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        Player81WorldSync.Context stale=
            Player81WorldSync.register(
                staleWriter,
                world,
                player,
                new DevAuthorityWorkbench()
            );

        ServerPacketWriter freshWriter=null;

        try{
            Player81WorldSync.transformForTest(
                stale,
                BootstrapPackets.player81WalkStep(4)
            );

            long beforeReplacementSequence=
                sequence(world);

            if(beforeReplacementSequence<=0L)
                throw new AssertionError(
                    "generation A transform did not publish"
                );

            if(!world.unregisterPlayer(
                    player,
                    generationA
                ))
                throw new AssertionError(
                    "generation A unregister failed"
                );

            long generationB=
                world.registerPlayer(
                    player,
                    "player81-generation-owner"
                );

            if(generationB==generationA)
                throw new AssertionError(
                    "replacement generation did not advance"
                );

            byte[] staleBody=
                BootstrapPackets.player81WalkStep(6);

            byte[] staleResult=
                Player81WorldSync.transform(
                    staleWriter,
                    staleBody
                );

            if(!Arrays.equals(
                    staleBody,
                    staleResult
                ))
                throw new AssertionError(
                    "stale generation transformed packet81"
                );

            if(sequence(world)!=
                    beforeReplacementSequence)
                throw new AssertionError(
                    "stale generation advanced Player81 WorldState sequence"
                );

            int staleBytesBefore=
                staleOut.size();

            boolean staleSkill=
                Player81WorldSync.sendSkillUpdate(
                    world,
                    player,
                    0,
                    1_000,
                    10
                );

            if(staleSkill)
                throw new AssertionError(
                    "stale generation received skill update"
                );

            if(staleOut.size()!=staleBytesBefore)
                throw new AssertionError(
                    "stale skill update wrote bytes"
                );

            if(Player81WorldSync
                    .latestPublishedEventSequence(
                        staleWriter
                    )!=0L)
                throw new AssertionError(
                    "stale writer exposed presentation sequence"
                );

            if(Player81WorldSync.clientIndexFor(
                    staleWriter,
                    player
                )!=-1)
                throw new AssertionError(
                    "stale writer exposed client index"
                );

            Player81WorldSync.unregister(
                staleWriter
            );

            ByteArrayOutputStream freshOut=
                new ByteArrayOutputStream();

            freshWriter=
                new ServerPacketWriter(
                    freshOut,
                    new IsaacCipher(
                        new int[]{5,6,7,8}
                    )
                );

            Player81WorldSync.Context fresh=
                Player81WorldSync.register(
                    freshWriter,
                    world,
                    player,
                    new DevAuthorityWorkbench()
                );

            if(fresh.ownerGeneration!=generationB)
                throw new AssertionError(
                    "fresh context did not capture replacement generation"
                );

            Player81WorldSync.transformForTest(
                fresh,
                BootstrapPackets.player81WalkStep(2)
            );

            if(sequence(world)<=0L)
                throw new AssertionError(
                    "replacement generation could not publish"
                );

            int freshBytesBefore=
                freshOut.size();

            boolean freshSkill=
                Player81WorldSync.sendSkillUpdate(
                    world,
                    player,
                    0,
                    2_000,
                    20
                );

            if(!freshSkill)
                throw new AssertionError(
                    "replacement generation skill update rejected"
                );

            if(freshOut.size()<=freshBytesBefore)
                throw new AssertionError(
                    "replacement skill update emitted no bytes"
                );

            System.out.println(
                "PLAYER81_GENERATION_OWNERSHIP_FENCE_PASS "+
                "generationAWorks=true "+
                "staleTransformRejected=true "+
                "staleSequenceUnchanged=true "+
                "staleSkillRejected=true "+
                "staleLookupsRejected=true "+
                "replacementGenerationWorks=true "+
                "terminalSkillWriterLatched=true "+
                "terminalSkillNoRetouch=true "+
                "queueSkillRetracted=true "+
                "queueSkillCommitStillWorks=true"
            );

            world.unregisterPlayer(
                player,
                generationB
            );
        }finally{
            Player81WorldSync.unregister(
                staleWriter
            );

            if(freshWriter!=null)
                Player81WorldSync.unregister(
                    freshWriter
                );

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );

            world.close();
        }
    }

    private static void assertTerminalSkillPublicationLatchesWriter()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                601L
            );
        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "player81-skill-terminal"
            );

        PartialFailOutputStream out=
            new PartialFailOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{21,22,23,24}
                )
            );

        Player81WorldSync.register(
            writer,
            world,
            player,
            new DevAuthorityWorkbench()
        );

        try{
            boolean published=
                Player81WorldSync.sendSkillUpdate(
                    world,
                    player,
                    0,
                    1_000,
                    10
                );

            if(published)
                throw new AssertionError(
                    "terminal skill publication reported committed"
                );

            if(!writer.terminal())
                throw new AssertionError(
                    "terminal skill publication did not latch exact writer"
                );

            int attemptsBeforeProbe=
                out.attempts;

            out.fail=false;

            boolean probeRejected=false;

            try{
                writer.fixed(
                    97,
                    new byte[0]
                );
            }catch(java.io.IOException expected){
                probeRejected=true;
            }

            if(!probeRejected)
                throw new AssertionError(
                    "terminal skill writer accepted later publication"
                );

            if(out.attempts!=
                    attemptsBeforeProbe)
                throw new AssertionError(
                    "terminal skill writer retouched transport before="+
                    attemptsBeforeProbe+
                    " after="+
                    out.attempts
                );
        }finally{
            Player81WorldSync.unregister(
                writer
            );

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void assertQueuePressureSkillPublicationRetracts()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                602L
            );
        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "player81-skill-retry"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                1024
            );
        queue.offerBatch(
            new byte[1016]
        );

        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{31,32,33,34}
                )
            );

        Player81WorldSync.register(
            writer,
            world,
            player,
            new DevAuthorityWorkbench()
        );

        try{
            int bytesBefore=
                queue.queuedBytes();

            boolean retracted=
                Player81WorldSync.sendSkillUpdate(
                    world,
                    player,
                    0,
                    2_000,
                    20
                );

            if(retracted)
                throw new AssertionError(
                    "queue-pressure skill publication reported committed"
                );

            if(writer.terminal())
                throw new AssertionError(
                    "queue-pressure skill publication terminalized writer"
                );

            if(queue.queuedBytes()!=
                    bytesBefore)
                throw new AssertionError(
                    "queue-pressure skill publication leaked bytes before="+
                    bytesBefore+
                    " after="+
                    queue.queuedBytes()
                );

            ByteArrayOutputStream drain=
                new ByteArrayOutputStream();

            queue.drainTo(
                drain,
                2048
            );

            if(queue.queuedBytes()!=0)
                throw new AssertionError(
                    "queue-pressure skill fixture did not drain"
                );

            boolean committed=
                Player81WorldSync.sendSkillUpdate(
                    world,
                    player,
                    0,
                    3_000,
                    30
                );

            if(!committed)
                throw new AssertionError(
                    "queue-backed skill publication did not recover after retraction"
                );

            if(queue.queuedBytes()<=0)
                throw new AssertionError(
                    "queue-backed skill publication committed no bytes"
                );
        }finally{
            Player81WorldSync.unregister(
                writer
            );

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static final class PartialFailOutputStream
        extends java.io.OutputStream {

        final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();
        int attempts;
        boolean fail=true;

        @Override public void write(
            int value
        )throws java.io.IOException{
            attempts++;
            bytes.write(
                value
            );

            if(fail)
                throw new java.io.IOException(
                    "EXPECTED_SKILL_PARTIAL_FAILURE"
                );
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws java.io.IOException{
            attempts++;

            if(length>0)
                bytes.write(
                    data[offset]
                );

            if(fail)
                throw new java.io.IOException(
                    "EXPECTED_SKILL_PARTIAL_FAILURE"
                );

            bytes.write(
                data,
                offset+(length>0?1:0),
                Math.max(
                    0,
                    length-(length>0?1:0)
                )
            );
        }
    }

    private static long sequence(
        World world
    )throws Exception{
        Object state=
            stateFor(world);

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

    private static Object stateFor(
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

    private Player81GenerationOwnershipFenceTest(){}
}
