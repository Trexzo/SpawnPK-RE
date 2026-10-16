package spk.local;

import java.util.Collections;

/**
 * G18.8.4 — test the REAL public create(World) property boundary, not
 * the separate explicit diagnostic injection overload. The opt-in
 * experiment must be captured at composition time. Flipping the
 * JVM property afterward cannot change an existing World graph.
 *
 * Both cases use genuine NPC death, installed G1, G14 malformed
 * test-only persistence, healthy activated G15/G16/G18 consumers
 * and actual ground drop settlement. No original SpawnPK policy.
 */
public final class G1884TerminalStageIsolationPropertySnapshotTest {
    private static final String USER="g1884-property-composition";
    private static final String PROPERTY=
        LocalLabMonsterSpawnerProvisioning
            .ISOLATE_TERMINAL_STAGES_PROPERTY;

    public static void main(String[] args)throws Exception {
        String prior=System.getProperty(PROPERTY);
        try {
            runCase(false,2901L);
            runCase(true,3001L);
            System.out.println(
                "G1884_TERMINAL_ISOLATION_PROPERTY_COMPOSITION_SNAPSHOT_PASS"+
                " absentAtCreateRetainsSequentialFailure=true"+
                " enabledAtCreateContainsG14Exception=true"+
                " postCreatePropertyFlipIgnoredBothWays=true"+
                " installedObserversActualNpcDeathsTwo=true"+
                " g1ReceiptsEachOne=true"+
                " groundSettlementsEachOne=true"+
                " originalSpawnpkPolicy=false"+
                " durableOutbox=false");
        }finally {
            if(prior==null)
                System.clearProperty(PROPERTY);
            else
                System.setProperty(PROPERTY,prior);
        }
    }

    // G1953/54: independent focused entry point for the actual installed
    // LocalLab observer, restoring the JVM property after its test case.
    static void runObserverDiagnosticCase(
        boolean isolate,
        long deathTick
    )throws Exception{
        String prior=System.getProperty(PROPERTY);
        try{
            runCase(isolate,deathTick);
        }finally{
            if(prior==null)System.clearProperty(PROPERTY);
            else System.setProperty(PROPERTY,prior);
        }
    }

