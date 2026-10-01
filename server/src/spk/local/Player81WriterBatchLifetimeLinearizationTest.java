package spk.local;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression for packet-81 operation classification versus writer batch lifetime.
 *
 * Player81 semantic work deliberately runs outside the writer monitor because it
 * acquires World lifecycle ownership. Batch begin/end/abort must therefore fail
 * closed while that unlocked semantic operation is in flight, rather than
 * changing the writer transaction underneath it.
 */
public final class Player81WriterBatchLifetimeLinearizationTest {
    public static void main(String[] args)throws Exception{
        testAbortRejectedWhileStagedPacket81InFlight();
        testEndRejectedWhileStagedPacket81InFlight();
        testBeginRejectedWhileUnbatchedPacket81InFlight();

        System.out.println(
            "PLAYER81_WRITER_BATCH_LIFETIME_PASS "+
            "abortRejectedWhileInFlight=true "+
            "endRejectedWhileInFlight=true "+
            "beginRejectedWhileUnbatchedInFlight=true "+
            "abortNoEscape=true "+
            "endCommitAfterJoin=true"
        );
    }

    private static void testAbortRejectedWhileStagedPacket81InFlight()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "player81-batch-lifetime-abort",
                new int[]{41,42,43,44}
            );

        try{
            long sequenceBefore=
                sequence(
                    fixture.world
                );

            fixture.writer.beginBatch();
            fixture.writer.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            LifecycleHold hold=
                LifecycleHold.acquire(
                    fixture.world
                );

            AtomicReference<Throwable> packetFailure=
                new AtomicReference<>();
            Thread packetThread=
                packetThread(
                    fixture.writer,
                    BootstrapPackets.player81WalkStep(4),
                    packetFailure,
                    "player81-batch-lifetime-abort-packet"
                );

            packetThread.start();
            awaitBlockedInPlayer81(
                packetThread,
                5_000L
            );

            boolean abortRejected=false;
            try{
                fixture.writer.abortBatch();
            }catch(IllegalStateException expected){
                abortRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "packet-81 operation is in flight"
                    );
            }

            if(!abortRejected)
                throw new AssertionError(
                    "abort did not reject in-flight packet81"
                );

            if(fixture.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "abort race emitted packet81 before release"
                );

            if(sequence(fixture.world)!=
                    sequenceBefore)
                throw new AssertionError(
                    "abort race committed Player81 semantics before release"
                );

            hold.release();
            hold.awaitDone();

            packetThread.join(
                5_000L
            );

            requireThreadSuccess(
                packetThread,
                packetFailure,
                "staged packet81 after abort rejection"
            );

            if(fixture.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "staged packet81 escaped before explicit outer commit"
                );

            fixture.writer.abortBatch();

            if(fixture.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "aborted packet81 batch emitted bytes"
                );

            if(sequence(fixture.world)!=
                    sequenceBefore)
                throw new AssertionError(
                    "aborted packet81 batch committed semantic state"
                );
        }finally{
            fixture.close();
        }
    }

    private static void testEndRejectedWhileStagedPacket81InFlight()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "player81-batch-lifetime-end",
                new int[]{45,46,47,48}
            );

        try{
            long sequenceBefore=
                sequence(
                    fixture.world
                );

            fixture.writer.beginBatch();
            fixture.writer.varShort(
                81,
                BootstrapPackets.player81Idle()
            );

            LifecycleHold hold=
                LifecycleHold.acquire(
                    fixture.world
                );

            AtomicReference<Throwable> packetFailure=
                new AtomicReference<>();
            Thread packetThread=
                packetThread(
                    fixture.writer,
                    BootstrapPackets.player81WalkStep(6),
                    packetFailure,
                    "player81-batch-lifetime-end-packet"
                );

            packetThread.start();
            awaitBlockedInPlayer81(
                packetThread,
                5_000L
            );

            boolean endRejected=false;
            try{
                fixture.writer.endBatch();
            }catch(IllegalStateException expected){
                endRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "packet-81 operation is in flight"
                    );
            }

            if(!endRejected)
                throw new AssertionError(
                    "endBatch did not reject in-flight packet81"
                );

            if(fixture.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "end race emitted packet81 before transform joined batch"
                );

            if(sequence(fixture.world)!=
                    sequenceBefore)
                throw new AssertionError(
                    "end race committed semantic state before packet joined batch"
                );

            hold.release();
            hold.awaitDone();

            packetThread.join(
                5_000L
            );

            requireThreadSuccess(
                packetThread,
                packetFailure,
                "staged packet81 after end rejection"
            );

            if(fixture.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "staged packet81 emitted before explicit retry endBatch"
                );

            fixture.writer.endBatch();

            if(fixture.queue.queuedBytes()==0)
                throw new AssertionError(
                    "healthy endBatch emitted no packet81 bytes"
                );

            if(sequence(fixture.world)<=
                    sequenceBefore)
                throw new AssertionError(
                    "healthy endBatch did not commit Player81 semantics"
                );
        }finally{
            fixture.close();
        }
    }

    private static void testBeginRejectedWhileUnbatchedPacket81InFlight()
        throws Exception
    {
        Fixture fixture=
            new Fixture(
                "player81-batch-lifetime-begin",
                new int[]{49,50,51,52}
            );

        try{
            long sequenceBefore=
                sequence(
                    fixture.world
                );

            LifecycleHold hold=
                LifecycleHold.acquire(
                    fixture.world
                );

            AtomicReference<Throwable> packetFailure=
                new AtomicReference<>();
            Thread packetThread=
                packetThread(
                    fixture.writer,
                    BootstrapPackets.player81WalkStep(2),
                    packetFailure,
                    "player81-batch-lifetime-begin-packet"
                );

            packetThread.start();
            awaitBlockedInPlayer81(
                packetThread,
                5_000L
            );

            boolean beginRejected=false;
            try{
                fixture.writer.beginBatch();
            }catch(IllegalStateException expected){
                beginRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "packet-81 operation is in flight"
                    );
            }

            if(!beginRejected)
                throw new AssertionError(
                    "beginBatch captured an in-flight unbatched packet81"
                );

            if(fixture.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "blocked unbatched packet81 emitted transport"
                );

            if(sequence(fixture.world)!=
                    sequenceBefore)
                throw new AssertionError(
                    "blocked unbatched packet81 committed semantics"
                );

            hold.release();
            hold.awaitDone();

            packetThread.join(
                5_000L
            );

            requireThreadSuccess(
                packetThread,
                packetFailure,
                "unbatched packet81 after begin rejection"
            );

            if(fixture.queue.queuedBytes()==0)
                throw new AssertionError(
                    "unbatched packet81 emitted no transport after release"
                );

            if(sequence(fixture.world)<=
                    sequenceBefore)
                throw new AssertionError(
                    "unbatched packet81 committed no semantics after release"
                );

            // The operation gate must retire after the packet completes.
            fixture.writer.beginBatch();
            fixture.writer.abortBatch();
        }finally{
            fixture.close();
        }
    }

    private static Thread packetThread(
        ServerPacketWriter writer,
        byte[] body,
        AtomicReference<Throwable> failure,
        String name
    ){
        Thread thread=
            new Thread(
                ()->{
                    try{
                        writer.varShort(
                            81,
                            body
                        );
                    }catch(Throwable problem){
                        failure.set(
                            problem
                        );
                    }
                },
                name
            );
        thread.setDaemon(
            true
        );
        return thread;
    }

    private static void requireThreadSuccess(
        Thread thread,
        AtomicReference<Throwable> failure,
        String label
    ){
        if(thread.isAlive())
            throw new AssertionError(
                label+
                " did not terminate"
            );

        if(failure.get()!=null)
            throw new AssertionError(
                label+
                " failed",
                failure.get()
            );
    }

    private static void awaitBlockedInPlayer81(
        Thread thread,
        long timeoutMillis
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );

        while(System.nanoTime()<
                deadline){
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
                    "packet81 operation exited before World ownership wait"
                );

            Thread.sleep(
                1L
            );
        }

        throw new AssertionError(
            "packet81 operation did not block on World ownership"
        );
    }

    private static long sequence(
        World world
    )throws Exception{
        Object state=
            worldState(
                world
            );

        if(state==null)
            return 0L;

        Field field=
            state.getClass()
                .getDeclaredField(
                    "sequence"
                );
        field.setAccessible(
            true
        );
        return field.getLong(
            state
        );
    }

    private static Object worldState(
        World world
    )throws Exception{
        Field field=
            Player81WorldSync.class
                .getDeclaredField(
                    "BY_WORLD"
                );
        field.setAccessible(
            true
        );

        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            Map<World,?> states=
                (Map<World,?>)
                    field.get(
                        null
                    );
            return states.get(
                world
            );
        }
    }

    private static final class LifecycleHold {
        final CountDownLatch entered=
            new CountDownLatch(
                1
            );
        final CountDownLatch release=
            new CountDownLatch(
                1
            );
        final AtomicReference<Throwable> failure=
            new AtomicReference<>();
        final Thread thread;

        private LifecycleHold(
            World world
        ){
            thread=
                new Thread(
                    ()->{
                        try{
                            boolean accepted=
                                world.runIfOpen(
                                    ()->{
                                        entered.countDown();

                                        try{
                                            if(!release.await(
                                                    5L,
                                                    TimeUnit.SECONDS))
                                                throw new IllegalStateException(
                                                    "lifecycle hold release timeout"
                                                );
                                        }catch(InterruptedException interrupted){
                                            Thread.currentThread()
                                                .interrupt();
                                            throw new IllegalStateException(
                                                "lifecycle hold interrupted",
                                                interrupted
                                            );
                                        }
                                    }
                                );

                            if(!accepted)
                                throw new IllegalStateException(
                                    "World lifecycle hold rejected"
                                );
                        }catch(Throwable problem){
                            failure.set(
                                problem
                            );
                        }
                    },
                    "player81-batch-lifetime-world-hold"
                );
            thread.setDaemon(
                true
            );
        }

        static LifecycleHold acquire(
            World world
        )throws Exception{
            LifecycleHold hold=
                new LifecycleHold(
                    world
                );
            hold.thread.start();

            if(!hold.entered.await(
                    5L,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "World lifecycle hold did not enter"
                );

            return hold;
        }

        void release(){
            release.countDown();
        }

        void awaitDone()
            throws Exception
        {
            thread.join(
                5_000L
            );

            if(thread.isAlive())
                throw new AssertionError(
                    "World lifecycle hold did not terminate"
                );

            if(failure.get()!=null)
                throw new AssertionError(
                    "World lifecycle hold failed",
                    failure.get()
                );
        }
    }

    private static final class Fixture
        implements AutoCloseable
    {
        final World world=
            World.isolatedForTest(
                604L
            );
        final WorldPlayer player=
            new WorldPlayer();
        final OutboundPacketQueue queue=
            new OutboundPacketQueue();
        final ServerPacketWriter writer;

        Fixture(
            String username,
            int[] seed
        ){
            world.registerPlayer(
                player,
                username
            );

            writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        seed
                    )
                );

            Player81WorldSync.register(
                writer,
                world,
                player,
                new DevAuthorityWorkbench()
            );
        }

        @Override
        public void close(){
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

    private Player81WriterBatchLifetimeLinearizationTest(){}
}
