package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;

public final class Player81WorldStateCleanupTest {
    public static void main(String[] args)throws Exception{
        int baseline=trackedWorlds();
        int writerBaseline=trackedWriters();

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

            worldCloseCleanup(
                baseline,
                writerBaseline
            );

            System.out.println(
                "PLAYER81_WORLD_STATE_CLEANUP_PASS "+
                "peerKeepsState=true "+
                "finalContextReleasedBeforeWorldUnregister=true "+
                "playersStillRegistered=2 "+
                "worldCloseReleasedState=true "+
                "worldCloseReleasedWriters=true "+
                "worldCloseContextsClosed=true "+
                "postCloseUnregisterIdempotent=true "+
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

    private static void worldCloseCleanup(
        int worldBaseline,
        int writerBaseline
    )throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();

        world.registerPlayer(
            a,
            "player81-close-a"
        );
        world.registerPlayer(
            b,
            "player81-close-b"
        );

        ServerPacketWriter wa=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{9,10,11,12}
                )
            );
        ServerPacketWriter wb=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{13,14,15,16}
                )
            );

        Player81WorldSync.Context ca=null;
        Player81WorldSync.Context cb=null;

        try{
            ca=
                Player81WorldSync.register(
                    wa,
                    world,
                    a,
                    new DevAuthorityWorkbench()
                );
            cb=
                Player81WorldSync.register(
                    wb,
                    world,
                    b,
                    new DevAuthorityWorkbench()
                );

            if(trackedWorlds()!=
                    worldBaseline+1||
               trackedWriters()!=
                    writerBaseline+2)
                throw new AssertionError(
                    "World-close Player81 fixture not retained"
                );

            world.close();

            if(trackedWorlds()!=
                    worldBaseline||
               trackedWriters()!=
                    writerBaseline)
                throw new AssertionError(
                    "World close retained Player81 static state"
                );

            if(ca==null||
               cb==null||
               !ca.closed||
               !cb.closed)
                throw new AssertionError(
                    "World close did not terminalize Player81 contexts"
                );

            Player81WorldSync.unregister(
                wa
            );
            Player81WorldSync.unregister(
                wb
            );

            if(trackedWorlds()!=
                    worldBaseline||
               trackedWriters()!=
                    writerBaseline)
                throw new AssertionError(
                    "post-close Player81 unregister changed baseline"
                );
        }finally{
            Player81WorldSync.unregister(
                wa
            );
            Player81WorldSync.unregister(
                wb
            );

            if(a.registered())
                world.unregisterPlayer(a);
            if(b.registered())
                world.unregisterPlayer(b);

            world.close();
        }
    }

    private static int trackedWriters()
        throws Exception{
        Field field=
            Player81WorldSync.class
                .getDeclaredField(
                    "BY_WRITER"
                );
        field.setAccessible(true);

        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<ServerPacketWriter,?>
                writers=
                    (IdentityHashMap<ServerPacketWriter,?>)
                        field.get(null);

            return writers.size();
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