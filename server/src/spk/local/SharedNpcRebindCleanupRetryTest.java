package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

public final class SharedNpcRebindCleanupRetryTest {
    private static final int QUEUE_CAPACITY=1024;

    public static void main(String[] args)throws Exception{
        assertRetractedCleanupKeepsOldContextAuthoritative();
        assertMultiContextRebindDoesNotDetachEarly();
        assertTerminalCleanupCannotResurrectWriter();

        System.out.println(
            "SHARED_NPC_REBIND_CLEANUP_RETRY_PASS "+
            "retractedCleanupRejectsReplacement=true "+
            "oldContextAuthorityPreserved=true "+
            "mirrorPreservedOnRetraction=true "+
            "retryCleanupCommitsOnce=true "+
            "freshContextInstalledAfterCleanup=true "+
            "noDuplicateMirrorAfterRebind=true "+
            "multiContextDetachDeferredUntilAllCleanupCommits=true "+
            "terminalCleanupRejectsReplacement=true "+
            "terminalContextRemainsFailClosed=true "+
            "terminalWriterNotResurrected=true"
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

    private static void assertMultiContextRebindDoesNotDetachEarly()
        throws Exception
    {
        World worldA=
            World.isolatedForTest(
                602L
            );
        World worldB=
            World.isolatedForTest(
                603L
            );

        WorldPlayer ownerA=
            new WorldPlayer();
        WorldPlayer ownerB=
            new WorldPlayer();
        WorldPlayer sourceB=
            new WorldPlayer();

        long ownerAGeneration=
            worldA.registerPlayer(
                ownerA,
                "rebind-multi-a"
            );
        long ownerBGeneration=
            worldB.registerPlayer(
                ownerB,
                "rebind-multi-b"
            );
        long sourceBGeneration=
            worldB.registerPlayer(
                sourceB,
                "rebind-multi-source"
            );

        OutboundPacketQueue queueA=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        OutboundPacketQueue queueB=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter writerA=
            new ServerPacketWriter(
                queueA,
                new IsaacCipher(
                    new int[]{1021,1022,1023,1024}
                )
            );
        ServerPacketWriter writerB=
            new ServerPacketWriter(
                queueB,
                new IsaacCipher(
                    new int[]{1025,1026,1027,1028}
                )
            );
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1029,1030,1031,1032}
                )
            );

        NpcRegistry npcsA=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry npcsB=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.register(
            sourceWriter,
            worldB,
            sourceB,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.Context viewerBSync=
            Player81WorldSync.register(
                writerB,
                worldB,
                ownerB,
                new DevAuthorityWorkbench()
            );

        SharedNpcWorldRelay.register(
            writerA,
            worldA,
            ownerA,
            npcsA,
            ownerA.movement()
        );
        SharedNpcWorldRelay.register(
            sourceWriter,
            worldB,
            sourceB,
            sourceNpcs,
            sourceB.movement()
        );
        SharedNpcWorldRelay.register(
            writerB,
            worldB,
            ownerB,
            npcsB,
            ownerB.movement()
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

            sourceNpcs.spawnPet(
                pet,
                sourceB.movement(),
                sourceWriter
            );

            Player81WorldSync.transformForTest(
                viewerBSync,
                BootstrapPackets.player81Idle()
            );

            SharedNpcWorldRelay.syncRemotePets(
                writerB
            );

            if(npcsB.snapshot().size()!=1)
                throw new AssertionError(
                    "multi-context fixture did not create one target-owner mirror"
                );

            drain(queueB);

            Object contextA=
                contextFor(
                    writerA
                );
            Object contextB=
                contextFor(
                    writerB
                );

            if(contextA==null||
               contextB==null||
               contextA==contextB)
                throw new AssertionError(
                    "multi-context fixture contexts invalid"
                );

            pressure=
                OutboundPacketQueue.reserveBatch(
                    queueB,
                    QUEUE_CAPACITY
                );

            boolean rejected=false;

            try{
                SharedNpcWorldRelay.register(
                    writerA,
                    worldB,
                    ownerB,
                    npcsB,
                    ownerB.movement()
                );
            }catch(SharedNpcWorldRelay.RetryableRegistrationException expected){
                rejected=true;
            }

            if(!rejected)
                throw new AssertionError(
                    "target-owner cleanup retraction did not reject multi-context rebind"
                );

            if(contextFor(
                    writerA
                )!=contextA)
                throw new AssertionError(
                    "incoming writer old Context detached before target-owner cleanup committed"
                );

            if(contextFor(
                    writerB
                )!=contextB)
                throw new AssertionError(
                    "target-owner old Context detached on retraction"
                );

            if(npcsB.snapshot().size()!=1||
               queueB.queuedBytes()!=0)
                throw new AssertionError(
                    "multi-context retraction changed target mirror/transport"
                );

            pressure.release();
            pressure=null;

            SharedNpcWorldRelay.register(
                writerA,
                worldB,
                ownerB,
                npcsB,
                ownerB.movement()
            );

            if(contextFor(
                    writerB
                )!=null)
                throw new AssertionError(
                    "old target-owner writer remained bound after successful retry"
                );

            Object rebound=
                contextFor(
                    writerA
                );

            if(rebound==null||
               rebound==contextA||
               rebound==contextB)
                throw new AssertionError(
                    "successful multi-context retry did not install fresh Context"
                );

            if(!npcsB.snapshot().isEmpty())
                throw new AssertionError(
                    "successful multi-context cleanup retained old mirror"
                );

            drain(queueB);

            Player81WorldSync.unregister(
                writerB
            );

            Player81WorldSync.Context reboundSync=
                Player81WorldSync.register(
                    writerA,
                    worldB,
                    ownerB,
                    new DevAuthorityWorkbench()
                );

            Player81WorldSync.transformForTest(
                reboundSync,
                BootstrapPackets.player81Idle()
            );

            SharedNpcWorldRelay.syncRemotePets(
                writerA
            );

            if(npcsB.snapshot().size()!=1)
                throw new AssertionError(
                    "multi-context rebound projection did not create exactly one mirror size="+
                    npcsB.snapshot().size()
                );

            drain(queueA);

            SharedNpcWorldRelay.syncRemotePets(
                writerA
            );

            if(npcsB.snapshot().size()!=1)
                throw new AssertionError(
                    "multi-context steady-state duplicated mirror"
                );
        }finally{
            if(pressure!=null)
                pressure.release();

            SharedNpcWorldRelay.unregister(
                writerA
            );
            SharedNpcWorldRelay.unregister(
                writerB
            );
            SharedNpcWorldRelay.unregister(
                sourceWriter
            );

            Player81WorldSync.unregister(
                writerA
            );
            Player81WorldSync.unregister(
                writerB
            );
            Player81WorldSync.unregister(
                sourceWriter
            );

            if(ownerA.registered())
                worldA.unregisterPlayer(
                    ownerA,
                    ownerAGeneration
                );

            if(ownerB.registered())
                worldB.unregisterPlayer(
                    ownerB,
                    ownerBGeneration
                );

            if(sourceB.registered())
                worldB.unregisterPlayer(
                    sourceB,
                    sourceBGeneration
                );

            worldA.close();
            worldB.close();
        }
    }

