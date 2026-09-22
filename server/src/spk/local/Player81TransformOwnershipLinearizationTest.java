package spk.local;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class Player81TransformOwnershipLinearizationTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "player81-transform-owner"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        Player81WorldSync.Context context=
            Player81WorldSync.register(
                writer,
                world,
                player,
                new DevAuthorityWorkbench()
            );

        byte[] body=
            BootstrapPackets.player81WalkStep(4);

        AtomicReference<byte[]> transformed=
            new AtomicReference<>();
        AtomicReference<Throwable> transformFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> unregisterFailure=
            new AtomicReference<>();
        AtomicBoolean unregisterResult=
            new AtomicBoolean();

        Thread transformThread=
            new Thread(
                ()->{
                    try{
                        transformed.set(
                            Player81WorldSync.transform(
                                writer,
                                body
                            )
                        );
                    }catch(Throwable failure){
                        transformFailure.set(failure);
                    }
                },
                "player81-transform-owner"
            );

        Thread unregisterThread=
            new Thread(
                ()->{
                    try{
                        unregisterResult.set(
                            world.unregisterPlayer(
                                player,
                                generation
                            )
                        );
                    }catch(Throwable failure){
                        unregisterFailure.set(failure);
                    }
                },
                "player81-transform-unregister"
            );

        transformThread.setDaemon(true);
        unregisterThread.setDaemon(true);

        try{
            synchronized(context){
                transformThread.start();

                long blockedDeadline=
                    System.nanoTime()+
                    TimeUnit.SECONDS.toNanos(5L);

                while(transformThread.getState()!=
                        Thread.State.BLOCKED&&
                      System.nanoTime()<
                        blockedDeadline)
                    Thread.sleep(2L);

                if(transformThread.getState()!=
                        Thread.State.BLOCKED)
                    throw new AssertionError(
                        "transform thread did not enter owned section before context monitor"
                    );

                unregisterThread.start();

                Thread.sleep(100L);

                if(!unregisterThread.isAlive())
                    throw new AssertionError(
                        "unregister crossed accepted Player81 transform ownership section"
                    );

                if(!world.players().owns(
                        player,
                        generation
                    ))
                    throw new AssertionError(
                        "player ownership disappeared while transform section was blocked"
                    );
            }

            transformThread.join(5_000L);

            if(transformThread.isAlive())
                throw new AssertionError(
                    "Player81 transform did not complete after context release"
                );

            if(transformFailure.get()!=null)
                throw new AssertionError(
                    "Player81 transform failed",
                    transformFailure.get()
                );

            unregisterThread.join(5_000L);

            if(unregisterThread.isAlive())
                throw new AssertionError(
                    "unregister did not complete after transform"
                );

            if(unregisterFailure.get()!=null)
                throw new AssertionError(
                    "unregister failed",
                    unregisterFailure.get()
                );

            if(!unregisterResult.get())
                throw new AssertionError(
                    "unregister returned false"
                );

            if(player.registered())
                throw new AssertionError(
                    "player remained registered"
                );

            byte[] ownedResult=
                transformed.get();

            if(ownedResult==null)
                throw new AssertionError(
                    "owned transform result missing"
                );

            long sequenceAfterOwned=
                sequence(world);

            if(sequenceAfterOwned<=0L)
                throw new AssertionError(
                    "owned transform did not publish Player81 state"
                );

            byte[] staleBody=
                BootstrapPackets.player81WalkStep(6);

            byte[] staleResult=
                Player81WorldSync.transform(
                    writer,
                    staleBody
                );

            if(staleResult!=staleBody&&
               !Arrays.equals(
                   staleResult,
                   staleBody
               ))
                throw new AssertionError(
                    "stale transform modified certified local packet"
                );

            if(sequence(world)!=sequenceAfterOwned)
                throw new AssertionError(
                    "stale transform regrew Player81 shared sequence"
                );

            System.out.println(
                "PLAYER81_TRANSFORM_OWNERSHIP_LINEARIZATION_PASS "+
                "transformOwnedSectionEntered=true "+
                "unregisterBlockedDuringTransform=true "+
                "unregisterCompletedAfterTransform=true "+
                "staleTransformRejected=true"
            );
        }finally{
            transformThread.join(1_000L);
            unregisterThread.join(1_000L);

            boolean threadsStopped=
                !transformThread.isAlive()&&
                !unregisterThread.isAlive();

            if(threadsStopped){
                Player81WorldSync.unregister(
                    writer
                );

                if(player.registered())
                    world.unregisterPlayer(
                        player,
                        player.generation()
                    );

                world.close();
            }
        }
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

    private Player81TransformOwnershipLinearizationTest(){}
}
