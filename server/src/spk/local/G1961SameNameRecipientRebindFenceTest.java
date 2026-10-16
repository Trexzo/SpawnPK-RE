package spk.local;

/**
 * G19.6.1/62: a newly registered WorldPlayer using the old recipient's
 * username cannot inherit a canonical NPC terminal event spawned under
 * the retired player object/generation.
 */
public final class G1961SameNameRecipientRebindFenceTest {
    static void run(boolean isolated)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer oldPlayer=new WorldPlayer();
        WorldPlayer newPlayer=new WorldPlayer();
        String username=isolated
            ?"g1962-rebound-recipient":"g1961-rebound-recipient";
        long oldGeneration=-1L;
        long newGeneration=-1L;
        try{
            oldGeneration=world.registerPlayer(oldPlayer,username);
            oldPlayer.movement().restoreAccountState(
                false,100,3087,3495,0);
            LocalLabTerminalStageIsolation diagnostics=
                isolated?new LocalLabTerminalStageIsolation():null;
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(
                    world,diagnostics);

            MonsterSpawnerService service=installed.service();
            service.openSession(
                username,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY
            );
            service.selectRow(username,0);
            service.activate(username,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                installed.runtime().spawnAndBind(
                    username,username,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            MonsterSpawnerPvmRuntime.RecipientOrigin origin=
                spawned.runtime.recipientOrigin;
            check(npc!=null&&origin!=null&&
                    origin.player==oldPlayer&&
                    origin.generation==oldGeneration&&
                    origin.stillCurrentIn(world),
                "canonical NPC has exact original spawn identity");

            check(world.unregisterPlayer(oldPlayer,oldGeneration),
                "old recipient must retire before replacement registers");
            newGeneration=world.registerPlayer(newPlayer,username);
            newPlayer.movement().restoreAccountState(
                false,100,3087,3495,0);
            check(world.players().byName(username)==newPlayer&&
                    newPlayer!=oldPlayer&&
                    !origin.stillCurrentIn(world),
                "same-name replacement cannot match old spawn origin");

            // Activate independent real objectives for the NEW account
            // so any incorrect name-based terminal credit is observable.
            LocalLabTaskScrollRuntime g15=world.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult scroll=
                g15.assign(username,world.clock().tick());
            check(scroll!=null&&scroll.assignedNow&&
                    scroll.task.objective.progress==0L,
                "replacement G15 objective starts without credit");
            LocalLabAdventureRuntime g16=world.localLabAdventures();
            LocalLabAdventureRuntime.ActivationResult adventure=
                g16.activate(username);
            check(adventure!=null&&adventure.activatedNow&&
                    g16.objective(username).progress==0L,
                "replacement G16 objective active");
            LocalLabDailyMoneyMakingRuntime g18=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult money=
                g18.activateEasyIfCurrent(
                    username,newPlayer,newGeneration);
            check(money!=null&&money.activatedNow&&
                    g18.snapshot(username).objective.progress==0L,
                "replacement G18 objective active");

            NpcLifecycleService.DamageResult lethal=
                world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick());
            check(lethal.newlyDied&&lethal.hitpointsAfter==0,
                "real canonical lethal after recipient replacement");
            MonsterSpawnerPvmRuntime.FinalizeResult settled=
                installed.runtime().finalizeIfOwned(npc);
            check(settled!=null&&
                    settled.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    settled.recipientOrigin==origin&&
                    settled.settlement!=null&&
                    installed.runtime().get(npc.id)==null,
                "original terminal provenance preserved through teardown");

            GroundItem ground=world.groundItems().findOwned(
                995,3088,3495,0,username);
            check(ground!=null&&ground.amount==1&&
                    world.pvmRecords().receiptCount()==0L&&
                    world.pvmRecords().snapshot(newPlayer).kills==0L&&
                    g15.active(username).objective.progress==0L&&
                    g16.objective(username).progress==0L&&
                    g18.snapshot(username).objective.progress==0L,
                "spawned NPC drop settles but rebound account gets ZERO credit");

            MonsterSpawnerPvmRuntime.TerminalObserverDiagnostics
                outer=installed.runtime().terminalObserverDiagnostics();
            check(outer.totalAttempts==1L&&
                    outer.failedAttempts==0L&&
                    outer.recentAttempts.size()==1&&
                    outer.recentAttempts.get(0).outcome==
                        MonsterSpawnerPvmRuntime.TerminalObserverOutcome
                            .RETURNED,
                "overall observer success is not proof of a replacement grant");

            if(isolated){
                LocalLabTerminalStageIsolation.DiagnosticSnapshot
                    stages=installed.terminalDiagnosticSnapshot();
                check(stages!=null&&
                        stages.observedCount==1L&&
                        stages.recentReports.size()==1,
                    "isolated production stages produced one diagnostic");
                LocalLabTerminalStageIsolation.Report report=
                    stages.recentReports.get(0);
                check(!report.spawnOriginMatched,
                    "isolated stage observer detects rejected original origin");
                for(LocalLabTerminalStageIsolation.Stage stage:
                    LocalLabTerminalStageIsolation.Stage.values())
                    check(report.outcome(stage)==
                        LocalLabTerminalStageIsolation.Outcome
                            .SKIPPED_NO_CURRENT_RECIPIENT,
                        "rebound player cannot receive isolated stage "+stage);
            }else{
                LocalLabSequentialTerminalStageTrace.Snapshot
                    stages=installed.sequentialTerminalDiagnosticSnapshot();
                check(stages!=null&&stages.observed==1L&&
                        stages.recent.size()==1&&
                        stages.recent.get(0).spawnRecipientGeneration==
                            oldGeneration,
                    "default trace preserves original generation");
                for(LocalLabSequentialTerminalStageTrace.Stage stage:
                    LocalLabSequentialTerminalStageTrace.Stage.values())
                    check(stages.recent.get(0).outcome(stage)==
                        LocalLabSequentialTerminalStageTrace.Outcome
                            .SKIPPED_NO_CURRENT_RECIPIENT,
                        "rebound player cannot receive default stage "+stage);
            }

            check(installed.runtime().finalizeIfOwned(npc).status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    world.pvmRecords().receiptCount()==0L&&
                    world.groundItems().findOwned(
                        995,3088,3495,0,username).amount==1,
                "settled death cannot be used to replay old credit");
        }finally{
            if(newGeneration>=0L&&
                world.players().owns(newPlayer,newGeneration))
                world.unregisterPlayer(newPlayer,newGeneration);
            if(oldGeneration>=0L&&
                world.players().owns(oldPlayer,oldGeneration))
                world.unregisterPlayer(oldPlayer,oldGeneration);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(false);
        System.out.println(
            "G1961_SAME_NAME_RECIPIENT_REBIND_FENCE_PASS"+
            " realCanonicalTerminal=true"+
            " replacementPlayerDidNotReceiveG1Credit=true"+
            " g4G14G15G16G18Skipped=true"+
            " originalSpawnGenerationRetained=true"+
            " dropSettledWithoutRewardReplay=true");
    }

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G19.6.1/62 "+reason);
    }

    private G1961SameNameRecipientRebindFenceTest(){}
}