    private static void assertTerminalCleanupCannotResurrectWriter()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                601L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "rebind-terminal-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "rebind-terminal-viewer"
            );

        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1011,1012,1013,1014}
                )
            );

        SwitchablePrefixFailOutputStream viewerOut=
            new SwitchablePrefixFailOutputStream();
        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerOut,
                new IsaacCipher(
                    new int[]{1015,1016,1017,1018}
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

        try{
            PetDefinitionRepository.Def pet=
                PetDefinitionRepository.get(
                    24019
                );

            if(pet==null)
                throw new AssertionError(
                    "missing pet 24019"
                );

            sourceNpcs.spawnPet(
                pet,
                source.movement(),
                sourceWriter
            );

            Player81WorldSync.transformForTest(
                viewerSync,
                BootstrapPackets.player81Idle()
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "terminal rebind fixture did not create one mirror"
                );

            Object oldContext=
                contextFor(
                    viewerWriter
                );

            if(oldContext==null)
                throw new AssertionError(
                    "terminal rebind old context missing"
                );

            viewerOut.enableFailure();

            int attemptsBefore=
                viewerOut.attempts();

            boolean terminalRejected=false;

            try{
                SharedNpcWorldRelay.register(
                    viewerWriter,
                    world,
                    viewer,
                    viewerNpcs,
                    viewer.movement()
                );
            }catch(SharedNpcWorldRelay.TerminalRegistrationException expected){
                terminalRejected=true;
            }

            if(!terminalRejected)
                throw new AssertionError(
                    "terminal cleanup did not reject replacement"
                );

            if(contextFor(
                    viewerWriter
                )!=oldContext)
                throw new AssertionError(
                    "terminal cleanup detached old fail-closed Context"
                );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "terminal cleanup lost mirror authority"
                );

            if(viewerOut.attempts()<=attemptsBefore)
                throw new AssertionError(
                    "terminal cleanup fixture did not reach failing transport"
                );

            int attemptsAfterFailure=
                viewerOut.attempts();

            boolean secondRejected=false;

            try{
                SharedNpcWorldRelay.register(
                    viewerWriter,
                    world,
                    viewer,
                    viewerNpcs,
                    viewer.movement()
                );
            }catch(SharedNpcWorldRelay.TerminalRegistrationException expected){
                secondRejected=true;
            }

            if(!secondRejected)
                throw new AssertionError(
                    "fail-closed writer was resurrected by later registration"
                );

            if(contextFor(
                    viewerWriter
                )!=oldContext)
                throw new AssertionError(
                    "later terminal registration replaced fail-closed Context"
                );

            if(viewerOut.attempts()!=
                    attemptsAfterFailure)
                throw new AssertionError(
                    "later registration retried terminal transport attemptsBefore="+
                    attemptsAfterFailure+
                    " after="+
                    viewerOut.attempts()
                );
        }finally{
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

    private static final class SwitchablePrefixFailOutputStream
        extends java.io.OutputStream {

        private final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();
        private boolean fail;
        private int attempts;

        synchronized void enableFailure(){
            fail=true;
        }

        synchronized int attempts(){
            return attempts;
        }

        @Override public synchronized void write(
            int value
        )throws java.io.IOException{
            attempts++;

            if(fail){
                bytes.write(value);
                throw new java.io.IOException(
                    "EXPECTED_TERMINAL_REBIND_PREFIX_FAILURE"
                );
            }

            bytes.write(value);
        }

        @Override public synchronized void write(
            byte[] data,
            int offset,
            int length
        )throws java.io.IOException{
            attempts++;

            if(fail){
                if(length>0)
                    bytes.write(
                        data[offset]
                    );
                throw new java.io.IOException(
                    "EXPECTED_TERMINAL_REBIND_PREFIX_FAILURE"
                );
            }

            bytes.write(
                data,
                offset,
                length
            );
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
