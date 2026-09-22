package spk.local;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldCommandCloseAdmissionFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(25L);

        WorldPlayer player=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                player,
                "command-close-owner"
            );

        CountDownLatch targetEntered=
            new CountDownLatch(1);
        CountDownLatch releaseTarget=
            new CountDownLatch(1);
        AtomicBoolean preCloseExecuted=
            new AtomicBoolean();
        AtomicBoolean closeWindowExecuted=
            new AtomicBoolean();
        AtomicReference<Throwable> closeFailure=
            new AtomicReference<>();

        world.attachTickTarget(
            new WorldTickTarget(){
                private final AtomicBoolean blocked=
                    new AtomicBoolean();

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
                    if(!blocked.compareAndSet(
                            false,
                            true
                        ))
                        return;

                    targetEntered.countDown();

                    boolean interrupted=false;

                    while(releaseTarget.getCount()>0L){
                        try{
                            releaseTarget.await(
                                10L,
                                TimeUnit.MILLISECONDS
                            );
                        }catch(InterruptedException ignored){
                            interrupted=true;
                        }
                    }

                    if(interrupted)
                        Thread.currentThread()
                            .interrupt();
                }
            }
        );

        CompletableFuture<Void> preClose=
            world.submit(
                player,
                generation,
                ()->preCloseExecuted.set(true)
            );

        Thread closeThread=null;

        try{
            world.start();

            preClose.get(
                5L,
                TimeUnit.SECONDS
            );

            if(!preCloseExecuted.get())
                throw new AssertionError(
                    "pre-close command did not execute"
                );

            if(!targetEntered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "blocking tick target did not start"
                );

            closeThread=
                new Thread(
                    ()->{
                        try{
                            world.close();
                        }catch(Throwable error){
                            closeFailure.set(error);
                        }
                    },
                    "world-command-close-owner"
                );
            closeThread.start();

            long closedDeadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(2L);

            while(!world.closed()&&
                  System.nanoTime()<closedDeadline)
                Thread.sleep(1L);

            if(!world.closed())
                throw new AssertionError(
                    "World close boundary was not published"
                );

            if(!closeThread.isAlive())
                throw new AssertionError(
                    "close owner left shutdown window before command admission test"
                );

            int sizeBefore=
                world.commands().size();
            int ownerQueuedBefore=
                world.commands()
                    .queuedFor(player.id());

            CompletableFuture<Void> closeWindow=
                world.submit(
                    player,
                    generation,
                    ()->closeWindowExecuted
                        .set(true)
                );

            if(!closeWindow.isDone())
                throw new AssertionError(
                    "close-window command was not rejected immediately"
                );

            assertWorldClosedRejection(
                closeWindow,
                "close-window"
            );

            if(world.commands().size()!=sizeBefore)
                throw new AssertionError(
                    "close-window submit grew command queue"
                );

            if(world.commands()
                    .queuedFor(player.id())!=
                    ownerQueuedBefore)
                throw new AssertionError(
                    "close-window submit changed per-player queue accounting"
                );

            if(closeWindowExecuted.get())
                throw new AssertionError(
                    "close-window command executed"
                );

            releaseTarget.countDown();

            closeThread.join(5_000L);

            if(closeThread.isAlive())
                throw new AssertionError(
                    "World close did not complete after target release"
                );

            if(closeFailure.get()!=null)
                throw new AssertionError(
                    "World close failed",
                    closeFailure.get()
                );

            CompletableFuture<Void> postTerminal=
                world.submit(
                    player,
                    generation,
                    ()->{
                        throw new AssertionError(
                            "post-terminal command executed"
                        );
                    }
                );

            assertWorldClosedRejection(
                postTerminal,
                "post-terminal"
            );

            if(world.commands().size()!=0)
                throw new AssertionError(
                    "terminal command queue not empty"
                );

            System.out.println(
                "WORLD_COMMAND_CLOSE_ADMISSION_FENCE_PASS "+
                "preCloseExecuted=true "+
                "closeWindowRejected=true "+
                "closeWindowQueueUnchanged=true "+
                "postTerminalRejected=true"
            );
        }finally{
            releaseTarget.countDown();

            if(closeThread!=null&&
               closeThread.isAlive())
                closeThread.join(5_000L);

            world.close();
        }
    }

    private static void assertWorldClosedRejection(
        CompletableFuture<Void> future,
        String phase
    ){
        boolean rejected=false;

        try{
            future.join();
        }catch(CompletionException error){
            Throwable cause=error.getCause();

            rejected=
                cause instanceof RejectedExecutionException&&
                "WORLD_CLOSED".equals(
                    cause.getMessage()
                );
        }

        if(!rejected)
            throw new AssertionError(
                phase+
                " command did not fail with WORLD_CLOSED"
            );
    }
}
