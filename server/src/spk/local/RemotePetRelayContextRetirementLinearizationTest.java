package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class RemotePetRelayContextRetirementLinearizationTest {
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
                "remote-pet-relay-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "remote-pet-relay-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{601,602,603,604}
                )
            );

        BlockingOutputStream viewerOut=
            new BlockingOutputStream();

        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerOut,
                new IsaacCipher(
                    new int[]{605,606,607,608}
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
        Thread retireThread=null;

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

            AtomicReference<Throwable> failure=
                new AtomicReference<>();
            AtomicBoolean retired=
                new AtomicBoolean(false);

            syncThread=
                new Thread(
                    ()->{
                        try{
                            SharedNpcWorldRelay
                                .syncRemotePets(
                                    viewerWriter
                                );
                        }catch(Throwable t){
                            failure.compareAndSet(
                                null,
                                t
                            );
                        }
                    },
                    "remote-pet-relay-sync"
                );

            syncThread.start();

            if(!viewerOut.awaitBlocked(
                    5_000L))
                throw new AssertionError(
                    "remote pet sync never entered blocking packet65 output"
                );

            retireThread=
                new Thread(
                    ()->{
                        try{
                            SharedNpcWorldRelay
                                .unregister(
                                    viewerWriter
                                );
                            retired.set(
                                true
                            );
                        }catch(Throwable t){
                            failure.compareAndSet(
                                null,
                                t
                            );
                        }
                    },
                    "remote-pet-relay-retire"
                );

            retireThread.start();

            Thread.sleep(150L);

            if(retired.get()||
               !retireThread.isAlive())
                throw new AssertionError(
                    "relay context retired through in-flight remote mirror sync"
                );

            if(!world.players().owns(
                    viewer,
                    viewerGeneration
                ))
                throw new AssertionError(
                    "fixture unexpectedly lost World viewer ownership"
                );

            viewerOut.release();

            syncThread.join(
                5_000L
            );
            retireThread.join(
                5_000L
            );

            if(syncThread.isAlive()||
               retireThread.isAlive())
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

            if(!retired.get())
                throw new AssertionError(
                    "relay context did not retire after sync exit"
                );

            if(!world.players().owns(
                    viewer,
                    viewerGeneration
                ))
                throw new AssertionError(
                    "relay retirement incorrectly retired World ownership"
                );

            int bytesBeforeStale=
                viewerOut.size();

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerOut.size()!=
                    bytesBeforeStale)
                throw new AssertionError(
                    "retired relay context emitted packet65 after unregister"
                );

            System.out.println(
                "REMOTE_PET_RELAY_CONTEXT_RETIREMENT_LINEARIZATION_PASS "+
                "syncEntered=true "+
                "relayUnregisterBlockedDuringSync=true "+
                "relayUnregisterCompletedAfterSync=true "+
                "worldOwnershipPreserved=true "+
                "retiredSyncRejected=true"
            );

            world.unregisterPlayer(
                source,
                sourceGeneration
            );
            world.unregisterPlayer(
                viewer,
                viewerGeneration
            );
        }finally{
            viewerOut.release();

            if(syncThread!=null&&
               syncThread.isAlive())
                syncThread.join(
                    5_000L
                );

            if(retireThread!=null&&
               retireThread.isAlive())
                retireThread.join(
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

    private RemotePetRelayContextRetirementLinearizationTest(){}
}
