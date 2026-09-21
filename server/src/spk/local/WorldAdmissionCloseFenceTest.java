package spk.local;

import java.util.concurrent.*;

public final class WorldAdmissionCloseFenceTest {
    public static void main(String[] args)throws Exception{
        testPostCloseRegistrationRejected();
        testPostCloseTickTargetRejected();
        testAtomicAdmissionStartsPulse();
        testConcurrentCloseAdmissionInvariant();

        System.out.println(
            "WORLD_ADMISSION_CLOSE_FENCE_PASS "+
            "postCloseRegisterRejected=true "+
            "postCloseTickAttachRejected=true "+
            "atomicAdmissionStartsPulse=true "+
            "raceFailureAtomic=true "+
            "postCloseUnregisterAllowed=true"
        );
    }

    private static void testPostCloseRegistrationRejected(){
        World world=
            World.isolatedForTest(
                60_000L
            );

        WorldPlayer player=
            new WorldPlayer();

        world.close();

        assertRejected(
            ()->world.registerPlayer(
                player,
                "closed-register"
            ),
            "post-close register"
        );

        if(world.players().size()!=0||
           player.registered())
            throw new AssertionError(
                "post-close register mutated membership"
            );

        world.close();
    }

    private static void testPostCloseTickTargetRejected(){
        World world=
            World.isolatedForTest(
                60_000L
            );

        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "closed-tick-target"
            );

        WorldTickTarget target=
            new WorldTickTarget(){
                @Override public EntityId ownerId(){
                    return player.id();
                }

                @Override public long ownerGeneration(){
                    return generation;
                }

                @Override public void onWorldTick(
                    long worldTick,
                    long nowMillis
                ){
                }
            };

        world.close();

        assertRejected(
            ()->world.attachTickTarget(
                target
            ),
            "post-close tick target attach"
        );

        if(!world.tickTargetsSnapshot().isEmpty())
            throw new AssertionError(
                "post-close tick target was attached"
            );

        if(!world.unregisterPlayer(player))
            throw new AssertionError(
                "post-close unregister was rejected"
            );

        if(player.registered()||
           world.players().size()!=0)
            throw new AssertionError(
                "post-close unregister did not clean membership"
            );

        world.close();
    }

    private static void testAtomicAdmissionStartsPulse(){
        World world=
            World.isolatedForTest(
                60_000L
            );

        WorldPlayer player=
            new WorldPlayer();

        try{
            long generation=
                world.registerPlayerAndStart(
                    player,
                    "atomic-admission"
                );

            if(!player.accepts(generation)||
               world.players().byId(
                   player.id())!=player)
                throw new AssertionError(
                    "atomic admission did not register player"
                );

            if(!world.pulse().running())
                throw new AssertionError(
                    "atomic admission did not start pulse"
                );
        }finally{
            world.close();

            if(player.registered())
                world.unregisterPlayer(
                    player
                );
        }
    }

    private static void testConcurrentCloseAdmissionInvariant()
        throws Exception{
        int admitted=0;
        int rejected=0;

        for(int iteration=0;
            iteration<40;
            iteration++){
            World world=
                World.isolatedForTest(
                    60_000L
                );

            WorldPlayer player=
                new WorldPlayer();

            ExecutorService executor=
                Executors.newFixedThreadPool(2);

            CountDownLatch ready=
                new CountDownLatch(2);
            CountDownLatch go=
                new CountDownLatch(1);

            try{
                Future<Boolean> admission=
                    executor.submit(
                        ()->{
                            ready.countDown();
                            go.await();

                            try{
                                world.registerPlayerAndStart(
                                    player,
                                    "race-admission"
                                );
                                return Boolean.TRUE;
                            }catch(IllegalStateException expected){
                                return Boolean.FALSE;
                            }
                        }
                    );

                Future<?> closing=
                    executor.submit(
                        ()->{
                            ready.countDown();

                            try{
                                go.await();
                            }catch(InterruptedException e){
                                Thread.currentThread()
                                    .interrupt();
                                throw new RuntimeException(e);
                            }

                            world.close();
                        }
                    );

                if(!ready.await(
                        2,
                        TimeUnit.SECONDS))
                    throw new AssertionError(
                        "race workers not ready iteration="+
                        iteration
                    );

                go.countDown();

                boolean succeeded=
                    admission.get(
                        5,
                        TimeUnit.SECONDS
                    );

                closing.get(
                    5,
                    TimeUnit.SECONDS
                );

                if(succeeded){
                    admitted++;

                    if(!player.registered()||
                       world.players().byId(
                           player.id())!=player)
                        throw new AssertionError(
                            "successful admission lost membership iteration="+
                            iteration
                        );
                }else{
                    rejected++;

                    if(player.registered()||
                       world.players().size()!=0)
                        throw new AssertionError(
                            "rejected admission left membership iteration="+
                            iteration
                        );
                }

                if(player.registered()&&
                   !world.unregisterPlayer(
                       player))
                    throw new AssertionError(
                        "post-close cleanup failed iteration="+
                        iteration
                    );

                if(world.players().size()!=0)
                    throw new AssertionError(
                        "race cleanup left membership iteration="+
                        iteration
                    );
            }finally{
                go.countDown();

                executor.shutdownNow();
                executor.awaitTermination(
                    2,
                    TimeUnit.SECONDS
                );

                if(player.registered())
                    world.unregisterPlayer(
                        player
                    );

                world.close();
            }
        }

        if(admitted+rejected!=40)
            throw new AssertionError(
                "race outcomes missing admitted="+
                admitted+
                " rejected="+
                rejected
            );
    }

    private static void assertRejected(
        Runnable action,
        String label
    ){
        boolean rejected=false;

        try{
            action.run();
        }catch(IllegalStateException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                label+" was accepted"
            );
    }

    private WorldAdmissionCloseFenceTest(){}
}
