package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class NpcPresentationDeliveryTransportRetractabilityTest {
    private static final int QUEUE_CAPACITY=1024;
    private static final int[] VIEWER_SEED=
        new int[]{901,902,903,904};

    public static void main(String[] args)throws Exception{
        assertQueueBackedEventRetryIsWireExact();
        assertDirectPartialFailureFailsClosed();

        System.out.println(
            "NPC_PRESENTATION_DELIVERY_TRANSPORT_RETRACTABILITY_PASS "+
            "eventRetryZeroBytes=true "+
            "eventPendingUntilCommit=true "+
            "eventRetryCipherRewound=true "+
            "eventRetryWireExact=true "+
            "directPartialFailureFailClosed=true "+
            "terminalRecipientDebtRetired=true "+
            "terminalWriterLatched=true "+
            "terminalRuntimeRetired=true "+
            "terminalRetirementOutsideRelayMonitor=true"
        );
    }

    private static void assertQueueBackedEventRetryIsWireExact()
        throws Exception
    {
        DeliveryFixture retry=
            new DeliveryFixture(
                "event-retry",
                new OutboundPacketQueue(
                    QUEUE_CAPACITY
                ),
                VIEWER_SEED.clone()
            );

        DeliveryFixture clean=
            new DeliveryFixture(
                "event-clean",
                new OutboundPacketQueue(
                    QUEUE_CAPACITY
                ),
                VIEWER_SEED.clone()
            );

        try{
            retry.spawnTarget();
            clean.spawnTarget();

            drain(retry.queue);
            drain(clean.queue);

            long retryNow=
                System.currentTimeMillis();
            long cleanNow=
                retryNow+10L;

            retry.enqueueEvent(
                retryNow,
                "transport-retry"
            );
            clean.enqueueEvent(
                cleanNow,
                "transport-retry"
            );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    retry.queue,
                    QUEUE_CAPACITY
                );

            SharedNpcWorldRelay.flushAfterPlayer81(
                retry.writer
            );

            if(retry.queue.queuedBytes()!=0)
                throw new AssertionError(
                    "retracted event delivery emitted bytes="+
                    retry.queue.queuedBytes()
                );

            if(retry.pending(
                    retryNow+1L
                ).size()!=1)
                throw new AssertionError(
                    "retracted event was not kept pending"
                );

            pressure.release();

            SharedNpcWorldRelay.flushAfterPlayer81(
                retry.writer
            );

            ByteArrayOutputStream actual=
                new ByteArrayOutputStream();

            retry.queue.drainTo(
                actual,
                Integer.MAX_VALUE
            );

            if(actual.size()==0)
                throw new AssertionError(
                    "event retry emitted no packet"
                );

            if(!retry.pending(
                    retryNow+2L
                ).isEmpty())
                throw new AssertionError(
                    "successful event retry was not marked delivered"
                );

            SharedNpcWorldRelay.flushAfterPlayer81(
                clean.writer
            );

            ByteArrayOutputStream expected=
                new ByteArrayOutputStream();

            clean.queue.drainTo(
                expected,
                Integer.MAX_VALUE
            );

            if(!clean.pending(
                    cleanNow+1L
                ).isEmpty())
                throw new AssertionError(
                    "clean event delivery did not settle"
                );

            if(!Arrays.equals(
                    actual.toByteArray(),
                    expected.toByteArray()))
                throw new AssertionError(
                    "event retry bytes differ from clean same-seed delivery actual="+
                    actual.size()+
                    " expected="+
                    expected.size()
                );
        }finally{
            retry.close();
            clean.close();
        }
    }

    private static void assertDirectPartialFailureFailsClosed()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                604L
            );
        WorldPlayer viewer=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                viewer,
                "event-direct-fail"
            );

        OutboundPacketQueue setupQueue=
            new OutboundPacketQueue();
        ServerPacketWriter setupWriter=
            new ServerPacketWriter(
                setupQueue,
                new IsaacCipher(
                    new int[]{911,912,913,914}
                )
            );
        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        PrefixThenFailOutputStream directOut=
            new PrefixThenFailOutputStream();
        ServerPacketWriter directWriter=
            new ServerPacketWriter(
                directOut,
                new IsaacCipher(
                    new int[]{921,922,923,924}
                )
            );

        try{
            String spawned=
                npcs.devSpawnNpc(
                    1488,
                    0,
                    0,
                    viewer.movement(),
                    setupWriter
                );

            if(spawned==null||
               !spawned.startsWith(
                    "DEV_NPC_SPAWN_OK"
               ))
                throw new AssertionError(
                    "direct failure target setup failed: "+
                    spawned
                );

            drain(setupQueue);

            NpcEntity target=
                onlyNpc(
                    npcs.snapshot()
                );

            Player81WorldSync.register(
                directWriter,
                world,
                viewer,
                new DevAuthorityWorkbench()
            );
            SharedNpcWorldRelay.register(
                directWriter,
                world,
                viewer,
                npcs,
                viewer.movement()
            );
            TradeService.register(
                world,
                viewer,
                generation,
                viewer.bank(),
                directWriter,
                ()->{}
            );

            Object relaySentinel=
                relayContextFor(
                    directWriter
                );

            long now=
                System.currentTimeMillis();

            enqueue(
                world,
                viewer,
                generation,
                target,
                now,
                "direct-partial"
            );

            final CountDownLatch tradeMonitorHeld=
                new CountDownLatch(1);
            final CountDownLatch releaseTradeMonitor=
                new CountDownLatch(1);
            final CountDownLatch relayMonitorAcquired=
                new CountDownLatch(1);
            final Throwable[] threadFailure={
                null
            };
            final boolean[] failed={
                false
            };
            Thread tradeHolder=
                new Thread(
                    ()->{
                        synchronized(TradeService.class){
                            tradeMonitorHeld.countDown();

                            try{
                                if(!releaseTradeMonitor.await(
                                        5L,
                                        TimeUnit.SECONDS
                                    ))
                                    throw new AssertionError(
                                        "Trade monitor release timed out"
                                    );
                            }catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new AssertionError(
                                    "Trade monitor holder interrupted",
                                    interrupted
                                );
                            }
                        }
                    },
                    "sharednpc-terminal-trade-holder"
                );
            Thread flushThread=null;
            Thread relayProbe=null;

            tradeHolder.start();

            if(!tradeMonitorHeld.await(
                    5L,
                    TimeUnit.SECONDS
                ))
                throw new AssertionError(
                    "Trade monitor holder did not start"
                );

            try{
                flushThread=
                    new Thread(
                        ()->{
                            try{
                                SharedNpcWorldRelay.flushAfterPlayer81(
                                    directWriter
                                );
                            }catch(IOException expected){
                                failed[0]=true;
                            }catch(Throwable failure){
                                threadFailure[0]=failure;
                            }
                        },
                        "sharednpc-terminal-flush"
                    );
                flushThread.start();

                awaitBlocked(
                    flushThread,
                    "terminal relay retirement did not wait on TradeService"
                );

                relayProbe=
                    new Thread(
                        ()->{
                            synchronized(SharedNpcWorldRelay.class){
                                relayMonitorAcquired.countDown();
                            }
                        },
                        "sharednpc-terminal-relay-probe"
                    );
                relayProbe.start();

                boolean relayReleased=
                    relayMonitorAcquired.await(
                        5L,
                        TimeUnit.SECONDS
                    );

                releaseTradeMonitor.countDown();

                joinThread(
                    flushThread,
                    "terminal relay flush"
                );
                joinThread(
                    relayProbe,
                    "terminal relay monitor probe"
                );
                joinThread(
                    tradeHolder,
                    "Trade monitor holder"
                );

                if(!relayReleased)
                    throw new AssertionError(
                        "terminal relay retirement retained SharedNpc monitor while waiting on TradeService"
                    );
            }finally{
                releaseTradeMonitor.countDown();

                if(flushThread!=null&&
                   flushThread.isAlive())
                    flushThread.interrupt();
                if(relayProbe!=null&&
                   relayProbe.isAlive())
                    relayProbe.interrupt();
                if(tradeHolder.isAlive())
                    tradeHolder.interrupt();
            }

            if(threadFailure[0]!=null)
                throw new AssertionError(
                    "terminal relay flush thread failed",
                    threadFailure[0]
                );

            if(!failed[0])
                throw new AssertionError(
                    "partial direct event failure was not surfaced"
                );

            if(directOut.bytes.size()!=1)
                throw new AssertionError(
                    "partial direct fixture emitted unexpected prefix bytes="+
                    directOut.bytes.size()
                );

            if(!directWriter.terminal())
                throw new AssertionError(
                    "terminal S2C65 failure did not latch writer terminal"
                );

            if(player81ContextFor(
                    directWriter
                )!=null)
                throw new AssertionError(
                    "terminal S2C65 failure retained Player81 authority"
                );

            if(TradeService.active(
                    viewer
                ))
                throw new AssertionError(
                    "terminal S2C65 failure retained Trade authority"
                );

            if(relayContextFor(
                    directWriter
                )!=relaySentinel)
                throw new AssertionError(
                    "terminal S2C65 failure replaced/removed exact SharedNpc sentinel"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now+1L
                    ).isEmpty())
                throw new AssertionError(
                    "failed-closed viewer retained presentation retry debt"
                );

            int attempts=
                directOut.attempts;

            SharedNpcWorldRelay.flushAfterPlayer81(
                directWriter
            );

            if(directOut.attempts!=attempts)
                throw new AssertionError(
                    "failed-closed relay retried direct transport"
                );
        }finally{
            TradeService.unregister(
                viewer,
                directWriter
            );
            SharedNpcWorldRelay.unregister(
                directWriter
            );
            Player81WorldSync.unregister(
                directWriter
            );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    generation
                );

            world.close();
        }
    }

    private static Object relayContextFor(
        ServerPacketWriter writer
    )throws Exception{
        Field field=
            SharedNpcWorldRelay.class
                .getDeclaredField(
                    "BY_WRITER"
                );
        field.setAccessible(true);

        synchronized(SharedNpcWorldRelay.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<ServerPacketWriter,Object> contexts=
                (IdentityHashMap<ServerPacketWriter,Object>)
                    field.get(null);

            return contexts.get(
                writer
            );
        }
    }

    private static Object player81ContextFor(
        ServerPacketWriter writer
    )throws Exception{
        Field field=
            Player81WorldSync.class
                .getDeclaredField(
                    "BY_WRITER"
                );
        field.setAccessible(true);

        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<ServerPacketWriter,Object> contexts=
                (IdentityHashMap<ServerPacketWriter,Object>)
                    field.get(null);

            return contexts.get(
                writer
            );
        }
    }

    private static final class DeliveryFixture {
        final World world=
            World.isolatedForTest(
                603L
            );
        final WorldPlayer viewer=
            new WorldPlayer();
        final long generation;
        final OutboundPacketQueue queue;
        final ServerPacketWriter writer;
        final NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcEntity target;

        DeliveryFixture(
            String username,
            OutboundPacketQueue queue,
            int[] seed
        )throws Exception{
            this.queue=queue;
            generation=
                world.registerPlayer(
                    viewer,
                    username
                );
            writer=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(seed)
                );
            SharedNpcWorldRelay.register(
                writer,
                world,
                viewer,
                npcs,
                viewer.movement()
            );
        }

        void spawnTarget()
            throws Exception
        {
            String spawned=
                npcs.devSpawnNpc(
                    1488,
                    0,
                    0,
                    viewer.movement(),
                    writer
                );

            if(spawned==null||
               !spawned.startsWith(
                    "DEV_NPC_SPAWN_OK"
               ))
                throw new AssertionError(
                    "event target setup failed: "+
                    spawned
                );

            target=
                onlyNpc(
                    npcs.snapshot()
                );
        }

        void enqueueEvent(
            long now,
            String text
        ){
            enqueue(
                world,
                viewer,
                generation,
                target,
                now,
                text
            );
        }

        List<WorldNpcPresentationEvents.Event>
            pending(
                long now
            ){
            return world.npcPresentationEvents()
                .pendingFor(
                    viewer.id(),
                    generation,
                    now
                );
        }

        void close(){
            SharedNpcWorldRelay.unregister(
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

    private static void enqueue(
        World world,
        WorldPlayer viewer,
        long generation,
        NpcEntity target,
        long now,
        String text
    ){
        LinkedHashMap<EntityId,Long> recipients=
            new LinkedHashMap<>();
        recipients.put(
            viewer.id(),
            generation
        );

        boolean queued=
            world.npcPresentationEvents()
                .enqueueOwned(
                    now,
                    EntityId.next(),
                    WorldNpcPresentationEvents
                        .Target.scene(
                            target.sceneIndex,
                            target.definitionId
                        ),
                    NpcSyncEncoder.Mask
                        .forceText(text),
                    0L,
                    recipients
                );

        if(!queued)
            throw new AssertionError(
                "presentation event setup rejected"
            );
    }

    private static NpcEntity onlyNpc(
        List<NpcEntity> npcs
    ){
        if(npcs.size()!=1)
            throw new AssertionError(
                "expected one NPC, got "+
                npcs.size()
            );
        return npcs.get(0);
    }

    private static void awaitBlocked(
        Thread thread,
        String message
    )throws InterruptedException{
        long deadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(
                5L
            );

        while(thread.isAlive()&&
              thread.getState()!=
                  Thread.State.BLOCKED&&
              System.nanoTime()<deadline)
            Thread.yield();

        if(thread.getState()!=
                Thread.State.BLOCKED)
            throw new AssertionError(
                message+
                " state="+
                thread.getState()
            );
    }

    private static void joinThread(
        Thread thread,
        String message
    )throws InterruptedException{
        thread.join(
            5000L
        );

        if(thread.isAlive())
            throw new AssertionError(
                message+
                " did not terminate"
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
                Integer.MAX_VALUE
            );
    }

    private static final class PrefixThenFailOutputStream
        extends OutputStream {

        final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();
        int attempts;

        @Override public void write(
            int value
        )throws IOException{
            bytes.write(value);
            attempts++;
            throw new IOException(
                "EXPECTED_EVENT_PARTIAL_DIRECT_FAILURE"
            );
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws IOException{
            attempts++;
            if(length>0)
                bytes.write(
                    data[offset]
                );
            throw new IOException(
                "EXPECTED_EVENT_PARTIAL_DIRECT_FAILURE"
            );
        }
    }

    private NpcPresentationDeliveryTransportRetractabilityTest(){}
}
