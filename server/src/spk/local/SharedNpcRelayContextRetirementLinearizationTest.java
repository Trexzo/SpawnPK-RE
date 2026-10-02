package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SharedNpcRelayContextRetirementLinearizationTest {
    public static void main(String[] args)throws Exception{
        assertUnregisterWaitsForInFlightRelayDelivery();

        System.out.println(
            "SHARED_NPC_RELAY_CONTEXT_RETIREMENT_LINEARIZATION_PASS "+
            "registryIdentityHeldAcrossDelivery=true "+
            "unregisterBlockedUntilSendAndMark=true "+
            "deliveryCommittedBeforeRetirement=true"
        );
    }

    private static void assertUnregisterWaitsForInFlightRelayDelivery()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer viewer=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                viewer,
                "relay-context-retirement"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{501,502,503,504}
                )
            );
        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        SharedNpcWorldRelay.register(
            writer,
            world,
            viewer,
            npcs,
            viewer.movement()
        );

        CountDownLatch writerHeld=
            new CountDownLatch(1);
        CountDownLatch releaseWriter=
            new CountDownLatch(1);
        CountDownLatch unregisterAttempted=
            new CountDownLatch(1);

        AtomicBoolean unregisterFinished=
            new AtomicBoolean(false);
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        Thread writerHolder=null;
        Thread flushThread=null;
        Thread unregisterThread=null;

        try{
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
                    "relay target setup failed: "+
                    spawned
                );

            NpcEntity target=
                npcs.snapshot().get(0);
            drain(queue);

            long now=
                System.currentTimeMillis();

            boolean queued=
                world.npcPresentationEvents()
                    .enqueue(
                        now,
                        EntityId.next(),
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "relay-retirement-linearization"
                            ),
                        0L,
                        Collections.singleton(
                            viewer.id()
                        )
                    );

            if(!queued)
                throw new AssertionError(
                    "relay event setup failed"
                );

            writerHolder=
                new Thread(
                    ()->{
                        synchronized(writer){
                            writerHeld.countDown();
                            try{
                                if(!releaseWriter.await(
                                        5,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "writer release timeout"
                                    );
                            }catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                failure.compareAndSet(
                                    null,
                                    interrupted
                                );
                            }
                        }
                    },
                    "relay-writer-holder"
                );

            flushThread=
                new Thread(
                    ()->{
                        try{
                            SharedNpcWorldRelay
                                .flushAfterPlayer81(
                                    writer
                                );
                        }catch(Throwable t){
                            failure.compareAndSet(
                                null,
                                t
                            );
                        }
                    },
                    "relay-flush-inflight"
                );

            unregisterThread=
                new Thread(
                    ()->{
                        unregisterAttempted.countDown();
                        try{
                            SharedNpcWorldRelay
                                .unregister(
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
                    "relay-unregister-race"
                );

            writerHolder.start();

            if(!writerHeld.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "writer holder did not acquire writer monitor"
                );

            flushThread.start();

            long blockedDeadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(5);

            while(flushThread.getState()!=
                    Thread.State.BLOCKED&&
                  System.nanoTime()<
                    blockedDeadline)
                Thread.sleep(5L);

            if(flushThread.getState()!=
                    Thread.State.BLOCKED)
                throw new AssertionError(
                    "relay flush did not block on writer while delivery authority was held state="+
                    flushThread.getState()
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
                    "relay context retired inside in-flight send/mark critical section"
                );

            releaseWriter.countDown();

            writerHolder.join(5000L);
            flushThread.join(5000L);
            unregisterThread.join(5000L);

            if(writerHolder.isAlive()||
               flushThread.isAlive()||
               unregisterThread.isAlive())
                throw new AssertionError(
                    "relay retirement regression threads did not terminate"
                );

            Throwable problem=
                failure.get();
            if(problem!=null)
                throw new AssertionError(
                    "relay retirement race failed",
                    problem
                );

            if(!unregisterFinished.get())
                throw new AssertionError(
                    "relay unregister did not complete after delivery exit"
                );

            if(queue.queuedBytes()==0)
                throw new AssertionError(
                    "in-flight relay delivery emitted no packet65"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now+1L
                    ).isEmpty())
                throw new AssertionError(
                    "relay event was not marked delivered before retirement"
                );

            int bytesAfterRetirement=
                queue.queuedBytes();

            SharedNpcWorldRelay.flushAfterPlayer81(
                writer
            );

            if(queue.queuedBytes()!=
                    bytesAfterRetirement)
                throw new AssertionError(
                    "retired relay context emitted output after unregister"
                );
        }finally{
            releaseWriter.countDown();

            if(writerHolder!=null)
                writerHolder.join(1000L);
            if(flushThread!=null)
                flushThread.join(1000L);
            if(unregisterThread!=null)
                unregisterThread.join(1000L);

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

    private SharedNpcRelayContextRetirementLinearizationTest(){}
}
