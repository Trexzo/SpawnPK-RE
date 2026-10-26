package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * P5: usable Rocktail healing from the real inventory Eat action,
 * exact packet-backed consume, cooldown/no-duplication and account
 * checkpoint persistence into an entirely fresh World.
 * CUSTOM_LOCALLAB heal/cooldown, NOT original SpawnPK policy.
 */
public final class PlayableRocktailHealingReconnectTest {
    private static final String USER="playable-healing";
    private static final int ITEM=G1RocktailConsumableHandler.ITEM_ID;
    private PlayableRocktailHealingReconnectTest(){}

    private static void check(boolean valid,String reason){
        if(!valid)throw new AssertionError("P5_PLAYABILITY "+reason);
    }

    private static ItemContainerAction eat(int slot){
        return new ItemContainerAction(
            122,BankState.NORMAL_INVENTORY_CONTAINER,slot,
            ITEM,0,"");
    }
    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),new IsaacCipher(
                new int[]{23,24,25,26}));
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected CLI arguments");
        Path folder=Files.createTempDirectory("spk-playable-p5-heal-");
        FilePlayerRepository repository=new FilePlayerRepository(
            name->folder.resolve(name+".properties"));
        World firstWorld=null,secondWorld=null;
        WorldPlayer player=null,restarted=null;
        long firstGeneration=0,secondGeneration=0;
        try{
            firstWorld=World.isolatedForTest(600L,repository);
            player=new WorldPlayer();
            firstGeneration=firstWorld.registerPlayer(player,USER);
            int originalBank=player.bank().bankAt(3).qty;
            check(originalBank==20,
                "default bank Rocktail preimage changed");
            BankState.PreparedInventoryMutation granted=
                player.bank().prepareAddInventoryAmount(ITEM,2);
            check(granted.accepted(),"two-food starter fixture refused");
            player.bank().commitPreparedInventoryMutation(granted);
            check(player.bank().inventoryCount(ITEM)==2,
                "fixture did not produce two nonstackable Rocktails");
            player.playerState().setCurrentLevel(
                PlayerState.HITPOINTS,50);
            G1RocktailConsumableHandler consumable=
                new G1RocktailConsumableHandler(
                    player,player.bank());

            G1RocktailConsumableHandler.Result first=
                consumable.handle(eat(0),10L,writer());
            check(first!=null&&first.consumed()&&
                first.beforeHitpoints==50&&
                first.afterHitpoints==60&&
                first.saveReason!=null&&
                player.bank().inventoryCount(ITEM)==1,
                "Eat action did not heal and remove exactly one item");

            G1RocktailConsumableHandler.Result tooSoon=
                consumable.handle(eat(1),11L,writer());
            check(tooSoon!=null&&tooSoon.status==
                G1RocktailConsumableHandler.Status.CADENCE_BLOCKED&&
                player.playerState().currentLevel(
                    PlayerState.HITPOINTS)==60&&
                player.bank().inventoryCount(ITEM)==1,
                "cooldown consumed or healed prematurely");

            G1RocktailConsumableHandler.Result second=
                consumable.handle(eat(1),13L,writer());
            check(second!=null&&second.consumed()&&
                second.afterHitpoints==70&&
                player.bank().inventoryCount(ITEM)==0,
                "eligible second Eat failed to consume final food");
            G1RocktailConsumableHandler.Result stale=
                consumable.handle(eat(1),16L,writer());
            check(stale!=null&&stale.status==
                G1RocktailConsumableHandler.Status.STALE_SLOT&&
                player.playerState().currentLevel(
                    PlayerState.HITPOINTS)==70,
                "repeat same-slot Eat created health or food");

            check(player.bank().bankAt(3).qty==originalBank,
                "unrelated bank stock was consumed by inventory Eat");
            PlayerSnapshot checkpoint=PlayerSnapshotCodec.capture(
                USER,player);
            repository.save(checkpoint);
            Optional<PlayerSnapshot> disk=
                repository.loadForWorldSession(USER);
            check(disk.isPresent(),"file checkpoint not readable");
            check(firstWorld.unregisterPlayer(player,firstGeneration),
                "first World did not remove player");
            firstWorld.close();
            firstWorld=null;

            secondWorld=World.isolatedForTest(600L,repository);
            restarted=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(disk.get(),restarted);
            secondGeneration=secondWorld.registerPlayer(
                restarted,USER);
            check(restarted.playerState().currentLevel(
                    PlayerState.HITPOINTS)==70&&
                restarted.bank().inventoryCount(ITEM)==0&&
                restarted.bank().bankAt(3).qty==originalBank,
                "fresh World lost healed HP or duplicated food");
            check(secondWorld.unregisterPlayer(
                restarted,secondGeneration),
                "fresh World did not unregister");
            secondWorld.close();
            secondWorld=null;

            check(Files.isRegularFile(
                folder.resolve(USER+".properties")),
                "account checkpoint missing after restart");
            System.out.println(
                "P5_PLAYABLE_ROCKTAIL_HEAL_RECONNECT_PASS"+
                " eatOpcode122=true inventoryConsumed2=true"+
                " hp50to70=true cooldownRejected=true"+
                " staleRetryDenied=true bankPreimageUnchanged=true"+
                " checkpointSaved=true freshWorldHp70=true"+
                " freshWorldInventoryNoFood=true"+
                " authority=CUSTOM_LOCALLAB"+
                " noOriginalGraphicalClientOrPowerLossProof=true");
        }finally{
            if(firstWorld!=null){
                if(player!=null&&player.registered())
                    firstWorld.unregisterPlayer(player,firstGeneration);
                if(!firstWorld.closed())firstWorld.close();
            }
            if(secondWorld!=null){
                if(restarted!=null&&restarted.registered())
                    secondWorld.unregisterPlayer(
                        restarted,secondGeneration);
                if(!secondWorld.closed())secondWorld.close();
            }
            try(Stream<Path> all=Files.walk(folder)){
                for(Path x:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(x);
            }
        }
    }
}
