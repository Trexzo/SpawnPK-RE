package spk.local;

import java.net.ServerSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class LocalServerShutdownCoordinatorConcurrentCloseTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );

        ExecutorService pool=
            Executors.newSingleThreadExecutor();

        ServerSocket game=
            new ServerSocket();
        ServerSocket aux=
            new ServerSocket();

        LocalServerShutdownCoordinator shutdown=
            new LocalServerShutdownCoordinator(
                world,
                pool,
                game,
                aux
            );

        CountDownLatch taskEntered=
            new CountDownLatch(1);
        CountDownLatch releaseTask=
            new CountDownLatch(1);
        AtomicInteger taskInterrupts=
            new AtomicInteger();

        if(!shutdown.submitAuxiliary(
                ()->{
                    taskEntered.countDown();

                    while(true){
                        try{
                            releaseTask.await();
                            return;
                        }catch(InterruptedException ignored){
                            taskInterrupts.incrementAndGet();
                        }
                    }
                }))
            throw new AssertionError(
                "blocking teardown task rejected"
            );

        if(!taskEntered.await(
                2,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "blocking teardown task did not start"
            );

        CountDownLatch ownerReturned=
            new CountDownLatch(1);

        Thread owner=
            new Thread(
                ()->{
                    try{
                        shutdown.close();
                    }finally{
                        ownerReturned.countDown();
                    }
                },
                "shutdown-owner-test"
            );

        owner.start();

        long closingDeadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(2);

        while(!shutdown.closing()&&
              System.nanoTime()<closingDeadline)
            Thread.yield();

        if(!shutdown.closing())
            throw new AssertionError(
                "owner did not enter closing state"
            );

        CountDownLatch secondEntered=
            new CountDownLatch(1);
        CountDownLatch secondReturned=
            new CountDownLatch(1);
        AtomicBoolean secondInterruptRestored=
            new AtomicBoolean();

        Thread second=
            new Thread(
                ()->{
                    secondEntered.countDown();

                    try{
                        shutdown.close();
                    }finally{
                        secondInterruptRestored.set(
                            Thread.currentThread()
                                .isInterrupted()
                        );
                        secondReturned.countDown();
                    }
                },
                "shutdown-second-test"
            );

        second.start();

        if(!secondEntered.await(
                1,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "second closer did not enter"
            );

        second.interrupt();

        Thread.sleep(200L);

        if(secondReturned.getCount()==0L)
            throw new AssertionError(
                "interrupted second close returned before terminal shutdown"
            );

        if(ownerReturned.getCount()==0L)
            throw new AssertionError(
                "owner returned while teardown task was still blocked"
            );

        releaseTask.countDown();

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
                "second close did not return after terminal completion"
            );

        owner.join(
            1_000L
        );
        second.join(
            1_000L
        );

        if(owner.isAlive()||
           second.isAlive())
            throw new AssertionError(
                "close caller thread retained ownerAlive="+
                owner.isAlive()+
                " secondAlive="+
                second.isAlive()
            );

        if(!secondInterruptRestored.get())
            throw new AssertionError(
                "second caller interrupt status not restored"
            );

        if(!pool.isTerminated())
            throw new AssertionError(
                "session pool not terminal"
            );

        if(!world.closed())
            throw new AssertionError(
                "World not terminal after owner close"
            );

        long repeatedStart=
            System.nanoTime();

        shutdown.close();

        long repeatedMillis=
            TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime()-
                repeatedStart
            );

        if(repeatedMillis>250L)
            throw new AssertionError(
                "post-terminal close unexpectedly blocked "+
                repeatedMillis+"ms"
            );

        System.out.println(
            "LOCAL_SERVER_CONCURRENT_SHUTDOWN_WAIT_PASS "+
            "secondBlockedUntilTerminal=true "+
            "interruptRestored=true "+
            "ownerTerminal=true "+
            "postTerminalIdempotent=true "+
            "taskInterrupts="+
            taskInterrupts.get()
        );
    }

    private LocalServerShutdownCoordinatorConcurrentCloseTest(){}
}
