package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Stream;

/**
 * G18.7.0: even an up-to-date, codec-valid COMPLETED G4 account file
 * collides with the SAME completed World-local latestTask after the
 * owner WorldPlayer is replaced under an unchanged username.
 *
 * Distinct from G18.6.8's intentionally STALE ACTIVE file. Saves
 * are explicit test-managed FilePlayerRepository calls; no TCP
 * admission, async World final save, original SpawnPK credit rule,
 * or simulated canonical NPC replay.
 */
public final class G1870SlayerCurrentCompletedFileSameWorldCollisionTest {
    private static final String USER="g1870-current-completed-file";
    private static final long FIRST_DEATH=91L;

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g1870-completed-file-");
        Path accountFile=root.resolve(USER+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(
            username->root.resolve(username+".properties"));
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer ownerA=new WorldPlayer();
        WorldPlayer ownerB=null;
        long genA=-1L,genB=-1L;
        try {
            genA=world.registerPlayer(ownerA,USER);
            LocalLabSlayerRuntime slayer=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult assigned=
                slayer.startMonsterHunter(USER,77L);
            check(assigned!=null&&assigned.created&&
                  assigned.status.task!=null&&
                  assigned.status.task.objective.progress==0L,
                "A starts legitimate Monster Hunter G4");
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            check(dmm.activateEasyIfCurrent(USER,ownerA,genA)
                    .activatedNow,
                "A starts World-owned EASY DMM");

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npcA=spawned.spawn.combat.spawn.npc;
            check(spawned.runtime.recipientOrigin!=null&&
                  spawned.runtime.recipientOrigin.player==ownerA&&
                  spawned.runtime.recipientOrigin.generation==genA&&
                  world.npcLifecycle().applyDamage(
                      npcA.id,99,FIRST_DEATH).newlyDied,
                "first real NPC death has exact A origin");
            MonsterSpawnerPvmRuntime.FinalizeResult first=
                installed.runtime().finalizeIfOwned(npcA);
            LocalLabSlayerRuntime.StatusSnapshot completedA=
                slayer.status(USER);
            LocalLabSlayerPersistence.Snapshot decoded=
                LocalLabSlayerPersistence.decode(
                    ownerA.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE));
            PvmRecordService records=world.pvmRecords();
            check(first!=null&&first.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                  first.settlement!=null&&
                  first.settlement.groundItems.size()==1&&
                  first.settlement.groundItems.get(0)
                      .settledAmount==1&&
                  completedA!=null&&completedA.complete()&&
                  completedA.completions==1L&&
                  decoded!=null&&
                  decoded.state==LocalLabSlayerPersistence.State.COMPLETED&&
                  decoded.progress==1L&&decoded.completions==1L&&
                  decoded.sourceAssignedTick==77L&&
                  decoded.sourceTransitionTick==FIRST_DEATH&&
                  records.snapshot(ownerA).kills==1L&&
                  records.receiptCount()==1&&
                  dmm.snapshot(USER).objective.progress==1L,
                "genuine first terminal committed G4/G1/G18 and coin");

            // Crucial difference from stale-ACTIVE G18.6.8:
            // persist the EXACT CURRENT COMPLETED task from A.
            repository.save(PlayerSnapshotCodec.capture(USER,ownerA));
            Optional<PlayerSnapshot> stored=repository.load(USER);
            check(stored.isPresent()&&Files.exists(accountFile),
                "genuine current COMPLETED account file exists");
            byte[] currentDisk=Files.readAllBytes(accountFile);
            SortedMap<String,String> exactCompleted=
                ownerA.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE);

