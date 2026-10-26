package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * P1 gameplay vertical slice: canonical PvM kill -> owned ground drop ->
 * exact Take -> bank deposit -> explicit checkpoint -> fresh World restore.
 * This is NOT original-client GUI or failed-final-save durability proof.
 */
public final class PlayablePvmBankReconnectSmokeTest {
    private static final String PLAYER="playable-p1";
    private static final int COINS=995;
    private PlayablePvmBankReconnectSmokeTest(){}

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError("P1_PLAYABILITY "+reason);
    }

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(new ByteArrayOutputStream(),
            new IsaacCipher(new int[4]));
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI arguments");
        Path root=Files.createTempDirectory("spk-playable-p1-");
        FilePlayerRepository repository=new FilePlayerRepository(
            name->root.resolve(name+".properties"));
        World original=null,restarted=null;
        WorldPlayer first=null,restoredPlayer=null;
        long firstGeneration=0,restoredGeneration=0;
        ServerPacketWriter relay=null;
        try{
            original=World.isolatedForTest(600L,repository);
            first=new WorldPlayer();
            firstGeneration=original.registerPlayer(first,PLAYER);
            first.movement().restoreAccountState(false,100,
                MovementState.INITIAL_X,MovementState.INITIAL_Y,0);
            BankState.Stack starter=first.bank().bankAt(0);
            require(starter!=null&&starter.itemId==COINS&&starter.qty>0,
                "no starting coin bank stack");
            final int beforeCoins=starter.qty;
            require(first.bank().inventoryCount(COINS)==0,
                "inventory begins with coins, not earned PvM loot");

            LocalMonsterSpawnerActivationRuntime activation=
                LocalLabMonsterSpawnerProvisioning.create(original);
            MonsterSpawnerService spawner=activation.service();
            NpcRegistry views=new NpcRegistry(new DevAuthorityWorkbench(),
                original.petNpcs(),first.id());
            relay=writer();
            SharedNpcWorldRelay.register(relay,original,first,views,
                first.movement());
            spawner.openSession(PLAYER,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            MonsterSpawnerService.SessionSnapshot selected=
                spawner.selectRow(PLAYER,0);
            spawner.activateIfCurrent(PLAYER,selected,1);
            MonsterSpawnerPvmSpawnExecutor.Result spawned=
                activation.executor().execute(PLAYER);
            require(spawned!=null&&
                spawned.status==MonsterSpawnerPvmSpawnExecutor.Status.SPAWNED&&
                spawned.spawn!=null,"Monster Spawner failed");
            WorldNpc npc=spawned.spawn.spawn.combat.spawn.npc;
            require(npc.definitionId==
                LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                "unexpected NPC definition");
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity projection=views.canonical(npc.id);
            require(projection!=null&&npc.id.equals(projection.canonicalId()),
                "canonical attack scene NPC not visible");

            final World fightingWorld=original;
            final WorldPlayer fighter=first;
            final long fightingGeneration=firstGeneration;
            LocalCanonicalNpcAttackHandler combat=
                new LocalCanonicalNpcAttackHandler(
                    fightingWorld,fighter,()->fightingGeneration,
                    fighter.equipment(),fighter.combatStyles(),views,
                    (target,expected)->{},
                    target->fightingWorld.finalizeMonsterSpawnerPvmIfOwned(
                        target),
                    target->fightingWorld.retryMonsterSpawnerPvmFinalizationIfPending(
                        target));
            LocalCanonicalNpcAttackHandler.Result hit=combat.handle(
                new NpcAction(72,projection.sceneIndex),
                projection,writer());
            require(hit!=null&&
                hit.status==LocalCanonicalNpcAttackHandler.Status.HIT&&
                hit.newlyDied&&hit.hitpointsAfter==0,
                "canonical PvM attack did not kill NPC");
            GroundItem drop=original.groundItems().findOwned(
                COINS,MovementState.INITIAL_X+1,
                MovementState.INITIAL_Y,0,PLAYER);
            require(drop!=null&&drop.amount==1,
                "earned owner-scoped drop missing");
            require(original.pvmRecords().snapshot(first).kills==1L,
                "PvM kill was not credited exactly once");

            first.movement().restoreAccountState(false,100,
                drop.tile.x,drop.tile.y,drop.tile.plane);
            ServerPacketWriter groundWriter=writer();
            SceneUpdatePublisher scene=new SceneUpdatePublisher(
                groundWriter,new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,0));
            LocalGroundItemInteractionHandler ground=
                new LocalGroundItemInteractionHandler(original,
                    first.bank(),first.movement());
            GroundItemInteraction action=new GroundItemInteraction(
                236,3,COINS,drop.tile.x,drop.tile.y);
            LocalGroundItemInteractionHandler.Result taken=ground.handle(
                action,PLAYER,scene,groundWriter);
            require(taken!=null&&"GROUND_TAKE".equals(taken.saveReason)&&
                first.bank().inventoryCount(COINS)==1&&
                original.groundItems().byId(drop.id)==null,
                "Take failed to transfer exactly one coin");
            ground.handle(action,PLAYER,scene,groundWriter);
            require(first.bank().inventoryCount(COINS)==1,
                "duplicate Take recreated settled loot");

            ServerPacketWriter bankingWriter=writer();
            first.bank().open(bankingWriter);
            String deposit=first.bank().depositInventory(bankingWriter);
            require(deposit.startsWith("DEPOSITED_ALL")&&
                first.bank().inventoryCount(COINS)==0&&
                first.bank().bankAt(0).qty==beforeCoins+1,
                "bank deposit failed to preserve earned coin delta");
            first.bank().close(bankingWriter);

            // Explicit checkpoint, NOT a durable LocalSession final-save.
            PlayerSnapshot checkpoint=PlayerSnapshotCodec.capture(
                PLAYER,first);
            require("1".equals(checkpoint.value(
                "extension.pvm-record.kills")),
                "earned kill progress missing from checkpoint");
            repository.save(checkpoint);
            Optional<PlayerSnapshot> onDisk=
                repository.loadForWorldSession(PLAYER);
            require(onDisk.isPresent(),"checkpoint not reloadable");

            SharedNpcWorldRelay.unregister(relay);
            relay=null;
            require(original.unregisterPlayer(first,firstGeneration),
                "first player unregister failed");
            original.close();
            original=null;

            restarted=World.isolatedForTest(600L,repository);
            restoredPlayer=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(onDisk.get(),restoredPlayer);
            restoredGeneration=restarted.registerPlayer(
                restoredPlayer,PLAYER);
            BankState.Stack restored=restoredPlayer.bank().bankAt(0);
            require(restored!=null&&restored.itemId==COINS&&
                restored.qty==beforeCoins+1,
                "new World did not restore the banked PvM coin");
            require(restoredPlayer.bank().inventoryCount(COINS)==0,
                "reconnect duplicated the banked reward");
            PlayerSnapshot observed=PlayerSnapshotCodec.capture(
                PLAYER,restoredPlayer);
            require("1".equals(observed.value(
                "extension.pvm-record.kills")),
                "new World lost persisted PvM kill count");
            require(restarted.unregisterPlayer(
                restoredPlayer,restoredGeneration),
                "restarted player unregister failed");
            restarted.close();
            restarted=null;
            require(Files.isRegularFile(
                root.resolve(PLAYER+".properties")),
                "account file disappeared");
            System.out.println("P1_PLAYABLE_PVM_LOOT_BANK_RECONNECT_PASS"+
                " spawner=true canonicalAttack=true ownedDrop995x1=true"+
                " take=true duplicateTakeDenied=true bankDeposit=true"+
                " freshWorldBankDelta=1 pvmKillsRestored=1"+
                " sourceOnlyNoOriginalClientOrFailedFinalSaveProof=true");
        }finally{
            if(relay!=null){
                try{SharedNpcWorldRelay.unregister(relay);}
                catch(Exception ignored){}
            }
            if(original!=null){
                if(first!=null&&first.registered())
                    original.unregisterPlayer(first,firstGeneration);
                if(!original.closed())original.close();
            }
            if(restarted!=null){
                if(restoredPlayer!=null&&restoredPlayer.registered())
                    restarted.unregisterPlayer(restoredPlayer,restoredGeneration);
                if(!restarted.closed())restarted.close();
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path path:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(path);
            }
        }
    }
}
