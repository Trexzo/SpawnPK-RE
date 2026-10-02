package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

public final class SharedNpcRebindCleanupRetryTest {
    private static final int QUEUE_CAPACITY=1024;

    public static void main(String[] args)throws Exception{
        assertRetractedCleanupKeepsOldContextAuthoritative();

        System.out.println(
            "SHARED_NPC_REBIND_CLEANUP_RETRY_PASS "+
            "retractedCleanupRejectsReplacement=true "+
            "oldContextAuthorityPreserved=true "+
            "mirrorPreservedOnRetraction=true "+
            "retryCleanupCommitsOnce=true "+
            "freshContextInstalledAfterCleanup=true "+
            "noDuplicateMirrorAfterRebind=true"
        );
    }

    private static void assertRetractedCleanupKeepsOldContextAuthoritative()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "rebind-cleanup-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "rebind-cleanup-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );

        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1001,1002,1003,1004}
                )
            );
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    new int[]{1005,1006,1007,1008}
                )
            );

        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

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

        OutboundPacketQueue.BatchReservation pressure=
            null;

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

            if(spawn==null||
               !spawn.startsWith(
                    "PET_SPAWN_OK"
               ))
                throw new AssertionError(
                    "source pet setup failed: "+
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

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "initial mirror projection size="+
                    viewerNpcs.snapshot().size()
                );

            NpcEntity initialMirror=
                viewerNpcs.snapshot().get(0);
            int initialScene=
                initialMirror.sceneIndex;

            drain(viewerQueue);

            Object oldContext=
                contextFor(
                    viewerWriter
                );

            if(oldContext==null)
                throw new AssertionError(
                    "initial relay context missing"
                );

            pressure=
                OutboundPacketQueue.reserveBatch(
                    viewerQueue,
                    QUEUE_CAPACITY
                );

            boolean rejected=false;

            try{
                SharedNpcWorldRelay.register(
                    viewerWriter,
                    world,
                    viewer,
                    viewerNpcs,
                    viewer.movement()
                );
            }catch(SharedNpcWorldRelay.RetryableRegistrationException expected){
                rejected=true;
            }

            if(!rejected)
                throw new AssertionError(
                    "retracted cleanup did not reject replacement"
                );

            if(contextFor(
                    viewerWriter
                )!=oldContext)
                throw new AssertionError(
                    "old relay context authority was detached on retraction"
                );

            if(viewerNpcs.snapshot().size()!=1||
               viewerNpcs.snapshot().get(0)!=
                    initialMirror||
               viewerNpcs.snapshot().get(0)
                    .sceneIndex!=initialScene)
                throw new AssertionError(
                    "retracted cleanup changed authoritative mirror"
                );

            if(viewerQueue.queuedBytes()!=0||
               viewerQueue.queuedPackets()!=0)
                throw new AssertionError(
                    "retracted cleanup emitted removal bytes"
                );

            pressure.release();
            pressure=null;

            SharedNpcWorldRelay.register(
                viewerWriter,
                world,
                viewer,
                viewerNpcs,
                viewer.movement()
            );

            Object newContext=
                contextFor(
                    viewerWriter
                );

            if(newContext==null||
               newContext==oldContext)
                throw new AssertionError(
                    "successful retry did not install fresh relay context"
                );

            if(!viewerNpcs.snapshot().isEmpty())
                throw new AssertionError(
                    "successful replacement cleanup retained old mirror size="+
                    viewerNpcs.snapshot().size()
                );

            if(viewerQueue.queuedPackets()!=1)
                throw new AssertionError(
                    "replacement cleanup did not publish exactly one removal packet packets="+
                    viewerQueue.queuedPackets()
                );

            drain(viewerQueue);

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "fresh context did not recreate exactly one mirror size="+
                    viewerNpcs.snapshot().size()
                );

            NpcEntity reboundMirror=
                viewerNpcs.snapshot().get(0);

            if(reboundMirror==initialMirror)
                throw new AssertionError(
                    "fresh context reused retired mirror object"
                );

            drain(viewerQueue);

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "steady-state sync duplicated mirror after rebind size="+
                    viewerNpcs.snapshot().size()
                );
        }finally{
            if(pressure!=null)
                pressure.release();

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

    private static Object contextFor(
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
            IdentityHashMap<ServerPacketWriter,Object>
                contexts=
                    (IdentityHashMap<ServerPacketWriter,Object>)
                    field.get(null);

            return contexts.get(
                writer
            );
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
                Integer.MAX_VALUE
            );
    }

    private SharedNpcRebindCleanupRetryTest(){}
}
