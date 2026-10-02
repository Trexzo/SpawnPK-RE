package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

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
            "terminalRecipientDebtRetired=true"
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

            SharedNpcWorldRelay.register(
                directWriter,
                world,
                viewer,
                npcs,
                viewer.movement()
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

            boolean failed=false;

            try{
                SharedNpcWorldRelay.flushAfterPlayer81(
                    directWriter
                );
            }catch(IOException expected){
                failed=true;
            }

            if(!failed)
                throw new AssertionError(
                    "partial direct event failure was not surfaced"
                );

            if(directOut.bytes.size()!=1)
                throw new AssertionError(
                    "partial direct fixture emitted unexpected prefix bytes="+
                    directOut.bytes.size()
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
            SharedNpcWorldRelay.unregister(
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
