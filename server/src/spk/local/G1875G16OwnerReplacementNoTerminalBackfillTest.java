package spk.local;

import java.util.Collections;

/**
 * G18.7.5: an invalid TEST-OWNED G16 Adventure snapshot throws
 * after real installed terminal G1, G4, G14 and G15 progression.
 * The subsequent G18 Daily Money Making objective loses the
 * already-finalized event. Two different NPC deaths are exercised,
 * without re-delivering either terminal or changing production code.
 *
 * CUSTOM_LOCALLAB-only; no inference about original SpawnPK policy,
 * normal authenticated account files or remote payload reachability.
 */
public final class G1875G16OwnerReplacementNoTerminalBackfillTest {
    private static final String USER="g1875-g16-new-owner-no-backfill";
    private static final long FIRST_DEATH_TICK=1501L;

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer owner=new WorldPlayer();
        WorldPlayer cleanReplacement=null;
        long generation=-1L;
        long replacementGeneration=-1L;
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
                    "version","invalid-g1875-test-only"));
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

            // The prior two NPCs have already been finalized, with
            // canonical receipt and coin settlement. Do not re-run
            // their terminal observers or backfill their G18 credit.
            check(world.unregisterPlayer(owner,generation),
                "malformed owner detached after two finalizations");
            cleanReplacement=new WorldPlayer();
            replacementGeneration=world.registerPlayer(
                cleanReplacement,USER);
            LocalLabAdventureRuntime.ActivationResult validAdventure=
                world.localLabAdventures().activate(USER);
            check(validAdventure!=null&&
                    validAdventure.activatedNow&&
                    world.localLabAdventures().objective(USER).progress==0L,
                "clean replacement can start valid G16 Adventure");
            LocalLabDailyChallengeRuntime.StatusResult renewedDaily=
                g14.status(USER);
            System.out.println(
                "G1875_PRE_THIRD_DIAGNOSTIC"+
                " dailyAssigned="+(renewedDaily!=null&&renewedDaily.assignedNow)+
                " dailyProgress="+(renewedDaily==null?-1L:renewedDaily.challenge.current)+
                " g15Progress="+(g15.active(USER)==null?-1L:
                    g15.active(USER).objective.progress)+
                " g18Progress="+g18.snapshot(USER).objective.progress+
                " receiptCount="+records.receiptCount()+
                " groundEntries="+world.groundItems().size()+
                " groundCoinQuantity="+world.groundItems().snapshot().stream()
                    .filter(item->item.itemId==995&&
                        USER.equals(item.owner))
                    .mapToInt(item->item.amount).sum()
            );
            check(renewedDaily!=null&&
                    !renewedDaily.assignedNow&&
                    renewedDaily.challenge.current==2L&&
                    g15.active(USER)!=null&&
                    g15.active(USER).objective.progress==2L&&
                    g18.snapshot(USER).objective.progress==0L&&
                    records.receiptCount()==2&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==2,
                "G14 retains World/name progress2, G18 has no backfill"+
                " despite G1 receipts2");

            // Only a genuinely NEW G1 due-time respawn can provide a
            // new consumer event for the clean WorldPlayer. This is
            // a THIRD distinct NPC, not replaying A or B.
            long thirdDue=FIRST_DEATH_TICK+
                2L*G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS+1L;
            world.events().runDue(thirdDue);
            MonsterSpawnerService.SessionSnapshot thirdSession=
                spawner.getSession(USER);
            check(thirdSession!=null&&
                    thirdSession.spawnedNpcIds.size()==1,
                "third canonical respawn after second finalization");
            EntityId thirdId=thirdSession.spawnedNpcIds.get(0);
            WorldNpc npcC=world.npcs().byId(thirdId);
            MonsterSpawnerPvmRuntime.Snapshot originC=
                installed.runtime().get(thirdId);
            check(npcC!=null&&
                    !thirdId.equals(npcA.id)&&
                    !thirdId.equals(npcB.id)&&
                    originC!=null&&originC.recipientOrigin!=null&&
                    originC.recipientOrigin.player==cleanReplacement&&
                    originC.recipientOrigin.generation==
                        replacementGeneration&&
                    world.npcLifecycle().applyDamage(
                        thirdId,99,thirdDue+1L).newlyDied,
                "third genuinely separate NPC belongs to clean owner");
            MonsterSpawnerPvmRuntime.FinalizeResult terminalC=
                installed.runtime().finalizeIfOwned(npcC);
            check(terminalC!=null&&
                    terminalC.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminalC.settlement!=null&&
                    terminalC.settlement.groundItems.size()==1&&
                    terminalC.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    records.snapshot(owner).kills==2L&&
                    records.snapshot(cleanReplacement).kills==1L&&
                    records.receiptCount()==3&&
                    world.localLabAdventures().objective(USER).progress==1L&&
                    g14.status(USER).challenge.current==3L&&
                    g15.active(USER).objective.progress==3L&&
                    g18.snapshot(USER).objective.progress==1L&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==3&&
                    installed.runtime().get(npcA.id)==null&&
                    installed.runtime().get(npcB.id)==null&&
                    installed.runtime().get(npcC.id)==null,
                "third new event credited once, prior two missed G18"+
                " events never backfilled");

            System.out.println(
                "G1875_G16_OWNER_REPLACEMENT_NO_TERMINAL_BACKFILL_PASS"+
                " actualInstalledObserver=true"+
                " twoGenuineIndependentNpcDeaths=true"+
                " g1ReceiptsThree=true"+
                " g4CompletionOne=true"+
                " g14NameOwnedProgressTwoThenThree=true"+
                " g15OriginalTwoAndNewThird=true"+
                " g16InvalidCacheSticky=true"+
                " g18ZeroBeforeThirdAndOneAfter=true"+
                " threeOneCoinGroundSettlements=true"+
                " allThreeTerminalsFinalized=true"+
                " noOldNpcTerminalReplayOrBackfill=true"+
                " testOnlyBadSnapshot=true"+
                " originalServerPolicyClaim=false"+
                " productionCodeChanged=false");
        }finally{
            if(cleanReplacement!=null&&replacementGeneration>=0L&&
                    world.players().owns(cleanReplacement,replacementGeneration))
                world.unregisterPlayer(cleanReplacement,replacementGeneration);
            if(generation>=0L&&world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            world.close();
        }
    }

    private static void check(boolean accepted,String reason){
        if(!accepted)throw new AssertionError("G18.7.5 "+reason);
    }

    private G1875G16OwnerReplacementNoTerminalBackfillTest(){}
}
