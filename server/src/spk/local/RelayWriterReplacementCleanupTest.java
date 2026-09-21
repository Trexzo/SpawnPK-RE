package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

public final class RelayWriterReplacementCleanupTest {
    public static void main(String[] args)throws Exception{
        int player81Baseline=trackedWorlds(
            Player81WorldSync.class
        );
        int npcBaseline=trackedWorlds(
            SharedNpcWorldRelay.class
        );

        World worldA=World.isolatedForTest(600L);
        World worldB=World.isolatedForTest(600L);
        WorldPlayer ownerA=new WorldPlayer();
        WorldPlayer ownerB=new WorldPlayer();

        worldA.registerPlayer(ownerA,"relay-replace-a");
        worldB.registerPlayer(ownerB,"relay-replace-b");

        ServerPacketWriter writer=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{1,2,3,4})
            );

        NpcRegistry npcsA=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry npcsB=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        MovementState movementA=
            new MovementState();
        MovementState movementB=
            new MovementState();

        Player81WorldSync.Context oldPlayer81=
            null;

        try{
            oldPlayer81=
                Player81WorldSync.register(
                    writer,
                    worldA,
                    ownerA,
                    new DevAuthorityWorkbench()
                );

            SharedNpcWorldRelay.register(
                writer,
                worldA,
                ownerA,
                npcsA,
                movementA
            );

            Player81WorldSync.transformForTest(
                oldPlayer81,
                BootstrapPackets.player81WalkStep(4)
            );
            Player81WorldSync.transformForTest(
                oldPlayer81,
                CombatSync.player81AnimationAndGfx(
                    827,
                    1310,
                    0,
                    0
                )
            );

            Object oldPlayer81State=
                stateFor(
                    Player81WorldSync.class,
                    worldA
                );

            if(oldPlayer81State==null)
                throw new AssertionError(
                    "Player81 World A state missing"
                );

            if(!map(
                    oldPlayer81State,
                    "motions"
                ).containsKey(ownerA.id()))
                throw new AssertionError(
                    "Player81 motion fixture missing"
                );

            if(!map(
                    oldPlayer81State,
                    "events"
                ).containsKey(ownerA.id()))
                throw new AssertionError(
                    "Player81 event fixture missing"
                );

            if(stateFor(
                    SharedNpcWorldRelay.class,
                    worldA
                )==null)
                throw new AssertionError(
                    "SharedNpc World A state missing"
                );

            if(trackedWorlds(
                    Player81WorldSync.class
                )!=player81Baseline+1)
                throw new AssertionError(
                    "unexpected Player81 baseline delta"
                );

            if(trackedWorlds(
                    SharedNpcWorldRelay.class
                )!=npcBaseline+1)
                throw new AssertionError(
                    "unexpected SharedNpc baseline delta"
                );

            Player81WorldSync.Context newPlayer81=
                Player81WorldSync.register(
                    writer,
                    worldB,
                    ownerB,
                    new DevAuthorityWorkbench()
                );

            SharedNpcWorldRelay.register(
                writer,
                worldB,
                ownerB,
                npcsB,
                movementB
            );

            if(stateFor(
                    Player81WorldSync.class,
                    worldA
                )!=null)
                throw new AssertionError(
                    "Player81 replacement retained World A"
                );

            if(stateFor(
                    SharedNpcWorldRelay.class,
                    worldA
                )!=null)
                throw new AssertionError(
                    "SharedNpc replacement retained World A"
                );

            if(!oldPlayer81.closed)
                throw new AssertionError(
                    "replaced Player81 context not closed"
                );

            if(map(
                    oldPlayer81State,
                    "contexts"
                ).containsKey(ownerA.id()))
                throw new AssertionError(
                    "replaced Player81 context retained"
                );

            if(map(
                    oldPlayer81State,
                    "motions"
                ).containsKey(ownerA.id()))
                throw new AssertionError(
                    "replaced Player81 motion retained"
                );

            if(map(
                    oldPlayer81State,
                    "events"
                ).containsKey(ownerA.id()))
                throw new AssertionError(
                    "replaced Player81 events retained"
                );

            Object player81WorldB=
                stateFor(
                    Player81WorldSync.class,
                    worldB
                );

            if(player81WorldB==null||
               !map(
                   player81WorldB,
                   "contexts"
               ).containsKey(ownerB.id()))
                throw new AssertionError(
                    "Player81 replacement missing World B context"
                );

            Object npcWorldB=
                stateFor(
                    SharedNpcWorldRelay.class,
                    worldB
                );

            if(npcWorldB==null||
               !map(
                   npcWorldB,
                   "contexts"
               ).containsKey(ownerB.id()))
                throw new AssertionError(
                    "SharedNpc replacement missing World B context"
                );

            if(Player81WorldSync.clientIndexFor(
                    writer,
                    ownerB)<0)
                throw new AssertionError(
                    "writer no longer points to World B Player81 context"
                );

            if(newPlayer81.closed)
                throw new AssertionError(
                    "new Player81 context unexpectedly closed"
                );

            Player81WorldSync.unregister(writer);
            SharedNpcWorldRelay.unregister(writer);

            if(trackedWorlds(
                    Player81WorldSync.class
                )!=player81Baseline)
                throw new AssertionError(
                    "Player81 final unregister retained world"
                );

            if(trackedWorlds(
                    SharedNpcWorldRelay.class
                )!=npcBaseline)
                throw new AssertionError(
                    "SharedNpc final unregister retained world"
                );

            System.out.println(
                "RELAY_WRITER_REPLACEMENT_CLEANUP_PASS "+
                "oldWorldReleased=true "+
                "player81OwnerPruned=true "+
                "oldContextClosed=true "+
                "newWorldBound=true "+
                "finalBaselineRestored=true"
            );
        }finally{
            Player81WorldSync.unregister(writer);
            SharedNpcWorldRelay.unregister(writer);

            if(ownerA.registered())
                worldA.unregisterPlayer(ownerA);
            if(ownerB.registered())
                worldB.unregisterPlayer(ownerB);

            worldA.close();
            worldB.close();
        }
    }

    private static int trackedWorlds(
        Class<?> relay
    )throws Exception{
        Field field=
            relay.getDeclaredField(
                "BY_WORLD"
            );
        field.setAccessible(true);

        synchronized(relay){
            @SuppressWarnings("unchecked")
            IdentityHashMap<World,?> states=
                (IdentityHashMap<World,?>)
                field.get(null);
            return states.size();
        }
    }

    private static Object stateFor(
        Class<?> relay,
        World world
    )throws Exception{
        Field field=
            relay.getDeclaredField(
                "BY_WORLD"
            );
        field.setAccessible(true);

        synchronized(relay){
            @SuppressWarnings("unchecked")
            Map<World,?> states=
                (Map<World,?>)
                field.get(null);
            return states.get(world);
        }
    }

    private static Map<?,?> map(
        Object state,
        String name
    )throws Exception{
        if(state==null)
            throw new AssertionError(
                "missing state for "+name
            );

        Field field=
            state.getClass()
                .getDeclaredField(name);
        field.setAccessible(true);
        return (Map<?,?>)field.get(state);
    }

    private RelayWriterReplacementCleanupTest(){}
}