    private static void runCase(
        boolean optInAtComposition,
        long deathTick
    )throws Exception{
        if(optInAtComposition)
            System.setProperty(PROPERTY,"true");
        else
            System.clearProperty(PROPERTY);

        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try {
            generation=world.registerPlayer(player,USER);

            LocalLabTaskScrollRuntime g15=
                world.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult scroll=
                g15.assign(USER,world.clock().tick());
            check(scroll!=null&&scroll.assignedNow&&
                    scroll.task.objective.progress==0L,
                "genuine G15 active objective before terminal");

            LocalLabAdventureRuntime g16=world.localLabAdventures();
            LocalLabAdventureRuntime.ActivationResult adv=
                g16.activate(USER);
            check(adv!=null&&adv.activatedNow&&
                    g16.objective(USER)!=null&&
                    g16.objective(USER).progress==0L,
                "genuine G16 active objective before terminal");

            LocalLabDailyMoneyMakingRuntime g18=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult dmm=
                g18.activateEasyIfCurrent(USER,player,generation);
            check(dmm!=null&&dmm.activatedNow&&
                    g18.snapshot(USER).objective.progress==0L,
                "genuine G18 active objective before terminal");

            // Keep G14 unhydrated before injecting invalid TEST-ONLY
            // state. No TCP or account-file corruption is modeled.
            player.snapshotExtensions().replaceNamespace(
                LocalLabDailyChallengePersistence.NAMESPACE,
                Collections.singletonMap(
                    "version","invalid-g1884-test-only"));

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);

            // Deliberate opposite property post-construction.
            // The existing observer must NEVER live-reconfigure.
            if(optInAtComposition)
                System.clearProperty(PROPERTY);
            else
                System.setProperty(PROPERTY,"true");

            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawn=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=spawn.spawn.combat.spawn.npc;
            check(spawn.runtime.recipientOrigin!=null&&
                    spawn.runtime.recipientOrigin.player==player&&
                    spawn.runtime.recipientOrigin.generation==generation&&
                    world.npcLifecycle().applyDamage(
                        npc.id,99,deathTick).newlyDied,
                "actual NPC captures exact owner and dies");

            MonsterSpawnerPvmRuntime.FinalizeResult finished=
                installed.runtime().finalizeIfOwned(npc);
            int expected=optInAtComposition?1:0;
            check(finished!=null&&
                    finished.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    finished.settlement!=null&&
                    finished.settlement.groundItems.size()==1&&
                    finished.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    world.pvmRecords().receiptCount()==1&&
                    world.pvmRecords().snapshot(player).kills==1L&&
                    g15.active(USER).objective.progress==expected&&
                    g16.objective(USER).progress==expected&&
                    g18.snapshot(USER).objective.progress==expected&&
                    installed.runtime().get(npc.id)==null,
                "post-composition toggle cannot alter delivery optIn="+
                    optInAtComposition);

            String invalid=null;
            try{
                world.localLabDailyChallenges().status(USER);
            }catch(IllegalStateException caught){
                invalid=caught.getMessage();
            }
            check(invalid!=null&&
                    invalid.contains("Daily Challenge persistence invalid"),
                "original malformed TEST-ONLY G14 still fails");

            // Inspect the REAL installed terminal-observer execution
            // after G1 and actual ground settlement. This overall outcome
            // is distinct from per-consumer G14 failure/credit.
            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics observer=
                installed.runtime().terminalObserverDiagnostics();
            check(observer.totalAttempts==1L&&
                    observer.failedAttempts==
                        (optInAtComposition?0L:1L)&&
                    observer.evictedAttempts==0L&&
                    observer.recentAttempts.size()==1,
                "one installed observer attempt after ground commit");
            MonsterSpawnerPvmRuntime.TerminalObserverAttempt attempt=
                observer.recentAttempts.get(0);
            check(attempt.npcId.equals(npc.id)&&
                    attempt.deathTick==deathTick&&
                    USER.equals(attempt.recipientRef)&&
                    attempt.spawnRecipientGeneration==generation&&
                    attempt.outcome==
                        (optInAtComposition
                            ?MonsterSpawnerPvmRuntime
                                .TerminalObserverOutcome.RETURNED
                            :MonsterSpawnerPvmRuntime
                                .TerminalObserverOutcome.THREW)&&
                    (optInAtComposition
                        ?attempt.failureType==null
                        :IllegalStateException.class.getName().equals(
                            attempt.failureType)),
                "overall observer outcome captures isolated vs sequential failure");

            LocalLabTerminalStageIsolation.DiagnosticSnapshot stages=
                installed.terminalDiagnosticSnapshot();
            if(optInAtComposition){
                check(stages!=null&&
                        stages.observedCount==1L&&
                        stages.recentReports.size()==1&&
                        stages.recentReports.get(0).outcome(
                            LocalLabTerminalStageIsolation.Stage.G14)==
                            LocalLabTerminalStageIsolation.Outcome.THREW&&
                        stages.recentReports.get(0).outcome(
                            LocalLabTerminalStageIsolation.Stage.G15)==
                            LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                        stages.recentReports.get(0).outcome(
                            LocalLabTerminalStageIsolation.Stage.G16)==
                            LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                        stages.recentReports.get(0).outcome(
                            LocalLabTerminalStageIsolation.Stage.G18)==
                            LocalLabTerminalStageIsolation.Outcome.RETURNED,
                    "overall RETURNED must not hide failed individual G14");
            }else
                check(stages==null,
                    "sequential mode does not claim isolated stage diagnostics");

            check(installed.runtime().finalizeIfOwned(npc).status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    world.pvmRecords().receiptCount()==1&&
                    installed.runtime().terminalObserverDiagnostics()
                        .totalAttempts==1L,
                "one real canonical terminal and observer no replay");
        }finally {
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError("G18.8.4 "+message);
    }

    private G1884TerminalStageIsolationPropertySnapshotTest(){}
}