            check(world.unregisterPlayer(ownerA,genA),
                "completed owner A detached");
            ownerB=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                repository.load(USER).get(),ownerB);
            genB=world.registerPlayer(ownerB,USER);
            check(exactCompleted.equals(
                    ownerB.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE))&&
                  records.snapshot(ownerB).kills==1L,
                "B loaded exact CURRENT completed G4 + persisted G1 kill1");

            // Live World task is completed and MATCHES the saved
            // completion logically, but ensureRestoredOwned checks
            // only whether latestTask(name) exists.
            LocalLabSlayerRuntime.StatusSnapshot rejected=
                slayer.status(USER);
            check(rejected!=null&&
                  "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                      rejected.persistenceError)&&
                  rejected.task==null&&
                  dmm.snapshot(USER).objective.progress==1L,
                "even matching completed file rejected in same World");

            // G1's real original delayed respawn now captures B.
            world.events().runDue(
                FIRST_DEATH+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "actual delayed second NPC spawned");
            EntityId nextId=session.spawnedNpcIds.get(0);
            WorldNpc npcB=world.npcs().byId(nextId);
            MonsterSpawnerPvmRuntime.Snapshot observed=
                installed.runtime().get(nextId);
            check(npcB!=null&&!nextId.equals(npcA.id)&&
                  observed!=null&&observed.recipientOrigin!=null&&
                  observed.recipientOrigin.player==ownerB&&
                  observed.recipientOrigin.generation==genB&&
                  world.npcLifecycle().applyDamage(
                      npcB.id,99,FIRST_DEATH+
                          G1MonsterSpawnerPvmLoopService
                              .RESPAWN_DELAY_TICKS+1L).newlyDied,
                "second real NPC death belongs to new exact B generation");
            MonsterSpawnerPvmRuntime.FinalizeResult second=
                installed.runtime().finalizeIfOwned(npcB);
            LocalLabSlayerRuntime.StatusSnapshot stillRejected=
                slayer.status(USER);
            check(second!=null&&second.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                  second.settlement!=null&&
                  second.settlement.groundItems.size()==1&&
                  second.settlement.groundItems.get(0)
                      .settledAmount==1&&
                  stillRejected!=null&&
                  "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                      stillRejected.persistenceError)&&
                  stillRejected.task==null&&
                  exactCompleted.equals(
                      ownerB.snapshotExtensions().namespace(
                          LocalLabSlayerPersistence.NAMESPACE))&&
                  records.snapshot(ownerA).kills==1L&&
                  records.snapshot(ownerB).kills==2L&&
                  records.receiptCount()==2&&
                  dmm.snapshot(USER).objective.progress==2L&&
                  world.groundItems().snapshot().stream()
                      .filter(item->item.itemId==995&&
                          USER.equals(item.owner))
                      .mapToInt(item->item.amount).sum()==2&&
                  installed.runtime().get(npcA.id)==null&&
                  installed.runtime().get(npcB.id)==null&&
                  Arrays.equals(currentDisk,Files.readAllBytes(accountFile)),
                "matching file G4 still refused; B G1 kill1->2 and "+
                "World G18/ground progress; file unchanged");

            System.out.println(
                "G1870_CURRENT_COMPLETED_G4_FILE_SAME_WORLD_COLLISION_PASS"+
                " exactCurrentCompletedFile=true"+
                " validCompletedSnapshotMatchedWorldTask=true"+
                " sameWorldValidCompletedHydrationRefused=true"+
                " firstNpcG4G1DmmCredit=true"+
                " secondDistinctNpcG1AndDmmCredit=true"+
                " playerBLoadedG1OneThenReachedTwo=true"+
                " g4DoesNotRetryAfterCollision=true"+
                " twoRealGroundCoinSettlements=true"+
                " realDiskByteIdenticalAfterSecondTerminal=true"+
                " originalServerPolicyClaim=false"+
                " authenticatedTcpOrAsyncFinalSave=false"+
                " productionCodeChanged=false");
        } finally {
            if(ownerB!=null&&genB>=0L&&
                  world.players().owns(ownerB,genB))
                world.unregisterPlayer(ownerB,genB);
            if(genA>=0L&&world.players().owns(ownerA,genA))
                world.unregisterPlayer(ownerA,genA);
            world.close();
            if(Files.exists(root)){
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path path:paths.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void check(boolean accepted,String message){
        if(!accepted)throw new AssertionError("G18.7.0 "+message);
    }

    private G1870SlayerCurrentCompletedFileSameWorldCollisionTest(){}
}
