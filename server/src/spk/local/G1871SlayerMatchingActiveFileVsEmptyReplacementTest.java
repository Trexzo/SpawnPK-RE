package spk.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Stream;

/**
 * G18.7.1: same-World username-held ACTIVE G4 task plus a valid,
 * CURRENT, matching ACTIVE account file. A same-name replacement
 * with matching persisted bytes is denied G4 progression. A later
 * blank-extension replacement is not denied and may complete the
 * SAME existing World task on its own genuinely distinct NPC kill.
 *
 * This characterizes CUSTOM_LOCALLAB only. Disk writes are explicit
 * test-owned FilePlayerRepository calls, not an authenticated TCP
 * session, production final save, original SpawnPK reward policy
 * or a same-terminal replay.
 */
public final class G1871SlayerMatchingActiveFileVsEmptyReplacementTest {
    private static final String USER="g1871-matching-active-file";
    private static final long FIRST_DEATH=91L;

    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("g1871-active-file-");
        Path file=root.resolve(USER+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(
            name->root.resolve(name+".properties"));
        World world=World.isolatedForTest(60_000L,repository);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer loaded=null,empty=null;
        long ga=-1L,gb=-1L,gc=-1L;
        try {
            ga=world.registerPlayer(original,USER);
            LocalLabSlayerRuntime g4=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult assigned=
                g4.startMonsterHunter(USER,77L);
            check(assigned!=null&&assigned.created&&
                    assigned.status.active()&&
                    assigned.status.task!=null&&
                    assigned.status.task.objective.progress==0L,
                "A legitimately starts active Monster Hunter");
            SortedMap<String,String> activeA=
                original.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE);
            LocalLabSlayerPersistence.Snapshot decoded=
                LocalLabSlayerPersistence.decode(activeA);
            check(decoded!=null&&
                    decoded.state==LocalLabSlayerPersistence.State.ACTIVE&&
                    decoded.progress==0L&&
                    decoded.completions==0L&&
                    decoded.sourceAssignedTick==77L,
                "A persists valid ACTIVE77");

            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            check(dmm.activateEasyIfCurrent(USER,original,ga)
                    .activatedNow,
                "A starts G18 World-owned DMM");
            // CURRENT persisted ACTIVE, deliberately not stale:
            // A has not completed a task or received a kill yet.
            repository.save(PlayerSnapshotCodec.capture(USER,original));
            check(Files.exists(file),"real matching ACTIVE file saved");
            byte[] frozenDisk=Files.readAllBytes(file);

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult firstSpawn=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npcA=firstSpawn.spawn.combat.spawn.npc;
            check(firstSpawn.runtime.recipientOrigin!=null&&
                    firstSpawn.runtime.recipientOrigin.player==original&&
                    firstSpawn.runtime.recipientOrigin.generation==ga,
                "NPC immutable spawn origin captures A");

            check(world.unregisterPlayer(original,ga),
                "A unregisters with active World task");
            loaded=new WorldPlayer();
            Optional<PlayerSnapshot> currentFile=repository.load(USER);
            check(currentFile.isPresent(),"B loads real current file");
            PlayerSnapshotCodec.applyValidated(
                currentFile.get(),loaded);
            check(activeA.equals(
                    loaded.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE)),
                "B owns exact matching codec-valid ACTIVE77 bytes");
            gb=world.registerPlayer(loaded,USER);
            LocalLabSlayerRuntime.StatusSnapshot bRefused=
                g4.status(USER);
            SlayerTaskService.Snapshot activeWorld=
                g4.slayer().active(USER);
            check(bRefused!=null&&
                    "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                        bRefused.persistenceError)&&
                    bRefused.task==null&&
                    activeWorld!=null&&
                    activeWorld.taskId.equals(
                        assigned.status.task.taskId)&&
                    activeWorld.objective.progress==0L,
                "matching file B denied while World task is still ACTIVE0");

            check(world.npcLifecycle().applyDamage(
                    npcA.id,99,FIRST_DEATH).newlyDied,
                "first real A-origin NPC dies with B current");
            MonsterSpawnerPvmRuntime.FinalizeResult terminalB=
                installed.runtime().finalizeIfOwned(npcA);
            PvmRecordService g1=world.pvmRecords();
            LocalLabSlayerRuntime.StatusSnapshot afterB=
                g4.status(USER);
            SlayerTaskService.Snapshot stillActive=
                g4.slayer().active(USER);
            check(terminalB!=null&&
                    terminalB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminalB.settlement!=null&&
                    terminalB.settlement.groundItems.size()==1&&
                    terminalB.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    afterB!=null&&
                    "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME".equals(
                        afterB.persistenceError)&&
                    afterB.task==null&&
                    stillActive!=null&&
                    stillActive.objective.progress==0L&&
                    activeA.equals(
                        loaded.snapshotExtensions().namespace(
                            LocalLabSlayerPersistence.NAMESPACE))&&
                    g1.snapshot(original).kills==0L&&
                    g1.snapshot(loaded).kills==1L&&
                    g1.receiptCount()==1&&
                    dmm.snapshot(USER).objective.progress==1L&&
                    Arrays.equals(frozenDisk,Files.readAllBytes(file)),
                "B G4 refused but G1/G18/ground progress, G4 remains ACTIVE0");

