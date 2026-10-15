package spk.local;

/**
 * G18.9.8 / #2547: two live independent Worlds both register the same
 * canonical username, then credit genuinely separate NPC terminals.
 * Their World-owned DMM objective Maps must not share mutable state.
 * This is not account writer ownership or disk-durability evidence.
 */
public final class G1898DailyMoneyMakingParallelWorldSameNameIsolationTest {
    private static final String USER="g1898-dmm-parallel";

    public static void main(String[] args)throws Exception{
        World a=World.isolatedForTest(60_000L);
        World b=World.isolatedForTest(60_000L);
        WorldPlayer ownerA=new WorldPlayer();
        WorldPlayer ownerB=new WorldPlayer();
        long genA=-1L,genB=-1L;
        try{
            genA=a.registerPlayer(ownerA,USER);
            genB=b.registerPlayer(ownerB,USER);
            LocalLabDailyMoneyMakingRuntime dmmA=
                a.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime dmmB=
                b.localLabDailyMoneyMaking();
            check(dmmA!=dmmB&&ownerA!=ownerB&&
                    a.players().owns(ownerA,genA)&&
                    b.players().owns(ownerB,genB),
                "two truly independent World/name/player instances");
            check(dmmA.activateEasyIfCurrent(USER,ownerA,genA)
                    .activatedNow&&
                    dmmB.activateEasyIfCurrent(USER,ownerB,genB)
                    .activatedNow&&
                    dmmA.snapshot(USER).objective.progress==0L&&
                    dmmB.snapshot(USER).objective.progress==0L,
                "both Worlds independently activate fresh EASY objectives");

            LocalMonsterSpawnerActivationRuntime spawnerA=
                LocalLabMonsterSpawnerProvisioning.create(a);
            LocalMonsterSpawnerActivationRuntime spawnerB=
                LocalLabMonsterSpawnerProvisioning.create(b);
            MonsterSpawnerService sa=spawnerA.service();
            MonsterSpawnerService sb=spawnerB.service();
            sa.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            sa.selectRow(USER,0);
            sa.activate(USER,1);
            sb.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            sb.selectRow(USER,0);
            sb.activate(USER,1);

            WorldNpc npcA=spawnerA.runtime().spawnAndBind(
                USER,USER,3088,3495,0).spawn.combat.spawn.npc;
            WorldNpc npcB=spawnerB.runtime().spawnAndBind(
                USER,USER,3088,3495,0).spawn.combat.spawn.npc;
            check(a.npcLifecycle().applyDamage(
                    npcA.id,99,17101L).newlyDied,
                "actual A NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult eventA=
                spawnerA.runtime().finalizeIfOwned(npcA);
            check(eventA!=null&&eventA.sourceWorld==a&&
                    eventA.sourceWorld!=b&&
                    eventA.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    eventA.settlement!=null&&
                    dmmA.snapshot(USER).objective.progress==1L&&
                    dmmB.snapshot(USER).objective.progress==0L&&
                    a.pvmRecords().receiptCount()==1&&
                    b.pvmRecords().receiptCount()==0,
                "A real credit does not change concurrent same-name World B");

            check(b.npcLifecycle().applyDamage(
                    npcB.id,99,17111L).newlyDied,
                "first actual B NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult eventB=
                spawnerB.runtime().finalizeIfOwned(npcB);
            check(eventB!=null&&eventB.sourceWorld==b&&
                    eventB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    eventB.settlement!=null&&
                    dmmA.snapshot(USER).objective.progress==1L&&
                    dmmB.snapshot(USER).objective.progress==1L&&
                    a.pvmRecords().receiptCount()==1&&
                    b.pvmRecords().receiptCount()==1,
                "B's first independent terminal advances only B");

            b.events().runDue(
                17111L+G1MonsterSpawnerPvmLoopService.RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot sessionB=
                sb.getSession(USER);
            check(sessionB!=null&&sessionB.spawnedNpcIds.size()==1,
                "real G1 B respawn");
            WorldNpc secondB=b.npcs().byId(
                sessionB.spawnedNpcIds.get(0));
            check(secondB!=null&&!secondB.id.equals(npcB.id)&&
                    b.npcLifecycle().applyDamage(
                        secondB.id,99,17121L).newlyDied,
                "second newly spawned B NPC dies");
            MonsterSpawnerPvmRuntime.FinalizeResult secondEventB=
                spawnerB.runtime().finalizeIfOwned(secondB);
            check(secondEventB!=null&&secondEventB.sourceWorld==b&&
                    secondEventB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    secondEventB.settlement!=null&&
                    dmmA.snapshot(USER).objective.progress==1L&&
                    dmmB.snapshot(USER).objective.progress==2L&&
                    a.pvmRecords().receiptCount()==1&&
                    b.pvmRecords().receiptCount()==2&&
                    spawnerA.runtime().finalizeIfOwned(npcA).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "parallel Worlds keep independent 1/3 vs 2/3 state");

            int coinsA=a.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&USER.equals(item.owner))
                .mapToInt(item->item.amount).sum();
            int coinsB=b.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&USER.equals(item.owner))
                .mapToInt(item->item.amount).sum();
            check(coinsA==1&&coinsB==2,
                "ground ownership stays World-local despite same username");

            System.out.println(
                "G1898_DMM_PARALLEL_WORLD_SAME_NAME_ISOLATION_PASS"+
                " twoSimultaneousWorlds=true"+
                " sameNormalizedUsername=true"+
                " independentRealNpcDeathsOneVsTwo=true"+
                " aEasyProgressOne=true"+
                " bEasyProgressTwo=true"+
                " aG1ReceiptOne=true"+
                " bG1ReceiptsTwo=true"+
                " aGroundCoinOne=true"+
                " bGroundCoinsTwo=true"+
                " noCrossWorldStateAlias=true"+
                " noDurableAccountWriterEpoch=true"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(genA>=0L&&a.players().owns(ownerA,genA))
                a.unregisterPlayer(ownerA,genA);
            if(genB>=0L&&b.players().owns(ownerB,genB))
                b.unregisterPlayer(ownerB,genB);
            a.close();
            b.close();
        }
    }

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G18.9.8 "+why);
    }

    private G1898DailyMoneyMakingParallelWorldSameNameIsolationTest(){}
}
