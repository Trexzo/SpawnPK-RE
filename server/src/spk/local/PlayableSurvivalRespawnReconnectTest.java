package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * P5.3 source gameplay composition:
 * damage -> Eat -> death -> reject dead Eat -> delayed respawn ->
 * damage -> Eat -> healthy checkpoint -> fresh World.
 *
 * All hitpoint/heal/respawn numbers are CUSTOM_LOCALLAB.
 * This directly exercises domain handlers, not an original graphical
 * client, real NPC attack wire, death-loot settlement or socket ACK.
 */
public final class PlayableSurvivalRespawnReconnectTest {
    private static final String USER="playable-survival";
    private static final int FOOD=G1RocktailConsumableHandler.ITEM_ID;
    private static final int HP=PlayerState.HITPOINTS;

    private PlayableSurvivalRespawnReconnectTest(){}

    private static void require(boolean ok,String what){
        if(!ok)throw new AssertionError("P53_SURVIVAL "+what);
    }

    private static ItemContainerAction eat(int slot){
        return new ItemContainerAction(
            122,BankState.NORMAL_INVENTORY_CONTAINER,
            slot,FOOD,0,"");
    }

    private static final class Capture {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        final ServerPacketWriter writer=new ServerPacketWriter(
            bytes,new IsaacCipher(new int[]{53,54,55,56}));
    }

