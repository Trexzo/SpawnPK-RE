package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * R62 offline completion evidence: fresh JVM after hard-stop, strict
 * post-state and revision checks, no admission or canonical mutations.
 */
public final class R62TradePairCompletionCertificateTest {
    private static final UUID ID=UUID.fromString(
        "11111111-2222-4333-8444-555555555555"
    );
    private static final int COIN=995,SHARK=385;

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("spk-r62-completion-");
        try{
            Path journal=root.resolve("journal");
            Path accounts=root.resolve("accounts");
            Path payloads=root.resolve("payloads");
            Path complete=root.resolve("completion");
            Files.createDirectory(journal);
            Files.createDirectory(accounts);
            Files.createDirectory(payloads);
            Files.createDirectory(complete);
            FilePlayerRepository canonical=new FilePlayerRepository(
                account->accounts.resolve(account+".properties")
            );
            PlayerSnapshot[] pair=createTradeAndIndex(journal,canonical);

            // PREPARED plus COMMIT with only old accounts cannot create a
            // completion proof and cannot grant an admission candidate.
            R61TradePairReplay.publishCommitDecision(
                journal,payloads,ID,pair[0],pair[2],pair[1],pair[3]
            );
            blocked(journal,accounts,payloads,complete);
            expectIOException(()->R62TradePairCompletionCertificate
                .publishCandidate(journal,accounts,payloads,complete,ID));
            Path marker=certificate(complete);
            require(!Files.exists(marker),
                "incomplete pair published completion marker");

            // Complete the pair (R61) and verify every party still stays
            // quarantined in the unchanged R59 PREPARED index.
            R61TradePairReplay.Outcome outcome=
                R61TradePairReplay.replayCommitted(
                    journal,accounts,payloads,ID,false
                );
            require(outcome.publishedAccounts==2 &&
                    outcome.bothPostStatesVerified,
                "offline replay did not complete both saves");
            checkQuarantined(journal);

            byte[] accountA=Files.readAllBytes(
                accounts.resolve("opensrc.properties")
            );
            byte[] accountB=Files.readAllBytes(
                accounts.resolve("src.properties")
            );
            byte[] manifest=Files.readAllBytes(
                journal.resolve("trade-r57-"+ID+".properties")
            );
            byte[] index=Files.readAllBytes(
                journal.resolve(R59TradePairIntentIndex.INDEX_FILE)
            );

            // An unknown/staged completion publication must be preserved
            // and must not be silently adopted or deleted.
            Path stage=complete.resolve(
                "."+marker.getFileName()+".r16-evidence.stage"
            );
            byte[] stageEvidence=new byte[]{7,9,13};
            Files.write(stage,stageEvidence);
            expectIOException(()->R62TradePairCompletionCertificate
                .publishCandidate(journal,accounts,payloads,complete,ID));
            require(Arrays.equals(Files.readAllBytes(stage),stageEvidence),
                "ambiguous stage was altered during refused publication");
            Files.delete(stage); // ONLY test-owned fixture bytes

            // A separate JVM publishes the final offline certificate
            // and then abruptly exits without Java finally/shutdown hooks.
            runWorker(journal,accounts,payloads,complete,"publish-halt");
            runWorker(journal,accounts,payloads,complete,"verify");
            R62TradePairCompletionCertificate.Review good=
                candidate(journal,accounts,payloads,complete);
            require(good.indexRevision==2L,
                "wrong index generation on completed pair");

            byte[] originalCertificate=Files.readAllBytes(marker);
            expectIOException(()->R62TradePairCompletionCertificate
                .publishCandidate(journal,accounts,payloads,complete,ID));
            require(Arrays.equals(
                originalCertificate,Files.readAllBytes(marker)),
                "duplicate publication rewrote immutable completion proof");

            // A simultaneous cooperating publisher locks the same journal
            // authority. A nested admission probe must refuse to proceed.
            R60TradePairCoordinator.withExclusive(journal,false,()->{
                blocked(journal,accounts,payloads,complete);
                return null;
            });
            candidate(journal,accounts,payloads,complete);

            // A valid-looking unrelated canonical autosave after the
            // certificate makes the evidence STALE, never auto-restored.
            WorldPlayer changed=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                canonical.load("opensrc").orElseThrow(
                    ()->new AssertionError("account lost")),changed
            );
            ServerPacketWriter writer=new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(new int[]{1,2,3,4})
            );
            changed.bank().spawnItem(560,1,writer);
            canonical.save(PlayerSnapshotCodec.capture("opensrc",changed));
            byte[] unexpectedAccount=Files.readAllBytes(
                accounts.resolve("opensrc.properties")
            );
            blocked(journal,accounts,payloads,complete);
            require(Arrays.equals(unexpectedAccount,Files.readAllBytes(
                accounts.resolve("opensrc.properties"))),
                "readiness probe overwrote unknown autosave");
            canonical.save(pair[2]); // restore only test-owned account
            candidate(journal,accounts,payloads,complete);

            // Tamper the certificate generation. A broken completion marker
            // is refused; no heuristic cleanup or replacement is performed.
            Properties altered=new Properties();
            try(InputStream input=Files.newInputStream(marker)){
                altered.load(input);
            }
            altered.setProperty("index.revision","99");
            try(OutputStream output=Files.newOutputStream(marker)){
                altered.store(output,"test-only stale revision");
            }
            blocked(journal,accounts,payloads,complete);
            Files.write(marker,originalCertificate);
            candidate(journal,accounts,payloads,complete);

            Files.write(stage,new byte[]{2,4,8});
            blocked(journal,accounts,payloads,complete);
            require(Files.size(stage)==3,
                "candidate probe discarded orphan stage");
            Files.delete(stage);
            candidate(journal,accounts,payloads,complete);

            // COMMIT removal is never interpreted as a clean completion.
            Path decision=payloads.resolve(
                "decision-"+ID+".properties"
            );
            byte[] committed=Files.readAllBytes(decision);
            Files.delete(decision); // only test-owned decision
            blocked(journal,accounts,payloads,complete);
            Files.write(decision,committed);
            candidate(journal,accounts,payloads,complete);

            // Even a harmless complete index re-publication to a new
            // revision requires a NEW trusted completion decision. R62
            // NEVER silently promotes an old certificate to that revision.
            long revision=R60TradePairCoordinator.withExclusive(
                journal,false,()->R59TradePairIntentIndex.publishRevision(
                    journal,2L,Collections.singletonList(ID)
                )
            );
            require(revision==3L,"index revision did not change");
            blocked(journal,accounts,payloads,complete);
            require(Files.exists(marker),
                "stale completion marker was automatically deleted");

            require(Arrays.equals(manifest,Files.readAllBytes(
                    journal.resolve("trade-r57-"+ID+".properties"))),
                "R62 changed the PREPARED manifest");
            require(Arrays.equals(accountB,Files.readAllBytes(
                    accounts.resolve("src.properties"))),
                "R62 changed secondary account bytes");
            // The fixture intentionally saved A once, then restored it to
            // the same game snapshot with a fresh saved.at timestamp.
            require(R57TradePairJournal.digest(
                canonical.load("opensrc").orElseThrow(
                    ()->new AssertionError("primary missing")
                )).equals(R57TradePairJournal.digest(pair[2])),
                "primary snapshot after negative-control restore is wrong");
            require(!Arrays.equals(index,Files.readAllBytes(
                    journal.resolve(R59TradePairIntentIndex.INDEX_FILE))),
                "revision-change negative control did not write new index");

            System.out.println(
                "R62_OFFLINE_COMPLETION_CERTIFICATE_PASS "+
                "bothPostStateAndCommitRequired=true "+
                "abruptCertificateWriterJvmHaltReviewedByNewJvm=true "+
                "noCanonicalAccountWritesDuringPublication=true "+
                "duplicateAndStagedCertificateDenied=true "+
                "busyLockAndUnknownAutosaveFailClosed=true "+
                "tamperedOrMissingCommitFailClosed=true "+
                "staleIndexRevisionDeniesCandidate=true "+
                "journalNeverRetiredOrLoginAuthorized=true "+
                "physicalPowerLossAndLiveReleaseNotProven=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> files=Files.walk(root)){
                files.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failure){
                        throw new IllegalStateException(failure);
                    }
                });
            }
        }
    }

    private static PlayerSnapshot[] createTradeAndIndex(
        Path journal,FilePlayerRepository canonical
    )throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
        world.registerPlayer(a,"opensrc");
        world.registerPlayer(b,"src");
        ServerPacketWriter writerA=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
        ServerPacketWriter writerB=new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{5,6,7,8})
        );
        try{
            a.bank().spawnItem(COIN,1000,writerA);
            b.bank().spawnItem(SHARK,2,writerB);
            PlayerSnapshot oldA=PlayerSnapshotCodec.capture("opensrc",a);
            PlayerSnapshot oldB=PlayerSnapshotCodec.capture("src",b);
            canonical.save(oldA);
            canonical.save(oldB);
            TradeService.register(world,a,a.bank(),writerA,()->{});
            TradeService.register(world,b,b.bank(),writerB,()->{});
            expectText(TradeService.start(world,a,b),"TRADE_UI_OPEN");
            expectText(TradeService.handleItemAction(a,
                new ItemContainerAction(145,3322,0,COIN,0,
                    "ITEM_ACTION_1")),"TRADE_OFFER_OK");
            int shark=EngineR4TradeFlowTest.find(b.bank(),SHARK);
            require(shark>=0,"shark offer slot absent");
            expectText(TradeService.handleItemAction(b,
                new ItemContainerAction(145,3322,shark,SHARK,0,
                    "ITEM_ACTION_1")),"TRADE_OFFER_OK");
            expectText(TradeService.handleWidget(a,3420),"WAITING_OTHER");
            expectText(TradeService.handleWidget(b,3420),"CONFIRM_OPEN");
            expectText(TradeService.handleWidget(a,3546),"WAITING_OTHER");
            expectText(TradeService.handleWidget(b,3546),"TRADE_COMMITTED");
            PlayerSnapshot afterA=PlayerSnapshotCodec.capture("opensrc",a);
            PlayerSnapshot afterB=PlayerSnapshotCodec.capture("src",b);

            R60TradePairCoordinator.initializeEmpty(journal);
            long next=R60TradePairCoordinator.registerPrepared(
                journal,1L,Collections.emptyList(),ID,
                oldA,afterA,oldB,afterB
            );
            require(next==2L,"pair not indexed at revision two");
            return new PlayerSnapshot[]{
                oldA,oldB,afterA,afterB
            };
        }finally{
            TradeService.unregister(a);
            TradeService.unregister(b);
            if(a.registered())world.unregisterPlayer(a);
            if(b.registered())world.unregisterPlayer(b);
            world.close();
        }
    }

    private static Path certificate(Path root){
        return root.resolve("completion-"+ID+".properties");
    }

    private static R62TradePairCompletionCertificate.Review candidate(
        Path journal,Path accounts,Path payloads,Path complete
    ){
        R62TradePairCompletionCertificate.Review result=
            R62TradePairCompletionCertificate.inspectCandidate(
                journal,accounts,payloads,complete,ID
            );
        require(result.status==
                R62TradePairCompletionCertificate.Status
                    .OFFLINE_COMPLETION_CANDIDATE &&
                !result.loginAuthorized() &&
                !result.journalQuarantineReleased() &&
                !result.automaticAccountRecoveryAttempted(),
            "unexpected candidate review: "+result.status+" "+result.reason);
        return result;
    }

    private static void blocked(
        Path journal,Path accounts,Path payloads,Path complete
    ){
        R62TradePairCompletionCertificate.Review result=
            R62TradePairCompletionCertificate.inspectCandidate(
                journal,accounts,payloads,complete,ID
            );
        require(result.status==
                R62TradePairCompletionCertificate.Status.BLOCK_INDETERMINATE &&
                !result.loginAuthorized() &&
                !result.journalQuarantineReleased(),
            "unsafe completion candidate accepted "+result.reason);
    }

    private static void checkQuarantined(Path journal){
        for(String user:new String[]{"opensrc","src"}){
            R59TradePairIntentIndex.Result result=
                R60TradePairCoordinator.inspect(journal,user);
            require(result.status==
                R59TradePairIntentIndex.Status.BLOCK_INDEXED_PAIR &&
                result.transactions.size()==1 &&
                ID.equals(result.transactions.get(0)),
                "R62 unexpectedly released "+user);
        }
    }

    private static void runWorker(
        Path journal,Path accounts,Path payloads,Path complete,String mode
    )throws Exception{
        Path javaExecutable=Paths.get(System.getProperty("java.home"),
            "bin",System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java");
        Process worker=new ProcessBuilder(
            javaExecutable.toString(),"-cp",System.getProperty("java.class.path"),
            "spk.local.R62TradePairCompletionWorker",
            journal.toString(),accounts.toString(),
            payloads.toString(),complete.toString(),ID.toString(),mode
        ).redirectErrorStream(true).start();
        if(!worker.waitFor(25,TimeUnit.SECONDS)){
            worker.destroyForcibly();
            worker.waitFor(5,TimeUnit.SECONDS);
            throw new AssertionError("test-owned completion worker timed out");
        }
        String output=new String(
            worker.getInputStream().readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8
        );
        if("publish-halt".equals(mode)){
            require(worker.exitValue()==92,
                "worker did not hard-halt after publish: "+
                worker.exitValue()+" "+output);
        }else{
            require(worker.exitValue()==0 &&
                    output.contains("R62_COMPLETION_WORKER_VERIFIED"),
                "separate JVM did not verify marker: "+
                worker.exitValue()+" "+output);
        }
    }

    private static void expectText(String actual,String text){
        require(actual!=null&&actual.contains(text),
            "expected "+text+" got "+actual);
    }
    @FunctionalInterface interface Action{void run()throws Exception;}
    private static void expectIOException(Action action)throws Exception{
        try{
            action.run();
            throw new AssertionError("expected fail-closed IOException");
        }catch(IOException expected){
            // The test separately verifies no account changes.
        }
    }
    private static void require(boolean valid,String message){
        if(!valid)throw new AssertionError(message);
    }
    private R62TradePairCompletionCertificateTest(){}
}

