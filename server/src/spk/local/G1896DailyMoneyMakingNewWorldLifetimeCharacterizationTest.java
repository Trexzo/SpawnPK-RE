package spk.local;

/**
 * G18.9.6 / #2547: same name after an actual World.close() has NO
 * in-memory Daily Money Making objective carried into a new isolated
 * World instance. This is CHARACTERIZATION of CUSTOM_LOCALLAB World-
 * owned state, not proof of account disk persistence or original rules.
 */
public final class G1896DailyMoneyMakingNewWorldLifetimeCharacterizationTest {
    private static final String USER="g1896-dmm-world-close";

    public static void main(String[] args)throws Exception{
        World a=World.isolatedForTest(60_000L);
        World b=null;
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long genA=-1L, genB=-1L;
        boolean firstClosed=false;
        try{
            genA=a.registerPlayer(original,USER);
            LocalLabDailyMoneyMakingRuntime dmmA=
                a.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult activated=
                dmmA.activateEasyIfCurrent(USER,original,genA);
            check(activated!=null&&activated.activatedNow&&
                    activated.snapshot.objective.progress==0L,
                "World A genuinely activated fresh EASY objective");
            LocalMonsterSpawnerActivationRuntime installedA=
                LocalLabMonsterSpawnerProvisioning.create(a);
            MonsterSpawnerService serviceA=installedA.service();
            serviceA.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            serviceA.selectRow(USER,0);
            serviceA.activate(USER,1);
            WorldNpc npcA=installedA.runtime().spawnAndBind(
                USER,USER,3088,3495,0).spawn.combat.spawn.npc;
            check(a.npcLifecycle().applyDamage(
                    npcA.id,99,13001L).newlyDied,
                "actual A World NPC dies");
            MonsterSpawnerPvmRuntime.FinalizeResult eventA=
                installedA.runtime().finalizeIfOwned(npcA);
            check(eventA!=null&&eventA.sourceWorld==a&&
                    eventA.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    eventA.settlement!=null&&
                    eventA.settlement.groundItems.size()==1&&
                    dmmA.snapshot(USER).objective.progress==1L&&
                    dmmA.activePlayerCount()==1&&
                    a.pvmRecords().receiptCount()==1,
                "World A observed genuine local DMM progress 1/3");

            check(a.unregisterPlayer(original,genA),
                "World A owner actually leaves");
            a.close();
            firstClosed=true;

            b=World.isolatedForTest(60_000L);
            replacement=new WorldPlayer();
            genB=b.registerPlayer(replacement,USER);
            LocalLabDailyMoneyMakingRuntime dmmB=
                b.localLabDailyMoneyMaking();
            check(dmmA!=dmmB&&
                    b.players().byName(USER)==replacement&&
                    b.players().owns(replacement,genB)&&
                    dmmB.activePlayerCount()==0&&
                    dmmB.snapshot(USER)==null,
                "new isolated World has empty name-keyed DMM map");

            LocalLabDailyMoneyMakingRuntime.ActivationResult newWorld=
                dmmB.activateEasyIfCurrent(USER,replacement,genB);
            check(newWorld!=null&&newWorld.activatedNow&&
                    newWorld.snapshot.objective.progress==0L&&
                    dmmB.activePlayerCount()==1,
                "CHARACTERIZED new World EASY starts 0/3 not 1/3");

            LocalMonsterSpawnerActivationRuntime installedB=
                LocalLabMonsterSpawnerProvisioning.create(b);
            MonsterSpawnerService serviceB=installedB.service();
            serviceB.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            serviceB.selectRow(USER,0);
            serviceB.activate(USER,1);
            WorldNpc npcB=installedB.runtime().spawnAndBind(
                USER,USER,3088,3495,0).spawn.combat.spawn.npc;
            check(b.npcLifecycle().applyDamage(
                    npcB.id,99,13011L).newlyDied,
                "genuine new World B NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult eventB=
                installedB.runtime().finalizeIfOwned(npcB);
            check(eventB!=null&&eventB.sourceWorld==b&&
                    eventB.sourceWorld!=eventA.sourceWorld&&
                    eventB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    eventB.settlement!=null&&
                    eventB.settlement.groundItems.size()==1&&
                    dmmB.snapshot(USER).objective.progress==1L&&
                    b.pvmRecords().receiptCount()==1&&
                    installedB.runtime().finalizeIfOwned(npcB).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "fresh B World credits exactly one new local kill");
            System.out.println(
                "G1896_DMM_NEW_WORLD_LIFETIME_CHARACTERIZED_PASS"+
                " firstWorldClosed=true"+
                " actualNpcDeathPerWorld=true"+
                " aEasyProgressOneBeforeClose=true"+
                " newWorldHasNoInMemoryObjective=true"+
                " newWorldActivateFreshZero=true"+
                " bProgressOneAfterNewKill=true"+
                " oneG1ReceiptPerWorld=true"+
                " noExternalAccountFilePersistenceTested=true"+
                " lifecyclePolicyUndecided=true"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(!firstClosed){
                if(genA>=0L&&a.players().owns(original,genA))
                    a.unregisterPlayer(original,genA);
                a.close();
            }
            if(b!=null){
                if(replacement!=null&&genB>=0L&&
                    b.players().owns(replacement,genB))
                    b.unregisterPlayer(replacement,genB);
                b.close();
            }
        }
    }

    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G18.9.6 "+reason);
    }

    private G1896DailyMoneyMakingNewWorldLifetimeCharacterizationTest(){}
}
