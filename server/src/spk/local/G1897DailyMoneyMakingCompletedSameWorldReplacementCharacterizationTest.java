package spk.local;

/**
 * G18.9.7 / #2547: characterize a COMPLETE 3/3 DMM objective
 * across a same-World A-to-B WorldPlayer replacement. No policy
 * reset, grant, claim or original SpawnPK rule is inferred.
 */
public final class G1897DailyMoneyMakingCompletedSameWorldReplacementCharacterizationTest {
    private static final String USER="g1897-dmm-complete";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=null;
        long genA=-1L, genB=-1L;
        try{
            genA=world.registerPlayer(a,USER);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            check(dmm.activateEasyIfCurrent(USER,a,genA).activatedNow,
                "A starts genuine EASY objective");
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);

            WorldNpc first=installed.runtime().spawnAndBind(
                USER,USER,3088,3495,0).spawn.combat.spawn.npc;
            WorldNpc npc=first;
            for(int index=0;index<3;index++){
                long tick=15101L+index*10L;
                check(world.npcLifecycle().applyDamage(
                        npc.id,99,tick).newlyDied,
                    "genuine A NPC death "+index);
                MonsterSpawnerPvmRuntime.FinalizeResult result=
                    installed.runtime().finalizeIfOwned(npc);
                check(result!=null&&result.sourceWorld==world&&
                        result.status==
                            MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                        result.settlement!=null&&
                        result.settlement.groundItems.size()==1&&
                        dmm.snapshot(USER).objective.progress==index+1L&&
                        world.pvmRecords().receiptCount()==index+1,
                    "authentic G1/ground/G18 step "+index);
                if(index<2){
                    world.events().runDue(
                        tick+G1MonsterSpawnerPvmLoopService.RESPAWN_DELAY_TICKS);
                    MonsterSpawnerService.SessionSnapshot session=
                        spawner.getSession(USER);
                    check(session!=null&&session.spawnedNpcIds.size()==1,
                        "real A G1 respawn "+index);
                    WorldNpc next=world.npcs().byId(
                        session.spawnedNpcIds.get(0));
                    check(next!=null&&!next.id.equals(npc.id),
                        "different real A NPC "+index);
                    npc=next;
                }
            }
            LocalLabDailyMoneyMakingRuntime.Snapshot completed=
                dmm.snapshot(USER);
            check(completed.objective.complete&&
                    completed.objective.progress==
                        LocalLabDailyMoneyMakingRuntime.GOAL&&
                    !completed.objective.claimed,
                "A genuinely completed 3/3, NO reward-claim action");

            check(world.unregisterPlayer(a,genA),
                "A unregisters after completion");
            b=new WorldPlayer();
            genB=world.registerPlayer(b,USER);
            LocalLabDailyMoneyMakingRuntime.Snapshot inherited=
                dmm.snapshot(USER);
            LocalLabDailyMoneyMakingRuntime.ActivationResult again=
                dmm.activateEasyIfCurrent(USER,b,genB);
            check(a!=b&&world.players().owns(b,genB)&&
                    !world.players().owns(a,genA)&&
                    inherited.objective.progress==3L&&
                    inherited.objective.complete&&
                    !inherited.objective.claimed&&
                    again!=null&&!again.activatedNow&&
                    again.snapshot.objective.progress==3L,
                "current same-World name policy retains COMPLETE for B");

            LocalLabDailyMoneyMakingRuntime.ProgressResult stale=
                dmm.recordMonsterSpawnerFinalizationIfCurrent(
                    USER,a,genA,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                    15137L);
            check(stale!=null&&!stale.progressed&&
                    dmm.snapshot(USER).objective.progress==3L,
                "stale A cannot credit B's completed state");

            world.events().runDue(
                15121L+G1MonsterSpawnerPvmLoopService.RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                spawner.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "actual fourth NPC scheduled by G1");
            WorldNpc fourth=world.npcs().byId(
                session.spawnedNpcIds.get(0));
            check(fourth!=null&&
                    world.npcLifecycle().applyDamage(
                        fourth.id,99,15131L).newlyDied,
                "real B NPC terminal after prior completion");
            MonsterSpawnerPvmRuntime.FinalizeResult fourthEvent=
                installed.runtime().finalizeIfOwned(fourth);
            check(fourthEvent!=null&&fourthEvent.sourceWorld==world&&
                    fourthEvent.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    fourthEvent.settlement!=null&&
                    fourthEvent.settlement.groundItems.size()==1&&
                    dmm.snapshot(USER).objective.progress==3L&&
                    dmm.snapshot(USER).objective.complete&&
                    !dmm.snapshot(USER).objective.claimed&&
                    world.pvmRecords().receiptCount()==4&&
                    installed.runtime().finalizeIfOwned(first).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "fourth real NPC leaves completed objective capped 3/3");

            int coins=world.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&USER.equals(item.owner))
                .mapToInt(item->item.amount).sum();
            check(coins==4,"four distinct genuine drop settlements");
            System.out.println(
                "G1897_DMM_COMPLETED_SAME_WORLD_REPLACEMENT_CHARACTERIZED_PASS"+
                " aThreeRealNpcDeaths=true"+
                " aCompletedThreeOfThree=true"+
                " bInheritedCompletedThreeOfThree=true"+
                " bActivationNoReset=true"+
                " staleACreditRefused=true"+
                " fourthRealNpcCompletionCapped=true"+
                " g1ReceiptsFour=true"+
                " groundCoinsFour=true"+
                " rewardClaimNotInvoked=true"+
                " lifecyclePolicyUndecided=true"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(genA>=0L&&world.players().owns(a,genA))
                world.unregisterPlayer(a,genA);
            if(b!=null&&genB>=0L&&world.players().owns(b,genB))
                world.unregisterPlayer(b,genB);
            world.close();
        }
    }

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G18.9.7 "+why);
    }

    private G1897DailyMoneyMakingCompletedSameWorldReplacementCharacterizationTest(){}
}