final class R62TradePairCompletionWorker {
    public static void main(String[] args)throws Exception{
        if(args.length!=6)throw new IllegalArgumentException("args");
        Path journal=Paths.get(args[0]);
        Path accounts=Paths.get(args[1]);
        Path payloads=Paths.get(args[2]);
        Path completion=Paths.get(args[3]);
        UUID id=UUID.fromString(args[4]);
        String mode=args[5];
        if("publish-halt".equals(mode)){
            R62TradePairCompletionCertificate.publishCandidate(
                journal,accounts,payloads,completion,id
            );
            // Only the test-owned worker terminates. Never halt the
            // launcher, existing server, or any unrelated user JVM.
            Runtime.getRuntime().halt(92);
            throw new AssertionError("Runtime.halt returned");
        }
        if(!"verify".equals(mode))
            throw new IllegalArgumentException("unknown mode");
        R62TradePairCompletionCertificate.Review evidence=
            R62TradePairCompletionCertificate.inspectCandidate(
                journal,accounts,payloads,completion,id
            );
        if(evidence.status!=
                R62TradePairCompletionCertificate.Status
                    .OFFLINE_COMPLETION_CANDIDATE ||
           evidence.loginAuthorized()||
           evidence.journalQuarantineReleased())
            throw new AssertionError(
                "offline certificate incorrect "+evidence.reason
            );
        System.out.println("R62_COMPLETION_WORKER_VERIFIED");
    }
}
