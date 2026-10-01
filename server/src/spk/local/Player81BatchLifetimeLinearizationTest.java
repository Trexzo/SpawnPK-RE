package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class Player81BatchLifetimeLinearizationTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(
            player,
            "player81-batch-lifetime"
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
            player,
            new DevAuthorityWorkbench()
        );

        try{
            assertAbortLinearized(
                world,
                player,
                generation,
                writer,
                queue
            );

            drain(queue);

            assertEndLinearized(
                world,
                player,
                generation,
                writer,
                queue
            );

            drain(queue);

            assertBeginLinearized(
                world,
                player,
                generation,
                writer,
                queue
            );

            System.out.println(
                "PLAYER81_BATCH_LIFETIME_LINEARIZATION_PASS "+
                "abortWaitedForInflight=true "+
                "abortZeroBytes=true "+
                "abortZeroSemanticCommit=true "+
                "endWaitedForInflight=true "+
                "endCommittedBytesAndSemantic=true "+
                "beginWaitedForUnbatched=true "+
                "unbatchedStayedOutsideNewBatch=true"
            );
        }finally{
            Player81WorldSync.unregister(
                writer
            );

            if(player.registered())
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void assertAbortLinearized(
        World world,
        WorldPlayer player,
        long generation,
        ServerPacketWriter writer,
        OutboundPacketQueue queue
    )throws Exception{
        writer.beginBatch();

        long before=
            Player81WorldSync
                .latestPublishedEventSequence(
                    writer
                );

        Hold hold=
            Hold.start(
                world,
                player,
                generation
            );

        AtomicReference<Throwable>
            packetFailure=
                new AtomicReference<>();
        AtomicReference<Throwable>
            abortFailure=
                new AtomicReference<>();

        Thread packet=
            packetThread(
                writer,
                packetFailure,
                "player81-abort-packet"
            );

        packet.start();
        awaitWorldBlocked(
            packet
        );

        Thread abort=
            new Thread(
                ()->{
                    try{
                        writer.abortBatch();
                    }catch(Throwable failure){
                        abortFailure.set(
                            failure
                        );
                    }
                },
                "player81-abort-batch"
            );

        abort.start();
        awaitBlocked(
            abort,
            "abort did not wait for in-flight packet81"
        );

        if(queue.queuedBytes()!=0)
            throw new AssertionError(
                "abort race published bytes before release"
            );

        hold.release();
        join(hold.thread,"abort world holder");
        join(packet,"abort packet");
        join(abort,"abort batch");

        throwIfSet(
            packetFailure,
            "abort packet failed"
        );
        throwIfSet(
            abortFailure,
            "abort failed"
        );
        throwIfSet(
            hold.failure,
            "abort holder failed"
        );

        if(queue.queuedBytes()!=0)
            throw new AssertionError(
                "aborted packet81 escaped bytes="+
                queue.queuedBytes()
            );

        long after=
            Player81WorldSync
                .latestPublishedEventSequence(
                    writer
                );

        if(after!=before)
            throw new AssertionError(
                "aborted packet81 committed semantic state before="+
                before+
                " after="+
                after
            );
    }

    private static void assertEndLinearized(
        World world,
        WorldPlayer player,
        long generation,
        ServerPacketWriter writer,
        OutboundPacketQueue queue
    )throws Exception{
        writer.beginBatch();

        long before=
            Player81WorldSync
                .latestPublishedEventSequence(
                    writer
                );

        Hold hold=
            Hold.start(
                world,
                player,
                generation
            );

        AtomicReference<Throwable>
            packetFailure=
                new AtomicReference<>();
        AtomicReference<Throwable>
            endFailure=
                new AtomicReference<>();

        Thread packet=
            packetThread(
                writer,
                packetFailure,
                "player81-end-packet"
            );

        packet.start();
        awaitWorldBlocked(
            packet
        );

        Thread end=
            new Thread(
                ()->{
                    try{
                        writer.endBatch();
                    }catch(Throwable failure){
                        endFailure.set(
                            failure
                        );
                    }
                },
                "player81-end-batch"
            );

        end.start();
        awaitBlocked(
            end,
            "endBatch did not wait for in-flight packet81"
        );

        if(queue.queuedBytes()!=0)
            throw new AssertionError(
                "end race published before packet81 joined batch"
            );

        hold.release();
        join(hold.thread,"end world holder");
        join(packet,"end packet");
        join(end,"end batch");

        throwIfSet(
            packetFailure,
            "end packet failed"
        );
        throwIfSet(
            endFailure,
            "endBatch failed"
        );
        throwIfSet(
            hold.failure,
            "end holder failed"
        );

        if(queue.queuedBytes()==0)
            throw new AssertionError(
                "endBatch committed no packet81 bytes"
            );

        long after=
            Player81WorldSync
                .latestPublishedEventSequence(
                    writer
                );

        if(after<=before)
            throw new AssertionError(
                "endBatch bytes committed without semantic state before="+
                before+
                " after="+
                after
            );
    }

    private static void assertBeginLinearized(
        World world,
        WorldPlayer player,
        long generation,
        ServerPacketWriter writer,
        OutboundPacketQueue queue
    )throws Exception{
        long before=
            Player81WorldSync
                .latestPublishedEventSequence(
                    writer
                );

        Hold hold=
            Hold.start(
                world,
                player,
                generation
            );

        AtomicReference<Throwable>
            packetFailure=
                new AtomicReference<>();
        AtomicReference<Throwable>
            beginFailure=
                new AtomicReference<>();

        Thread packet=
            packetThread(
                writer,
                packetFailure,
                "player81-unbatched-packet"
            );

        packet.start();
        awaitWorldBlocked(
            packet
        );

        Thread begin=
            new Thread(
                ()->{
                    try{
                        writer.beginBatch();
                    }catch(Throwable failure){
                        beginFailure.set(
                            failure
                        );
                    }
                },
                "player81-concurrent-begin"
            );

        begin.start();
        awaitBlocked(
            begin,
            "beginBatch did not wait for in-flight unbatched packet81"
        );

        hold.release();
        join(hold.thread,"begin world holder");
        join(packet,"unbatched packet");
        join(begin,"concurrent begin");

        throwIfSet(
            packetFailure,
            "unbatched packet failed"
        );
        throwIfSet(
            beginFailure,
            "beginBatch failed"
        );
        throwIfSet(
            hold.failure,
            "begin holder failed"
        );

        if(queue.queuedBytes()==0)
            throw new AssertionError(
                "unbatched packet81 was captured by later batch"
            );

        long after=
            Player81WorldSync
                .latestPublishedEventSequence(
                    writer
                );

        if(after<=before)
            throw new AssertionError(
                "unbatched packet81 bytes lacked semantic commit before="+
                before+
                " after="+
                after
            );

        writer.abortBatch();
    }

    private static Thread packetThread(
        ServerPacketWriter writer,
        AtomicReference<Throwable> failure,
        String name
    ){
        Thread thread=
            new Thread(
                ()->{
                    try{
                        writer.varShort(
                            81,
                            BootstrapPackets
                                .player81Idle()
                        );
                    }catch(Throwable error){
                        failure.set(
                            error
                        );
                    }
                },
                name
            );

        thread.setDaemon(true);
        return thread;
    }

    private static void awaitWorldBlocked(
        Thread thread
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(5L);

        while(System.nanoTime()<deadline){
            if(thread.getState()==
                    Thread.State.BLOCKED){
                for(StackTraceElement element:
                        thread.getStackTrace()){
                    if(Player81WorldSync.class
                            .getName()
                            .equals(
                                element.getClassName()
                            ))
                        return;
                }
            }

            if(!thread.isAlive())
                throw new AssertionError(
                    "packet81 exited before World ownership barrier"
                );

            Thread.sleep(1L);
        }

        throw new AssertionError(
            "packet81 did not block in Player81 World ownership"
        );
    }

    private static void awaitBlocked(
        Thread thread,
        String message
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(5L);

        while(System.nanoTime()<deadline){
            if(thread.getState()==
                    Thread.State.BLOCKED)
                return;

            if(!thread.isAlive())
                throw new AssertionError(
                    message+
                    " (thread exited)"
                );

            Thread.sleep(1L);
        }

        throw new AssertionError(
            message
        );
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream sink=
            new ByteArrayOutputStream();

        while(queue.queuedBytes()>0)
            queue.drainTo(
                sink,
                1<<20
            );
    }

    private static void join(
        Thread thread,
        String name
    )throws Exception{
        thread.join(
            5_000L
        );

        if(thread.isAlive())
            throw new AssertionError(
                name+
                " did not terminate"
            );
    }

    private static void throwIfSet(
        AtomicReference<Throwable> failure,
        String message
    ){
        Throwable caught=
            failure.get();

        if(caught!=null)
            throw new AssertionError(
                message,
                caught
            );
    }

    private static final class Hold {
        final Thread thread;
        final CountDownLatch release;
        final AtomicReference<Throwable> failure;

        private Hold(
            Thread thread,
            CountDownLatch release,
            AtomicReference<Throwable> failure
        ){
            this.thread=thread;
            this.release=release;
            this.failure=failure;
        }

        static Hold start(
            World world,
            WorldPlayer player,
            long generation
        )throws Exception{
            CountDownLatch entered=
                new CountDownLatch(1);
            CountDownLatch release=
                new CountDownLatch(1);
            AtomicReference<Throwable>
                failure=
                    new AtomicReference<>();

            Thread thread=
                new Thread(
                    ()->{
                        try{
                            boolean accepted=
                                world.withOpenPlayerOwnershipIfCurrent(
                                    player,
                                    generation,
                                    ()->{
                                        entered.countDown();

                                        try{
                                            if(!release.await(
                                                    5L,
                                                    TimeUnit.SECONDS))
                                                throw new IOException(
                                                    "hold release timeout"
                                                );
                                        }catch(InterruptedException interrupted){
                                            Thread.currentThread()
                                                .interrupt();
                                            throw new IOException(
                                                "hold interrupted",
                                                interrupted
                                            );
                                        }
                                    }
                                );

                            if(!accepted)
                                throw new AssertionError(
                                    "World ownership hold rejected"
                                );
                        }catch(Throwable error){
                            failure.set(
                                error
                            );
                        }
                    },
                    "player81-world-hold"
                );

            thread.setDaemon(true);
            thread.start();

            if(!entered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "World ownership hold did not enter"
                );

            return new Hold(
                thread,
                release,
                failure
            );
        }

        void release(){
            release.countDown();
        }
    }

    private Player81BatchLifetimeLinearizationTest(){}
}
