package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * R61: restart simulation in a distinct JVM after four real filesystem
 * checkpoints. Also verify no decision, damaged payload, unknown account
 * state and a second interrupted replay all fail closed.
 */
public final class R61TradePairReplayTest {
    private static final UUID ID=UUID.fromString(
        "11111111-2222-4333-8444-555555555555"
    );
    private static final int COIN=995,SHARK=385;

    private static final class Fixture {
        final Path journal,accounts,payloads;
        final PlayerSnapshot beforeA,beforeB,afterA,afterB;
        Fixture(Path journal,Path accounts,Path payloads,
                PlayerSnapshot beforeA,PlayerSnapshot beforeB,
                PlayerSnapshot afterA,PlayerSnapshot afterB){
            this.journal=journal;this.accounts=accounts;this.payloads=payloads;
            this.beforeA=beforeA;this.beforeB=beforeB;
            this.afterA=afterA;this.afterB=afterB;
        }
        FilePlayerRepository accounts(){
            return new FilePlayerRepository(
                name->accounts.resolve(name+".properties")
            );
        }
    }

    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r61-pair-replay-");
        try{
            for(int mask=0;mask<4;mask++){
                Fixture f=fixture(scratch.resolve("checkpoint-"+mask));
                R61TradePairReplay.publishCommitDecision(
                    f.journal,f.payloads,ID,
                    f.beforeA,f.afterA,f.beforeB,f.afterB
                );
                if((mask&1)!=0)f.accounts().save(f.afterA);
                if((mask&2)!=0)f.accounts().save(f.afterB);
                runWorker(f,"recover");
                requireSavedPair(f);
                R61TradePairReplay.Outcome idempotent=
                    R61TradePairReplay.replayCommitted(
                        f.journal,f.accounts,f.payloads,ID,false
                    );
                require(idempotent.publishedAccounts==0 &&
                        idempotent.before==
                            R57TradePairJournal.State.BOTH_AFTER &&
                        idempotent.unresolvedIntentStillBlocksLogin,
                    "second replay changed an already completed pair");
                requireBothQuarantined(f);
            }

            Fixture retry=fixture(scratch.resolve("interrupted-replay"));
            R61TradePairReplay.publishCommitDecision(
                retry.journal,retry.payloads,ID,
                retry.beforeA,retry.afterA,retry.beforeB,retry.afterB
            );
            runWorker(retry,"interrupt");
            // JVM stopped after publishing only account A; B stayed old.
            requireReview(retry,
                R57TradePairJournal.State.ONLY_A_AFTER);
            runWorker(retry,"recover");
            requireSavedPair(retry);
            requireBothQuarantined(retry);

            // A DIFFERENT test-owned child JVM is hard-terminated via
            // Runtime.halt immediately after the first individual atomic
            // account publication. It runs no finally/save/cleanup hook.
            // This proves fresh-process replay of an abrupt JVM exit, not
            // hardware power-loss durability.
            Fixture hardStop=fixture(scratch.resolve("abrupt-jvm-exit"));
            R61TradePairReplay.publishCommitDecision(
                hardStop.journal,hardStop.payloads,ID,
                hardStop.beforeA,hardStop.afterA,
                hardStop.beforeB,hardStop.afterB
            );
            runWorker(hardStop,"halt");
            requireReview(hardStop,
                R57TradePairJournal.State.ONLY_A_AFTER);
            runWorker(hardStop,"recover");
            requireSavedPair(hardStop);
            requireBothQuarantined(hardStop);

            // A non-cooperative R59 writer can manually register a
            // second PREPARED trade involving the SAME first account.
            // A replay must refuse this ambiguous account ownership even
            // when the requested transaction remains present in the index.
            Fixture overlap=fixture(scratch.resolve("overlap-negative"));
            R61TradePairReplay.publishCommitDecision(
                overlap.journal,overlap.payloads,ID,
                overlap.beforeA,overlap.afterA,
                overlap.beforeB,overlap.afterB
            );
            byte[] overlappingBeforeA=Files.readAllBytes(
                overlap.accounts.resolve("opensrc.properties"));
            byte[] overlappingBeforeB=Files.readAllBytes(
                overlap.accounts.resolve("src.properties"));
            UUID foreignId=UUID.fromString(
                "77777777-8888-4999-aaaa-bbbbbbbbbbbb");
            WorldPlayer conflictingA=new WorldPlayer();
            WorldPlayer conflictingB=new WorldPlayer();
            PlayerSnapshot firstOld=PlayerSnapshotCodec.capture(
                "opensrc",conflictingA);
            PlayerSnapshot otherOld=PlayerSnapshotCodec.capture(
                "otherb",conflictingB);
            ServerPacketWriter conflictWriter=new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(new int[]{4,3,2,1})
            );
            conflictingA.bank().spawnItem(995,1,conflictWriter);
            conflictingB.bank().spawnItem(385,1,conflictWriter);
            R57TradePairJournal.writePrepared(
                overlap.journal.resolve(
                    "trade-r57-"+foreignId+".properties"),
                foreignId,firstOld,
                PlayerSnapshotCodec.capture("opensrc",conflictingA),
                otherOld,
                PlayerSnapshotCodec.capture("otherb",conflictingB)
            );
            long overlappedRevision=R59TradePairIntentIndex.publishRevision(
                overlap.journal,2L,Arrays.asList(ID,foreignId)
            );
            require(overlappedRevision==3L,
                "non-cooperative overlap fixture failed");
            require(R59TradePairIntentIndex.inspect(
                overlap.journal,"opensrc").transactions.size()==2,
                "overlap fixture does not contain two pending pairs");
            expectIOException(()->R61TradePairReplay.replayCommitted(
                overlap.journal,overlap.accounts,overlap.payloads,
                ID,false
            ));
            require(Arrays.equals(overlappingBeforeA,Files.readAllBytes(
                    overlap.accounts.resolve("opensrc.properties"))) &&
                    Arrays.equals(overlappingBeforeB,Files.readAllBytes(
                    overlap.accounts.resolve("src.properties"))),
                "overlapping pair allowed canonical mutation");

