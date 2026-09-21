package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

public final class RelayOwnerWriterRebindCleanupTest {
    public static void main(String[] args)throws Exception{
        int player81WorldBaseline=
            mapSize(
                Player81WorldSync.class,
                "BY_WORLD"
            );
        int player81WriterBaseline=
            mapSize(
                Player81WorldSync.class,
                "BY_WRITER"
            );
        int npcWorldBaseline=
            mapSize(
                SharedNpcWorldRelay.class,
                "BY_WORLD"
            );
        int npcWriterBaseline=
            mapSize(
                SharedNpcWorldRelay.class,
                "BY_WRITER"
            );

        World world=
            World.isolatedForTest(600L);
        WorldPlayer owner=
            new WorldPlayer();

        world.registerPlayer(
            owner,
            "relay-owner-rebind"
        );

        ServerPacketWriter writerA=
            writer(
                new int[]{1,2,3,4}
            );
        ServerPacketWriter writerB=
            writer(
                new int[]{5,6,7,8}
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

        try{
            Player81WorldSync.Context first=
                Player81WorldSync.register(
                    writerA,
                    world,
                    owner,
                    new DevAuthorityWorkbench()
                );

            SharedNpcWorldRelay.register(
                writerA,
                world,
                owner,
                npcsA,
                movementA
            );

            Player81WorldSync.transformForTest(
                first,
                BootstrapPackets.player81WalkStep(4)
            );
            Player81WorldSync.transformForTest(
                first,
                CombatSync.player81AnimationAndGfx(
                    827,
                    1310,
                    0,
                    0
                )
            );

            Object oldState=
                stateFor(
                    Player81WorldSync.class,
                    world
                );

            if(oldState==null)
                throw new AssertionError(
                    "initial Player81 state missing"
                );

            if(!map(
                    oldState,
                    "motions"
                ).containsKey(owner.id())||
               !map(
                    oldState,
                    "events"
                ).containsKey(owner.id()))
                throw new AssertionError(
                    "initial owner history missing"
                );

            Player81WorldSync.Context second=
                Player81WorldSync.register(
                    writerB,
                    world,
                    owner,
                    new DevAuthorityWorkbench()
                );

            SharedNpcWorldRelay.register(
                writerB,
                world,
                owner,
                npcsB,
                movementB
            );

            if(writerMap(
                    Player81WorldSync.class
                ).containsKey(writerA))
                throw new AssertionError(
                    "old Player81 writer retained"
                );

            if(writerMap(
                    SharedNpcWorldRelay.class
                ).containsKey(writerA))
                throw new AssertionError(
                    "old SharedNpc writer retained"
                );

            if(!writerMap(
                    Player81WorldSync.class
                ).containsKey(writerB)||
               !writerMap(
                    SharedNpcWorldRelay.class
                ).containsKey(writerB))
                throw new AssertionError(
                    "new writer not installed in both relays"
                );

            if(!first.closed)
                throw new AssertionError(
                    "old Player81 context not closed"
                );

            if(map(
                    oldState,
                    "contexts"
                ).containsKey(owner.id())||
               map(
                    oldState,
                    "motions"
               ).containsKey(owner.id())||
               map(
                    oldState,
                    "events"
               ).containsKey(owner.id()))
                throw new AssertionError(
                    "old Player81 owner state retained"
                );

            Object reboundPlayer81=
                stateFor(
                    Player81WorldSync.class,
                    world
                );
            Object reboundNpc=
                stateFor(
                    SharedNpcWorldRelay.class,
                    world
                );

            if(reboundPlayer81==null||
               reboundNpc==null)
                throw new AssertionError(
                    "target WorldState missing after owner rebind"
                );

            if(!map(
                    reboundPlayer81,
                    "contexts"
                ).containsKey(owner.id())||
               !map(
                    reboundNpc,
                    "contexts"
                ).containsKey(owner.id()))
                throw new AssertionError(
                    "owner missing after writer rebind"
                );

            // Re-register the same writer/owner in the same World. Cleanup may
            // remove the sole WorldState, so register must reacquire/create it
            // before installing the replacement context.
            Player81WorldSync.Context third=
                Player81WorldSync.register(
                    writerB,
                    world,
                    owner,
                    new DevAuthorityWorkbench()
                );

            SharedNpcWorldRelay.register(
                writerB,
                world,
                owner,
                npcsB,
                movementB
            );

            if(!second.closed)
                throw new AssertionError(
                    "same-writer replaced Player81 context not closed"
                );

            if(third.closed)
                throw new AssertionError(
                    "fresh Player81 context unexpectedly closed"
                );

            Object sameWriterPlayer81=
                stateFor(
                    Player81WorldSync.class,
                    world
                );
            Object sameWriterNpc=
                stateFor(
                    SharedNpcWorldRelay.class,
                    world
                );

            if(sameWriterPlayer81==null||
               sameWriterNpc==null||
               !map(
                   sameWriterPlayer81,
                   "contexts"
               ).containsKey(owner.id())||
               !map(
                   sameWriterNpc,
                   "contexts"
               ).containsKey(owner.id()))
                throw new AssertionError(
                    "same-writer re-registration detached WorldState"
                );

            if(mapSize(
                    Player81WorldSync.class,
                    "BY_WRITER"
                )!=player81WriterBaseline+1||
               mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WRITER"
                )!=npcWriterBaseline+1)
                throw new AssertionError(
                    "writer maps contain stale bindings"
                );

            Player81WorldSync.unregister(
                writerB
            );
            SharedNpcWorldRelay.unregister(
                writerB
            );

            if(mapSize(
                    Player81WorldSync.class,
                    "BY_WORLD"
                )!=player81WorldBaseline||
               mapSize(
                    Player81WorldSync.class,
                    "BY_WRITER"
                )!=player81WriterBaseline||
               mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WORLD"
                )!=npcWorldBaseline||
               mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WRITER"
                )!=npcWriterBaseline)
                throw new AssertionError(
                    "relay maps did not return to baseline"
                );

            System.out.println(
                "RELAY_OWNER_WRITER_REBIND_CLEANUP_PASS "+
                "oldWriterRemoved=true "+
                "oldOwnerPruned=true "+
                "sameWriterWorldStatePreserved=true "+
                "newWriterOnly=true "+
                "baselineRestored=true"
            );
        }finally{
            Player81WorldSync.unregister(
                writerA
            );
            Player81WorldSync.unregister(
                writerB
            );
            SharedNpcWorldRelay.unregister(
                writerA
            );
            SharedNpcWorldRelay.unregister(
                writerB
            );

            if(owner.registered())
                world.unregisterPlayer(
                    owner
                );

            world.close();
        }
    }

    private static ServerPacketWriter writer(
        int[] seed
    ){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(seed)
        );
    }

    private static int mapSize(
        Class<?> relay,
        String fieldName
    )throws Exception{
        Field field=
            relay.getDeclaredField(
                fieldName
            );
        field.setAccessible(true);

        synchronized(relay){
            return ((Map<?,?>)
                field.get(null)).size();
        }
    }

    private static Map<?,?> writerMap(
        Class<?> relay
    )throws Exception{
        Field field=
            relay.getDeclaredField(
                "BY_WRITER"
            );
        field.setAccessible(true);

        synchronized(relay){
            @SuppressWarnings("unchecked")
            IdentityHashMap<Object,Object> map=
                (IdentityHashMap<Object,Object>)
                field.get(null);
            return new IdentityHashMap<>(
                map
            );
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
        return (Map<?,?>)
            field.get(state);
    }

    private RelayOwnerWriterRebindCleanupTest(){}
}