    public static void main(String[] args)throws Exception {
        require(args.length==0,"unexpected CLI arguments");
        Path root=Files.createTempDirectory("spk-playable-p53-survival-");
        FilePlayerRepository repository=new FilePlayerRepository(
            name->root.resolve(name+".properties"));
        World original=null,renewed=null;
        WorldPlayer player=null,reloaded=null;
        long originalGeneration=0,reloadedGeneration=0;
        try {
            original=World.isolatedForTest(600L,repository);
            player=new WorldPlayer();
            originalGeneration=original.registerPlayer(player,USER);
            BankState bank=player.bank();
            BankState.Stack stocked=bank.bankAt(3);
            require(stocked!=null&&stocked.itemId==FOOD&&
                stocked.qty==20,"starter bank food baseline");
            BankState.PreparedInventoryMutation grant=
                bank.prepareAddInventoryAmount(FOOD,3);
            require(grant.accepted(),"isolated food fixture refused");
            bank.commitPreparedInventoryMutation(grant);
            require(bank.inventoryCount(FOOD)==3,
                "three separate Rocktails not installed");

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(player);
            G1RocktailConsumableHandler food=
                new G1RocktailConsumableHandler(player,bank);
            require(player.playerState().currentLevel(HP)==99,
                "initial hitpoints changed");

            PlayerLifecycleService.DamageResult firstDamage=
                lifecycle.applyDamage(49,10L,"P53_NONLETHAL");
            require(!firstDamage.died&&
                firstDamage.hpAfter==50&&
                player.lifecycle().alive(),"nonlethal 99->50");

            Capture beforeDeath=new Capture();
            G1RocktailConsumableHandler.Result firstFood=
                food.handle(eat(0),10L,beforeDeath.writer);
            require(firstFood!=null&&firstFood.consumed()&&
                firstFood.beforeHitpoints==50&&
                firstFood.afterHitpoints==60&&
                bank.inventoryCount(FOOD)==2&&
                beforeDeath.bytes.size()>0,
                "predeath Eat did not grant exactly 10 HP");

            PlayerLifecycleService.DamageResult lethal=
                lifecycle.applyDamage(500,20L,"P53_LETHAL");
            require(lethal.died&&lethal.hpAfter==0&&
                player.lifecycle().dead()&&
                player.lifecycle().deathTick()==20L&&
                player.lifecycle().respawnTick()==25L&&
                player.lifecycle().deathSequence()==1L,
                "lethal death or 5-tick respawn deadline");

            Capture whileDead=new Capture();
            G1RocktailConsumableHandler.Result deniedFood=
                food.handle(eat(1),21L,whileDead.writer);
            require(deniedFood!=null&&
                deniedFood.status==G1RocktailConsumableHandler.Status.DEAD&&
                whileDead.bytes.size()==0&&
                bank.inventoryCount(FOOD)==2&&
                player.playerState().currentLevel(HP)==0,
                "dead player consumed item, healed or published packet");

            PlayerLifecycleService.DamageResult deniedDamage=
                lifecycle.applyDamage(5,22L,"P53_DEAD_DUPLICATE");
            require(deniedDamage.ignoredDead&&
                deniedDamage.applied==0&&
                player.lifecycle().deathSequence()==1L,
                "damage while dead created second death or modified HP");

            require(lifecycle.tick(24L)==
                PlayerLifecycleService.TickResult.NONE&&
                player.lifecycle().dead()&&
                bank.inventoryCount(FOOD)==2,
                "respawn fired before tick 25");

            require(lifecycle.tick(25L)==
                PlayerLifecycleService.TickResult.RESPAWNED&&
                player.lifecycle().alive()&&
                player.playerState().currentLevel(HP)==99&&
                player.movement().x()==MovementState.INITIAL_X&&
                player.movement().y()==MovementState.INITIAL_Y&&
                bank.inventoryCount(FOOD)==2,
                "respawn state/Home/food mismatch");
            require(lifecycle.tick(26L)==
                PlayerLifecycleService.TickResult.NONE,
                "one death caused two respawns");

            PlayerLifecycleService.DamageResult afterRespawn=
                lifecycle.applyDamage(20,26L,"P53_POST_RESPAWN");
            require(!afterRespawn.died&&afterRespawn.hpAfter==79,
                "respawn survivor cannot be damaged");

            Capture afterDeath=new Capture();
            G1RocktailConsumableHandler.Result secondFood=
                food.handle(eat(1),26L,afterDeath.writer);
            require(secondFood!=null&&secondFood.consumed()&&
                secondFood.beforeHitpoints==79&&
                secondFood.afterHitpoints==89&&
                bank.inventoryCount(FOOD)==1&&
                afterDeath.bytes.size()>0,
                "post-respawn Eat did not restore usable survival");

            Capture stale=new Capture();
            G1RocktailConsumableHandler.Result repeated=
                food.handle(eat(1),29L,stale.writer);
            require(repeated!=null&&
                repeated.status==G1RocktailConsumableHandler.Status.STALE_SLOT&&
                stale.bytes.size()==0&&
                player.playerState().currentLevel(HP)==89&&
                bank.inventoryCount(FOOD)==1,
                "duplicate post-respawn Eat created health or items");
            require(bank.bankAt(3).qty==20,
                "survival consumed unrelated bank stock");

            PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(
                USER,player);
            repository.save(snapshot);
            Optional<PlayerSnapshot> file=
                repository.loadForWorldSession(USER);
            require(file.isPresent(),"healthy checkpoint unreadable");

            require(original.unregisterPlayer(player,originalGeneration),
                "original World unregister");
            original.close();
            original=null;

            renewed=World.isolatedForTest(600L,repository);
            reloaded=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(file.get(),reloaded);
            reloadedGeneration=renewed.registerPlayer(
                reloaded,USER);
            require(reloaded.playerState().currentLevel(HP)==89&&
                reloaded.bank().inventoryCount(FOOD)==1&&
                reloaded.bank().bankAt(3).qty==20&&
                reloaded.lifecycle().alive()&&
                reloaded.movement().x()==MovementState.INITIAL_X&&
                reloaded.movement().y()==MovementState.INITIAL_Y,
                "fresh World lost survival postimage");
            require(renewed.unregisterPlayer(
                reloaded,reloadedGeneration),
                "fresh World unregister");
            renewed.close();
            renewed=null;

            require(Files.isRegularFile(root.resolve(USER+".properties")),
                "survival account file missing");
            System.out.println(
                "P53_PLAYABLE_SURVIVAL_RESPAWN_RECONNECT_PASS"+
                " damage99to50=true heal50to60=true"+
                " lethal20=true deadEatRejectedZeroBytes=true"+
                " deadDuplicateDamageRejected=true"+
                " earlyRespawnRejected=true respawnTick25Hp99Home=true"+
                " postRespawnDamage99to79=true"+
                " eat79to89=true staleEatZeroBytes=true"+
                " bankStockPreserved20=true freshWorldHp89Food1=true"+
                " authority=CUSTOM_LOCALLAB"+
                " noClientGUIOrDeathLootOrPowerLossProof=true");
        } finally {
            if(original!=null){
                if(player!=null&&player.registered())
                    original.unregisterPlayer(player,originalGeneration);
                if(!original.closed())original.close();
            }
            if(renewed!=null){
                if(reloaded!=null&&reloaded.registered())
                    renewed.unregisterPlayer(
                        reloaded,reloadedGeneration);
                if(!renewed.closed())renewed.close();
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
