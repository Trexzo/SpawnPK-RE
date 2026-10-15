package spk.local;

/**
 * G18.7.6 healthy installed Monster Spawner terminal control.
 * G1/G4/G14/G15/G16/G18 legitimately process TWO different NPC
 * deaths. Duplicate finalizeIfOwned for either same NPC returns
 * NOT_OWNED and cannot recredit any consumer or ground settlement.
 * CUSTOM_LOCALLAB-only, not original SpawnPK policy or durable ACK.
 */
public final class G1876AllConsumerSuccessAndDuplicateTerminalSuppressionTest {
    private static final String USER="g1876-all-consumers-healthy";
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

            LocalLabAdventureRuntime g16=world.localLabAdventures();
            LocalLabAdventureRuntime.ActivationResult adventure=
                g16.activate(USER);
            check(adventure!=null&&adventure.activatedNow&&
                    g16.objective(USER)!=null&&
                    g16.objective(USER).progress==0L,
                "legitimate G16 Adventure ACTIVE0");

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
                    g16.objective(USER).progress==1L&&
                    g18.snapshot(USER).objective.progress==1L,
                "first real terminal reaches all eligible consumers");

            MonsterSpawnerPvmRuntime.FinalizeResult duplicateA=
                installed.runtime().finalizeIfOwned(npcA);
            check(duplicateA!=null&&
                    duplicateA.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    records.receiptCount()==1&&
                    g14.status(USER).challenge.current==1L&&
                    g15.active(USER).objective.progress==1L&&
                    g16.objective(USER).progress==1L&&
                    g18.snapshot(USER).objective.progress==1L,
                "original NPC cannot be re-finalized or recredited");

            world.events().runDue(
                FIRST_DEATH_TICK+G1MonsterSpawnerPvmLoopService
                    .RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "G1 one delayed respawn remains after clean terminal");

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
                    g16.objective(USER).progress==2L&&
                    g18.snapshot(USER).objective.progress==2L&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==2&&
                    installed.runtime().get(npcA.id)==null&&
                    installed.runtime().get(npcB.id)==null,
                "two real terminals reach every eligible consumer");

            MonsterSpawnerPvmRuntime.FinalizeResult duplicateB=
                installed.runtime().finalizeIfOwned(npcB);
            check(duplicateB!=null&&
                    duplicateB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    records.snapshot(owner).kills==2L&&
                    records.receiptCount()==2&&
                    g14.status(USER).challenge.current==2L&&
                    g15.active(USER).objective.progress==2L&&
                    g16.objective(USER).progress==2L&&
                    g18.snapshot(USER).objective.progress==2L&&
                    world.groundItems().snapshot().stream()
                        .filter(item->item.itemId==995&&
                            USER.equals(item.owner))
                        .mapToInt(item->item.amount).sum()==2,
                "second NPC re-finalization cannot repeat any credit");

            System.out.println(
                "G1876_HEALTHY_ALL_CONSUMERS_DUPLICATE_TERMINAL_SUPPRESSION_PASS"+
                " actualInstalledObserver=true"+
                " twoGenuineIndependentNpcDeaths=true"+
                " g1ReceiptsTwo=true"+
                " g4CompletionOne=true"+
                " g14ProgressTwo=true"+
                " g15ProgressTwo=true"+
                " g16ValidProgressTwo=true"+
                " g18ValidProgressTwo=true"+
                " twoOneCoinGroundSettlements=true"+
                " bothTerminalsFinalized=true"+
                " duplicateFinalizeReturnsNotOwned=true"+
                " noMalformedSnapshotOrSyntheticObserver=true"+
                " originalServerPolicyClaim=false"+
                " productionCodeChanged=false");
        }finally{
            if(generation>=0L&&world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            world.close();
        }
    }

    private static void check(boolean accepted,String reason){
        if(!accepted)throw new AssertionError("G18.7.6 "+reason);
    }

    private G1876AllConsumerSuccessAndDuplicateTerminalSuppressionTest(){}
}
