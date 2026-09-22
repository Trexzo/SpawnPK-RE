package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class RemotePetSyncOwnershipLinearizationTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "remote-pet-sync-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "remote-pet-sync-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        BlockingOutputStream viewerOut=
            new BlockingOutputStream();

        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerOut,
                new IsaacCipher(
                    new int[]{5,6,7,8}
                )
            );

        Player81WorldSync.Context sourceSync=
            Player81WorldSync.register(
                sourceWriter,
                world,
                source,
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.Context viewerSync=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                new DevAuthorityWorkbench()
            );

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

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

        Thread syncThread=null;
        Thread unregisterThread=null;

        try{
            PetDefinitionRepository.Def pet=
                PetDefinitionRepository.get(
                    24019
                );

            if(pet==null)
                throw new AssertionError(
                    "missing pet 24019"
                );

            String spawn=
                sourceNpcs.spawnPet(
                    pet,
                    source.movement(),
                    sourceWriter
                );

            if(!spawn.startsWith(
                    "PET_SPAWN_OK"))
                throw new AssertionError(
                    spawn
                );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            if(Player81WorldSync.clientIndexFor(
                    viewerWriter,
                    source
                )<0)
                throw new AssertionError(
                    "viewer did not establish source visibility"
                );

            viewerOut.arm();

            AtomicReference<Throwable> syncFailure=
                new AtomicReference<>();

            syncThread=
                new Thread(
                    ()->{
                        try{
                            SharedNpcWorldRelay
                                .syncRemotePets(
                                    viewerWriter
                                );
                        }catch(Throwable failure){
                            syncFailure.set(
                                failure
                            );
                        }
                    },
                    "remote-pet-sync-owner"
                );

            syncThread.start();

            if(!viewerOut.awaitBlocked(
                    5_000L))
                throw new AssertionError(
                    "remote pet sync never entered blocking packet65 output"
                );

            AtomicBoolean unregisterResult=
                new AtomicBoolean();

            unregisterThread=
                new Thread(
                    ()->unregisterResult.set(
                        world.unregisterPlayer(
                            viewer,
                            viewerGeneration
                        )
                    ),
                    "remote-pet-sync-unregister"
                );

            unregisterThread.start();

            Thread.sleep(100L);

            if(!unregisterThread.isAlive())
                throw new AssertionError(
                    "viewer unregister crossed accepted remote-pet sync ownership section"
                );

            if(!world.players().owns(
                    viewer,
                    viewerGeneration
                ))
                throw new AssertionError(
                    "viewer ownership disappeared while sync section was blocked"
                );

            viewerOut.release();

            syncThread.join(5_000L);

            if(syncThread.isAlive())
                throw new AssertionError(
                    "remote pet sync did not finish after output release"
                );

            if(syncFailure.get()!=null)
                throw new AssertionError(
                    "remote pet sync failed",
                    syncFailure.get()
                );

            unregisterThread.join(
                5_000L
            );

            if(unregisterThread.isAlive())
                throw new AssertionError(
                    "viewer unregister did not finish after sync release"
                );

            if(!unregisterResult.get())
                throw new AssertionError(
                    "viewer unregister returned false"
                );

            if(viewer.registered())
                throw new AssertionError(
                    "viewer remained registered after unregister"
                );

            int bytesBeforeStaleSync=
                viewerOut.size();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerOut.size()!=
                    bytesBeforeStaleSync)
                throw new AssertionError(
                    "stale viewer sync emitted packet65 after unregister"
                );

            System.out.println(
                "REMOTE_PET_SYNC_OWNERSHIP_LINEARIZATION_PASS "+
                "syncEntered=true "+
                "unregisterBlockedDuringSync=true "+
                "unregisterCompletedAfterSync=true "+
                "staleSyncRejected=true"
            );

            world.unregisterPlayer(
                source,
                sourceGeneration
            );
        }finally{
            viewerOut.release();

            if(syncThread!=null&&
               syncThread.isAlive())
                syncThread.join(
                    5_000L
                );

            if(unregisterThread!=null&&
               unregisterThread.isAlive())
                unregisterThread.join(
                    5_000L
                );

            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            Player81WorldSync.unregister(
                sourceWriter
            );
            Player81WorldSync.unregister(
                viewerWriter
            );

            if(source.registered())
                world.unregisterPlayer(
                    source,
                    source.generation()
                );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewer.generation()
                );

            world.close();
        }
    }

    private static final class BlockingOutputStream
        extends OutputStream {

        private final ByteArrayOutputStream delegate=
            new ByteArrayOutputStream();
        private final CountDownLatch blocked=
            new CountDownLatch(1);
        private final CountDownLatch release=
            new CountDownLatch(1);
        private final AtomicBoolean blockOnce=
            new AtomicBoolean();
        private volatile boolean armed;

        void arm(){
            armed=true;
        }

        boolean awaitBlocked(
            long timeoutMillis
        )throws InterruptedException{
            return blocked.await(
                timeoutMillis,
                TimeUnit.MILLISECONDS
            );
        }

        void release(){
            release.countDown();
        }

        synchronized int size(){
            return delegate.size();
        }

        @Override public void write(
            int value
        )throws IOException{
            maybeBlock();
            synchronized(this){
                delegate.write(value);
            }
        }

        @Override public void write(
            byte[] bytes,
            int offset,
            int length
        )throws IOException{
            maybeBlock();
            synchronized(this){
                delegate.write(
                    bytes,
                    offset,
                    length
                );
            }
        }

        private void maybeBlock()
            throws IOException{
            if(!armed||
               !blockOnce.compareAndSet(
                   false,
                   true
               ))
                return;

            blocked.countDown();

            boolean interrupted=false;

            for(;;){
                try{
                    release.await();
                    break;
                }catch(InterruptedException ignored){
                    interrupted=true;
                }
            }

            if(interrupted)
                Thread.currentThread()
                    .interrupt();
        }
    }

    private RemotePetSyncOwnershipLinearizationTest(){}
}