            Fixture negatives=fixture(scratch.resolve("negative-controls"));
            byte[] initialA=Files.readAllBytes(
                negatives.accounts.resolve("opensrc.properties"));
            byte[] initialB=Files.readAllBytes(
                negatives.accounts.resolve("src.properties"));

            // PREPARED alone is not a commit decision. Never roll forward.
            expectIOException(()->R61TradePairReplay.replayCommitted(
                negatives.journal,negatives.accounts,negatives.payloads,
                ID,false
            ));
            require(Arrays.equals(initialA,Files.readAllBytes(
                    negatives.accounts.resolve("opensrc.properties"))) &&
                    Arrays.equals(initialB,Files.readAllBytes(
                    negatives.accounts.resolve("src.properties"))),
                "PREPARED-only replay modified canonical files");

            R61TradePairReplay.publishCommitDecision(
                negatives.journal,negatives.payloads,ID,
                negatives.beforeA,negatives.afterA,
                negatives.beforeB,negatives.afterB
            );
            Path decision=negatives.payloads.resolve(
                "decision-"+ID+".properties");
            byte[] decisionBytes=Files.readAllBytes(decision);
            Path ambiguousStage=negatives.payloads.resolve(
                ".decision-"+ID+".properties.r16-fixture.stage");
            Files.write(ambiguousStage,new byte[]{23,17,5});
            expectIOException(()->R61TradePairReplay.replayCommitted(
                negatives.journal,negatives.accounts,negatives.payloads,
                ID,false
            ));
            require(Files.size(ambiguousStage)==3,
                "orphan COMMIT stage automatically deleted");
            Files.delete(ambiguousStage); // private test-owned artifact

