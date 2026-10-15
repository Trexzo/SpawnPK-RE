package spk.local;

import java.util.Collections;

/**
 * G18.7.3: installed terminal observer has a sequential failure
 * boundary AFTER valid G14 Daily Challenge delivery: malformed,
 * TEST-OWNED G15 Task Scroll persistence refuses and throws,
 * preventing activated G16 Adventure and World-owned G18 Daily
 * Money Making from observing two genuine settled NPC deaths.
 *
 * No production observer substitution, NPC death replay, reward
 * grants, remote persistence input or original SpawnPK policy.
 */
public final class G1873G15InvalidPersistenceTerminalConsumerIsolationTest {
    private static final String USER="g1873-g15-consumer-failure";
    private static final long FIRST_DEATH=1401L;

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer invalidOwner=null;
        WorldPlayer blankObserver=null;
        long originalGeneration=-1L;
        long invalidGeneration=-1L;
        long observerGeneration=-1L;
        try{
            originalGeneration=world.registerPlayer(original,USER);

            LocalLabSlayerRuntime g4=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult hunter=
                g4.startMonsterHunter(
                    USER,world.clock().tick());
            check(hunter!=null&&hunter.created&&
                    hunter.status.active()&&
                    hunter.status.task.objective.progress==0L,
                "A starts legitimate G4 World task");

            LocalLabTaskScrollRuntime g15=world.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult taskScroll=
                g15.assign(USER,world.clock().tick());
            check(taskScroll!=null&&taskScroll.assignedNow&&
                    taskScroll.task.objective.progress==0L&&
                    g15.assignmentCount()==1,
                "A assigns real G15 Task Scroll World task");

            LocalLabDailyMoneyMakingRuntime g18=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult dmm=
                g18.activateEasyIfCurrent(
                    USER,original,originalGeneration);
            check(dmm!=null&&dmm.activatedNow&&
                    dmm.snapshot.objective.progress==0L,
                "A activates World-owned G18 EASY objective");

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
                    firstSpawn.runtime.recipientOrigin.generation==
                        originalGeneration,
                "genuine canonical NPC captures immutable original A");

            check(world.unregisterPlayer(original,originalGeneration),
                "original A unregisters before NPC death");
            invalidOwner=new WorldPlayer();
            invalidGeneration=world.registerPlayer(invalidOwner,USER);

            // B has a blank G4 extension so the legitimate World
            // Monster Hunter task remains visible and can complete.
            // G14 is explicitly assigned and G16 explicitly activated
            // as B, proving that both are healthy, eligible consumers.
            LocalLabDailyChallengeRuntime g14=
                world.localLabDailyChallenges();
            LocalLabDailyChallengeRuntime.StatusResult challenge=
                g14.status(USER);
            check(challenge!=null&&challenge.assignedNow&&
                    challenge.challenge.current==0L,
                "B genuinely activates valid G14 Daily Challenge");
            LocalLabAdventureRuntime g16=
                world.localLabAdventures();
            LocalLabAdventureRuntime.ActivationResult adventure=
                g16.activate(USER);
            check(adventure!=null&&adventure.activatedNow&&
                    g16.objective(USER)!=null&&
                    g16.objective(USER).progress==0L,
                "B genuinely activates G16 Adventure");
            check(g4.status(USER).active()&&
                    g15.assignmentCount()==1&&
                    g18.snapshot(USER).objective.progress==0L,
                "G4/G15/G18 World-owned tasks remain valid for B");

            // B's G15 has NOT yet hydrated. Only its test-managed
            // namespace is malformed; G14 and G16 are valid.
            invalidOwner.snapshotExtensions().replaceNamespace(
                LocalLabTaskScrollPersistence.NAMESPACE,
                Collections.singletonMap(
                    "version","invalid-g1873-test-only"));

            check(world.npcLifecycle().applyDamage(
                    npcA.id,99,FIRST_DEATH).newlyDied,
                "first actual canonical NPC dies under B");
            MonsterSpawnerPvmRuntime.FinalizeResult first=
                installed.runtime().finalizeIfOwned(npcA);
            PvmRecordService g1=world.pvmRecords();
            check(first!=null&&
                    first.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    first.settlement!=null&&
                    first.settlement.groundItems.size()==1&&
                    first.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    g1.snapshot(invalidOwner).kills==1L&&
                    g1.receiptCount()==1&&
                    g4.status(USER).complete()&&
                    g4.status(USER).completions==1L&&
                    g14.status(USER).challenge.current==1L&&
                    g16.objective(USER).progress==0L&&
                    g18.snapshot(USER).objective.progress==0L,
                "first real terminal reaches G1/G4/G14 before invalid G15"+
                " blocks G16/G18");

