package spk.local;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldConcurrentCloseTerminalWaitTest {
    public static void main(String[] args)throws Exception{
        BlockingRepository repository=
            new BlockingRepository();

        World world=
            World.isolatedForTest(
                60_000L,
                repository
            );

        WorldPlayer player=
            new WorldPlayer();

        AtomicReference<
            WorldPlayerPersistence.SaveTicket
        > ticketRef=
            new AtomicReference<>();

        AtomicReference<Throwable> ownerFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> secondFailure=
            new AtomicReference<>();

        CountDownLatch ownerReturned=
            new CountDownLatch(1);
        CountDownLatch secondEntered=
            new CountDownLatch(1);
        CountDownLatch secondReturned=
            new CountDownLatch(1);

        AtomicBoolean secondInterruptRestored=
            new AtomicBoolean();

        try{
            world.registerPlayer(
                player,
                LocalAccountProfiles.PRIMARY
            );
            world.start();

            world.submitAndWait(
                player,
                ()->ticketRef.set(
                    world.persistence()
                        .captureAndSave(
                            LocalAccountProfiles.PRIMARY,
                            player,
                            0,
                            "[world-concurrent-close-test] ",
                            "BLOCK_OWNER_CLOSE"
                        )
                ),
                5_000L
            );

            if(ticketRef.get()==null)
                throw new AssertionError(
                    "save ticket missing"
                );

            if(!repository.started.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "repository save did not start"
                );

            Thread owner=
                new Thread(
                    ()->{
                        try{
                            world.close();
                        }catch(Throwable error){
                            ownerFailure.set(error);
                        }finally{
                            ownerReturned.countDown();
                        }
                    },
                    "world-close-owner-test"
                );

            owner.start();

            waitFor(
                ()->world.closed()&&
                    world.commands().closed(),
                3_000L,
                "owner teardown past command inbox"
            );

            if(ownerReturned.getCount()==0L)
                throw new AssertionError(
                    "owner close returned while repository was blocked"
                );

            Thread second=
                new Thread(
                    ()->{
                        secondEntered.countDown();

                        try{
                            world.close();
                        }catch(Throwable error){
                            secondFailure.set(error);
                        }finally{
                            secondInterruptRestored.set(
                                Thread.currentThread()
                                    .isInterrupted()
                            );
                            secondReturned.countDown();
                        }
                    },
                    "world-close-second-test"
                );

            second.start();

            if(!secondEntered.await(
                    1,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "second closer did not enter"
                );

            second.interrupt();

            if(secondReturned.await(
                    250,
                    TimeUnit.MILLISECONDS))
                throw new AssertionError(
                    "concurrent World.close returned before terminal teardown"
                );

            if(ownerReturned.getCount()==0L)
                throw new AssertionError(
                    "owner close became terminal before blocked repository release"
                );

            repository.release.countDown();

            if(!repository.finished.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "repository did not finish after release"
                );

            if(!ownerReturned.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "owner close did not reach terminal completion"
                );

            if(!secondReturned.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "second close did not return after owner completion"
                );

            owner.join(1_000L);
            second.join(1_000L);

            if(owner.isAlive()||
               second.isAlive())
                throw new AssertionError(
                    "close caller thread retained ownerAlive="+
                    owner.isAlive()+
                    " secondAlive="+
                    second.isAlive()
                );

            if(ownerFailure.get()!=null)
                throw new AssertionError(
                    "owner close failed",
                    ownerFailure.get()
                );

            if(secondFailure.get()!=null)
                throw new AssertionError(
                    "second close failed",
                    secondFailure.get()
                );

            if(!secondInterruptRestored.get())
                throw new AssertionError(
                    "second closer interrupt status not restored"
                );

            if(!world.closed())
                throw new AssertionError(
                    "World terminal fence not retained"
                );

            if(!world.commands().closed())
                throw new AssertionError(
                    "command inbox not terminal"
                );

            WorldPlayerPersistence.SaveTicket ticket=
                ticketRef.get();

            if(!ticket.completion.isDone()||
               ticket.completion
                    .isCompletedExceptionally())
                throw new AssertionError(
                    "released save did not complete cleanly"
                );

            // Latch is already terminal: repeated close must be harmless.
            world.close();

            System.out.println(
                "WORLD_CONCURRENT_CLOSE_TERMINAL_WAIT_PASS "+
                "secondBlockedUntilTerminal=true "+
                "interruptRestored=true "+
                "ownerTerminal=true "+
                "postTerminalIdempotent=true"
            );
        }finally{
            repository.release.countDown();

            if(player.registered())
                world.unregisterPlayer(player);

            world.close();
        }
    }

    private static void waitFor(
        Check condition,
        long timeoutMillis,
        String label
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );

        while(System.nanoTime()<deadline){
            if(condition.ok())
                return;

            Thread.sleep(5L);
        }

        throw new AssertionError(
            label+" timeout"
        );
    }

    private interface Check {
        boolean ok();
    }

    private static final class BlockingRepository
        implements PlayerRepository {

        final CountDownLatch started=
            new CountDownLatch(1);
        final CountDownLatch release=
            new CountDownLatch(1);
        final CountDownLatch finished=
            new CountDownLatch(1);

        @Override public Optional<PlayerSnapshot> load(
            String username
        ){
            return Optional.empty();
        }

        @Override public void save(
            PlayerSnapshot snapshot
        )throws IOException{
            started.countDown();

            try{
                while(true){
                    try{
                        release.await();
                        return;
                    }catch(InterruptedException ignored){
                        // Deliberately ignore interruption so World.close()
                        // remains inside persistence teardown.
                    }
                }
            }finally{
                finished.countDown();
            }
        }
    }

    private WorldConcurrentCloseTerminalWaitTest(){}
}
