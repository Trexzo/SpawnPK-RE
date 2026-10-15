package spk.local;

import java.util.Collections;

/**
 * G18.7.4: an invalid TEST-OWNED G16 Adventure snapshot throws
 * after real installed terminal G1, G4, G14 and G15 progression.
 * The subsequent G18 Daily Money Making objective loses the
 * already-finalized event. Two different NPC deaths are exercised,
 * without re-delivering either terminal or changing production code.
 *
 * CUSTOM_LOCALLAB-only; no inference about original SpawnPK policy,
 * normal authenticated account files or remote payload reachability.
 */
public final class G1874G16InvalidPersistenceTerminalConsumerIsolationTest {
    private static final String USER="g1874-g16-consumer-failure";
    private static final long FIRST_DEATH_TICK=1501L;

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(owner,USER);

            LocalLabSlayerRuntime g4=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult hunter=
                g4.startMonsterHunter(USER,world.clock().tick());
            check(hunter!=null&&hunter.created&&
                    hunter.status.active()&&
                    hunter.status.task.objective.progress==0L,
                "legitimate G4 Monster Hunter ACTIVE0");

            LocalLabDailyChallengeRuntime g14=
                world.localLabDailyChallenges();
            LocalLabDailyChallengeRuntime.StatusResult daily=
                g14.status(USER);
            check(daily!=null&&daily.assignedNow&&
                    daily.challenge.current==0L,
                "legitimate G14 Daily Challenge ACTIVE0");

            LocalLabTaskScrollRuntime g15=world.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult scroll=
                g15.assign(USER,world.clock().tick());
            check(scroll!=null&&scroll.assignedNow&&
                    scroll.task.objective.progress==0L,
                "legitimate G15 Task Scroll ACTIVE0");

            LocalLabDailyMoneyMakingRuntime g18=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult easy=
                g18.activateEasyIfCurrent(USER,owner,generation);
            check(easy!=null&&easy.activatedNow&&
                    easy.snapshot.objective.progress==0L,
                "legitimate G18 World EASY0");

            // No previous G16 call has hydrated this WorldPlayer.
            // A deliberately invalid, test-only G16 namespace causes
            // the actual installed observer's G16 stage to fail.
            owner.snapshotExtensions().replaceNamespace(
                LocalLabAdventurePersistence.NAMESPACE,
                Collections.singletonMap(
                    "version","invalid-g1874-test-only"));
            check(owner.snapshotExtensions().namespace(
                    LocalLabAdventurePersistence.NAMESPACE)
                    .size()==1,
                "fixture corrupts ONLY G16 persistence before hydration");

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
                    firstSpawn.runtime.recipientOrigin.player==owner&&
                    firstSpawn.runtime.recipientOrigin.generation==
                        generation&&
                    world.npcLifecycle().applyDamage(
                        npcA.id,99,FIRST_DEATH_TICK).newlyDied,
                "first genuine NPC and immutable original owner origin");

            MonsterSpawnerPvmRuntime.FinalizeResult terminalA=
                installed.runtime().finalizeIfOwned(npcA);
            PvmRecordService records=world.pvmRecords();
            check(terminalA!=null&&
                    terminalA.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminalA.settlement!=null&&
                    terminalA.settlement.groundItems.size()==1&&
                    terminalA.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    records.snapshot(owner).kills==1L&&
                    records.receiptCount()==1&&
                    g4.status(USER).complete()&&
                    g4.status(USER).completions==1L&&
                    g14.status(USER).challenge.current==1L&&
                    g15.active(USER).objective.progress==1L&&
                    g18.snapshot(USER).objective.progress==0L,
                "G1/G4/G14/G15 progressed before G16 failure,"+
                " but downstream G18 was skipped");

            String firstFailure=null;
            try{
                world.localLabAdventures().activate(USER);
            }catch(IllegalStateException expected){
                firstFailure=expected.getMessage();
            }
            check(firstFailure!=null&&
                    firstFailure.contains("Adventure persistence invalid")&&
                    owner.snapshotExtensions().namespace(
                        LocalLabAdventurePersistence.NAMESPACE)
                        .size()==1,
                "G16 invalid persistence latches to this exact owner");

            world.events().runDue(
                FIRST_DEATH_TICK+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "G1 delayed respawn survives G16 observer failure");

            EntityId secondId=session.spawnedNpcIds.get(0);
            WorldNpc npcB=world.npcs().byId(secondId);
            MonsterSpawnerPvmRuntime.Snapshot originB=
                installed.runtime().get(secondId);
            check(npcB!=null&&!secondId.equals(npcA.id)&&
                    originB!=null&&originB.recipientOrigin!=null&&
                    originB.recipientOrigin.player==owner&&
                    originB.recipientOrigin.generation==generation&&
                    world.npcLifecycle().applyDamage(
                        secondId,99,FIRST_DEATH_TICK+
                            G1MonsterSpawnerPvmLoopService
                                .RESPAWN_DELAY_TICKS+1L).newlyDied,
                "second distinct genuinely spawned NPC lethal terminal");

            MonsterSpawnerPvmRuntime.FinalizeResult terminalB=
                installed.runtime().finalizeIfOwned(npcB);
            check(terminalB!=null&&
                    terminalB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminalB.settlement!=null&&
                    terminalB.settlement.groundItems.size()==1&&
                    terminalB.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    records.snapshot(owner).kills==2L&&
                    records.receiptCount()==2&&
                    g4.status(USER).complete()&&
                    g4.status(USER).completions==1L&&
                    g14.status(USER).challenge.current==2L&&
                    g15.active(USER).objective.progress==2L&&
                    g18.snapshot(USER).objective.progress==0L&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==2&&
                    installed.runtime().get(npcA.id)==null&&
                    installed.runtime().get(npcB.id)==null,
                "two actual terminal events reach G14/G15 but not G18");

            String secondFailure=null;
            try{
                world.localLabAdventures().activate(USER);
            }catch(IllegalStateException expected){
                secondFailure=expected.getMessage();
            }
            check(firstFailure.equals(secondFailure),
                "G16 same-owner cached invalid persistence remains sticky");

            System.out.println(
                "G1874_G16_INVALID_PERSISTENCE_BLOCKS_G18_PASS"+
                " actualInstalledObserver=true"+
                " twoGenuineIndependentNpcDeaths=true"+
                " g1ReceiptsTwo=true"+
                " g4CompletionOne=true"+
                " g14ProgressTwo=true"+
                " g15ProgressTwo=true"+
                " g16InvalidCacheSticky=true"+
                " g18ActivatedButProgressZero=true"+
                " twoOneCoinGroundSettlements=true"+
                " bothTerminalsFinalized=true"+
                " noOldNpcTerminalReplay=true"+
                " testOnlyBadSnapshot=true"+
                " originalServerPolicyClaim=false"+
                " productionCodeChanged=false");
        }finally{
            if(generation>=0L&&world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            world.close();
        }
    }

    private static void check(boolean accepted,String reason){
        if(!accepted)throw new AssertionError("G18.7.4 "+reason);
    }

    private G1874G16InvalidPersistenceTerminalConsumerIsolationTest(){}
}
