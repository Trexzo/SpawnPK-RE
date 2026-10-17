package spk.local;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Permanent regression for Issue #1574.
 *
 * A prepared packet81 commit must retain exact writer-context authority for the
 * whole semantic+transport commit callback. Concurrent unregister/rebind may
 * linearize before that critical section or after it, never through it.
 */
public final class Player81ContextRetirementLinearizationTest {
    public static void main(String[] args)throws Exception{
        assertUnregisterWaitsForOwnedCommit();

        System.out.println(
            "PLAYER81_CONTEXT_RETIREMENT_LINEARIZATION_PASS "+
            "registryIdentityHeldAcrossCommit=true "+
            "unregisterBlockedUntilCommitExit=true"
        );
    }

    private static void assertUnregisterWaitsForOwnedCommit()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "packet81-retirement-owner"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue();
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

        Player81WorldSync.PreparedBatchStart start=
            Player81WorldSync.beginPreparedBatchStatus(
                writer
            );

        if(start.status!=
                Player81WorldSync
                    .PreparedBatchStartStatus
                    .PREPARED||
           start.prepared==null)
            throw new AssertionError(
                "valid registered context did not prepare"
            );

        CountDownLatch commitEntered=
            new CountDownLatch(1);
        CountDownLatch allowCommit=
            new CountDownLatch(1);
        CountDownLatch unregisterAttempted=
            new CountDownLatch(1);

        AtomicBoolean unregisterFinished=
            new AtomicBoolean(false);
        AtomicBoolean commitAccepted=
            new AtomicBoolean(false);
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        Thread commitThread=
            new Thread(
                ()->{
                    try{
                        boolean accepted=
                            Player81WorldSync
                                .withPreparedBatchOwnership(
                                    start.prepared,
                                    ()->{
                                        commitEntered.countDown();

                                        try{
                                            if(!allowCommit.await(
                                                    5,
                                                    TimeUnit.SECONDS))
                                                throw new IOException(
                                                    "timed out waiting to release prepared commit"
                                                );
                                        }catch(InterruptedException interrupted){
                                            Thread.currentThread().interrupt();
                                            throw new IOException(
                                                "prepared commit interrupted",
                                                interrupted
                                            );
                                        }

                                        Player81WorldSync
                                            .commitPreparedBatchOwned(
                                                start.prepared
                                            );
                                    }
                                );

                        commitAccepted.set(
                            accepted
                        );
                    }catch(Throwable t){
                        failure.compareAndSet(
                            null,
                            t
                        );
                    }
                },
                "packet81-owned-commit"
            );

        Thread unregisterThread=
            new Thread(
                ()->{
                    try{
                        if(!commitEntered.await(
                                5,
                                TimeUnit.SECONDS))
                            throw new AssertionError(
                                "commit callback never entered"
                            );

                        unregisterAttempted.countDown();

                        Player81WorldSync.unregister(
                            writer
                        );

                        unregisterFinished.set(
                            true
                        );
                    }catch(Throwable t){
                        failure.compareAndSet(
                            null,
                            t
                        );
                    }
                },
                "packet81-unregister-race"
            );

        try{
            commitThread.start();

            if(!commitEntered.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "prepared commit callback did not start"
                );

            unregisterThread.start();

            if(!unregisterAttempted.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "unregister contender did not start"
                );

            Thread.sleep(150L);

            if(unregisterFinished.get())
                throw new AssertionError(
                    "unregister retired writer context inside prepared commit critical section"
                );

            allowCommit.countDown();

            commitThread.join(5000L);
            unregisterThread.join(5000L);

            if(commitThread.isAlive()||
               unregisterThread.isAlive())
                throw new AssertionError(
                    "context-retirement regression threads did not terminate"
                );

            Throwable problem=
                failure.get();
            if(problem!=null)
                throw new AssertionError(
                    "context-retirement race failed",
                    problem
                );

            if(!commitAccepted.get())
                throw new AssertionError(
                    "prepared commit lost valid authority"
                );

            if(!unregisterFinished.get())
                throw new AssertionError(
                    "unregister did not complete after commit exit"
                );

            if(!start.prepared.completed)
                throw new AssertionError(
                    "prepared semantic commit did not complete"
                );

            if(Player81WorldSync.clientIndexFor(
                    writer,
                    player
                )!=-1)
                throw new AssertionError(
                    "writer context remained registered after unregister"
                );
        }finally{
            allowCommit.countDown();

            try{
                Player81WorldSync.unregister(
                    writer
                );
            }catch(Throwable ignored){}

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }
}
