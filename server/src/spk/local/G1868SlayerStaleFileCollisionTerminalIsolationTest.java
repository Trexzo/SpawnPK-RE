package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Stream;

/**
 * G18.6.8: a codec-valid on-disk ACTIVE G4 extension is stale against
 * one completed World-local task after A -> B -> C same-name players.
 * The original installed observer must refuse G4 nonthrowingly yet
 * still progress G1 and G18 on two distinct canonical NPC deaths.
 *
 * Only test-owned explicit FilePlayerRepository.save/load is used.
 * No TCP authentication, World final-save admission, account recovery,
 * synthetic terminal callback, or original SpawnPK reward policy.
 */
public final class G1868SlayerStaleFileCollisionTerminalIsolationTest {
    private static final String USER="g1868-stale-valid-disk";
    private static final long FIRST_TICK=901L;

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g1868-slayer-disk-");
        Path accountFile=dir.resolve(USER+".properties");
        FilePlayerRepository files=new FilePlayerRepository(
            username->dir.resolve(username+".properties"));
        World world=World.isolatedForTest(60_000L,files);
        WorldPlayer ownerA=new WorldPlayer();
        WorldPlayer ownerB=null;
        WorldPlayer ownerC=null;
        long genA=-1L,genB=-1L,genC=-1L;
        try{
            genA=world.registerPlayer(ownerA,USER);
            LocalLabSlayerRuntime slayer=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult started=
                slayer.startMonsterHunter(USER,world.clock().tick());
            check(started!=null&&started.created&&
                    started.status.task!=null&&
                    started.status.task.objective.progress==0L,
                "original A legitimately starts G4");
            SortedMap<String,String> originalActive=
                ownerA.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE);
            LocalLabSlayerPersistence.Snapshot decoded=
                LocalLabSlayerPersistence.decode(originalActive);
            check(decoded!=null&&
                    decoded.state==LocalLabSlayerPersistence.State.ACTIVE&&
                    decoded.progress==0L&&decoded.completions==0L,
                "original G4 ACTIVE bytes codec-valid");

            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            check(dmm.activateEasyIfCurrent(USER,ownerA,genA)
                    .activatedNow,
                "A activates World-owned DMM objective before first death");

