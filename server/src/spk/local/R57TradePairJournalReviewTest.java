package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;

/**
 * R57 cross-file journal evidence with a real successful trade and four
 * restart/save checkpoints. No journal replay or login integration occurs.
 */
public final class R57TradePairJournalReviewTest {
    private static final UUID ID=UUID.fromString(
        "11111111-2222-4333-8444-555555555555");
    private static final int COIN=995,SHARK=385;

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("spk-r57-intent-review-");
        try{
            checkState(root.resolve("none"),0,
                R57TradePairJournal.State.BOTH_BEFORE);
            checkState(root.resolve("only-a"),1,
                R57TradePairJournal.State.ONLY_A_AFTER);
            checkState(root.resolve("only-b"),2,
                R57TradePairJournal.State.ONLY_B_AFTER);
            checkState(root.resolve("both"),3,
                R57TradePairJournal.State.BOTH_AFTER);
            System.out.println(
                "R57_PAIRED_TRADE_INTENT_OFFLINE_REVIEW_PASS "+
                "fourRestartOutcomesClassified=true "+
                "allPendingIntentsDenyAdmission=true "+
                "journalContainsNoSnapshotValues=true "+
                "duplicateIntentWriteDenied=true "+
                "ambiguousStagePreservedAndDenied=true "+
                "tamperedAndUnknownSnapshotsDenied=true "+
                "noAutoRecoveryOrLiveHook=true "+
                "physicalCrashDurabilityNotProven=true");
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException failure){
                        throw new IllegalStateException(failure);
                    }
                });
            }
        }
    }

    private static void checkState(
        Path root,int savedMask,R57TradePairJournal.State expected
    )throws Exception{
        Files.createDirectory(root);
        FilePlayerRepository repo=new FilePlayerRepository(
            name->root.resolve(name+".properties"));
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
            repo.save(beforeA);
            repo.save(beforeB);

            TradeService.register(world,a,a.bank(),writerA,()->{});
            TradeService.register(world,b,b.bank(),writerB,()->{});
            expect(TradeService.start(world,a,b),"TRADE_UI_OPEN");
            expect(TradeService.handleItemAction(a,
                new ItemContainerAction(145,3322,0,COIN,0,
                    "ITEM_ACTION_1")),"TRADE_OFFER_OK");
            int sharkSlot=EngineR4TradeFlowTest.find(b.bank(),SHARK);
            require(sharkSlot>=0,"missing shark");
            expect(TradeService.handleItemAction(b,
                new ItemContainerAction(145,3322,sharkSlot,SHARK,0,
                    "ITEM_ACTION_1")),"TRADE_OFFER_OK");
            expect(TradeService.handleWidget(a,3420),"WAITING_OTHER");
            expect(TradeService.handleWidget(b,3420),"CONFIRM_OPEN");
            expect(TradeService.handleWidget(a,3546),"WAITING_OTHER");
            expect(TradeService.handleWidget(b,3546),"TRADE_COMMITTED");
            PlayerSnapshot afterA=PlayerSnapshotCodec.capture("opensrc",a);
            PlayerSnapshot afterB=PlayerSnapshotCodec.capture("src",b);

            Path intent=root.resolve(".trade-r57-intent.properties");
            R57TradePairJournal.writePrepared(
                intent,ID,beforeA,afterA,beforeB,afterB);
            byte[] persistedIntent=Files.readAllBytes(intent);
            String utf8=new String(persistedIntent,StandardCharsets.UTF_8);
            require(!utf8.contains("inventory.0=")&&
                    !utf8.contains("bank.0="),
                "private account values leaked to journal");
            expectIOException(()->R57TradePairJournal.writePrepared(
                intent,ID,beforeA,afterA,beforeB,afterB),
                "existing intent is immutable");

            if((savedMask&1)!=0)repo.save(afterA);
            if((savedMask&2)!=0)repo.save(afterB);

            byte[] canonicalA=Files.readAllBytes(
                root.resolve("opensrc.properties"));
            byte[] canonicalB=Files.readAllBytes(
                root.resolve("src.properties"));
            PlayerSnapshot loadedA=repo.load("opensrc").orElseThrow(
                ()->new AssertionError("missing primary snapshot"));
            PlayerSnapshot loadedB=repo.load("src").orElseThrow(
                ()->new AssertionError("missing secondary snapshot"));

            R57TradePairJournal.Review observed=
                R57TradePairJournal.review(intent,loadedA,loadedB);
            require(observed.state==expected,
                "savedMask="+savedMask+" detected="+observed.state+
                " expected="+expected);
            require(observed.transaction.equals(ID) &&
                    !observed.loginAllowed &&
                    !observed.automaticRecoveryAttempted,
                "pending intent unexpectedly authorized login/recovery");
            R57TradePairJournal.Review replay=
                R57TradePairJournal.review(intent,loadedA,loadedB);
            require(replay.state==observed.state,
                "second read-only review was not idempotent");

            // Unknown post-state is evidence of mutation; never classify it
            // as safe merely because a separate account file loads.
            WorldPlayer altered=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(loadedA,altered);
            altered.bank().spawnItem(560,1,
                new ServerPacketWriter(new OutboundPacketQueue(),
                    new IsaacCipher(new int[]{9,8,7,6})));
            PlayerSnapshot unexpected=PlayerSnapshotCodec.capture(
                "opensrc",altered);
            require(R57TradePairJournal.review(
                intent,unexpected,loadedB).state==
                    R57TradePairJournal.State.UNRECOGNIZED,
                "unknown snapshot accepted as known side of trade");

            // A leftover staged intent can be concurrent. The reviewer must
            // preserve it and block rather than age-based cleanup or adoption.
            Path stage=root.resolve(
                "."+intent.getFileName()+".r16-fixture.stage");
            Files.write(stage,new byte[]{5,6,7});
            expectIOException(()->R57TradePairJournal.review(
                intent,loadedA,loadedB),"AMBIGUOUS_ACCOUNT_STAGING_DENIED");
            require(Arrays.equals(
                Files.readAllBytes(stage),new byte[]{5,6,7}),
                "stage bytes changed by reviewer");
            Files.delete(stage); // ONLY the test-created stage

            // Deliberately tamper a test-owned manifest digest and prove
            // fail-closed parsing. No recovery or normal account is changed.
            Properties tampered=new Properties();
            try(InputStream in=Files.newInputStream(intent)){
                tampered.load(in);
            }
            tampered.setProperty("a.after.sha256","not-a-sha256");
            try(OutputStream out=Files.newOutputStream(intent)){
                tampered.store(out,"test-only malformed journal");
            }
            expectIOException(()->R57TradePairJournal.review(
                intent,loadedA,loadedB),"invalid digest");
            Files.write(intent,persistedIntent); // restore test-only intent

            require(Arrays.equals(canonicalA,Files.readAllBytes(
                    root.resolve("opensrc.properties"))) &&
                    Arrays.equals(canonicalB,Files.readAllBytes(
                    root.resolve("src.properties"))),
                "read-only journal tests modified canonical accounts");
            require(R57TradePairJournal.review(
                intent,loadedA,loadedB).state==expected,
                "test-owned intent did not restore original review outcome");
        }finally{
            TradeService.unregister(a);
            TradeService.unregister(b);
            if(a.registered())world.unregisterPlayer(a);
            if(b.registered())world.unregisterPlayer(b);
            world.close();
        }
    }

    @FunctionalInterface
    private interface CheckedAction { void run()throws Exception; }

    private static void expectIOException(
        CheckedAction action,String fragment)throws Exception{
        try{
            action.run();
            throw new AssertionError("expected refusal: "+fragment);
        }catch(IOException error){
            require(error.getMessage().contains(fragment),
                "unexpected reason "+error+" expected="+fragment);
        }
    }

    private static void expect(String actual,String marker){
        require(actual!=null&&actual.contains(marker),
            "expected "+marker+" got "+actual);
    }
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private R57TradePairJournalReviewTest(){}
}
