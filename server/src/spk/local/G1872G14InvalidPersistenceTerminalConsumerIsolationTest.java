package spk.local;

import java.util.Collections;

/**
 * G18.7.2: the real installed Monster Spawner observer is sequential:
 * G1 canonical loop -> G4 -> G14 -> G15 -> G16 -> G18. A malformed
 * TEST-OWNED G14 snapshot throws, so the later consumers never receive
 * the already-settled terminal. G1 and G4 still receive their events.
 *
 * No synthetic observer, no production modifications, no replay and
 * no claim about the original SpawnPK server's award policy.
 */
public final class G1872G14InvalidPersistenceTerminalConsumerIsolationTest {
    private static final String USER="g1872-g14-consumer-failure";
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
                    "version","invalid-g1872-test-only"));
            check(player.snapshotExtensions().namespace(
                    LocalLabDailyChallengePersistence.NAMESPACE)
                    .size()==1,
                "only invalid fixture G14 extension injected");

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
                    scrolls.active(USER).objective.progress==0L&&
                    adventures.objective(USER).progress==0L&&
                    dmm.snapshot(USER).objective.progress==0L,
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
                    scrolls.active(USER).objective.progress==0L&&
                    adventures.objective(USER).progress==0L&&
                    dmm.snapshot(USER).objective.progress==0L&&
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
                "G1872_G14_INVALID_PERSISTENCE_BLOCKS_G15_G16_G18_PASS"+
                " installedObserver=true"+
                " actualTwoDistinctNpcDeaths=true"+
                " g1ReceiptsTwo=true"+
                " g4FirstTerminalCompletes=true"+
                " g14InvalidCached=true"+
                " g15G16G18StayAtZero=true"+
                " twoGroundCoinSettlements=true"+
                " terminalStillFinalizedDespiteCallbackFailure=true"+
                " noCanonicalNpcReplay=true"+
                " syntheticObserverUsed=false"+
                " originalServerRewardPolicy=false"+
                " productionCodeChanged=false");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void check(boolean accepted,String detail){
        if(!accepted)throw new AssertionError("G18.7.2 "+detail);
    }

    private G1872G14InvalidPersistenceTerminalConsumerIsolationTest(){}
}