            // Persist legitimate old ACTIVE account bytes to actual disk
            // before B completes the live World task. This explicit
            // repository test write is not an async session-final save.
            files.save(PlayerSnapshotCodec.capture(USER,ownerA));
            check(Files.exists(accountFile),"real ACTIVE account file saved");
            byte[] frozenDisk=Files.readAllBytes(accountFile);
            Optional<PlayerSnapshot> diskBefore=files.load(USER);
            check(diskBefore.isPresent(),"real ACTIVE file reload succeeds");
            WorldPlayer probe=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(diskBefore.get(),probe);
            check(originalActive.equals(
                    probe.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE)),
                "saved disk decodes exactly A's valid ACTIVE snapshot");

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawnA=
                installed.runtime().spawnAndBind(USER,USER,3088,3495,0);
            WorldNpc first=spawnA.spawn.combat.spawn.npc;
            check(spawnA.runtime.recipientOrigin!=null&&
                    spawnA.runtime.recipientOrigin.player==ownerA&&
                    spawnA.runtime.recipientOrigin.generation==genA,
                "canonical NPC immutable origin A");

            check(world.unregisterPlayer(ownerA,genA),
                "A detached before first real death");
            ownerB=new WorldPlayer();
            genB=world.registerPlayer(ownerB,USER);
            LocalLabSlayerRuntime.StatusSnapshot taskOnB=
                slayer.status(USER);
            check(taskOnB!=null&&
                    taskOnB.persistenceError==null&&
                    taskOnB.task!=null&&
                    taskOnB.task.taskId.equals(
                        started.status.task.taskId),
                "new same-name B observes A's active World task");
            check(world.npcLifecycle().applyDamage(
                    first.id,99,FIRST_TICK).newlyDied,
                "A-origin first canonical NPC dies under B");
            MonsterSpawnerPvmRuntime.FinalizeResult settledB=
                installed.runtime().finalizeIfOwned(first);
            LocalLabSlayerPersistence.Snapshot bCompleted=
                LocalLabSlayerPersistence.decode(
                    ownerB.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE));
            PvmRecordService records=world.pvmRecords();
            check(settledB!=null&&
                    settledB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    settledB.settlement!=null&&
                    settledB.settlement.groundItems.size()==1&&
                    settledB.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    bCompleted!=null&&
                    bCompleted.state==LocalLabSlayerPersistence.State.COMPLETED&&
                    bCompleted.progress==1L&&bCompleted.completions==1L&&
                    records.snapshot(ownerA).kills==0L&&
                    records.snapshot(ownerB).kills==1L&&
                    records.receiptCount()==1&&
                    dmm.snapshot(USER).objective.progress==1L,
                "B receives one real terminal and completes World G4 task");
            check(Arrays.equals(frozenDisk,Files.readAllBytes(accountFile)),
                "real disk remains original ACTIVE after B's completion");

            // Unlike G18.6.3, C's stale codec-valid ACTIVE state is
            // obtained from an actual FilePlayerRepository.load +
            // PlayerSnapshotCodec.applyValidated, not direct namespace
            // injection. A -> B -> C all occur in SAME open World.
            check(world.unregisterPlayer(ownerB,genB),
                "B detached after completing G4");
            ownerC=new WorldPlayer();
            Optional<PlayerSnapshot> diskForC=files.load(USER);
            check(diskForC.isPresent(),"C loads real old account file");
            PlayerSnapshotCodec.applyValidated(diskForC.get(),ownerC);
            check(originalActive.equals(
                    ownerC.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE)),
                "C inherited exact VALID ACTIVE bytes from physical disk");
            genC=world.registerPlayer(ownerC,USER);
            LocalLabSlayerRuntime.StatusSnapshot rejected=
                slayer.status(USER);
            check(rejected!=null&&
                    "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                        rejected.persistenceError)&&
                    rejected.task==null&&
                    dmm.snapshot(USER).objective.progress==1L,
                "G4 rejects real loaded ACTIVE file against completed task");

            world.events().runDue(
                FIRST_TICK+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "real G1 due-time respawn to C");
            EntityId secondId=session.spawnedNpcIds.get(0);
            WorldNpc second=world.npcs().byId(secondId);
            MonsterSpawnerPvmRuntime.Snapshot secondOwned=
                installed.runtime().get(secondId);
            check(second!=null&&
                    !secondId.equals(first.id)&&
                    secondOwned!=null&&
                    secondOwned.recipientOrigin!=null&&
                    secondOwned.recipientOrigin.player==ownerC&&
                    secondOwned.recipientOrigin.generation==genC,
                "different real NPC captures exact C generation");

            check(world.npcLifecycle().applyDamage(
                    second.id,99,FIRST_TICK+
                        G1MonsterSpawnerPvmLoopService
                            .RESPAWN_DELAY_TICKS+1L).newlyDied,
                "second independent canonical NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult settledC=
                installed.runtime().finalizeIfOwned(second);
            LocalLabSlayerRuntime.StatusSnapshot stillRejected=
                slayer.status(USER);
            check(settledC!=null&&
                    settledC.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    settledC.settlement!=null&&
                    settledC.settlement.groundItems.size()==1&&
                    settledC.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    stillRejected!=null&&
                    "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                        stillRejected.persistenceError)&&
                    stillRejected.task==null&&
                    records.snapshot(ownerA).kills==0L&&
                    records.snapshot(ownerB).kills==1L&&
                    records.snapshot(ownerC).kills==1L&&
                    records.receiptCount()==2&&
                    dmm.snapshot(USER).objective.progress==2L&&
                    originalActive.equals(
                        ownerC.snapshotExtensions().namespace(
                            LocalLabSlayerPersistence.NAMESPACE))&&
                    LocalLabSlayerPersistence.decode(
                        ownerB.snapshotExtensions().namespace(
                            LocalLabSlayerPersistence.NAMESPACE))
                        .state==LocalLabSlayerPersistence.State.COMPLETED&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==2&&
                    installed.runtime().get(first.id)==null&&
                    installed.runtime().get(second.id)==null,
                "C G4 remains refused while two G1/DMM events settle");
            check(Arrays.equals(frozenDisk,Files.readAllBytes(accountFile)),
                "disk still EXACT original ACTIVE after BOTH real terminals");

            System.out.println(
                "G1868_DISK_STALE_VALID_ACTIVE_G4_COLLISION_ISOLATION_PASS"+
                " sourceRealFileValidActive=true"+
                " originalSpawnOriginA=true"+
                " realTerminalBCompletedG4=true"+
                " diskRemainsActiveBeforeAndAfter=true"+
                " playerCLoadedViaValidatedDiskCodec=true"+
                " G4ValidStateCollidesWithCompletedWorld=true"+
                " realRespawnBoundToC=true"+
                " G4SoftFailClosedButG1DmmAdvance=true"+
                " twoUniqueRealNpcDeathsAndGroundSettlements=true"+
                " noOriginalServerPolicyClaim=true"+
                " noTcpLoginOrAsyncFinalSave=true"+
                " productionCodeChanged=false");
        }finally{
            if(ownerC!=null&&genC>=0L&&
                    world.players().owns(ownerC,genC))
                world.unregisterPlayer(ownerC,genC);
            if(ownerB!=null&&genB>=0L&&
                    world.players().owns(ownerB,genB))
                world.unregisterPlayer(ownerB,genB);
            if(genA>=0L&&world.players().owns(ownerA,genA))
                world.unregisterPlayer(ownerA,genA);
            world.close();
            if(Files.exists(dir)){
                try(Stream<Path> paths=Files.walk(dir)){
                    for(Path p:paths.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))
                        Files.deleteIfExists(p);
                }
            }
        }
    }

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G18.6.8 "+reason);
    }

    private G1868SlayerStaleFileCollisionTerminalIsolationTest(){}
}
