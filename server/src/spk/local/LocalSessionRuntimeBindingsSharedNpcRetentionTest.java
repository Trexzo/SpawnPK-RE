package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;

public final class LocalSessionRuntimeBindingsSharedNpcRetentionTest {
    private static final int QUEUE_CAPACITY=1024;

    private static final class Bridge
        implements LocalSessionRuntimeBindings.SessionBridge {
        @Override public void saveAccount(
            String tag,
            String reason
        ){}
    }

    public static void main(String[] args)throws Exception{
        assertRetryableSharedNpcContextSurvivesBindingRollback();
        assertTerminalSharedNpcFailureRetiresBrokenWriterBundle();
        assertTerminalTradePeerCloseFailureRetiresPeerBundle();
        assertTerminalOldOwnerWriterIdentityIsExact();

        System.out.println(
            "LOCAL_SESSION_RUNTIME_BINDINGS_SHARED_NPC_RETENTION_PASS "+
            "retryableOldContextPreserved=true "+
            "retryableOldPlayer81Preserved=true "+
            "retryableBindingRetrySucceeds=true "+
            "terminalRelaySentinelRetained=true "+
            "terminalPlayer81Retired=true "+
            "terminalTradeRetired=true "+
            "terminalPeerOnlyClose=true "+
            "terminalBrokenWriterNotRetouched=true "+
            "terminalPeerWriterRetired=true "+
            "terminalPeerSentinelRetained=true "+
            "terminalPeerWriterNotRetouched=true "+
            "terminalTradeCancelledOnce=true "+
            "terminalOldOwnerWriterExact=true "+
            "attemptedNewWriterNotRetired=true"
        );
    }