            String firstError=null;
            try{
                g15.active(USER);
            }catch(IllegalStateException expected){
                firstError=expected.getMessage();
            }
            check(firstError!=null&&
                    firstError.contains("Task Scroll persistence invalid")&&
                    g15.assignmentCount()==1,
                "G15 malformed persistence remains cached on B");

            world.events().runDue(
                FIRST_DEATH+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "real G1 delayed respawn available despite G15 failure");

            EntityId nextId=session.spawnedNpcIds.get(0);
            WorldNpc npcB=world.npcs().byId(nextId);
            MonsterSpawnerPvmRuntime.Snapshot originB=
                installed.runtime().get(nextId);
            check(npcB!=null&&!nextId.equals(npcA.id)&&
                    originB!=null&&originB.recipientOrigin!=null&&
                    originB.recipientOrigin.player==invalidOwner&&
                    originB.recipientOrigin.generation==invalidGeneration&&
                    world.npcLifecycle().applyDamage(
                        nextId,99,FIRST_DEATH+
                            G1MonsterSpawnerPvmLoopService
                                .RESPAWN_DELAY_TICKS+1L).newlyDied,
                "second genuinely distinct canonical NPC dies under B");
            MonsterSpawnerPvmRuntime.FinalizeResult second=
                installed.runtime().finalizeIfOwned(npcB);

            check(second!=null&&
                    second.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    second.settlement!=null&&
                    second.settlement.groundItems.size()==1&&
                    second.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    g1.snapshot(invalidOwner).kills==2L&&
                    g1.receiptCount()==2&&
                    g4.status(USER).complete()&&
                    g4.status(USER).completions==1L&&
                    g14.status(USER).challenge.current==2L&&
                    g16.objective(USER).progress==0L&&
                    g18.snapshot(USER).objective.progress==0L&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==2&&
                    installed.runtime().get(npcA.id)==null&&
                    installed.runtime().get(npcB.id)==null,
                "second real terminal G14 progresses again but G16/G18"+
                " stay skipped after G15 error");

            String secondError=null;
            try{
                g15.active(USER);
            }catch(IllegalStateException expected){
                secondError=expected.getMessage();
            }
            check(firstError.equals(secondError),
                "G15 malformed cache sticky for exact B identity");

            // Do not retry either old NPC: inspect the still-open
            // G15 World assignment via a DIFFERENT blank WorldPlayer.
            check(world.unregisterPlayer(invalidOwner,invalidGeneration),
                "B unregisters after two real events");
            blankObserver=new WorldPlayer();
            observerGeneration=world.registerPlayer(blankObserver,USER);
            TaskScrollService.Snapshot g15World=g15.active(USER);
            check(g15World!=null&&
                    g15World.objective.progress==0L&&
                    g15.assignmentCount()==1&&
                    g14.status(USER).challenge.current==2L&&
                    g18.snapshot(USER).objective.progress==0L,
                "new blank owner can inspect G15 World task still zero,"+
                " G14 two and G18 zero");

            System.out.println(
                "G1873_G15_INVALID_PERSISTENCE_BLOCKS_G16_G18_PASS"+
                " installedObserver=true"+
                " actualTwoDistinctNpcDeaths=true"+
                " g1ReceiptsTwo=true"+
                " g4CompletedOnce=true"+
                " g14RealProgressTwo=true"+
                " g15InvalidCachedOnB=true"+
                " g15WorldProgressZeroVerifiedOnBlankC=true"+
                " g16AdventureSkipped=true"+
                " g18DailyMoneyMakingSkipped=true"+
                " twoCoinSettlements=true"+
                " observerStillReturnsFinalized=true"+
                " noNpcTerminalReplay=true"+
                " originalServerPolicyClaim=false"+
                " productionCodeChanged=false");
        }finally{
            if(blankObserver!=null&&observerGeneration>=0L&&
                    world.players().owns(blankObserver,observerGeneration))
                world.unregisterPlayer(blankObserver,observerGeneration);
            if(invalidOwner!=null&&invalidGeneration>=0L&&
                    world.players().owns(invalidOwner,invalidGeneration))
                world.unregisterPlayer(invalidOwner,invalidGeneration);
            if(originalGeneration>=0L&&
                    world.players().owns(original,originalGeneration))
                world.unregisterPlayer(original,originalGeneration);
            world.close();
        }
    }

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G18.7.3 "+reason);
    }

    private G1873G15InvalidPersistenceTerminalConsumerIsolationTest(){}
}