            // A third player C has EMPTY G4 extension but same name.
            // Unlike B's perfectly matching state, this passes the
            // absence-of-extension hydration path and sees the task.
            check(world.unregisterPlayer(loaded,gb),
                "B leaves after real first terminal");
            empty=new WorldPlayer();
            gc=world.registerPlayer(empty,USER);
            check(empty.snapshotExtensions().namespace(
                    LocalLabSlayerPersistence.NAMESPACE).isEmpty(),
                "C starts with intentionally empty G4 extension");
            LocalLabSlayerRuntime.StatusSnapshot exposed=
                g4.status(USER);
            check(exposed!=null&&exposed.persistenceError==null&&
                    exposed.active()&&exposed.task!=null&&
                    exposed.task.taskId.equals(
                        assigned.status.task.taskId)&&
                    exposed.task.objective.progress==0L,
                "empty C can see SAME ACTIVE World task B was denied");

            world.events().runDue(
                FIRST_DEATH+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "G1 real due-time respawn for C");
            EntityId nextId=session.spawnedNpcIds.get(0);
            WorldNpc npcC=world.npcs().byId(nextId);
            MonsterSpawnerPvmRuntime.Snapshot nextOrigin=
                installed.runtime().get(nextId);
            check(npcC!=null&&!nextId.equals(npcA.id)&&
                    nextOrigin!=null&&
                    nextOrigin.recipientOrigin!=null&&
                    nextOrigin.recipientOrigin.player==empty&&
                    nextOrigin.recipientOrigin.generation==gc&&
                    world.npcLifecycle().applyDamage(
                        nextId,99,FIRST_DEATH+
                            G1MonsterSpawnerPvmLoopService
                                .RESPAWN_DELAY_TICKS+1L).newlyDied,
                "distinct C-origin NPC genuinely dies");
            MonsterSpawnerPvmRuntime.FinalizeResult terminalC=
                installed.runtime().finalizeIfOwned(npcC);
            LocalLabSlayerRuntime.StatusSnapshot afterC=
                g4.status(USER);
            LocalLabSlayerPersistence.Snapshot cCompleted=
                LocalLabSlayerPersistence.decode(
                    empty.snapshotExtensions().namespace(
                        LocalLabSlayerPersistence.NAMESPACE));
            check(terminalC!=null&&
                    terminalC.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminalC.settlement!=null&&
                    terminalC.settlement.groundItems.size()==1&&
                    terminalC.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    afterC!=null&&afterC.persistenceError==null&&
                    afterC.complete()&&afterC.completions==1L&&
                    afterC.task!=null&&
                    afterC.task.taskId.equals(
                        assigned.status.task.taskId)&&
                    cCompleted!=null&&
                    cCompleted.state==
                        LocalLabSlayerPersistence.State.COMPLETED&&
                    cCompleted.progress==1L&&
                    cCompleted.completions==1L&&
                    activeA.equals(
                        loaded.snapshotExtensions().namespace(
                            LocalLabSlayerPersistence.NAMESPACE))&&
                    g1.snapshot(loaded).kills==1L&&
                    g1.snapshot(empty).kills==1L&&
                    g1.receiptCount()==2&&
                    dmm.snapshot(USER).objective.progress==2L&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==2&&
                    Arrays.equals(frozenDisk,Files.readAllBytes(file))&&
                    installed.runtime().get(npcA.id)==null&&
                    installed.runtime().get(npcC.id)==null,
                "C completes old G4 task on separate death while B "+
                "matching file stays rejected and disk unchanged");

            System.out.println(
                "G1871_MATCHING_ACTIVE_FILE_BLOCKS_G4_BUT_EMPTY_OWNER_COMPLETES_PASS"+
                " currentFileMatchesActiveWorldTask=true"+
                " BMatchingHydrationRefused=true"+
                " actualFirstG1DmmTerminalWithoutG4Credit=true"+
                " WorldTaskStillActiveAfterFirstDeath=true"+
                " CEmptyExtensionCanAdoptSameTask=true"+
                " secondDistinctNpcCompletesTaskForC=true"+
                " G1ReceiptPerCanonicalNpc=true"+
                " oneGroundSettlementPerNpc=true"+
                " originalActiveDiskUnmodified=true"+
                " originalServerPolicyClaim=false"+
                " tcpLogoutOrAsyncSaveTested=false"+
                " productionCodeChanged=false");
        }finally{
            if(empty!=null&&gc>=0L&&world.players().owns(empty,gc))
                world.unregisterPlayer(empty,gc);
            if(loaded!=null&&gb>=0L&&world.players().owns(loaded,gb))
                world.unregisterPlayer(loaded,gb);
            if(ga>=0L&&world.players().owns(original,ga))
                world.unregisterPlayer(original,ga);
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

    private static void check(boolean valid,String label){
        if(!valid)throw new AssertionError("G18.7.1 "+label);
    }

    private G1871SlayerMatchingActiveFileVsEmptyReplacementTest(){}
}