    private static void assertRetryableSharedNpcContextSurvivesBindingRollback()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                700L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "binding-retain-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "binding-retain-viewer"
            );

        DevAuthorityWorkbench viewerDev=
            new DevAuthorityWorkbench();
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                viewerDev
            );
        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    new int[]{1101,1102,1103,1104}
                )
            );
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1105,1106,1107,1108}
                )
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.Context oldViewerSync=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                viewerDev
            );

        /*
         * #1621 prepublishes S2C104 before SharedNpc admission. Seed the
         * already-authoritative old Player81 contexts while transport is
         * healthy so the injected failure reaches SharedNpc rebind cleanup.
         */
        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
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

        LocalSessionRuntimeBindings bindings=
            new LocalSessionRuntimeBindings(
                world,
                viewer,
                viewerDev,
                viewerNpcs,
                viewer.movement(),
                viewer.bank(),
                new Bridge()
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
                source.movement(),
                sourceWriter
            );

            Player81WorldSync.transformForTest(
                oldViewerSync,
                BootstrapPackets.player81Idle()
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "retryable wrapper fixture mirror count="+
                    viewerNpcs.snapshot().size()
                );

            drain(viewerQueue);

            Object oldRelayContext=
                relayContextFor(
                    viewerWriter
                );

            pressure=
                OutboundPacketQueue.reserveBatch(
                    viewerQueue,
                    QUEUE_CAPACITY
                );

            boolean rejected=false;

            try{
                bindings.register(
                    viewerWriter,
                    "[binding-sharednpc-retain] "
                );
            }catch(SharedNpcWorldRelay.RetryableRegistrationException expected){
                rejected=true;
            }

            if(!rejected)
                throw new AssertionError(
                    "runtime binding did not surface retryable SharedNpc rejection"
                );

            if(bindings.context()!=null)
                throw new AssertionError(
                    "retryable binding failure retained local Player81 context"
                );

            if(relayContextFor(
                    viewerWriter
                )!=oldRelayContext)
                throw new AssertionError(
                    "binding rollback unregistered retained retryable SharedNpc Context"
                );

            if(player81ContextFor(
                    viewerWriter
                )!=oldViewerSync)
                throw new AssertionError(
                    "retryable SharedNpc rejection did not preserve exact old Player81 Context"
                );

            if(viewerNpcs.snapshot().size()!=1||
               viewerQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "retryable binding rollback changed mirror/transport authority"
                );

            pressure.release();
            pressure=null;

            bindings.register(
                viewerWriter,
                "[binding-sharednpc-retain] "
            );

            if(bindings.context()==null)
                throw new AssertionError(
                    "binding retry did not install Player81 context"
                );

            Object newRelayContext=
                relayContextFor(
                    viewerWriter
                );

            if(newRelayContext==null||
               newRelayContext==oldRelayContext)
                throw new AssertionError(
                    "binding retry did not install fresh SharedNpc Context"
                );

            if(!viewerNpcs.snapshot().isEmpty())
                throw new AssertionError(
                    "binding retry retained stale old mirror"
                );

            drain(viewerQueue);

            Player81WorldSync.transformForTest(
                bindings.context(),
                BootstrapPackets.player81Idle()
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "successful binding retry did not recreate exactly one mirror"
                );

            bindings.unregister();
        }finally{
            if(pressure!=null)
                pressure.release();

            bindings.unregister();

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

    private static void assertTerminalSharedNpcFailureRetiresBrokenWriterBundle()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                701L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "binding-terminal-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "binding-terminal-viewer"
            );

        DevAuthorityWorkbench viewerDev=
            new DevAuthorityWorkbench();
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                viewerDev
            );
        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        SwitchablePrefixFailOutputStream viewerOut=
            new SwitchablePrefixFailOutputStream();
        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerOut,
                new IsaacCipher(
                    new int[]{1111,1112,1113,1114}
                )
            );
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1115,1116,1117,1118}
                )
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.Context oldViewerSync=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                viewerDev
            );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
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

        TradeService.register(
            world,
            source,
            sourceGeneration,
            source.bank(),
            sourceWriter,
            ()->{}
        );
        TradeService.register(
            world,
            viewer,
            viewerGeneration,
            viewer.bank(),
            viewerWriter,
            ()->{}
        );

        LocalSessionRuntimeBindings bindings=
            new LocalSessionRuntimeBindings(
                world,
                viewer,
                viewerDev,
                viewerNpcs,
                viewer.movement(),
                viewer.bank(),
                new Bridge()
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
                oldViewerSync,
                BootstrapPackets.player81Idle()
            );
            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "terminal wrapper fixture mirror count="+
                    viewerNpcs.snapshot().size()
                );

            String tradeOpen=
                TradeService.start(
                    world,
                    source,
                    viewer
                );

            if(tradeOpen==null||
               !tradeOpen.contains(
                    "TRADE_UI_OPEN"
                ))
                throw new AssertionError(
                    "terminal SharedNpc Trade fixture failed: "+
                    tradeOpen
                );

            drain(sourceQueue);

            Object oldRelayContext=
                relayContextFor(
                    viewerWriter
                );

            int attemptsBeforeFailure=
                viewerOut.attempts();
            viewerOut.enableFailure();

            boolean terminal=false;

            try{
                bindings.register(
                    viewerWriter,
                    "[binding-sharednpc-terminal] ",
                    viewerGeneration
                );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                terminal=
                    expected.owner==viewer&&
                    expected.writer==viewerWriter;
            }

            if(!terminal)
                throw new AssertionError(
                    "terminal SharedNpc failure did not carry exact owner/writer"
                );

            if(viewerOut.attempts()!=
                    attemptsBeforeFailure+1)
                throw new AssertionError(
                    "terminal cleanup retouched broken writer before="+
                    attemptsBeforeFailure+
                    " after="+
                    viewerOut.attempts()
                );

            if(relayContextFor(
                    viewerWriter
                )!=oldRelayContext)
                throw new AssertionError(
                    "terminal SharedNpc sentinel identity was removed/replaced"
                );

            if(player81ContextFor(
                    viewerWriter
                )!=null)
                throw new AssertionError(
                    "terminal SharedNpc failure retained Player81 authority"
                );

            if(TradeService.active(viewer)||
               TradeService.active(source))
                throw new AssertionError(
                    "terminal SharedNpc failure retained live Trade"
                );

            if(sourceQueue.queuedBytes()==0)
                throw new AssertionError(
                    "healthy Trade peer did not receive terminal close"
                );

            int attemptsAfterFailure=
                viewerOut.attempts();

            boolean retryRejected=false;
            try{
                bindings.register(
                    viewerWriter,
                    "[binding-sharednpc-terminal] ",
                    viewerGeneration
                );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                retryRejected=
                    expected.owner==viewer&&
                    expected.writer==viewerWriter;
            }

            if(!retryRejected)
                throw new AssertionError(
                    "terminal SharedNpc writer was resurrected"
                );

            if(viewerOut.attempts()!=
                    attemptsAfterFailure)
                throw new AssertionError(
                    "terminal retry touched broken writer"
                );
        }finally{
            viewerOut.disableFailure();

            bindings.unregister();
            TradeService.unregister(
                source
            );
            TradeService.unregister(
                viewer
            );
            SharedNpcWorldRelay.unregister(
                sourceWriter
            );

            /*
             * The viewer relay is intentionally a retained fail-closed
             * sentinel. Final fixture teardown may remove it after transport
             * failure injection is disabled.
             */
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

    private static void assertTerminalTradePeerCloseFailureRetiresPeerBundle()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                703L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "binding-terminal-peer-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "binding-terminal-peer-viewer"
            );

        DevAuthorityWorkbench viewerDev=
            new DevAuthorityWorkbench();
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                viewerDev
            );
        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        SwitchablePrefixFailOutputStream viewerOut=
            new SwitchablePrefixFailOutputStream();
        SwitchablePrefixFailOutputStream sourceOut=
            new SwitchablePrefixFailOutputStream();

        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerOut,
                new IsaacCipher(
                    new int[]{1131,1132,1133,1134}
                )
            );
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceOut,
                new IsaacCipher(
                    new int[]{1135,1136,1137,1138}
                )
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.Context oldViewerSync=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                viewerDev
            );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
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

        TradeService.register(
            world,
            source,
            sourceGeneration,
            source.bank(),
            sourceWriter,
            ()->{}
        );
        TradeService.register(
            world,
            viewer,
            viewerGeneration,
            viewer.bank(),
            viewerWriter,
            ()->{}
        );

        LocalSessionRuntimeBindings bindings=
            new LocalSessionRuntimeBindings(
                world,
                viewer,
                viewerDev,
                viewerNpcs,
                viewer.movement(),
                viewer.bank(),
                new Bridge()
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
                oldViewerSync,
                BootstrapPackets.player81Idle()
            );
            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "terminal peer fixture mirror missing"
                );

            String tradeOpen=
                TradeService.start(
                    world,
                    source,
                    viewer
                );

            if(tradeOpen==null||
               !tradeOpen.contains(
                    "TRADE_UI_OPEN"
               ))
                throw new AssertionError(
                    "terminal peer Trade fixture failed: "+
                    tradeOpen
                );

            Object oldViewerRelay=
                relayContextFor(
                    viewerWriter
                );
            Object oldSourceRelay=
                relayContextFor(
                    sourceWriter
                );

            int viewerAttemptsBefore=
                viewerOut.attempts();
            int sourceAttemptsBefore=
                sourceOut.attempts();

            sourceOut.enableFailure();
            viewerOut.enableFailure();

            boolean terminal=false;
            try{
                bindings.register(
                    viewerWriter,
                    "[binding-sharednpc-terminal-peer] ",
                    viewerGeneration
                );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                terminal=
                    expected.owner==viewer&&
                    expected.writer==viewerWriter;
            }

            if(!terminal)
                throw new AssertionError(
                    "terminal peer fixture did not surface original writer"
                );

            if(viewerOut.attempts()!=
                    viewerAttemptsBefore+1)
                throw new AssertionError(
                    "original terminal writer was retouched during peer retirement"
                );

            if(sourceOut.attempts()!=
                    sourceAttemptsBefore+1)
                throw new AssertionError(
                    "terminal peer close was duplicated/retouched before="+
                    sourceAttemptsBefore+
                    " after="+
                    sourceOut.attempts()
                );

            if(relayContextFor(
                    viewerWriter
                )!=oldViewerRelay)
                throw new AssertionError(
                    "original terminal relay sentinel changed"
                );

            if(relayContextFor(
                    sourceWriter
                )!=oldSourceRelay)
                throw new AssertionError(
                    "terminal peer relay sentinel was removed/replaced"
                );

            if(player81ContextFor(
                    viewerWriter
                )!=null||
               player81ContextFor(
                    sourceWriter
               )!=null)
                throw new AssertionError(
                    "terminal peer fixture retained Player81 authority"
                );

            if(TradeService.active(viewer)||
               TradeService.active(source))
                throw new AssertionError(
                    "terminal peer failure retained live Trade"
                );

            int sourceAttemptsAfter=
                sourceOut.attempts();

            boolean sourceRejected=false;
            try{
                SharedNpcWorldRelay.preflightRegistration(
                    sourceWriter,
                    world,
                    source
                );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                sourceRejected=
                    expected.owner==source&&
                    expected.writer==sourceWriter;
            }

            if(!sourceRejected)
                throw new AssertionError(
                    "terminal peer writer could be resurrected"
                );

            if(sourceOut.attempts()!=
                    sourceAttemptsAfter)
                throw new AssertionError(
                    "terminal peer preflight retouched broken writer"
                );

            int viewerAttemptsAfter=
                viewerOut.attempts();

            boolean viewerRejected=false;
            try{
                bindings.register(
                    viewerWriter,
                    "[binding-sharednpc-terminal-peer] ",
                    viewerGeneration
                );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                viewerRejected=
                    expected.owner==viewer&&
                    expected.writer==viewerWriter;
            }

            if(!viewerRejected||
               viewerOut.attempts()!=
                    viewerAttemptsAfter)
                throw new AssertionError(
                    "original terminal writer retry touched transport"
                );

            if(sourceOut.attempts()!=
                    sourceAttemptsAfter)
                throw new AssertionError(
                    "original retry recursively retouched terminal peer"
                );
        }finally{
            sourceOut.disableFailure();
            viewerOut.disableFailure();

            bindings.unregister();
            TradeService.unregister(
                source
            );
            TradeService.unregister(
                viewer
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

    private static void assertTerminalOldOwnerWriterIdentityIsExact()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                702L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "binding-terminal-oldowner-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "binding-terminal-oldowner-viewer"
            );

        DevAuthorityWorkbench viewerDev=
            new DevAuthorityWorkbench();
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                viewerDev
            );
        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        SwitchablePrefixFailOutputStream oldViewerOut=
            new SwitchablePrefixFailOutputStream();
        OutboundPacketQueue newViewerQueue=
            new OutboundPacketQueue();
        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter oldViewerWriter=
            new ServerPacketWriter(
                oldViewerOut,
                new IsaacCipher(
                    new int[]{1121,1122,1123,1124}
                )
            );
        ServerPacketWriter newViewerWriter=
            new ServerPacketWriter(
                newViewerQueue,
                new IsaacCipher(
                    new int[]{1125,1126,1127,1128}
                )
            );
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1129,1130,1131,1132}
                )
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.Context oldViewerSync=
            Player81WorldSync.register(
                oldViewerWriter,
                world,
                viewer,
                viewerDev
            );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
        );

        SharedNpcWorldRelay.register(
            sourceWriter,
            world,
            source,
            sourceNpcs,
            source.movement()
        );
        SharedNpcWorldRelay.register(
            oldViewerWriter,
            world,
            viewer,
            viewerNpcs,
            viewer.movement()
        );

        TradeService.register(
            world,
            source,
            sourceGeneration,
            source.bank(),
            sourceWriter,
            ()->{}
        );
        TradeService.register(
            world,
            viewer,
            viewerGeneration,
            viewer.bank(),
            oldViewerWriter,
            ()->{}
        );

        LocalSessionRuntimeBindings bindings=
            new LocalSessionRuntimeBindings(
                world,
                viewer,
                viewerDev,
                viewerNpcs,
                viewer.movement(),
                viewer.bank(),
                new Bridge()
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
                oldViewerSync,
                BootstrapPackets.player81Idle()
            );
            SharedNpcWorldRelay.syncRemotePets(
                oldViewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "old-owner terminal fixture mirror missing"
                );

            String tradeOpen=
                TradeService.start(
                    world,
                    source,
                    viewer
                );
            if(tradeOpen==null||
               !tradeOpen.contains(
                    "TRADE_UI_OPEN"
               ))
                throw new AssertionError(
                    "old-owner terminal Trade fixture failed: "+
                    tradeOpen
                );

            drain(sourceQueue);

            Object oldRelayContext=
                relayContextFor(
                    oldViewerWriter
                );

            int oldAttemptsBefore=
                oldViewerOut.attempts();
            oldViewerOut.enableFailure();

            boolean terminal=false;
            try{
                bindings.register(
                    newViewerWriter,
                    "[binding-sharednpc-oldowner-terminal] ",
                    viewerGeneration
                );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                terminal=
                    expected.owner==viewer&&
                    expected.writer==oldViewerWriter;
            }

            if(!terminal)
                throw new AssertionError(
                    "terminal old-owner failure did not identify old writer"
                );

            if(oldViewerOut.attempts()!=
                    oldAttemptsBefore+1)
                throw new AssertionError(
                    "old terminal writer was retouched during cleanup"
                );

            if(relayContextFor(
                    oldViewerWriter
                )!=oldRelayContext)
                throw new AssertionError(
                    "old-owner terminal relay sentinel was removed"
                );

            if(player81ContextFor(
                    oldViewerWriter
                )!=null)
                throw new AssertionError(
                    "old-owner terminal Player81 authority survived"
                );

            if(player81ContextFor(
                    newViewerWriter
                )!=null||
               relayContextFor(
                    newViewerWriter
                )!=null)
                throw new AssertionError(
                    "failed replacement installed authority on attempted new writer"
                );

            if(newViewerQueue.queuedBytes()==0)
                throw new AssertionError(
                    "attempted new writer did not receive prepublished option state"
                );

            if(TradeService.active(viewer)||
               TradeService.active(source))
                throw new AssertionError(
                    "old-owner terminal failure retained Trade"
                );

            int oldAttemptsAfter=
                oldViewerOut.attempts();

            boolean retryRejected=false;
            try{
                bindings.register(
                    newViewerWriter,
                    "[binding-sharednpc-oldowner-terminal] ",
                    viewerGeneration
                );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                retryRejected=
                    expected.owner==viewer&&
                    expected.writer==oldViewerWriter;
            }

            if(!retryRejected||
               oldViewerOut.attempts()!=
                    oldAttemptsAfter)
                throw new AssertionError(
                    "old-owner terminal sentinel did not reject without retouch"
                );
        }finally{
            oldViewerOut.disableFailure();

            bindings.unregister();
            TradeService.unregister(
                source
            );
            TradeService.unregister(
                viewer
            );
            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.unregister(
                oldViewerWriter
            );
            SharedNpcWorldRelay.unregister(
                newViewerWriter
            );
            Player81WorldSync.unregister(
                sourceWriter
            );
            Player81WorldSync.unregister(
                oldViewerWriter
            );
            Player81WorldSync.unregister(
                newViewerWriter
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

    private static final class SwitchablePrefixFailOutputStream
        extends OutputStream {

        private final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();
        private boolean fail;
        private int attempts;

        synchronized void enableFailure(){
            fail=true;
        }

        synchronized void disableFailure(){
            fail=false;
        }

        synchronized int attempts(){
            return attempts;
        }

        @Override public synchronized void write(
            int value
        )throws IOException{
            attempts++;

            if(fail){
                bytes.write(value);
                throw new IOException(
                    "EXPECTED_RUNTIME_BINDING_TERMINAL_PREFIX_FAILURE"
                );
            }

            bytes.write(value);
        }

        @Override public synchronized void write(
            byte[] data,
            int offset,
            int length
        )throws IOException{
            attempts++;

            if(fail){
                if(length>0)
                    bytes.write(
                        data[offset]
                    );
                throw new IOException(
                    "EXPECTED_RUNTIME_BINDING_TERMINAL_PREFIX_FAILURE"
                );
            }

            bytes.write(
                data,
                offset,
                length
            );
        }
    }

    private LocalSessionRuntimeBindingsSharedNpcRetentionTest(){}
}
