package spk.local;

/**
 * G18.8.8: two genuinely different NPC deaths, one with exact
 * current WorldPlayer and one after that WorldPlayer unregisters.
 * Every stage's read-only all-time counts distinguish a normal
 * RETURN from an absent-recipient SKIP, never an applied credit.
 */
public final class G1888TerminalStageReturnedSkippedRollupTest {
    private static final String USER="g1888-stage-returned-skipped";
    private static final String PROPERTY=
        LocalLabMonsterSpawnerProvisioning
            .ISOLATE_TERMINAL_STAGES_PROPERTY;

    public static void main(String[] args)throws Exception{
        String prior=System.getProperty(PROPERTY);
        World world=World.isolatedForTest(60_000L);
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        try{
            System.setProperty(PROPERTY,"true");
            generation=world.registerPlayer(owner,USER);
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            System.clearProperty(PROPERTY);

            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc first=spawned.spawn.combat.spawn.npc;
            check(world.npcLifecycle().applyDamage(
                    first.id,99,5401L).newlyDied,
                "first genuine NPC dies with current original owner");
            MonsterSpawnerPvmRuntime.FinalizeResult firstDone=
                installed.runtime().finalizeIfOwned(first);
            check(firstDone!=null&&
                    firstDone.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    firstDone.settlement!=null&&
                    firstDone.settlement.groundItems.size()==1&&
                    world.pvmRecords().receiptCount()==1,
                "one actual settled G1 credited death");

            LocalLabTerminalStageIsolation.DiagnosticSnapshot firstView=
                installed.terminalDiagnosticSnapshot();
            check(firstView!=null&&firstView.observedCount==1L&&
                    firstView.recentReports.size()==1&&
                    firstView.recentReports.get(0)
                        .spawnOriginMatchedAtDispatch,
                "first real terminal has matching current spawn origin");
            for(LocalLabTerminalStageIsolation.Stage stage:
                    LocalLabTerminalStageIsolation.Stage.values()){
                LocalLabTerminalStageIsolation.OutcomeTotals totals=
                    firstView.stage(stage);
                check(totals!=null&&totals.returned==1L&&
                        totals.threw==0L&&totals.skipped==0L,
                    "live stage returned once "+stage);
            }

            world.events().runDue(
                5401L+G1MonsterSpawnerPvmLoopService.RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "real G1 delayed respawn available");
            EntityId nextId=session.spawnedNpcIds.get(0);
            WorldNpc second=world.npcs().byId(nextId);
            check(second!=null&&!nextId.equals(first.id),
                "second genuinely new canonical NPC");
            check(world.unregisterPlayer(owner,generation),
                "original registered owner leaves before second death");
            check(world.players().byName(USER)==null,
                "no replacement recipient in World");
            check(world.npcLifecycle().applyDamage(
                    second.id,99,5411L).newlyDied,
                "second NPC genuinely dies after player departure");
            MonsterSpawnerPvmRuntime.FinalizeResult secondDone=
                installed.runtime().finalizeIfOwned(second);
            check(secondDone!=null&&
                    secondDone.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    secondDone.settlement!=null&&
                    secondDone.settlement.groundItems.size()==1&&
                    world.pvmRecords().receiptCount()==1,
                "drop settles but departed recipient gains no second G1");

            LocalLabTerminalStageIsolation.DiagnosticSnapshot all=
                installed.terminalDiagnosticSnapshot();
            check(all.observedCount==2L&&all.evictedCount==0L&&
                    all.recentReports.size()==2&&
                    !all.recentReports.get(1)
                        .spawnOriginMatchedAtDispatch&&
                    all.recentReports.get(1).observedGeneration==-1L&&
                    firstView.observedCount==1L&&
                    firstView.recentReports.size()==1,
                "detached report distinguishes second absent recipient");
            for(LocalLabTerminalStageIsolation.Stage stage:
                    LocalLabTerminalStageIsolation.Stage.values()){
                LocalLabTerminalStageIsolation.OutcomeTotals counts=
                    all.stage(stage);
                check(counts!=null&&
                        counts.returned==1L&&
                        counts.skipped==1L&&
                        counts.threw==0L&&
                        all.recentReports.get(1).outcome(stage)==
                            LocalLabTerminalStageIsolation.Outcome
                                .SKIPPED_NO_CURRENT_RECIPIENT,
                    "all-time stage counts one return and one skip "+stage);
            }
            check(installed.runtime().finalizeIfOwned(first).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    installed.terminalDiagnosticSnapshot()
                        .observedCount==2L,
                "already-finalized original not re-run");

            int coins=world.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&USER.equals(item.owner))
                .mapToInt(item->item.amount).sum();
            check(coins==2,
                "two independent drop settlements even with second skip");
            System.out.println(
                "G1888_TERMINAL_STAGE_RETURNED_SKIPPED_ROLLUP_PASS"+
                " genuineNpcDeathsTwo=true"+
                " g1ReceiptOnlyOriginalOne=true"+
                " groundCoinsTwo=true"+
                " eachStageReturnedOne=true"+
                " eachStageSkippedOne=true"+
                " eachStageThrewZero=true"+
                " detachedSnapshots=true"+
                " noDuplicateFinalization=true"+
                " noDurableRewardAck=true"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(generation>=0L&&world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            world.close();
            if(prior==null)
                System.clearProperty(PROPERTY);
            else
                System.setProperty(PROPERTY,prior);
        }
    }

    private static void check(boolean accepted,String explanation){
        if(!accepted)throw new AssertionError("G18.8.8 "+explanation);
    }

    private G1888TerminalStageReturnedSkippedRollupTest(){}
}
