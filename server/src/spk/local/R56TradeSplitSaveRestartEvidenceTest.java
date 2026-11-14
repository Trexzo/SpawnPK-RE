package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R56: prove the exact pre-journal two-file crash window by restarting from
 * TWO real atomically replaced repository snapshots after saving only one.
 * Evidence-only. It must not alter real profiles or silently auto-reconcile.
 */
public final class R56TradeSplitSaveRestartEvidenceTest {
    private static final int COIN=995;
    private static final int SHARK=385;

    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("spk-r56-split-save-evidence-");
        try {
            verifySaveOutcome(root.resolve("none-saved"),0);
            verifySaveOutcome(root.resolve("primary-saved"),1);
            verifySaveOutcome(root.resolve("secondary-saved"),2);
            verifySaveOutcome(root.resolve("both-saved"),3);
            System.out.println(
                "R56_TRADE_SPLIT_SAVE_RESTART_EVIDENCE_PASS "+
                "noSaveBaselineConservesItems=true "+
                "primaryOnlyCoinLossAndSharkDuplication=true "+
                "secondaryOnlyCoinDuplicationAndSharkLoss=true "+
                "bothSavesConserveItems=true "+
                "bothCanonicalLoadsSucceed=true "+
                "realRepositoryAtomicPerFileNotPerTrade=true "+
                "noLiveAccountsTouched=true "+
                "noAutomaticRecoveryAttempted=true "+
                "twoAccountCrashAtomicityProven=false");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){
                paths.sorted(Comparator.reverseOrder()).forEach(path->{
                    try { Files.deleteIfExists(path); }
                    catch(IOException e){ throw new IllegalStateException(e); }
                });
            }
        }
    }

    private static void verifySaveOutcome(Path dir,int savedMask)
            throws Exception{
        Files.createDirectory(dir);
        FilePlayerRepository repo=new FilePlayerRepository(
            username->dir.resolve(username+".properties"));
        World world=World.isolatedForTest(60_000L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=new WorldPlayer();
        world.registerPlayer(a,"opensrc");
        world.registerPlayer(b,"src");
        ServerPacketWriter writerA=new ServerPacketWriter(
            new OutboundPacketQueue(),new IsaacCipher(new int[]{1,2,3,4}));
        ServerPacketWriter writerB=new ServerPacketWriter(
            new OutboundPacketQueue(),new IsaacCipher(new int[]{5,6,7,8}));
        AtomicInteger saveCallbacks=new AtomicInteger();
        try {
            a.bank().spawnItem(COIN,1000,writerA);
            b.bank().spawnItem(SHARK,2,writerB);

            PlayerSnapshot beforeA=PlayerSnapshotCodec.capture("opensrc",a);
            PlayerSnapshot beforeB=PlayerSnapshotCodec.capture("src",b);
            repo.save(beforeA);
            repo.save(beforeB);
            requireInventory(repo,"opensrc",1000,0);
            requireInventory(repo,"src",0,2);

            TradeService.register(world,a,a.bank(),writerA,
                saveCallbacks::incrementAndGet);
            TradeService.register(world,b,b.bank(),writerB,
                saveCallbacks::incrementAndGet);
            requireText(TradeService.start(world,a,b),"TRADE_UI_OPEN");
            requireText(TradeService.handleItemAction(a,
                new ItemContainerAction(145,3322,0,COIN,0,"ITEM_ACTION_1")),
                "TRADE_OFFER_OK");
            int slot=EngineR4TradeFlowTest.find(b.bank(),SHARK);
            require(slot>=0,"shark slot not found");
            requireText(TradeService.handleItemAction(b,
                new ItemContainerAction(145,3322,slot,SHARK,0,
                    "ITEM_ACTION_1")),"TRADE_OFFER_OK");
            requireText(TradeService.handleWidget(a,3420),"WAITING_OTHER");
            requireText(TradeService.handleWidget(b,3420),"CONFIRM_OPEN");
            requireText(TradeService.handleWidget(a,3546),"WAITING_OTHER");
            requireText(TradeService.handleWidget(b,3546),"TRADE_COMMITTED");
            require(saveCallbacks.get()==2,
                "trade did not invoke both independent save callbacks");

            // Trade did complete in server memory. Only ONE canonical
            // account file is replaced before the simulated crash.
            require(a.bank().inventoryCount(COIN)==999&&
                    a.bank().inventoryCount(SHARK)==1,
                    "primary post-trade server inventory wrong");
            require(b.bank().inventoryCount(COIN)==1&&
                    b.bank().inventoryCount(SHARK)==1,
                    "secondary post-trade server inventory wrong");
            PlayerSnapshot afterA=PlayerSnapshotCodec.capture("opensrc",a);
            PlayerSnapshot afterB=PlayerSnapshotCodec.capture("src",b);
            if((savedMask&1)!=0)repo.save(afterA);
            if((savedMask&2)!=0)repo.save(afterB);

            // Simulate a new JVM by DISCARDING the live WorldPlayer state.
            // The independent persisted files both parse and load normally.
            WorldPlayer loadedA=loaded(repo,"opensrc");
            WorldPlayer loadedB=loaded(repo,"src");
            int totalCoins=loadedA.bank().inventoryCount(COIN)+
                loadedB.bank().inventoryCount(COIN);
            int totalSharks=loadedA.bank().inventoryCount(SHARK)+
                loadedB.bank().inventoryCount(SHARK);

            if(savedMask==1){
                requireInventory(loadedA,999,1);
                requireInventory(loadedB,0,2);
                require(totalCoins==999&&totalSharks==3,
                    "primary-only persisted trade should expose 1 lost coin / duplicated shark");
            }else if(savedMask==2){
                requireInventory(loadedA,1000,0);
                requireInventory(loadedB,1,1);
                require(totalCoins==1001&&totalSharks==1,
                    "secondary-only persisted trade should expose 1 duplicate coin / lost shark");
            }else if(savedMask==0){
                requireInventory(loadedA,1000,0);
                requireInventory(loadedB,0,2);
                require(totalCoins==1000&&totalSharks==2,
                    "neither save should preserve the original inventory sum");
            }else if(savedMask==3){
                requireInventory(loadedA,999,1);
                requireInventory(loadedB,1,1);
                require(totalCoins==1000&&totalSharks==2,
                    "both saves should preserve the traded inventory sum");
            }else throw new AssertionError("unknown saved-mask fixture");
        }finally{
            TradeService.unregister(a);
            TradeService.unregister(b);
            if(a.registered())world.unregisterPlayer(a);
            if(b.registered())world.unregisterPlayer(b);
            world.close();
        }
    }

    private static WorldPlayer loaded(FilePlayerRepository repo,String name)
            throws Exception{
        PlayerSnapshot snap=repo.load(name).orElseThrow(
            ()->new AssertionError("canonical saved account missing: "+name));
        WorldPlayer state=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(snap,state);
        return state;
    }

    private static void requireInventory(
        FilePlayerRepository repo,String name,int coins,int sharks)
            throws Exception{
        requireInventory(loaded(repo,name),coins,sharks);
    }

    private static void requireInventory(
        WorldPlayer player,int coins,int sharks){
        require(player.bank().inventoryCount(COIN)==coins,
            "wrong persisted coins: "+player.bank().inventoryCount(COIN));
        require(player.bank().inventoryCount(SHARK)==sharks,
            "wrong persisted sharks: "+player.bank().inventoryCount(SHARK));
    }

    private static void requireText(String actual,String expected){
        require(actual!=null&&actual.contains(expected),
            "expected "+expected+" got "+actual);
    }

    private static void require(boolean condition,String why){
        if(!condition)throw new AssertionError(why);
    }

    private R56TradeSplitSaveRestartEvidenceTest() {}
}
