package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class SharedNpcSyncContextRetirementLinearizationTest {
    public static void main(String[] args)throws Exception{
        assertUnregisterWaitsForProjectionSync();

        System.out.println(
            "SHARED_NPC_SYNC_CONTEXT_RETIREMENT_LINEARIZATION_PASS "+
            "registryIdentityHeldAcrossProjection=true "+
            "unregisterBlockedBeforeProjectionMutation=true "+
            "projectionCommittedBeforeRetirement=true "+
            "retiredContextNoFurtherSync=true"
        );
    }

    private static void assertUnregisterWaitsForProjectionSync()
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
                "relay-sync-retirement"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{601,602,603,604}
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

        WorldNpc canonical=
            world.npcs().spawn(
                1488,
                viewer.movement().x()+1,
                viewer.movement().y(),
                viewer.movement().plane()
            );
        SharedNpcWorldRelay.trackCanonicalNpc(
            world,
            canonical
        );

        CountDownLatch worldNpcRegistryHeld=
            new CountDownLatch(1);
        CountDownLatch releaseWorldNpcRegistry=
            new CountDownLatch(1);
        CountDownLatch unregisterAttempted=
            new CountDownLatch(1);

        AtomicBoolean unregisterFinished=
            new AtomicBoolean(false);
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        Thread registryHolder=null;
        Thread syncThread=null;
        Thread unregisterThread=null;

        try{
            registryHolder=
                new Thread(
                    ()->{
                        synchronized(world.npcs()){
                            worldNpcRegistryHeld.countDown();
                            try{
                                if(!releaseWorldNpcRegistry.await(
                                        5,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "world NPC registry release timeout"
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
                    "relay-sync-world-npc-holder"
                );

            syncThread=
                new Thread(
                    ()->{
                        try{
                            SharedNpcWorldRelay
                                .syncRemotePets(
                                    writer
                                );
                        }catch(Throwable t){
                            failure.compareAndSet(
                                null,
                                t
                            );
                        }
                    },
                    "relay-sync-inflight"
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
                    "relay-sync-unregister-race"
                );

            registryHolder.start();

            if(!worldNpcRegistryHeld.await(
                    5,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "world NPC registry holder did not acquire monitor"
                );

            syncThread.start();

            long blockedDeadline=
                System.nanoTime()+
                TimeUnit.SECONDS.toNanos(5);

            while(syncThread.getState()!=
                    Thread.State.BLOCKED&&
                  System.nanoTime()<
                    blockedDeadline)
                Thread.sleep(5L);

            if(syncThread.getState()!=
                    Thread.State.BLOCKED)
                throw new AssertionError(
                    "relay sync did not block on WorldNpcRegistry state="+
                    syncThread.getState()
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
                    "relay context retired while projection sync still owned registry authority"
                );

            releaseWorldNpcRegistry.countDown();

            registryHolder.join(5000L);
            syncThread.join(5000L);
            unregisterThread.join(5000L);

            if(registryHolder.isAlive()||
               syncThread.isAlive()||
               unregisterThread.isAlive())
                throw new AssertionError(
                    "relay sync retirement regression threads did not terminate"
                );

            Throwable problem=
                failure.get();
            if(problem!=null)
                throw new AssertionError(
                    "relay sync retirement race failed",
                    problem
                );

            if(!unregisterFinished.get())
                throw new AssertionError(
                    "relay unregister did not complete after projection exit"
                );

            if(queue.queuedBytes()==0)
                throw new AssertionError(
                    "authoritative projection emitted no packet65 before retirement"
                );

            int bytesAfterRetirement=
                queue.queuedBytes();

            SharedNpcWorldRelay.syncRemotePets(
                writer
            );

            if(queue.queuedBytes()!=
                    bytesAfterRetirement)
                throw new AssertionError(
                    "retired relay context emitted projection after unregister"
                );
        }finally{
            releaseWorldNpcRegistry.countDown();

            if(registryHolder!=null)
                registryHolder.join(1000L);
            if(syncThread!=null)
                syncThread.join(1000L);
            if(unregisterThread!=null)
                unregisterThread.join(1000L);

            SharedNpcWorldRelay.unregister(
                writer
            );
            SharedNpcWorldRelay.untrackCanonicalNpc(
                world,
                canonical.id
            );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    generation
                );

            world.close();
        }
    }

    @SuppressWarnings("unused")
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

    private SharedNpcSyncContextRetirementLinearizationTest(){}
}
