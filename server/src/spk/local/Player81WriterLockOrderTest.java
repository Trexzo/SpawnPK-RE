package spk.local;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class Player81WriterLockOrderTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer viewer=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                viewer,
                "player81-lock-order"
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

        Player81WorldSync.register(
            writer,
            world,
            viewer,
            new DevAuthorityWorkbench()
        );

        SharedNpcWorldRelay.register(
            writer,
            world,
            viewer,
            new NpcRegistry(
                new DevAuthorityWorkbench()
            ),
            viewer.movement()
        );

        CountDownLatch lifecycleHeld=
            new CountDownLatch(1);
        CountDownLatch allowWriterAttempt=
            new CountDownLatch(1);
        CountDownLatch lifecycleWriterDone=
            new CountDownLatch(1);

        AtomicBoolean lifecycleAccepted=
            new AtomicBoolean();
        AtomicReference<Throwable>
            lifecycleFailure=
                new AtomicReference<>();
        AtomicReference<Throwable>
            player81Failure=
                new AtomicReference<>();

        Thread lifecycleThread=
            new Thread(
                ()->{
                    try{
                        boolean accepted=
                            world.withOpenPlayerOwnershipIfCurrent(
                                viewer,
                                generation,
                                ()->{
                                    lifecycleHeld.countDown();

                                    try{
                                        if(!allowWriterAttempt.await(
                                                5L,
                                                TimeUnit.SECONDS))
                                            throw new IOException(
                                                "writer attempt release timeout"
                                            );
                                    }catch(InterruptedException interrupted){
                                        Thread.currentThread()
                                            .interrupt();
                                        throw new IOException(
                                            "interrupted",
                                            interrupted
                                        );
                                    }

                                    writer.fixed(
                                        134,
                                        new byte[0]
                                    );

                                    lifecycleWriterDone
                                        .countDown();
                                }
                            );

                        lifecycleAccepted.set(
                            accepted
                        );
                    }catch(Throwable failure){
                        lifecycleFailure.set(
                            failure
                        );
                    }
                },
                "player81-lock-order-lifecycle"
            );

        Thread player81Thread=
            new Thread(
                ()->{
                    try{
                        writer.varShort(
                            81,
                            BootstrapPackets
                                .player81Idle()
                        );
                    }catch(Throwable failure){
                        player81Failure.set(
                            failure
                        );
                    }
                },
                "player81-lock-order-writer"
            );

        lifecycleThread.setDaemon(true);
        player81Thread.setDaemon(true);

        try{
            lifecycleThread.start();

            if(!lifecycleHeld.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "lifecycle owner did not enter"
                );

            player81Thread.start();

            awaitBlockedOnWorldOwnership(
                player81Thread,
                5_000L
            );

            if(queue.queuedBytes()!=0)
                throw new AssertionError(
                    "packet81 published before exact World ownership was available"
                );

            allowWriterAttempt.countDown();

            if(!lifecycleWriterDone.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "lifecycle owner could not acquire same writer; lock inversion remains"
                );

            lifecycleThread.join(
                5_000L
            );

            player81Thread.join(
                5_000L
            );

            if(lifecycleThread.isAlive()||
               player81Thread.isAlive())
                throw new AssertionError(
                    "lock-order threads did not terminate"
                );

            if(lifecycleFailure.get()!=null)
                throw new AssertionError(
                    "lifecycle thread failed",
                    lifecycleFailure.get()
                );

            if(player81Failure.get()!=null)
                throw new AssertionError(
                    "packet81 thread failed",
                    player81Failure.get()
                );

            if(!lifecycleAccepted.get())
                throw new AssertionError(
                    "World ownership section was not accepted"
                );

            if(queue.queuedPackets()<2)
                throw new AssertionError(
                    "expected packet81 plus lifecycle-owned writer publication packets="+
                    queue.queuedPackets()
                );

            System.out.println(
                "PLAYER81_WRITER_LOCK_ORDER_PASS "+
                "packet81WaitedForWorldOwnership=true "+
                "writerMonitorFreeWhileWaiting=true "+
                "lifecycleOwnerWriterWriteSucceeded=true "+
                "bothThreadsCompleted=true"
            );
        }finally{
            allowWriterAttempt.countDown();

            lifecycleThread.join(
                1_000L
            );
            player81Thread.join(
                1_000L
            );

            boolean threadsStopped=
                !lifecycleThread.isAlive()&&
                !player81Thread.isAlive();

            if(threadsStopped){
                SharedNpcWorldRelay.unregister(
                    writer
                );
                Player81WorldSync.unregister(
                    writer
                );

                if(viewer.registered())
                    world.unregisterPlayer(
                        viewer,
                        generation
                    );

                world.close();
            }
        }
    }

    private static void awaitBlockedOnWorldOwnership(
        Thread thread,
        long timeoutMillis
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );

        while(System.nanoTime()<deadline){
            if(thread.getState()==
                    Thread.State.BLOCKED){
                boolean inPlayer81OwnershipWait=false;

                for(StackTraceElement element:
                        thread.getStackTrace()){
                    if(!Player81WorldSync.class
                            .getName()
                            .equals(
                                element.getClassName()
                            ))
                        continue;

                    String method=
                        element.getMethodName();

                    if("transform".equals(method)||
                       "transformPrepared".equals(method)||
                       "beginPreparedBatchStatus".equals(method)||
                       "withPreparedBatchOwnership".equals(method)){
                        inPlayer81OwnershipWait=true;
                        break;
                    }
                }

                if(inPlayer81OwnershipWait)
                    return;
            }

            if(!thread.isAlive())
                throw new AssertionError(
                    "packet81 call exited before World ownership wait"
                );

            Thread.sleep(1L);
        }

        throw new AssertionError(
            "packet81 did not block waiting for World ownership"
        );
    }

    private Player81WriterLockOrderTest(){}
}
