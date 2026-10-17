package spk.local;

import java.util.Collections;

/**
 * Chat2 G18.7.7: an already FINALIZED canonical NPC cannot be
 * replayed via any ordinary MonsterSpawnerPvmRuntime retry entry,
 * even after replacing a player whose invalid G14 snapshot caused
 * a partially delivered installed terminal observer callback.
 *
 * A separate genuinely spawned later NPC can still credit the
 * healthy replacement. Only CUSTOM_LOCALLAB/test-injected invalid
 * persistence; no original SpawnPK rewards or durable outbox claim.
 */
public final class G1877G14FailureNoDuplicateRetryAfterOwnerReplacementTest {
    private static final String USER="g1877-g14-retry-boundary";
    private static final long FIRST_DEATH=1701L;

    public static void main(String[] args)throws Exception {
        World world=World.isolatedForTest(60_000L);
        WorldPlayer invalidA=new WorldPlayer();
        WorldPlayer healthyB=null;
        long genA=-1L,genB=-1L;
        try {
            genA=world.registerPlayer(invalidA,USER);
            LocalLabSlayerRuntime g4=world.localLabSlayer();
            LocalLabSlayerRuntime.StartResult hunter=
                g4.startMonsterHunter(USER,world.clock().tick());
            check(hunter!=null&&hunter.created&&
                    hunter.status.active()&&
                    hunter.status.task.objective.progress==0L,
                "A activates normal G4 Slayer task");

            LocalLabTaskScrollRuntime g15=world.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult scroll=
                g15.assign(USER,world.clock().tick());
            check(scroll!=null&&scroll.assignedNow&&
                    scroll.task.objective.progress==0L,
                "A activates genuine World G15 task");

            LocalLabAdventureRuntime g16=world.localLabAdventures();
            LocalLabAdventureRuntime.ActivationResult adventure=
                g16.activate(USER);
            check(adventure!=null&&adventure.activatedNow&&
                    g16.objective(USER)!=null&&
                    g16.objective(USER).progress==0L,
                "A activates genuine World G16 objective");

            LocalLabDailyMoneyMakingRuntime g18=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult easy=
                g18.activateEasyIfCurrent(USER,invalidA,genA);
            check(easy!=null&&easy.activatedNow&&
                    g18.snapshot(USER).objective.progress==0L,
                "A activates genuine World G18 EASY");

            // A has NOT hydrated G14. Only this test-created
            // in-memory extension is invalid. No file, TCP or
            // client input can be inferred from the fixture.
            invalidA.snapshotExtensions().replaceNamespace(
                LocalLabDailyChallengePersistence.NAMESPACE,
                Collections.singletonMap(
                    "version","invalid-g1877-test-only"));

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawnA=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc first=spawnA.spawn.combat.spawn.npc;
            check(spawnA.runtime.recipientOrigin!=null&&
                    spawnA.runtime.recipientOrigin.player==invalidA&&
                    spawnA.runtime.recipientOrigin.generation==genA&&
                    world.npcLifecycle().applyDamage(
                        first.id,99,FIRST_DEATH).newlyDied,
                "first real NPC dies with immutable A origin");

            MonsterSpawnerPvmRuntime.FinalizeResult finalizedA=
                installed.runtime().finalizeIfOwned(first);
            PvmRecordService records=world.pvmRecords();
            check(finalizedA!=null&&
                    finalizedA.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    finalizedA.settlement!=null&&
                    finalizedA.settlement.groundItems.size()==1&&
                    finalizedA.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    records.snapshot(invalidA).kills==1L&&
                    records.receiptCount()==1&&
                    g4.status(USER).complete()&&
                    g4.status(USER).completions==1L&&
                    g15.active(USER).objective.progress==0L&&
                    g16.objective(USER).progress==0L&&
                    g18.snapshot(USER).objective.progress==0L&&
                    installed.runtime().get(first.id)==null,
                "G1/G4 succeed; G14 error prevents later stages"+
                " but finalizes and removes first NPC");

            String invalidError=null;
            try {
                world.localLabDailyChallenges().status(USER);
            }catch(IllegalStateException expected) {
                invalidError=expected.getMessage();
            }
            check(invalidError!=null&&
                    invalidError.contains("Daily Challenge persistence invalid"),
                "A's test-created G14 failure remains explainable");

            check(world.unregisterPlayer(invalidA,genA),
                "A leaves after finalized partial delivery");
            healthyB=new WorldPlayer();
            genB=world.registerPlayer(healthyB,USER);

            LocalLabDailyChallengeRuntime g14=
                world.localLabDailyChallenges();
            LocalLabDailyChallengeRuntime.StatusResult challenge=
                g14.status(USER);
            LocalLabAdventureRuntime.ActivationResult newAdventure=
                g16.activate(USER);
            check(newAdventure!=null&&newAdventure.activatedNow&&
                    g16.objective(USER)!=null&&
                    g16.objective(USER).progress==0L,
                "B must explicitly activate NEW G16 owner-local task");
            check(challenge!=null&&challenge.assignedNow&&
                    challenge.challenge.current==0L&&
                    g15.active(USER).objective.progress==0L&&
                    g16.objective(USER).progress==0L&&
                    g18.snapshot(USER).objective.progress==0L&&
                    records.receiptCount()==1&&
                    world.groundItems().size()==1,
                "healthy replacement has valid G14 but no"+
                " automatic historical G14/G15/G16/G18 backfill");

            // THREE distinct public/runtime retry avenues against
            // EXACT old NPC identity, after its settled owner has
            // gone: no second kill receipt, callback or coin.
            MonsterSpawnerPvmRuntime.FinalizeResult repeated=
                installed.runtime().finalizeIfOwned(first);
            MonsterSpawnerPvmRuntime.FinalizeResult pendingSettlement=
                installed.runtime().retrySettlement(first.id);
            MonsterSpawnerPvmRuntime.FinalizeResult pendingFinalization=
                installed.runtime().retryFinalizationIfPending(first);
            int pendingCount=installed.runtime().retryPendingSettlementsOnce();
            check(repeated!=null&&
                    repeated.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    pendingSettlement!=null&&
                    pendingSettlement.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    pendingFinalization==null&&
                    pendingCount==0&&
                    records.receiptCount()==1&&
                    records.snapshot(healthyB).kills==0L&&
                    g14.status(USER).challenge.current==0L&&
                    g15.active(USER).objective.progress==0L&&
                    g16.objective(USER).progress==0L&&
                    g18.snapshot(USER).objective.progress==0L&&
                    world.groundItems().size()==1,
                "old finalized partial delivery cannot be retried"+
                " or reapplied after B replaces A");

            // Healthy B can receive exactly one FUTURE genuine
            // canonical event; the old G14/G15/G16/G18 loss
            // intentionally remains uncorrected.
            world.events().runDue(
                FIRST_DEATH+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "normal G1 delayed respawn for B");
            EntityId secondId=session.spawnedNpcIds.get(0);
            WorldNpc second=world.npcs().byId(secondId);
            MonsterSpawnerPvmRuntime.Snapshot originB=
                installed.runtime().get(secondId);
            check(second!=null&&!secondId.equals(first.id)&&
                    originB!=null&&originB.recipientOrigin!=null&&
                    originB.recipientOrigin.player==healthyB&&
                    originB.recipientOrigin.generation==genB&&
                    world.npcLifecycle().applyDamage(
                        secondId,99,FIRST_DEATH+
                            G1MonsterSpawnerPvmLoopService
                                .RESPAWN_DELAY_TICKS+1L).newlyDied,
                "new independent NPC dies under exact healthy B");

            MonsterSpawnerPvmRuntime.FinalizeResult finalizedB=
                installed.runtime().finalizeIfOwned(second);
            check(finalizedB!=null&&
                    finalizedB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    finalizedB.settlement!=null&&
                    finalizedB.settlement.groundItems.size()==1&&
                    finalizedB.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    records.snapshot(invalidA).kills==1L&&
                    records.snapshot(healthyB).kills==1L&&
                    records.receiptCount()==2&&
                    g4.status(USER).complete()&&
                    g4.status(USER).completions==1L&&
                    g14.status(USER).challenge.current==1L&&
                    g15.active(USER).objective.progress==1L&&
                    g16.objective(USER).progress==1L&&
                    g18.snapshot(USER).objective.progress==1L&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==2&&
                    installed.runtime().get(first.id)==null&&
                    installed.runtime().get(second.id)==null,
                "only new second terminal credits healthy consumers"+
                " once; old terminal never backfills");

            System.out.println(
                "G1877_G14_FAILED_TERMINAL_DUPLICATE_RETRY_VETO_PASS"+
                " firstTerminalPartiallyDeliveredAndFinalized=true"+
                " cleanOwnerAfterError=true"+
                " oldFinalizeNotOwned=true"+
                " oldSettlementRetryNotOwned=true"+
                " oldFinalizationRetryNull=true"+
                " pendingSettlementCountZero=true"+
                " noOldNpcCreditOrCoinReplay=true"+
                " oneFutureNewNpcCreditsG14G15G16G18=true"+
                " g1ReceiptsExactlyTwo=true"+
                " originalSpawnpkPolicyClaim=false"+
                " durableStageOutboxImplemented=false"+
                " productionCodeChanged=false");
        }finally{
            if(healthyB!=null&&genB>=0L&&
                    world.players().owns(healthyB,genB))
                world.unregisterPlayer(healthyB,genB);
            if(genA>=0L&&world.players().owns(invalidA,genA))
                world.unregisterPlayer(invalidA,genA);
            world.close();
        }
    }

    private static void check(boolean success,String reason) {
        if(!success)throw new AssertionError("G18.7.7 "+reason);
    }

    private G1877G14FailureNoDuplicateRetryAfterOwnerReplacementTest(){}
}
