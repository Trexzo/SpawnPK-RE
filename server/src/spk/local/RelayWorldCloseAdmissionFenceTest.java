package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class RelayWorldCloseAdmissionFenceTest {
    public static void main(String[] args)throws Exception{
        int relayWorldBaseline=
            mapSize(
                SharedNpcWorldRelay.class,
                "BY_WORLD"
            );
        int relayWriterBaseline=
            mapSize(
                SharedNpcWorldRelay.class,
                "BY_WRITER"
            );

        World world=
            World.isolatedForTest(25L);

        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "relay-close-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "relay-close-viewer"
            );

        ServerPacketWriter sourceWriter=
            writer(11);
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue();
        ServerPacketWriter viewerWriter=
            writer(
                viewerQueue,
                21
            );

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        NpcEntity target=
            new NpcEntity(
                129,
                1488,
                source.movement().x(),
                source.movement().y()
            );

        WorldNpc trackedGeneric=null;

        CountDownLatch targetEntered=
            new CountDownLatch(1);
        CountDownLatch releaseTarget=
            new CountDownLatch(1);
        AtomicReference<Throwable> closeFailure=
            new AtomicReference<>();

        Thread closeThread=null;

        try{
            SharedNpcWorldRelay.register(
                sourceWriter,
                world,
                source,
                sourceNpcs,
                source.movement()
            );

            SharedNpcWorldRelay.register(
                viewerWriter,
                world,
                viewer,
                viewerNpcs,
                viewer.movement()
            );

            trackedGeneric=
                world.npcs().spawn(
                    1530,
                    source.movement().x()+1,
                    source.movement().y(),
                    0
                );

            SharedNpcWorldRelay.trackCanonicalNpc(
                world,
                trackedGeneric
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerQueue.queuedPackets()<=0||
               viewerQueue.queuedBytes()<=0)
                throw new AssertionError(
                    "generic canonical NPC was not projected into viewer"
                );

            if(mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WORLD"
                )<=relayWorldBaseline)
                throw new AssertionError(
                    "generic canonical NPC did not retain relay WorldState"
                );

            SharedNpcWorldRelay.relayMask(
                sourceWriter,
                sourceNpcs,
                target,
                NpcSyncEncoder.Mask.forceText(
                    "before-close"
                )
            );

            if(world.npcPresentationEvents()
                    .size()!=1)
                throw new AssertionError(
                    "pre-close relay mask was not queued"
                );

            int removed=
                world.npcPresentationEvents()
                    .removeSource(
                        source.id(),
                        System.currentTimeMillis()
                    );

            if(removed!=1||
               world.npcPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "pre-close relay fixture was not cleared"
                );

            world.attachTickTarget(
                new WorldTickTarget(){
                    private final AtomicBoolean blocked=
                        new AtomicBoolean();

                    @Override public EntityId ownerId(){
                        return source.id();
                    }

                    @Override public long ownerGeneration(){
                        return sourceGeneration;
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

            world.start();

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
                    "relay-world-close-owner"
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
                    "close owner left relay admission window"
                );

            if(world.npcPresentationEvents()
                    .closed())
                throw new AssertionError(
                    "presentation queue closed before relay admission window"
                );

            SharedNpcWorldRelay.relayMask(
                sourceWriter,
                sourceNpcs,
                target,
                NpcSyncEncoder.Mask.forceText(
                    "during-close"
                )
            );

            if(world.npcPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "relay mask entered terminal World during close window"
                );

            int viewerPackets=
                viewerQueue.queuedPackets();
            int viewerBytes=
                viewerQueue.queuedBytes();
            int presentationSize=
                world.npcPresentationEvents()
                    .size();

            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            if(viewerQueue.queuedPackets()!=
                    viewerPackets||
               viewerQueue.queuedBytes()!=
                    viewerBytes)
                throw new AssertionError(
                    "close-boundary relay unregister emitted packet I/O"
                );

            if(world.npcPresentationEvents()
                    .size()!=presentationSize)
                throw new AssertionError(
                    "close-boundary relay unregister mutated presentation queue"
                );

            if(mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WRITER"
                )!=relayWriterBaseline+1)
                throw new AssertionError(
                    "close-boundary relay unregister did not detach exact writer"
                );

            if(mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WORLD"
                )<=relayWorldBaseline)
                throw new AssertionError(
                    "close-boundary relay unregister prematurely removed retained generic WorldState"
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

            if(!world.npcPresentationEvents()
                    .closed()||
               world.npcPresentationEvents()
                    .size()!=0)
                throw new AssertionError(
                    "presentation queue not terminal after World close"
                );

            if(mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WORLD"
                )!=relayWorldBaseline||
               mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WRITER"
                )!=relayWriterBaseline)
                throw new AssertionError(
                    "World close did not release relay static state"
                );

            expect(
                IllegalStateException.class,
                ()->SharedNpcWorldRelay.register(
                    sourceWriter,
                    world,
                    source,
                    sourceNpcs,
                    source.movement()
                ),
                "post-close relay writer registration"
            );

            WorldNpc finalTrackedGeneric=
                trackedGeneric;

            expect(
                IllegalStateException.class,
                ()->SharedNpcWorldRelay.trackCanonicalNpc(
                    world,
                    finalTrackedGeneric
                ),
                "post-close canonical NPC tracking"
            );

            if(mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WORLD"
                )!=relayWorldBaseline||
               mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WRITER"
                )!=relayWriterBaseline)
                throw new AssertionError(
                    "post-close relay admission resurrected terminal World"
                );

            // Terminal detach makes later session cleanup harmless/idempotent.
            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            if(mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WORLD"
                )!=relayWorldBaseline||
               mapSize(
                    SharedNpcWorldRelay.class,
                    "BY_WRITER"
                )!=relayWriterBaseline)
                throw new AssertionError(
                    "post-close relay unregister changed baseline"
                );

            System.out.println(
                "RELAY_WORLD_CLOSE_ADMISSION_FENCE_PASS "+
                "preCloseQueued=true "+
                "closeWindowQueueOpen=true "+
                "closeWindowRelayRejected=true "+
                "terminalQueueEmpty=true "+
                "worldCloseRelayCleanup=true "+
                "genericNpcWorldReleased=true "+
                "postCloseRegisterRejected=true "+
                "postCloseTrackRejected=true "+
                "closeBoundaryUnregisterSilent=true "+
                "postCloseUnregisterIdempotent=true "+
                "relayBaselineRestored=true"
            );
        }finally{
            releaseTarget.countDown();

            if(closeThread!=null&&
               closeThread.isAlive())
                closeThread.join(5_000L);

            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            if(source.registered())
                world.unregisterPlayer(
                    source,
                    sourceGeneration
                );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewerGeneration
                );

            world.close();
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
        String label
    )throws Exception{
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(
                    failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static ServerPacketWriter writer(
        int seed
    ){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private static ServerPacketWriter writer(
        OutboundPacketQueue queue,
        int seed
    ){
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private static int mapSize(
        Class<?> relay,
        String fieldName
    )throws Exception{
        Field field=
            relay.getDeclaredField(
                fieldName
            );
        field.setAccessible(true);

        synchronized(relay){
            return ((Map<?,?>)
                field.get(null)).size();
        }
    }

    private RelayWorldCloseAdmissionFenceTest(){}
}
