package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Stream;

/**
 * G18.6.9: codec-valid but stale on-disk ACTIVE Slayer task has
 * a different outcome in a NEW isolated World than the completed
 * task retained by the old World (G18.6.8). Exactly two different
 * canonical NPCs really die, once per World; no death replay.
 *
 * All saves are explicit TEST-OWNED FilePlayerRepository operations.
 * Not authenticated login, production session final save, original
 * SpawnPK authority, or a claim of duplicated external rewards.
 */
public final class G1869SlayerStaleActiveFreshWorldFileRecoveryTest {
    private static final String USER="g1869-stale-fresh-world";
    private static final long SOURCE_ASSIGNED=77L;
    private static final long SOURCE_DEATH=91L;

    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("g1869-g4-stale-");
        Path accountPath=root.resolve(USER+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(
            username->root.resolve(username+".properties"));
        World sourceWorld=null,restoredWorld=null;
        WorldPlayer original=new WorldPlayer();
        WorldPlayer sameName=new WorldPlayer();
        WorldPlayer restored=new WorldPlayer();
        long gOriginal=-1L,gSameName=-1L,gRestored=-1L;
        byte[] frozenDisk=null;
        try {
            sourceWorld=World.isolatedForTest(60_000L,repository);
            gOriginal=sourceWorld.registerPlayer(original,USER);
            LocalLabSlayerRuntime slayer=sourceWorld.localLabSlayer();
            LocalLabSlayerRuntime.StartResult start=
                slayer.startMonsterHunter(USER,SOURCE_ASSIGNED);
            check(start!=null&&start.created&&
                    start.status.task!=null&&
                    start.status.task.assignedTick==SOURCE_ASSIGNED&&
                    start.status.task.objective.progress==0L,
                "legitimate old World ACTIVE task starts at77");
            SortedMap<String,String> activeSource=
                original.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE);
            LocalLabSlayerPersistence.Snapshot persistedActive=
                LocalLabSlayerPersistence.decode(activeSource);
            check(persistedActive!=null&&
                    persistedActive.state==
                        LocalLabSlayerPersistence.State.ACTIVE&&
                    persistedActive.sourceAssignedTick==SOURCE_ASSIGNED&&
                    persistedActive.completions==0L,
                "valid original ACTIVE file source");

            // Save the real account file intentionally BEFORE the
            // old World finishes the task, then never publish B's
            // completion. This is deliberately stale fixture state.
            repository.save(PlayerSnapshotCodec.capture(USER,original));
            check(Files.exists(accountPath),"original file on disk");
            frozenDisk=Files.readAllBytes(accountPath);
            check(repository.load(USER).isPresent(),
                "original file readable with repository");

            LocalLabDailyMoneyMakingRuntime oldDmm=
                sourceWorld.localLabDailyMoneyMaking();
            check(oldDmm.activateEasyIfCurrent(
                    USER,original,gOriginal).activatedNow,
                "old World DMM activated legitimately");
            LocalMonsterSpawnerActivationRuntime installedOld=
                LocalLabMonsterSpawnerProvisioning.create(sourceWorld);
            MonsterSpawnerService spawnerOld=installedOld.service();
            spawnerOld.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawnerOld.selectRow(USER,0);
            spawnerOld.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawnOld=
                installedOld.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc oldNpc=spawnOld.spawn.combat.spawn.npc;
            check(spawnOld.runtime.recipientOrigin!=null&&
                    spawnOld.runtime.recipientOrigin.player==original&&
                    spawnOld.runtime.recipientOrigin.generation==gOriginal,
                "old NPC captures original exact WorldPlayer");

            check(sourceWorld.unregisterPlayer(original,gOriginal),
                "original unregisters before old World completion");
            gSameName=sourceWorld.registerPlayer(sameName,USER);
            check(slayer.status(USER).task!=null&&
                    sameName.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE).isEmpty(),
                "replacement B sees inherited username-owned task");
            check(sourceWorld.npcLifecycle().applyDamage(
                    oldNpc.id,99,SOURCE_DEATH).newlyDied,
                "old World real NPC death at91");
            MonsterSpawnerPvmRuntime.FinalizeResult oldSettlement=
                installedOld.runtime().finalizeIfOwned(oldNpc);
            LocalLabSlayerRuntime.StatusSnapshot completedOld=
                slayer.status(USER);
            LocalLabSlayerPersistence.Snapshot bCompleted=
                LocalLabSlayerPersistence.decode(
                    sameName.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE));
            check(oldSettlement!=null&&
                    oldSettlement.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    oldSettlement.settlement!=null&&
                    oldSettlement.settlement.groundItems.size()==1&&
                    oldSettlement.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    completedOld.complete()&&
                    completedOld.completions==1L&&
                    bCompleted!=null&&
                    bCompleted.state==
                        LocalLabSlayerPersistence.State.COMPLETED&&
                    bCompleted.completions==1L&&
                    sourceWorld.pvmRecords().snapshot(original).kills==0L&&
                    sourceWorld.pvmRecords().snapshot(sameName).kills==1L&&
                    sourceWorld.pvmRecords().receiptCount()==1&&
                    oldDmm.snapshot(USER).objective.progress==1L&&
                    Arrays.equals(frozenDisk,Files.readAllBytes(accountPath)),
                "B completed old World task but no disk postimage save");
            sourceWorld.close();
            sourceWorld=null;
            check(Arrays.equals(frozenDisk,Files.readAllBytes(accountPath)),
                "old World shutdown left explicit old file unchanged");

            // Read the actual stale physical file into an entirely
            // independent World. Old World's completed latestTask
            // and G1 receipts are not available to the new World.
            Optional<PlayerSnapshot> loaded=repository.load(USER);
            check(loaded.isPresent(),"fresh World reads real old file");
            PlayerSnapshotCodec.applyValidated(loaded.get(),restored);
            check(activeSource.equals(
                    restored.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE)),
                "fresh World player loads exact VALID ACTIVE file");
            restoredWorld=World.isolatedForTest(60_000L,repository);
            gRestored=restoredWorld.registerPlayer(restored,USER);
            long replayTick=restoredWorld.clock().tick();
            LocalLabSlayerRuntime.StatusSnapshot hydrated=
                restoredWorld.localLabSlayer().status(USER);
            check(hydrated!=null&&hydrated.persistenceError==null&&
                    hydrated.active()&&hydrated.completions==0L&&
                    hydrated.task.objective.progress==0L&&
                    hydrated.task.assignedTick==replayTick&&
                    activeSource.equals(
                        restored.snapshotExtensions().namespace(
                            LocalLabSlayerPersistence.NAMESPACE))&&
                    restoredWorld.pvmRecords().receiptCount()==0&&
                    restoredWorld.groundItems().size()==0,
                "fresh World accepts stale ACTIVE disk bytes without "+
                "importing old World task/receipt state");

            LocalLabDailyMoneyMakingRuntime newDmm=
                restoredWorld.localLabDailyMoneyMaking();
            check(newDmm.activateEasyIfCurrent(
                    USER,restored,gRestored).activatedNow,
                "new World DMM starts independently from0");
            LocalMonsterSpawnerActivationRuntime installedNew=
                LocalLabMonsterSpawnerProvisioning.create(restoredWorld);
            MonsterSpawnerService spawnerNew=installedNew.service();
            spawnerNew.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawnerNew.selectRow(USER,0);
            spawnerNew.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawnNew=
                installedNew.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc newNpc=spawnNew.spawn.combat.spawn.npc;
            check(spawnNew.runtime.recipientOrigin!=null&&
                    spawnNew.runtime.recipientOrigin.player==restored&&
                    spawnNew.runtime.recipientOrigin.generation==gRestored&&
                    restoredWorld.npcLifecycle().applyDamage(
                        newNpc.id,99,replayTick+500L).newlyDied,
                "new World independent genuine NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult newSettlement=
                installedNew.runtime().finalizeIfOwned(newNpc);
            LocalLabSlayerRuntime.StatusSnapshot completedNew=
                restoredWorld.localLabSlayer().status(USER);
            LocalLabSlayerPersistence.Snapshot newPostimage=
                LocalLabSlayerPersistence.decode(
                    restored.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE));
            check(newSettlement!=null&&
                    newSettlement.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    newSettlement.settlement!=null&&
                    newSettlement.settlement.groundItems.size()==1&&
                    newSettlement.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    completedNew!=null&&completedNew.complete()&&
                    completedNew.completions==1L&&
                    newPostimage!=null&&
                    newPostimage.state==
                        LocalLabSlayerPersistence.State.COMPLETED&&
                    newPostimage.progress==1L&&
                    newPostimage.completions==1L&&
                    newPostimage.sourceAssignedTick==replayTick&&
                    restoredWorld.pvmRecords().snapshot(restored).kills==1L&&
                    restoredWorld.pvmRecords().receiptCount()==1&&
                    newDmm.snapshot(USER).objective.progress==1L&&
                    restoredWorld.groundItems().size()==1&&
                    Arrays.equals(frozenDisk,Files.readAllBytes(accountPath)),
                "new World completes independent task and file stays old "+
                "until explicit save; no same NPC event replay");

            repository.save(PlayerSnapshotCodec.capture(USER,restored));
            Optional<PlayerSnapshot> afterSave=repository.load(USER);
            check(afterSave.isPresent(),"explicit new completion file");
            WorldPlayer probe=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(afterSave.get(),probe);
            LocalLabSlayerPersistence.Snapshot finalDisk=
                LocalLabSlayerPersistence.decode(
                    probe.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE));
            check(finalDisk!=null&&
                    finalDisk.state==
                        LocalLabSlayerPersistence.State.COMPLETED&&
                    finalDisk.completions==1L&&
                    finalDisk.sourceAssignedTick==replayTick&&
                    !Arrays.equals(frozenDisk,Files.readAllBytes(accountPath)),
                "only deliberate second repository save updates account");

            System.out.println(
                "G1869_STALE_ACTIVE_FILE_FRESH_WORLD_SECOND_COMPLETION_PASS"+
                " realPhysicalOriginalActiveAccount=true"+
                " BCompletedOldWorldWithoutSaving=true"+
                " oldWorldClosedBeforeReload=true"+
                " sameValidFileAcceptedInNewWorld=true"+
                " independentSecondCanonicalNpcDeath=true"+
                " G4TaskCompletesOncePerWorld=true"+
                " oneG1ReceiptPerWorld=true"+
                " newWorldDmmStartsIndependently=true"+
                " explicitSecondSaveRequiredForDiskChange=true"+
                " notSameNpcDeathReplay=true"+
                " originalServerPolicyClaim=false"+
                " authenticatedTcpOrCrashTested=false"+
                " productionChanged=false");
        }finally{
            if(restoredWorld!=null)restoredWorld.close();
            if(sourceWorld!=null)sourceWorld.close();
            if(Files.exists(root)){
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path path:paths.sorted(
                            Comparator.reverseOrder()).toArray(Path[]::new))
                        Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void check(boolean good,String message){
        if(!good)throw new AssertionError("G18.6.9 "+message);
    }

    private G1869SlayerStaleActiveFreshWorldFileRecoveryTest(){}
}
