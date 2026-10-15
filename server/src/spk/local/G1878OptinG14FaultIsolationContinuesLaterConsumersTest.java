package spk.local;

import java.util.Collections;

/**
 * G18.7.8 opt-in LOCAL LAB terminal stage fault containment.
 * A test-only malformed G14 persists, but independent G15/G16/G18
 * callbacks continue after two distinct settled NPC deaths.
 * G1/ground are single-commit. Reports are diagnostic, not ACK/replay.
 */
public final class G1878OptinG14FaultIsolationContinuesLaterConsumersTest {
    private static final String USER="g1878-optin-g14-isolation";
    private static final long DEATH_TICK=1301L;

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,USER);
            LocalLabSlayerRuntime slayer=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult started=
                slayer.startMonsterHunter(USER,world.clock().tick());
            check(started!=null&&started.created&&
                    started.status.active()&&
                    started.status.task.objective.progress==0L,
                "genuine active G4 Monster Hunter task");

            LocalLabTaskScrollRuntime scrolls=world.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult scroll=
                scrolls.assign(USER,world.clock().tick());
            check(scroll!=null&&scroll.assignedNow&&
                    scroll.task.objective.progress==0L,
                "genuine G15 Task Scroll pre-activation");

            LocalLabAdventureRuntime adventures=world.localLabAdventures();
            LocalLabAdventureRuntime.ActivationResult adventure=
                adventures.activate(USER);
            check(adventure!=null&&adventure.activatedNow&&
                    adventures.objective(USER)!=null&&
                    adventures.objective(USER).progress==0L,
                "genuine G16 Adventure pre-activation");

            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult activated=
                dmm.activateEasyIfCurrent(USER,player,generation);
            check(activated!=null&&activated.activatedNow&&
                    activated.snapshot.objective.progress==0L,
                "genuine G18 DMM pre-activation");

            // G14 has not been called for this WorldPlayer yet.
            // Corrupt ONLY a test-owned namespace before G14's first
            // installed terminal callback, with no production hook.
            player.snapshotExtensions().replaceNamespace(
                LocalLabDailyChallengePersistence.NAMESPACE,
                Collections.singletonMap(
                    "version","invalid-g1878-test-only"));
            check(player.snapshotExtensions().namespace(
                    LocalLabDailyChallengePersistence.NAMESPACE)
                    .size()==1,
                "only invalid fixture G14 extension injected");

            LocalLabTerminalStageIsolation isolation=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world,isolation);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult firstSpawn=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc first=firstSpawn.spawn.combat.spawn.npc;
            check(firstSpawn.runtime.recipientOrigin!=null&&
                    firstSpawn.runtime.recipientOrigin.player==player&&
                    firstSpawn.runtime.recipientOrigin.generation==generation&&
                    world.npcLifecycle().applyDamage(
                        first.id,99,DEATH_TICK).newlyDied,
                "first actual canonical NPC death");

            // G14's IllegalStateException is caught by the ACTUAL
            // MonsterSpawnerPvmRuntime terminal-observer boundary
            // after G1/G4. Therefore FINALIZED returns, but there is
            // no consumer outbox for G15/G16/G18.
            MonsterSpawnerPvmRuntime.FinalizeResult terminal1=
                installed.runtime().finalizeIfOwned(first);
            check(isolation.history().size()==1&&
                    isolation.history().get(0).npcId.equals(first.id)&&
                    isolation.history().get(0).outcome(
                        LocalLabTerminalStageIsolation.Stage.G4)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                    isolation.history().get(0).outcome(
                        LocalLabTerminalStageIsolation.Stage.G14)==
                        LocalLabTerminalStageIsolation.Outcome.THREW&&
                    isolation.history().get(0).outcome(
                        LocalLabTerminalStageIsolation.Stage.G15)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                    isolation.history().get(0).outcome(
                        LocalLabTerminalStageIsolation.Stage.G16)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                    isolation.history().get(0).outcome(
                        LocalLabTerminalStageIsolation.Stage.G18)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                    isolation.history().get(0).spawnOriginMatchedAtDispatch,
                "first terminal isolates G14 fault and reaches later stages");
            PvmRecordService records=world.pvmRecords();
            LocalLabSlayerRuntime.StatusSnapshot g4First=
                slayer.status(USER);
            check(terminal1!=null&&
                    terminal1.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal1.settlement!=null&&
                    terminal1.settlement.groundItems.size()==1&&
                    terminal1.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    g4First!=null&&g4First.complete()&&
                    g4First.completions==1L&&
                    g4First.task.objective.progress==1L&&
                    records.snapshot(player).kills==1L&&
                    records.receiptCount()==1&&
                    scrolls.active(USER).objective.progress==1L&&
                    adventures.objective(USER).progress==1L&&
                    dmm.snapshot(USER).objective.progress==1L,
                "first real terminal G1+G4 applied but G15/G16/G18 skipped");

            String firstG14Error=null;
            try{
                world.localLabDailyChallenges().status(USER);
            }catch(IllegalStateException expected){
                firstG14Error=expected.getMessage();
            }
            check(firstG14Error!=null&&
                    firstG14Error.contains(
                        "Daily Challenge persistence invalid")&&
                    player.snapshotExtensions().namespace(
                        LocalLabDailyChallengePersistence.NAMESPACE)
                        .size()==1,
                "G14 invalid state still latched after first terminal");

            // G1 schedules a truly NEW NPC after the first finalization.
            world.events().runDue(
                DEATH_TICK+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "real G1 delayed respawn remains available");
            EntityId nextId=session.spawnedNpcIds.get(0);
            WorldNpc second=world.npcs().byId(nextId);
            MonsterSpawnerPvmRuntime.Snapshot secondOrigin=
                installed.runtime().get(nextId);
            check(second!=null&&!nextId.equals(first.id)&&
                    secondOrigin!=null&&
                    secondOrigin.recipientOrigin!=null&&
                    secondOrigin.recipientOrigin.player==player&&
                    secondOrigin.recipientOrigin.generation==generation&&
                    world.npcLifecycle().applyDamage(
                        nextId,99,DEATH_TICK+
                            G1MonsterSpawnerPvmLoopService
                                .RESPAWN_DELAY_TICKS+1L).newlyDied,
                "second distinct canonical NPC death");

            MonsterSpawnerPvmRuntime.FinalizeResult terminal2=
                installed.runtime().finalizeIfOwned(second);
            check(isolation.history().size()==2&&
                    isolation.history().get(1).npcId.equals(second.id)&&
                    isolation.history().get(1).outcome(
                        LocalLabTerminalStageIsolation.Stage.G14)==
                        LocalLabTerminalStageIsolation.Outcome.THREW&&
                    isolation.history().get(1).outcome(
                        LocalLabTerminalStageIsolation.Stage.G15)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                    isolation.history().get(1).outcome(
                        LocalLabTerminalStageIsolation.Stage.G16)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                    isolation.history().get(1).outcome(
                        LocalLabTerminalStageIsolation.Stage.G18)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED,
                "second genuine NPC preserves isolated stage sequence");
            check(installed.runtime().finalizeIfOwned(first).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    isolation.history().size()==2,
                "old finalized event cannot rerun or add duplicate report");
            LocalLabSlayerRuntime.StatusSnapshot g4Final=
                slayer.status(USER);
            check(terminal2!=null&&
                    terminal2.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal2.settlement!=null&&
                    terminal2.settlement.groundItems.size()==1&&
                    terminal2.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    records.snapshot(player).kills==2L&&
                    records.receiptCount()==2&&
                    g4Final!=null&&g4Final.complete()&&
                    g4Final.completions==1L&&
                    scrolls.active(USER).objective.progress==2L&&
                    adventures.objective(USER).progress==2L&&
                    dmm.snapshot(USER).objective.progress==2L&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==2&&
                    installed.runtime().get(first.id)==null&&
                    installed.runtime().get(second.id)==null,
                "second real terminal G1 receipt+coin but G14 still "+
                "prevents later consumers and cannot be replayed");

            String secondG14Error=null;
            try{
                world.localLabDailyChallenges().status(USER);
            }catch(IllegalStateException expected){
                secondG14Error=expected.getMessage();
            }
            check(secondG14Error!=null&&
                    secondG14Error.equals(firstG14Error),
                "G14 invalid persistence remains same-generation sticky");

            System.out.println(
                "G1878_G14_INVALID_STAGE_ISOLATED_G15_G16_G18_PASS"+
                " optInExplicit=true"+
                " g1ReceiptsTwo=true"+
                " g14FailedTwice=true"+
                " g15G16G18ProgressTwo=true"+
                " settledCoinsTwo=true"+
                " diagnosticReportsTwo=true"+
                " duplicateFinalizationVeto=true"+
                " durableOutbox=false"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void check(boolean accepted,String detail){
        if(!accepted)throw new AssertionError("G18.7.8 "+detail);
    }

    private G1878OptinG14FaultIsolationContinuesLaterConsumersTest(){}
}
