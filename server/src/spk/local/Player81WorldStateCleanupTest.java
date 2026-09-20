package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;

public final class Player81WorldStateCleanupTest {
    public static void main(String[] args)throws Exception{
        int baseline=trackedWorlds();

        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();

        world.registerPlayer(a,"player81-cleanup-a");
        world.registerPlayer(b,"player81-cleanup-b");

        ServerPacketWriter wa=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{1,2,3,4})
            );

        ServerPacketWriter wb=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{5,6,7,8})
            );

        try{
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

            if(trackedWorlds()!=baseline+1)
                throw new AssertionError(
                    "world state not created baseline="+baseline+
                    " tracked="+trackedWorlds()
                );

            Player81WorldSync.unregister(wa);

            if(trackedWorlds()!=baseline+1)
                throw new AssertionError(
                    "world state removed while peer context remained"
                );

            if(world.players().size()!=2)
                throw new AssertionError(
                    "test requires players to remain registered before final sync unregister"
                );

            Player81WorldSync.unregister(wb);

            if(world.players().size()!=2)
                throw new AssertionError(
                    "Player81 unregister unexpectedly changed world membership"
                );

            if(trackedWorlds()!=baseline)
                throw new AssertionError(
                    "empty Player81 world state retained baseline="+baseline+
                    " tracked="+trackedWorlds()
                );

            System.out.println(
                "PLAYER81_WORLD_STATE_CLEANUP_PASS "+
                "peerKeepsState=true "+
                "finalContextReleasedBeforeWorldUnregister=true "+
                "playersStillRegistered=2 "+
                "baseline="+baseline
            );
        }finally{
            Player81WorldSync.unregister(wa);
            Player81WorldSync.unregister(wb);
            world.unregisterPlayer(a);
            world.unregisterPlayer(b);
            world.close();
        }
    }

    private static int trackedWorlds()throws Exception{
        Field field=Player81WorldSync.class.getDeclaredField("BY_WORLD");
        field.setAccessible(true);
        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<World,?> states=
                (IdentityHashMap<World,?>)field.get(null);
            return states.size();
        }
    }
}