            Path payloadDir=negatives.payloads.resolve("trade-"+ID);
            FilePlayerRepository payloadRepository=new FilePlayerRepository(
                user->payloadDir.resolve(user+".properties")
            );
            WorldPlayer unexpectedPayload=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                negatives.afterA,unexpectedPayload
            );
            ServerPacketWriter writer=new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(new int[]{1,2,3,4})
            );
            unexpectedPayload.bank().spawnItem(560,1,writer);
            payloadRepository.save(PlayerSnapshotCodec.capture(
                "opensrc",unexpectedPayload
            ));
            expectIOException(()->R61TradePairReplay.replayCommitted(
                negatives.journal,negatives.accounts,negatives.payloads,
                ID,false
            ));
            require(Arrays.equals(initialA,Files.readAllBytes(
                    negatives.accounts.resolve("opensrc.properties"))) &&
                    Arrays.equals(initialB,Files.readAllBytes(
                    negatives.accounts.resolve("src.properties"))),
                "mutated COMMIT payload caused an account write");
            // Restore only a test-owned file before the next negative.
            payloadRepository.save(negatives.afterA);

            // Unknown canonical state after a committed transaction must
            // never be overwritten by guesses or blind account reload.
            WorldPlayer unknownAccount=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                negatives.beforeA,unknownAccount
            );
            unknownAccount.bank().spawnItem(560,1,writer);
            negatives.accounts().save(PlayerSnapshotCodec.capture(
                "opensrc",unknownAccount
            ));
            byte[] alteredA=Files.readAllBytes(
                negatives.accounts.resolve("opensrc.properties"));
            expectIOException(()->R61TradePairReplay.replayCommitted(
                negatives.journal,negatives.accounts,negatives.payloads,
                ID,false
            ));
            require(Arrays.equals(alteredA,Files.readAllBytes(
                    negatives.accounts.resolve("opensrc.properties"))) &&
                    Arrays.equals(initialB,Files.readAllBytes(
                    negatives.accounts.resolve("src.properties"))),
                "unknown account state was overwritten during refusal");
            require(Arrays.equals(decisionBytes,Files.readAllBytes(decision)),
                "COMMIT evidence was modified or removed during replay");

            System.out.println(
                "R61_OFFLINE_PAIRED_TRADE_REPLAY_PASS "+
                "fourRestartCheckpointsReconciled=true "+
                "freshJvmReplayAndIdempotentRetry=true "+
                "interruptedReplayResumesWithoutDuplication=true "+
                "abruptChildJvmHaltAfterFirstSaveReplayed=true "+
                "preparedWithoutCommitNeverPublishes=true "+
                "damagedPostPayloadAndUnknownCanonicalDenied=true "+
                "orphanCommitStagePreserved=true "+
                "overlappingIndexedIntentsDenied=true "+
                "bothAccountLoginsRemainQuarantined=true "+
                "noProductionRecoveryHook=true "+
                "physicalPowerLossDurabilityNotProven=true");
        }finally{
            try(java.util.stream.Stream<Path> files=Files.walk(scratch)){
                files.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failure){
                        throw new IllegalStateException(failure);
                    }
                });
            }
        }
    }

    private static Fixture fixture(Path root)throws Exception{
        Files.createDirectory(root);
        Path journal=root.resolve("journal");
        Path accounts=root.resolve("accounts");
        Path payloads=root.resolve("payloads");
        Files.createDirectory(journal);
        Files.createDirectory(accounts);
        Files.createDirectory(payloads);

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
            PlayerSnapshot beforeA=PlayerSnapshotCodec.capture("opensrc",a);
            PlayerSnapshot beforeB=PlayerSnapshotCodec.capture("src",b);
            FilePlayerRepository repo=new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            );
            repo.save(beforeA);
            repo.save(beforeB);
            TradeService.register(world,a,a.bank(),writerA,()->{});
            TradeService.register(world,b,b.bank(),writerB,()->{});
            mustContain(TradeService.start(world,a,b),"TRADE_UI_OPEN");
            mustContain(TradeService.handleItemAction(a,
                new ItemContainerAction(
                    145,3322,0,COIN,0,"ITEM_ACTION_1"
                )),"TRADE_OFFER_OK");
            int sharkSlot=EngineR4TradeFlowTest.find(b.bank(),SHARK);
            require(sharkSlot>=0,"shark slot missing");
            mustContain(TradeService.handleItemAction(b,
                new ItemContainerAction(
                    145,3322,sharkSlot,SHARK,0,"ITEM_ACTION_1"
                )),"TRADE_OFFER_OK");
            mustContain(TradeService.handleWidget(a,3420),"WAITING_OTHER");
            mustContain(TradeService.handleWidget(b,3420),"CONFIRM_OPEN");
            mustContain(TradeService.handleWidget(a,3546),"WAITING_OTHER");
            mustContain(TradeService.handleWidget(b,3546),"TRADE_COMMITTED");
            PlayerSnapshot afterA=PlayerSnapshotCodec.capture("opensrc",a);
            PlayerSnapshot afterB=PlayerSnapshotCodec.capture("src",b);
            R60TradePairCoordinator.initializeEmpty(journal);
            long revision=R60TradePairCoordinator.registerPrepared(
                journal,1L,Collections.emptyList(),ID,
                beforeA,afterA,beforeB,afterB
            );
            require(revision==2L,"registered journal/index revision");
            return new Fixture(
                journal,accounts,payloads,beforeA,beforeB,afterA,afterB
            );
        }finally{
            TradeService.unregister(a);
            TradeService.unregister(b);
            if(a.registered())world.unregisterPlayer(a);
            if(b.registered())world.unregisterPlayer(b);
            world.close();
        }
    }

    private static void mustContain(String value,String expected){
        if(value==null||(expected!=null&&!value.contains(expected)))
            throw new AssertionError(
                "expected "+expected+" got "+value
            );
    }

    private static void requireReview(
        Fixture f,R57TradePairJournal.State wanted)throws Exception{
        PlayerSnapshot nowA=f.accounts().load("opensrc").orElseThrow(
            ()->new AssertionError("primary missing"));
        PlayerSnapshot nowB=f.accounts().load("src").orElseThrow(
            ()->new AssertionError("peer missing"));
        R57TradePairJournal.Review review=R57TradePairJournal.review(
            f.journal.resolve("trade-r57-"+ID+".properties"),
            nowA,nowB
        );
        require(review.state==wanted,
            "expected "+wanted+" got "+review.state);
    }

    private static void requireSavedPair(Fixture f)throws Exception{
        requireReview(f,R57TradePairJournal.State.BOTH_AFTER);
        WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(
            f.accounts().load("opensrc").orElseThrow(
                ()->new AssertionError("primary absent")),a
        );
        PlayerSnapshotCodec.applyValidated(
            f.accounts().load("src").orElseThrow(
                ()->new AssertionError("peer absent")),b
        );
        require(a.bank().inventoryCount(COIN)==999 &&
                a.bank().inventoryCount(SHARK)==1 &&
                b.bank().inventoryCount(COIN)==1 &&
                b.bank().inventoryCount(SHARK)==1,
            "roll-forward lost or duplicated traded items");
    }

    private static void requireBothQuarantined(Fixture f){
        for(String account:new String[]{"opensrc","src"}){
            R59TradePairIntentIndex.Result r=
                R60TradePairCoordinator.inspect(f.journal,account);
            require(r.status==
                    R59TradePairIntentIndex.Status.BLOCK_INDEXED_PAIR,
                "completed offline replay accidentally authorized login");
        }
    }

    private static void runWorker(Fixture f,String mode)throws Exception{
        String executable=System.getProperty("os.name").toLowerCase()
            .contains("win")?"java.exe":"java";
        Path java=Paths.get(System.getProperty("java.home"),
            "bin",executable);
        Process worker=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            "spk.local.R61TradePairReplayWorker",
            f.journal.toString(),f.accounts.toString(),
            f.payloads.toString(),ID.toString(),mode
        ).redirectErrorStream(true).start();
        if(!worker.waitFor(25,TimeUnit.SECONDS)){
            worker.destroyForcibly();
            worker.waitFor(5,TimeUnit.SECONDS);
            throw new AssertionError("test-owned restart worker timed out");
        }
        String output=new String(
            worker.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        if("halt".equals(mode)){
            require(worker.exitValue()==91,
                "test worker did not exit via abrupt Runtime.halt: "+
                "code="+worker.exitValue()+" output="+output);
        }else{
            require(worker.exitValue()==0 &&
                    output.contains("R61_REPLAY_WORKER_PASS"),
                "fresh JVM replay failed: exit="+worker.exitValue()+
                " output="+output);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {void run()throws Exception;}
    private static void expectIOException(ThrowingAction action)
            throws Exception{
        try{
            action.run();
            throw new AssertionError(
                "expected an unsafe replay to fail closed"
            );
        }catch(IOException expected){
            // Verified refusal: no mutation is separately asserted.
        }
    }
    private static void require(boolean truth,String why){
        if(!truth)throw new AssertionError(why);
    }
    private R61TradePairReplayTest(){}
}

/** Separate JVM with no game socket or access to live account profiles. */
final class R61TradePairReplayWorker {
    public static void main(String[] args)throws Exception{
        if(args.length!=5)throw new IllegalArgumentException("worker args");
        Path journal=Paths.get(args[0]);
        Path accounts=Paths.get(args[1]);
        Path payloads=Paths.get(args[2]);
        UUID id=UUID.fromString(args[3]);
        boolean interrupt="interrupt".equals(args[4]);
        boolean halt="halt".equals(args[4]);
        try{
            R61TradePairReplay.Outcome result=
                R61TradePairReplay.replayCommitted(
                    journal,accounts,payloads,id,interrupt||halt
                );
            if(interrupt||halt)
                throw new AssertionError("missing injected publication gap");
            if(!result.bothPostStatesVerified ||
               !result.unresolvedIntentStillBlocksLogin)
                throw new AssertionError("replay incorrectly marked safe");
        }catch(IOException failure){
            if((!interrupt&&!halt)||!failure.getMessage().contains(
                "R61_INJECTED_FAILURE_AFTER_FIRST_ACCOUNT"))
                throw failure;
            if(halt){
                // ONLY this explicitly test-owned worker JVM terminates.
                // Parent JVM and unrelated existing processes remain intact.
                Runtime.getRuntime().halt(91);
                throw new AssertionError("Runtime.halt returned");
            }
        }
        System.out.println("R61_REPLAY_WORKER_PASS mode="+args[4]);
    }
}
