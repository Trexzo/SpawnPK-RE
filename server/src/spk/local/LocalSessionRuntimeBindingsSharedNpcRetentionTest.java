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
        assertRetractedTradePeerClosePreservesPeerRuntime();
        assertTerminalTradePeerCloseFailureRetiresPeerBundle();
        assertTerminalCompetingRootPeerCloseRetiresPeerBundle();
        assertTerminalTradeCancellationWriterRetiresOnlyFailedParticipant();
        assertTerminalConfirmPublicationWriterIsNotRetouched();
        assertTerminalStartRootWriterIsNotRetouched();
        assertTerminalTradeXPromptWriterIsNotRetouched();
        assertTerminalStartRollbackWriterIsRetired();
        assertTerminalWriterLatchStopsSessionReuse();
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
            "retractedPeerRuntimePreserved=true "+
            "retractedPeerWriterRetrySucceeds=true "+
            "terminalPeerWriterRetired=true "+
            "terminalPeerSentinelRetained=true "+
            "terminalPeerWriterNotRetouched=true "+
            "terminalTradeCancelledOnce=true "+
            "terminalCompetingRootPeerRetired=true "+
            "terminalCompetingRootCommitted=true "+
            "terminalTradeCancelWriterRetired=true "+
            "terminalTradeCancelHealthyPeerClosed=true "+
            "terminalConfirmWriterNotRetouched=true "+
            "terminalConfirmHealthyPeerClosed=true "+
            "terminalStartWriterNotRetouched=true "+
            "terminalStartHealthyPeerClosed=true "+
            "terminalTradeOfferXWriterRetired=true "+
            "terminalTradeRemoveXWriterRetired=true "+
            "terminalTradeXHealthyPeerClosed=true "+
            "terminalStartRollbackWriterRetired=true "+
            "terminalStartRollbackPrimaryPreserved=true "+
            "terminalWriterLatched=true "+
            "terminalSessionIngressRejected=true "+
            "terminalReplacementWriterHealthy=true "+
            "terminalSentinelSurvivesUnregister=true "+
            "terminalUnregisterPreservesReplacement=true "+
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

    private static void assertRetractedTradePeerClosePreservesPeerRuntime()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                704L
            );
        WorldPlayer broken=
            new WorldPlayer();
        WorldPlayer peer=
            new WorldPlayer();

        long brokenGeneration=
            world.registerPlayer(
                broken,
                "binding-retracted-peer-broken"
            );
        long peerGeneration=
            world.registerPlayer(
                peer,
                "binding-retracted-peer-healthy"
            );

        NpcRegistry brokenNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry peerNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        ServerPacketWriter brokenWriter=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{1141,1142,1143,1144}
                )
            );
        OutboundPacketQueue peerQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        ServerPacketWriter peerWriter=
            new ServerPacketWriter(
                peerQueue,
                new IsaacCipher(
                    new int[]{1145,1146,1147,1148}
                )
            );

        OutboundPacketQueue.BatchReservation pressure=null;

        Player81WorldSync.Context peerSync=
            Player81WorldSync.register(
                peerWriter,
                world,
                peer,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.register(
            brokenWriter,
            world,
            broken,
            new DevAuthorityWorkbench()
        );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
        );

        SharedNpcWorldRelay.register(
            brokenWriter,
            world,
            broken,
            brokenNpcs,
            broken.movement()
        );
        SharedNpcWorldRelay.register(
            peerWriter,
            world,
            peer,
            peerNpcs,
            peer.movement()
        );

        TradeService.register(
            world,
            broken,
            brokenGeneration,
            broken.bank(),
            brokenWriter,
            ()->{}
        );
        TradeService.register(
            world,
            peer,
            peerGeneration,
            peer.bank(),
            peerWriter,
            ()->{}
        );

        try{
            String tradeOpen=
                TradeService.start(
                    world,
                    broken,
                    peer
                );

            if(tradeOpen==null||
               !tradeOpen.contains(
                    "TRADE_UI_OPEN"
                ))
                throw new AssertionError(
                    "retracted peer Trade fixture failed: "+
                    tradeOpen
                );

            drain(peerQueue);

            Object peerRelay=
                relayContextFor(
                    peerWriter
                );

            pressure=
                OutboundPacketQueue.reserveBatch(
                    peerQueue,
                    QUEUE_CAPACITY
                );

            TradeService.BrokenWriterRetirement retirement=
                TradeService.retireBrokenWriter(
                    broken,
                    brokenWriter
                );

            if(retirement.peerClose!=
                    TradeService.BrokenWriterPeerClose
                        .RETRACTED_RETRYABLE||
               retirement.peerOwner!=peer||
               retirement.peerWriter!=peerWriter||
               retirement.terminalPeer())
                throw new AssertionError(
                    "peer close was not classified retractable"
                );

            if(TradeService.active(broken)||
               TradeService.active(peer))
                throw new AssertionError(
                    "retracted peer close retained live Trade"
                );

            if(player81ContextFor(
                    peerWriter
                )!=peerSync||
               relayContextFor(
                    peerWriter
                )!=peerRelay)
                throw new AssertionError(
                    "retracted peer close changed healthy peer runtime authority"
                );

            if(peerQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "retracted peer close admitted bytes"
                );

            pressure.release();
            pressure=null;

            ServerPacketWriter.RecoverablePacketResult retry=
                peerWriter.publishRecoverablePacketIfIdle(
                    ()->peerWriter.fixed(
                        219,
                        new byte[0]
                    )
                );

            if(retry!=
                    ServerPacketWriter
                        .RecoverablePacketResult
                        .COMMITTED||
               peerQueue.queuedBytes()==0)
                throw new AssertionError(
                    "healthy peer writer did not recover after retractable close"
                );
        }finally{
            if(pressure!=null)
                pressure.release();

            TradeService.unregister(
                broken
            );
            TradeService.unregister(
                peer
            );
            SharedNpcWorldRelay.unregister(
                brokenWriter
            );
            SharedNpcWorldRelay.unregister(
                peerWriter
            );
            Player81WorldSync.unregister(
                brokenWriter
            );
            Player81WorldSync.unregister(
                peerWriter
            );

            if(broken.registered())
                world.unregisterPlayer(
                    broken,
                    brokenGeneration
                );
            if(peer.registered())
                world.unregisterPlayer(
                    peer,
                    peerGeneration
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

    private static void assertTerminalCompetingRootPeerCloseRetiresPeerBundle()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                705L
            );
        WorldPlayer current=
            new WorldPlayer();
        WorldPlayer peer=
            new WorldPlayer();

        long currentGeneration=
            world.registerPlayer(
                current,
                "binding-competing-root-current"
            );
        long peerGeneration=
            world.registerPlayer(
                peer,
                "binding-competing-root-peer"
            );

        DevAuthorityWorkbench currentDev=
            new DevAuthorityWorkbench();
        DevAuthorityWorkbench peerDev=
            new DevAuthorityWorkbench();
        NpcRegistry currentNpcs=
            new NpcRegistry(
                currentDev
            );
        NpcRegistry peerNpcs=
            new NpcRegistry(
                peerDev
            );

        OutboundPacketQueue currentQueue=
            new OutboundPacketQueue();
        SwitchablePrefixFailOutputStream peerOut=
            new SwitchablePrefixFailOutputStream();

        ServerPacketWriter currentWriter=
            new ServerPacketWriter(
                currentQueue,
                new IsaacCipher(
                    new int[]{1151,1152,1153,1154}
                )
            );
        ServerPacketWriter peerWriter=
            new ServerPacketWriter(
                peerOut,
                new IsaacCipher(
                    new int[]{1155,1156,1157,1158}
                )
            );

        Player81WorldSync.Context currentSync=
            Player81WorldSync.register(
                currentWriter,
                world,
                current,
                currentDev
            );
        Player81WorldSync.register(
            peerWriter,
            world,
            peer,
            peerDev
        );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
        );

        SharedNpcWorldRelay.register(
            currentWriter,
            world,
            current,
            currentNpcs,
            current.movement()
        );
        SharedNpcWorldRelay.register(
            peerWriter,
            world,
            peer,
            peerNpcs,
            peer.movement()
        );

        TradeService.register(
            world,
            current,
            currentGeneration,
            current.bank(),
            currentWriter,
            ()->{}
        );
        TradeService.register(
            world,
            peer,
            peerGeneration,
            peer.bank(),
            peerWriter,
            ()->{}
        );

        try{
            String tradeOpen=
                TradeService.start(
                    world,
                    current,
                    peer
                );

            if(tradeOpen==null||
               !tradeOpen.contains(
                    "TRADE_UI_OPEN"
               ))
                throw new AssertionError(
                    "terminal competing-root Trade fixture failed: "+
                    tradeOpen
                );

            drain(
                currentQueue
            );

            Object currentRelay=
                relayContextFor(
                    currentWriter
                );
            Object peerRelay=
                relayContextFor(
                    peerWriter
                );
            int peerAttemptsBefore=
                peerOut.attempts();

            peerOut.enableFailure();

            String replacement=
                LocalSession
                    .replaceMonsterSpawnerRootForCurrentSession(
                        world,
                        current,
                        currentGeneration,
                        ()->{
                            currentWriter.fixed(
                                97,
                                BootstrapPackets
                                    .interface97(
                                        15106
                                    )
                            );
                            return "TEST_TERMINAL_COMPETING_ROOT";
                        }
                    );

            if(!"TEST_TERMINAL_COMPETING_ROOT"
                    .equals(
                        replacement
                    ))
                throw new AssertionError(
                    "committed competing root was reported failed: "+
                    replacement
                );

            if(currentQueue.queuedBytes()!=3)
                throw new AssertionError(
                    "committed competing root bytes changed expected=3 actual="+
                    currentQueue.queuedBytes()
                );

            if(peerOut.attempts()!=
                    peerAttemptsBefore+1)
                throw new AssertionError(
                    "terminal competing-root peer close was duplicated/retouched before="+
                    peerAttemptsBefore+
                    " after="+
                    peerOut.attempts()
                );

            if(TradeService.active(
                    current
                )||
               TradeService.active(
                    peer
               ))
                throw new AssertionError(
                    "terminal competing-root peer failure retained live Trade"
                );

            if(player81ContextFor(
                    peerWriter
                )!=null)
                throw new AssertionError(
                    "terminal competing-root peer retained Player81 authority"
                );

            if(relayContextFor(
                    peerWriter
                )!=peerRelay)
                throw new AssertionError(
                    "terminal competing-root peer relay sentinel changed"
                );

            if(player81ContextFor(
                    currentWriter
                )!=currentSync||
               relayContextFor(
                    currentWriter
                )!=currentRelay)
                throw new AssertionError(
                    "terminal competing-root peer failure retired initiating runtime"
                );

            int peerAttemptsAfter=
                peerOut.attempts();
            boolean rejected=false;

            try{
                SharedNpcWorldRelay
                    .preflightRegistration(
                        peerWriter,
                        world,
                        peer
                    );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                rejected=
                    expected.owner==peer&&
                    expected.writer==peerWriter;
            }

            if(!rejected)
                throw new AssertionError(
                    "terminal competing-root peer writer was resurrectable"
                );

            if(peerOut.attempts()!=
                    peerAttemptsAfter)
                throw new AssertionError(
                    "terminal competing-root preflight retouched peer writer"
                );
        }finally{
            peerOut.disableFailure();

            TradeService.unregister(
                current
            );
            TradeService.unregister(
                peer
            );
            SharedNpcWorldRelay.unregister(
                currentWriter
            );
            SharedNpcWorldRelay.unregister(
                peerWriter
            );
            Player81WorldSync.unregister(
                currentWriter
            );
            Player81WorldSync.unregister(
                peerWriter
            );

            if(current.registered())
                world.unregisterPlayer(
                    current,
                    currentGeneration
                );
            if(peer.registered())
                world.unregisterPlayer(
                    peer,
                    peerGeneration
                );

            world.close();
        }
    }

    private static void assertTerminalTradeCancellationWriterRetiresOnlyFailedParticipant()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                706L
            );
        WorldPlayer failed=
            new WorldPlayer();
        WorldPlayer healthy=
            new WorldPlayer();

        long failedGeneration=
            world.registerPlayer(
                failed,
                "binding-cancel-terminal-failed"
            );
        long healthyGeneration=
            world.registerPlayer(
                healthy,
                "binding-cancel-terminal-healthy"
            );

        DevAuthorityWorkbench failedDev=
            new DevAuthorityWorkbench();
        DevAuthorityWorkbench healthyDev=
            new DevAuthorityWorkbench();
        NpcRegistry failedNpcs=
            new NpcRegistry(
                failedDev
            );
        NpcRegistry healthyNpcs=
            new NpcRegistry(
                healthyDev
            );

        SwitchablePrefixFailOutputStream failedOut=
            new SwitchablePrefixFailOutputStream();
        SwitchablePrefixFailOutputStream healthyOut=
            new SwitchablePrefixFailOutputStream();

        ServerPacketWriter failedWriter=
            new ServerPacketWriter(
                failedOut,
                new IsaacCipher(
                    new int[]{1161,1162,1163,1164}
                )
            );
        ServerPacketWriter healthyWriter=
            new ServerPacketWriter(
                healthyOut,
                new IsaacCipher(
                    new int[]{1165,1166,1167,1168}
                )
            );

        Player81WorldSync.register(
            failedWriter,
            world,
            failed,
            failedDev
        );
        Player81WorldSync.Context healthySync=
            Player81WorldSync.register(
                healthyWriter,
                world,
                healthy,
                healthyDev
            );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
        );

        SharedNpcWorldRelay.register(
            failedWriter,
            world,
            failed,
            failedNpcs,
            failed.movement()
        );
        SharedNpcWorldRelay.register(
            healthyWriter,
            world,
            healthy,
            healthyNpcs,
            healthy.movement()
        );

        TradeService.register(
            world,
            failed,
            failedGeneration,
            failed.bank(),
            failedWriter,
            ()->{}
        );
        TradeService.register(
            world,
            healthy,
            healthyGeneration,
            healthy.bank(),
            healthyWriter,
            ()->{}
        );

        try{
            String tradeOpen=
                TradeService.start(
                    world,
                    failed,
                    healthy
                );

            if(tradeOpen==null||
               !tradeOpen.contains(
                    "TRADE_UI_OPEN"
               ))
                throw new AssertionError(
                    "terminal cancel Trade fixture failed: "+
                    tradeOpen
                );

            Object failedRelay=
                relayContextFor(
                    failedWriter
                );
            Object healthyRelay=
                relayContextFor(
                    healthyWriter
                );
            int failedAttemptsBefore=
                failedOut.attempts();
            int healthyAttemptsBefore=
                healthyOut.attempts();

            failedOut.enableFailure();

            String result=
                TradeService.handleWidget(
                    failed,
                    TradeService.FIRST_DECLINE
                );

            if(result==null||
               !result.contains(
                    "TRADE_CANCELLED_DECLINE"
               ))
                throw new AssertionError(
                    "terminal close changed committed cancel result: "+
                    result
                );

            if(failedOut.attempts()!=
                    failedAttemptsBefore+1)
                throw new AssertionError(
                    "failed cancellation writer was duplicated/retouched before="+
                    failedAttemptsBefore+
                    " after="+
                    failedOut.attempts()
                );

            if(healthyOut.attempts()!=
                    healthyAttemptsBefore+1)
                throw new AssertionError(
                    "healthy cancellation participant did not receive exactly one close before="+
                    healthyAttemptsBefore+
                    " after="+
                    healthyOut.attempts()
                );

            if(TradeService.active(
                    failed
                )||
               TradeService.active(
                    healthy
               ))
                throw new AssertionError(
                    "terminal cancellation close retained live Trade"
                );

            if(player81ContextFor(
                    failedWriter
                )!=null)
                throw new AssertionError(
                    "terminal cancellation writer retained Player81 authority"
                );

            if(relayContextFor(
                    failedWriter
                )!=failedRelay)
                throw new AssertionError(
                    "terminal cancellation SharedNpc sentinel changed"
                );

            if(player81ContextFor(
                    healthyWriter
                )!=healthySync||
               relayContextFor(
                    healthyWriter
                )!=healthyRelay)
                throw new AssertionError(
                    "terminal cancellation retired healthy participant runtime"
                );

            int failedAttemptsAfter=
                failedOut.attempts();
            boolean rejected=false;

            try{
                SharedNpcWorldRelay
                    .preflightRegistration(
                        failedWriter,
                        world,
                        failed
                    );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                rejected=
                    expected.owner==failed&&
                    expected.writer==failedWriter;
            }

            if(!rejected)
                throw new AssertionError(
                    "terminal cancellation writer was resurrectable"
                );

            if(failedOut.attempts()!=
                    failedAttemptsAfter)
                throw new AssertionError(
                    "terminal cancellation preflight retouched failed writer"
                );
        }finally{
            failedOut.disableFailure();

            TradeService.unregister(
                failed
            );
            TradeService.unregister(
                healthy
            );
            SharedNpcWorldRelay.unregister(
                failedWriter
            );
            SharedNpcWorldRelay.unregister(
                healthyWriter
            );
            Player81WorldSync.unregister(
                failedWriter
            );
            Player81WorldSync.unregister(
                healthyWriter
            );

            if(failed.registered())
                world.unregisterPlayer(
                    failed,
                    failedGeneration
                );
            if(healthy.registered())
                world.unregisterPlayer(
                    healthy,
                    healthyGeneration
                );

            world.close();
        }
    }

    private static void assertTerminalConfirmPublicationWriterIsNotRetouched()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                707L
            );
        WorldPlayer healthy=
            new WorldPlayer();
        WorldPlayer failed=
            new WorldPlayer();

        long healthyGeneration=
            world.registerPlayer(
                healthy,
                "binding-confirm-healthy"
            );
        long failedGeneration=
            world.registerPlayer(
                failed,
                "binding-confirm-failed"
            );

        DevAuthorityWorkbench healthyDev=
            new DevAuthorityWorkbench();
        DevAuthorityWorkbench failedDev=
            new DevAuthorityWorkbench();
        NpcRegistry healthyNpcs=
            new NpcRegistry(
                healthyDev
            );
        NpcRegistry failedNpcs=
            new NpcRegistry(
                failedDev
            );

        SwitchablePrefixFailOutputStream healthyOut=
            new SwitchablePrefixFailOutputStream();
        SwitchablePrefixFailOutputStream failedOut=
            new SwitchablePrefixFailOutputStream();

        ServerPacketWriter healthyWriter=
            new ServerPacketWriter(
                healthyOut,
                new IsaacCipher(
                    new int[]{1171,1172,1173,1174}
                )
            );
        ServerPacketWriter failedWriter=
            new ServerPacketWriter(
                failedOut,
                new IsaacCipher(
                    new int[]{1175,1176,1177,1178}
                )
            );

        Player81WorldSync.Context healthySync=
            Player81WorldSync.register(
                healthyWriter,
                world,
                healthy,
                healthyDev
            );
        Player81WorldSync.register(
            failedWriter,
            world,
            failed,
            failedDev
        );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
        );

        SharedNpcWorldRelay.register(
            healthyWriter,
            world,
            healthy,
            healthyNpcs,
            healthy.movement()
        );
        SharedNpcWorldRelay.register(
            failedWriter,
            world,
            failed,
            failedNpcs,
            failed.movement()
        );

        TradeService.register(
            world,
            healthy,
            healthyGeneration,
            healthy.bank(),
            healthyWriter,
            ()->{}
        );
        TradeService.register(
            world,
            failed,
            failedGeneration,
            failed.bank(),
            failedWriter,
            ()->{}
        );

        try{
            String tradeOpen=
                TradeService.start(
                    world,
                    healthy,
                    failed
                );

            if(tradeOpen==null||
               !tradeOpen.contains(
                    "TRADE_UI_OPEN"
               ))
                throw new AssertionError(
                    "terminal confirm Trade fixture failed: "+
                    tradeOpen
                );

            String firstAccept=
                TradeService.handleWidget(
                    healthy,
                    TradeService.FIRST_ACCEPT
                );

            if(firstAccept==null||
               !firstAccept.contains(
                    "WAITING_OTHER"
               ))
                throw new AssertionError(
                    "terminal confirm first accept fixture failed: "+
                    firstAccept
                );

            Object healthyRelay=
                relayContextFor(
                    healthyWriter
                );
            Object failedRelay=
                relayContextFor(
                    failedWriter
                );
            int healthyAttemptsBefore=
                healthyOut.attempts();
            int failedAttemptsBefore=
                failedOut.attempts();

            failedOut.enableFailure();

            boolean failedAsExpected=false;

            try{
                TradeService.handleWidget(
                    failed,
                    TradeService.FIRST_ACCEPT
                );
            }catch(IOException expected){
                failedAsExpected=
                    "EXPECTED_RUNTIME_BINDING_TERMINAL_PREFIX_FAILURE"
                        .equals(
                            expected.getMessage()
                        );
            }

            if(!failedAsExpected)
                throw new AssertionError(
                    "terminal confirm publication failure was not propagated"
                );

            if(failedOut.attempts()!=
                    failedAttemptsBefore+1)
                throw new AssertionError(
                    "terminal confirm writer was retouched after failure before="+
                    failedAttemptsBefore+
                    " after="+
                    failedOut.attempts()
                );

            if(healthyOut.attempts()!=
                    healthyAttemptsBefore+7)
                throw new AssertionError(
                    "healthy confirm peer expected six confirm packets plus one close before="+
                    healthyAttemptsBefore+
                    " after="+
                    healthyOut.attempts()
                );

            if(TradeService.active(
                    healthy
                )||
               TradeService.active(
                    failed
               ))
                throw new AssertionError(
                    "terminal confirm publication retained live Trade"
                );

            if(player81ContextFor(
                    failedWriter
                )!=null)
                throw new AssertionError(
                    "terminal confirm writer retained Player81 authority"
                );

            if(relayContextFor(
                    failedWriter
                )!=failedRelay)
                throw new AssertionError(
                    "terminal confirm SharedNpc sentinel changed"
                );

            if(player81ContextFor(
                    healthyWriter
                )!=healthySync||
               relayContextFor(
                    healthyWriter
                )!=healthyRelay)
                throw new AssertionError(
                    "terminal confirm failure retired healthy peer runtime"
                );

            int failedAttemptsAfter=
                failedOut.attempts();
            boolean rejected=false;

            try{
                SharedNpcWorldRelay
                    .preflightRegistration(
                        failedWriter,
                        world,
                        failed
                    );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                rejected=
                    expected.owner==failed&&
                    expected.writer==failedWriter;
            }

            if(!rejected)
                throw new AssertionError(
                    "terminal confirm writer was resurrectable"
                );

            if(failedOut.attempts()!=
                    failedAttemptsAfter)
                throw new AssertionError(
                    "terminal confirm preflight retouched failed writer"
                );
        }finally{
            failedOut.disableFailure();

            TradeService.unregister(
                healthy
            );
            TradeService.unregister(
                failed
            );
            SharedNpcWorldRelay.unregister(
                healthyWriter
            );
            SharedNpcWorldRelay.unregister(
                failedWriter
            );
            Player81WorldSync.unregister(
                healthyWriter
            );
            Player81WorldSync.unregister(
                failedWriter
            );

            if(healthy.registered())
                world.unregisterPlayer(
                    healthy,
                    healthyGeneration
                );
            if(failed.registered())
                world.unregisterPlayer(
                    failed,
                    failedGeneration
                );

            world.close();
        }
    }

    private static void assertTerminalStartRootWriterIsNotRetouched()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                708L
            );
        WorldPlayer healthy=
            new WorldPlayer();
        WorldPlayer failed=
            new WorldPlayer();

        long healthyGeneration=
            world.registerPlayer(
                healthy,
                "binding-start-healthy"
            );
        long failedGeneration=
            world.registerPlayer(
                failed,
                "binding-start-failed"
            );

        DevAuthorityWorkbench healthyDev=
            new DevAuthorityWorkbench();
        DevAuthorityWorkbench failedDev=
            new DevAuthorityWorkbench();
        NpcRegistry healthyNpcs=
            new NpcRegistry(
                healthyDev
            );
        NpcRegistry failedNpcs=
            new NpcRegistry(
                failedDev
            );

        SwitchablePrefixFailOutputStream healthyOut=
            new SwitchablePrefixFailOutputStream();
        SwitchablePrefixFailOutputStream failedOut=
            new SwitchablePrefixFailOutputStream();

        ServerPacketWriter healthyWriter=
            new ServerPacketWriter(
                healthyOut,
                new IsaacCipher(
                    new int[]{1181,1182,1183,1184}
                )
            );
        ServerPacketWriter failedWriter=
            new ServerPacketWriter(
                failedOut,
                new IsaacCipher(
                    new int[]{1185,1186,1187,1188}
                )
            );

        Player81WorldSync.Context healthySync=
            Player81WorldSync.register(
                healthyWriter,
                world,
                healthy,
                healthyDev
            );
        Player81WorldSync.register(
            failedWriter,
            world,
            failed,
            failedDev
        );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
        );

        SharedNpcWorldRelay.register(
            healthyWriter,
            world,
            healthy,
            healthyNpcs,
            healthy.movement()
        );
        SharedNpcWorldRelay.register(
            failedWriter,
            world,
            failed,
            failedNpcs,
            failed.movement()
        );

        TradeService.register(
            world,
            healthy,
            healthyGeneration,
            healthy.bank(),
            healthyWriter,
            ()->{}
        );
        TradeService.register(
            world,
            failed,
            failedGeneration,
            failed.bank(),
            failedWriter,
            ()->{}
        );

        try{
            Object healthyRelay=
                relayContextFor(
                    healthyWriter
                );
            Object failedRelay=
                relayContextFor(
                    failedWriter
                );
            int healthyAttemptsBefore=
                healthyOut.attempts();
            int failedAttemptsBefore=
                failedOut.attempts();

            failedOut.enableFailure();

            boolean failedAsExpected=false;

            try{
                TradeService.start(
                    world,
                    healthy,
                    failed
                );
            }catch(IOException expected){
                failedAsExpected=
                    "EXPECTED_RUNTIME_BINDING_TERMINAL_PREFIX_FAILURE"
                        .equals(
                            expected.getMessage()
                        );
            }

            if(!failedAsExpected)
                throw new AssertionError(
                    "terminal start-root publication failure was not propagated"
                );

            if(failedOut.attempts()!=
                    failedAttemptsBefore+1)
                throw new AssertionError(
                    "terminal start-root writer was retouched after failure before="+
                    failedAttemptsBefore+
                    " after="+
                    failedOut.attempts()
                );

            if(healthyOut.attempts()!=
                    healthyAttemptsBefore+7)
                throw new AssertionError(
                    "healthy start-root peer expected six root packets plus one close before="+
                    healthyAttemptsBefore+
                    " after="+
                    healthyOut.attempts()
                );

            if(TradeService.active(
                    healthy
                )||
               TradeService.active(
                    failed
               ))
                throw new AssertionError(
                    "terminal start-root failure retained live Trade"
                );

            if(player81ContextFor(
                    failedWriter
                )!=null)
                throw new AssertionError(
                    "terminal start-root writer retained Player81 authority"
                );

            if(relayContextFor(
                    failedWriter
                )!=failedRelay)
                throw new AssertionError(
                    "terminal start-root SharedNpc sentinel changed"
                );

            if(player81ContextFor(
                    healthyWriter
                )!=healthySync||
               relayContextFor(
                    healthyWriter
                )!=healthyRelay)
                throw new AssertionError(
                    "terminal start-root failure retired healthy peer runtime"
                );

            int failedAttemptsAfter=
                failedOut.attempts();
            boolean rejected=false;

            try{
                SharedNpcWorldRelay
                    .preflightRegistration(
                        failedWriter,
                        world,
                        failed
                    );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                rejected=
                    expected.owner==failed&&
                    expected.writer==failedWriter;
            }

            if(!rejected)
                throw new AssertionError(
                    "terminal start-root writer was resurrectable"
                );

            if(failedOut.attempts()!=
                    failedAttemptsAfter)
                throw new AssertionError(
                    "terminal start-root preflight retouched failed writer"
                );
        }finally{
            failedOut.disableFailure();

            TradeService.unregister(
                healthy
            );
            TradeService.unregister(
                failed
            );
            SharedNpcWorldRelay.unregister(
                healthyWriter
            );
            SharedNpcWorldRelay.unregister(
                failedWriter
            );
            Player81WorldSync.unregister(
                healthyWriter
            );
            Player81WorldSync.unregister(
                failedWriter
            );

            if(healthy.registered())
                world.unregisterPlayer(
                    healthy,
                    healthyGeneration
                );
            if(failed.registered())
                world.unregisterPlayer(
                    failed,
                    failedGeneration
                );

            world.close();
        }
    }

    private static void assertTerminalTradeXPromptWriterIsNotRetouched()
        throws Exception
    {
        assertTerminalTradeXPromptWriterIsNotRetouched(
            false,
            709L
        );
        assertTerminalTradeXPromptWriterIsNotRetouched(
            true,
            710L
        );
    }

    private static void assertTerminalTradeXPromptWriterIsNotRetouched(
        boolean remove,
        long seed
    )throws Exception
    {
        World world=
            World.isolatedForTest(
                seed
            );
        WorldPlayer failed=
            new WorldPlayer();
        WorldPlayer healthy=
            new WorldPlayer();

        long failedGeneration=
            world.registerPlayer(
                failed,
                remove
                    ?"binding-x-remove-failed"
                    :"binding-x-offer-failed"
            );
        long healthyGeneration=
            world.registerPlayer(
                healthy,
                remove
                    ?"binding-x-remove-healthy"
                    :"binding-x-offer-healthy"
            );

        DevAuthorityWorkbench failedDev=
            new DevAuthorityWorkbench();
        DevAuthorityWorkbench healthyDev=
            new DevAuthorityWorkbench();
        NpcRegistry failedNpcs=
            new NpcRegistry(
                failedDev
            );
        NpcRegistry healthyNpcs=
            new NpcRegistry(
                healthyDev
            );

        SwitchablePrefixFailOutputStream failedOut=
            new SwitchablePrefixFailOutputStream();
        SwitchablePrefixFailOutputStream healthyOut=
            new SwitchablePrefixFailOutputStream();

        ServerPacketWriter failedWriter=
            new ServerPacketWriter(
                failedOut,
                new IsaacCipher(
                    remove
                        ?new int[]{1191,1192,1193,1194}
                        :new int[]{1201,1202,1203,1204}
                )
            );
        ServerPacketWriter healthyWriter=
            new ServerPacketWriter(
                healthyOut,
                new IsaacCipher(
                    remove
                        ?new int[]{1195,1196,1197,1198}
                        :new int[]{1205,1206,1207,1208}
                )
            );

        Player81WorldSync.register(
            failedWriter,
            world,
            failed,
            failedDev
        );
        Player81WorldSync.Context healthySync=
            Player81WorldSync.register(
                healthyWriter,
                world,
                healthy,
                healthyDev
            );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
        );

        SharedNpcWorldRelay.register(
            failedWriter,
            world,
            failed,
            failedNpcs,
            failed.movement()
        );
        SharedNpcWorldRelay.register(
            healthyWriter,
            world,
            healthy,
            healthyNpcs,
            healthy.movement()
        );

        failed.bank().spawnItem(
            995,
            1000,
            failedWriter
        );

        TradeService.register(
            world,
            failed,
            failedGeneration,
            failed.bank(),
            failedWriter,
            ()->{}
        );
        TradeService.register(
            world,
            healthy,
            healthyGeneration,
            healthy.bank(),
            healthyWriter,
            ()->{}
        );

        try{
            String tradeOpen=
                TradeService.start(
                    world,
                    failed,
                    healthy
                );

            if(tradeOpen==null||
               !tradeOpen.contains(
                    "TRADE_UI_OPEN"
               ))
                throw new AssertionError(
                    "terminal X-prompt Trade fixture failed remove="+
                    remove+
                    " result="+
                    tradeOpen
                );

            int coinSlot=-1;
            for(int slot=0;
                slot<failed.bank().inventoryCapacity();
                slot++){
                BankState.Stack stack=
                    failed.bank().inventoryAt(
                        slot
                    );

                if(stack!=null&&
                   stack.itemId==995){
                    coinSlot=slot;
                    break;
                }
            }

            if(coinSlot<0)
                throw new AssertionError(
                    "terminal X-prompt fixture missing coins"
                );

            if(remove){
                String offered=
                    TradeService.handleItemAction(
                        failed,
                        new ItemContainerAction(
                            145,
                            3322,
                            coinSlot,
                            995,
                            0,
                            "ITEM_ACTION_1"
                        )
                    );

                if(offered==null||
                   !offered.contains(
                        "TRADE_OFFER_OK"
                   ))
                    throw new AssertionError(
                        "terminal Remove-X fixture offer failed: "+
                        offered
                    );
            }

            Object failedRelay=
                relayContextFor(
                    failedWriter
                );
            Object healthyRelay=
                relayContextFor(
                    healthyWriter
                );
            int failedAttemptsBefore=
                failedOut.attempts();
            int healthyAttemptsBefore=
                healthyOut.attempts();

            failedOut.enableFailure();

            boolean failedAsExpected=false;

            try{
                TradeService.handleItemAction(
                    failed,
                    new ItemContainerAction(
                        135,
                        remove
                            ?3415
                            :3322,
                        remove
                            ?0
                            :coinSlot,
                        995,
                        0,
                        "ITEM_ACTION_X"
                    )
                );
            }catch(IOException expected){
                failedAsExpected=
                    "EXPECTED_RUNTIME_BINDING_TERMINAL_PREFIX_FAILURE"
                        .equals(
                            expected.getMessage()
                        );
            }

            if(!failedAsExpected)
                throw new AssertionError(
                    "terminal X-prompt publication failure was not propagated remove="+
                    remove
                );

            if(failedOut.attempts()!=
                    failedAttemptsBefore+1)
                throw new AssertionError(
                    "terminal X-prompt writer was retouched remove="+
                    remove+
                    " before="+
                    failedAttemptsBefore+
                    " after="+
                    failedOut.attempts()
                );

            if(healthyOut.attempts()!=
                    healthyAttemptsBefore+1)
                throw new AssertionError(
                    "healthy X-prompt peer did not receive exactly one close remove="+
                    remove+
                    " before="+
                    healthyAttemptsBefore+
                    " after="+
                    healthyOut.attempts()
                );

            if(TradeService.active(
                    failed
                )||
               TradeService.active(
                    healthy
               ))
                throw new AssertionError(
                    "terminal X-prompt failure retained live Trade remove="+
                    remove
                );

            if(TradeService.handleAmount(
                    failed,
                    1
                )!=null)
                throw new AssertionError(
                    "terminal X-prompt failure retained hidden amount authority remove="+
                    remove
                );

            if(player81ContextFor(
                    failedWriter
                )!=null)
                throw new AssertionError(
                    "terminal X-prompt writer retained Player81 authority remove="+
                    remove
                );

            if(relayContextFor(
                    failedWriter
                )!=failedRelay)
                throw new AssertionError(
                    "terminal X-prompt SharedNpc sentinel changed remove="+
                    remove
                );

            if(player81ContextFor(
                    healthyWriter
                )!=healthySync||
               relayContextFor(
                    healthyWriter
                )!=healthyRelay)
                throw new AssertionError(
                    "terminal X-prompt failure retired healthy peer runtime remove="+
                    remove
                );

            int failedAttemptsAfter=
                failedOut.attempts();
            boolean rejected=false;

            try{
                SharedNpcWorldRelay
                    .preflightRegistration(
                        failedWriter,
                        world,
                        failed
                    );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                rejected=
                    expected.owner==failed&&
                    expected.writer==failedWriter;
            }

            if(!rejected)
                throw new AssertionError(
                    "terminal X-prompt writer was resurrectable remove="+
                    remove
                );

            if(failedOut.attempts()!=
                    failedAttemptsAfter)
                throw new AssertionError(
                    "terminal X-prompt preflight retouched writer remove="+
                    remove
                );
        }finally{
            failedOut.disableFailure();

            TradeService.unregister(
                failed
            );
            TradeService.unregister(
                healthy
            );
            SharedNpcWorldRelay.unregister(
                failedWriter
            );
            SharedNpcWorldRelay.unregister(
                healthyWriter
            );
            Player81WorldSync.unregister(
                failedWriter
            );
            Player81WorldSync.unregister(
                healthyWriter
            );

            if(failed.registered())
                world.unregisterPlayer(
                    failed,
                    failedGeneration
                );
            if(healthy.registered())
                world.unregisterPlayer(
                    healthy,
                    healthyGeneration
                );

            world.close();
        }
    }

    private static void assertTerminalStartRollbackWriterIsRetired()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                711L
            );
        WorldPlayer rollbackFailed=
            new WorldPlayer();
        WorldPlayer ownerFailed=
            new WorldPlayer();

        long rollbackGeneration=
            world.registerPlayer(
                rollbackFailed,
                "binding-start-rollback-failed"
            );
        long ownerGeneration=
            world.registerPlayer(
                ownerFailed,
                "binding-start-owner-failed"
            );

        DevAuthorityWorkbench rollbackDev=
            new DevAuthorityWorkbench();
        DevAuthorityWorkbench ownerDev=
            new DevAuthorityWorkbench();
        NpcRegistry rollbackNpcs=
            new NpcRegistry(
                rollbackDev
            );
        NpcRegistry ownerNpcs=
            new NpcRegistry(
                ownerDev
            );

        SwitchablePrefixFailOutputStream rollbackOut=
            new SwitchablePrefixFailOutputStream();
        SwitchablePrefixFailOutputStream ownerOut=
            new SwitchablePrefixFailOutputStream();

        ServerPacketWriter rollbackWriter=
            new ServerPacketWriter(
                rollbackOut,
                new IsaacCipher(
                    new int[]{1211,1212,1213,1214}
                )
            );
        ServerPacketWriter ownerWriter=
            new ServerPacketWriter(
                ownerOut,
                new IsaacCipher(
                    new int[]{1215,1216,1217,1218}
                )
            );

        Player81WorldSync.register(
            rollbackWriter,
            world,
            rollbackFailed,
            rollbackDev
        );
        Player81WorldSync.Context ownerSync=
            Player81WorldSync.register(
                ownerWriter,
                world,
                ownerFailed,
                ownerDev
            );

        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
        );

        SharedNpcWorldRelay.register(
            rollbackWriter,
            world,
            rollbackFailed,
            rollbackNpcs,
            rollbackFailed.movement()
        );
        SharedNpcWorldRelay.register(
            ownerWriter,
            world,
            ownerFailed,
            ownerNpcs,
            ownerFailed.movement()
        );

        TradeService.register(
            world,
            rollbackFailed,
            rollbackGeneration,
            rollbackFailed.bank(),
            rollbackWriter,
            ()->{}
        );
        TradeService.register(
            world,
            ownerFailed,
            ownerGeneration,
            ownerFailed.bank(),
            ownerWriter,
            ()->{},
            action->{
                action.publish();
                rollbackOut.enableFailure();
                throw new IOException(
                    "EXPECTED_START_OWNER_WRAPPER_FAILURE"
                );
            }
        );

        try{
            Object rollbackRelay=
                relayContextFor(
                    rollbackWriter
                );
            Object ownerRelay=
                relayContextFor(
                    ownerWriter
                );
            int rollbackAttemptsBefore=
                rollbackOut.attempts();
            int ownerAttemptsBefore=
                ownerOut.attempts();

            IOException primary=null;

            try{
                TradeService.start(
                    world,
                    rollbackFailed,
                    ownerFailed
                );
            }catch(IOException expected){
                primary=expected;
            }

            if(primary==null||
               !"EXPECTED_START_OWNER_WRAPPER_FAILURE"
                    .equals(
                        primary.getMessage()
                    ))
                throw new AssertionError(
                    "start rollback did not preserve owner-wrapper primary failure"
                );

            boolean cleanupSuppressed=false;
            for(Throwable suppressed:
                    primary.getSuppressed())
                if("EXPECTED_RUNTIME_BINDING_TERMINAL_PREFIX_FAILURE"
                        .equals(
                            suppressed.getMessage()
                        )){
                    cleanupSuppressed=true;
                    break;
                }

            if(!cleanupSuppressed)
                throw new AssertionError(
                    "terminal rollback cleanup failure not suppressed on primary"
                );

            if(rollbackOut.attempts()!=
                    rollbackAttemptsBefore+7)
                throw new AssertionError(
                    "rollback-failed writer attempts expected six start packets plus one failed cleanup before="+
                    rollbackAttemptsBefore+
                    " after="+
                    rollbackOut.attempts()
                );

            if(ownerOut.attempts()!=
                    ownerAttemptsBefore+7)
                throw new AssertionError(
                    "owner-failed healthy writer expected six start packets plus one rollback close before="+
                    ownerAttemptsBefore+
                    " after="+
                    ownerOut.attempts()
                );

            if(TradeService.active(
                    rollbackFailed
                )||
               TradeService.active(
                    ownerFailed
               ))
                throw new AssertionError(
                    "start rollback failure retained live Trade"
                );

            if(player81ContextFor(
                    rollbackWriter
                )!=null)
                throw new AssertionError(
                    "start rollback terminal writer retained Player81 authority"
                );

            if(relayContextFor(
                    rollbackWriter
                )!=rollbackRelay)
                throw new AssertionError(
                    "start rollback terminal SharedNpc sentinel changed"
                );

            if(player81ContextFor(
                    ownerWriter
                )!=ownerSync||
               relayContextFor(
                    ownerWriter
                )!=ownerRelay)
                throw new AssertionError(
                    "start rollback terminal failure retired healthy owner writer"
                );

            int rollbackAttemptsAfter=
                rollbackOut.attempts();
            boolean rejected=false;

            try{
                SharedNpcWorldRelay
                    .preflightRegistration(
                        rollbackWriter,
                        world,
                        rollbackFailed
                    );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                rejected=
                    expected.owner==rollbackFailed&&
                    expected.writer==rollbackWriter;
            }

            if(!rejected)
                throw new AssertionError(
                    "start rollback terminal writer was resurrectable"
                );

            if(rollbackOut.attempts()!=
                    rollbackAttemptsAfter)
                throw new AssertionError(
                    "start rollback terminal preflight retouched writer"
                );
        }finally{
            rollbackOut.disableFailure();

            TradeService.unregister(
                rollbackFailed
            );
            TradeService.unregister(
                ownerFailed
            );
            SharedNpcWorldRelay.unregister(
                rollbackWriter
            );
            SharedNpcWorldRelay.unregister(
                ownerWriter
            );
            Player81WorldSync.unregister(
                rollbackWriter
            );
            Player81WorldSync.unregister(
                ownerWriter
            );

            if(rollbackFailed.registered())
                world.unregisterPlayer(
                    rollbackFailed,
                    rollbackGeneration
                );
            if(ownerFailed.registered())
                world.unregisterPlayer(
                    ownerFailed,
                    ownerGeneration
                );

            world.close();
        }
    }

    private static void assertTerminalWriterLatchStopsSessionReuse()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                712L
            );
        WorldPlayer owner=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                owner,
                "binding-terminal-latch-owner"
            );

        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry oldNpcs=
            new NpcRegistry(
                dev
            );
        NpcRegistry replacementNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        SwitchablePrefixFailOutputStream oldOut=
            new SwitchablePrefixFailOutputStream();
        SwitchablePrefixFailOutputStream replacementOut=
            new SwitchablePrefixFailOutputStream();

        ServerPacketWriter oldWriter=
            new ServerPacketWriter(
                oldOut,
                new IsaacCipher(
                    new int[]{1221,1222,1223,1224}
                )
            );
        ServerPacketWriter replacementWriter=
            new ServerPacketWriter(
                replacementOut,
                new IsaacCipher(
                    new int[]{1225,1226,1227,1228}
                )
            );

        Player81WorldSync.register(
            oldWriter,
            world,
            owner,
            dev
        );
        SharedNpcWorldRelay.register(
            oldWriter,
            world,
            owner,
            oldNpcs,
            owner.movement()
        );
        TradeService.register(
            world,
            owner,
            generation,
            owner.bank(),
            oldWriter,
            ()->{}
        );

        Object oldRelay=
            relayContextFor(
                oldWriter
            );

        try{
            IOException terminalCause=
                new IOException(
                    "EXPECTED_TERMINAL_LATCH"
                );

            LocalSessionRuntimeBindings
                .retireTerminalRuntimeBundle(
                    owner,
                    oldWriter,
                    true,
                    terminalCause
                );

            if(!oldWriter.terminal())
                throw new AssertionError(
                    "terminal retirement did not latch exact writer"
                );

            if(player81ContextFor(
                    oldWriter
                )!=null)
                throw new AssertionError(
                    "terminal latch fixture retained Player81 authority"
                );

            if(relayContextFor(
                    oldWriter
                )!=oldRelay)
                throw new AssertionError(
                    "terminal latch fixture lost SharedNpc sentinel"
                );

            int attemptsBefore=
                oldOut.attempts();
            boolean publicationRejected=false;

            try{
                oldWriter.fixed(
                    219,
                    new byte[0]
                );
            }catch(IOException expected){
                publicationRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage()
                        .contains(
                            "server packet writer terminal"
                        );
            }

            if(!publicationRejected)
                throw new AssertionError(
                    "terminal writer allowed later publication"
                );

            if(oldOut.attempts()!=
                    attemptsBefore)
                throw new AssertionError(
                    "terminal writer rejection touched transport before="+
                    attemptsBefore+
                    " after="+
                    oldOut.attempts()
                );

            boolean ingressRejected=false;

            try{
                LocalSession
                    .requireLiveSessionWriter(
                        oldWriter
                    );
            }catch(IOException expected){
                ingressRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage()
                        .contains(
                            "terminal session packet writer"
                        );
            }

            if(!ingressRejected)
                throw new AssertionError(
                    "LocalSession ingress guard accepted terminal writer"
                );

            if(replacementWriter.terminal())
                throw new AssertionError(
                    "terminal latch leaked to replacement writer"
                );

            SharedNpcWorldRelay.preflightRegistration(
                replacementWriter,
                world,
                owner
            );
            SharedNpcWorldRelay.register(
                replacementWriter,
                world,
                owner,
                replacementNpcs,
                owner.movement()
            );
            Player81WorldSync.register(
                replacementWriter,
                world,
                owner,
                new DevAuthorityWorkbench()
            );
            TradeService.register(
                world,
                owner,
                generation,
                owner.bank(),
                replacementWriter,
                ()->{}
            );

            Object replacementRelay=
                relayContextFor(
                    replacementWriter
                );

            if(replacementRelay==null||
               relayOwnerContextFor(
                    world,
                    owner
                )!=replacementRelay)
                throw new AssertionError(
                    "replacement SharedNpc context was not active before stale unregister"
                );

            int oldAttemptsBeforeUnregister=
                oldOut.attempts();

            SharedNpcWorldRelay.unregister(
                oldWriter
            );

            if(relayContextFor(
                    oldWriter
                )!=oldRelay)
                throw new AssertionError(
                    "terminal SharedNpc unregister removed fail-closed sentinel"
                );

            if(relayContextFor(
                    replacementWriter
                )!=replacementRelay||
               relayOwnerContextFor(
                    world,
                    owner
                )!=replacementRelay)
                throw new AssertionError(
                    "terminal SharedNpc unregister corrupted healthy replacement context"
                );

            boolean oldRejected=false;
            try{
                SharedNpcWorldRelay.preflightRegistration(
                    oldWriter,
                    world,
                    owner
                );
            }catch(SharedNpcWorldRelay
                    .TerminalRegistrationException expected){
                oldRejected=
                    expected.owner==owner&&
                    expected.writer==oldWriter;
            }

            if(!oldRejected)
                throw new AssertionError(
                    "terminal SharedNpc writer became resurrectable after unregister"
                );

            if(oldOut.attempts()!=
                    oldAttemptsBeforeUnregister)
                throw new AssertionError(
                    "terminal SharedNpc unregister/preflight touched broken transport"
                );

            LocalSession.requireLiveSessionWriter(
                replacementWriter
            );

            int replacementAttemptsBefore=
                replacementOut.attempts();

            replacementWriter.fixed(
                219,
                new byte[0]
            );

            if(replacementOut.attempts()!=
                    replacementAttemptsBefore+1)
                throw new AssertionError(
                    "replacement writer publication failed"
                );
        }finally{
            TradeService.unregister(
                owner,
                replacementWriter
            );
            SharedNpcWorldRelay.unregister(
                replacementWriter
            );
            Player81WorldSync.unregister(
                replacementWriter
            );

            if(owner.registered())
                world.unregisterPlayer(
                    owner,
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

    private static Object relayOwnerContextFor(
        World world,
        WorldPlayer owner
    )throws Exception{
        Field worldField=
            SharedNpcWorldRelay.class
                .getDeclaredField(
                    "BY_WORLD"
                );
        worldField.setAccessible(true);

        synchronized(SharedNpcWorldRelay.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<World,Object> worlds=
                (IdentityHashMap<World,Object>)
                worldField.get(null);

            Object state=
                worlds.get(
                    world
                );

            if(state==null)
                return null;

            Field contextsField=
                state.getClass()
                    .getDeclaredField(
                        "contexts"
                    );
            contextsField.setAccessible(true);

            @SuppressWarnings("unchecked")
            java.util.Map<EntityId,Object> contexts=
                (java.util.Map<EntityId,Object>)
                contextsField.get(
                    state
                );

            return contexts.get(
                owner.id()
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
