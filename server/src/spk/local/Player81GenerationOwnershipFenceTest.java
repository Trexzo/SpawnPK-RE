package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

public final class Player81GenerationOwnershipFenceTest {
    public static void main(String[] args)throws Exception{
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
                "replacementGenerationWorks=true"
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
