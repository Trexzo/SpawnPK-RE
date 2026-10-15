package spk.local;

/**
 * G18.9.5 / #2547: CHARACTERIZATION, not a policy choice.
 * Daily Money Making currently holds username-keyed state inside a
 * single World. A replacement exact WorldPlayer may see an already
 * started EASY objective, even while stale old-player writes fail.
 */
public final class G1895DailyMoneyMakingSameWorldReplacementLifetimeCharacterizationTest {
    private static final String USER="g1895-dmm-reconnect";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=null;
        long generationA=-1L, generationB=-1L;
        try{
            generationA=world.registerPlayer(a,USER);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            LocalLabDailyMoneyMakingRuntime.ActivationResult active=
                dmm.activateEasyIfCurrent(USER,a,generationA);
            check(active!=null&&active.activatedNow&&
                    active.snapshot.objective.progress==0L,
                "A activates genuinely new EASY zero-progress objective");
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService service=installed.service();
            service.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            service.selectRow(USER,0);
            service.activate(USER,1);
            WorldNpc first=installed.runtime().spawnAndBind(
                USER,USER,3088,3495,0).spawn.combat.spawn.npc;
            check(world.npcLifecycle().applyDamage(
                    first.id,99,11601L).newlyDied,
                "first genuine NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult settledA=
                installed.runtime().finalizeIfOwned(first);
            check(settledA!=null&&
                    settledA.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    settledA.settlement!=null&&
                    settledA.settlement.groundItems.size()==1&&
                    world.pvmRecords().receiptCount()==1&&
                    dmm.snapshot(USER).objective.progress==1L,
                "canonical G1 and DMM have exactly one credit before exit");

            check(world.unregisterPlayer(a,generationA),
                "original A unregisters exact generation");
            b=new WorldPlayer();
            generationB=world.registerPlayer(b,USER);
            check(a!=b&&world.players().byName(USER)==b&&
                    world.players().owns(b,generationB)&&
                    !world.players().owns(a,generationA),
                "replacement B holds same name with exact identity authority");

            LocalLabDailyMoneyMakingRuntime.Snapshot inherited=
                dmm.snapshot(USER);
            LocalLabDailyMoneyMakingRuntime.ActivationResult resumed=
                dmm.activateEasyIfCurrent(USER,b,generationB);
            check(inherited!=null&&inherited.objective.progress==1L&&
                    resumed!=null&&!resumed.activatedNow&&
                    resumed.snapshot.objective.progress==1L&&
                    dmm.activePlayerCount()==1,
                "CHARACTERIZED: one World name-state retains EASY 1/3 for B");

            LocalLabDailyMoneyMakingRuntime.ActivationResult stale=
                dmm.activateEasyIfCurrent(USER,a,generationA);
            LocalLabDailyMoneyMakingRuntime.ProgressResult staleCredit=
                dmm.recordMonsterSpawnerFinalizationIfCurrent(
                    USER,a,generationA,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                    11605L);
            check(stale==null&&staleCredit!=null&&
                    !staleCredit.progressed&&
                    dmm.snapshot(USER).objective.progress==1L,
                "stale A mutation does not advance B's carried progress");

            world.events().runDue(
                11601L+G1MonsterSpawnerPvmLoopService.RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                service.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "real G1 delayed respawn survives same World replacement");
            WorldNpc second=world.npcs().byId(
                session.spawnedNpcIds.get(0));
            check(second!=null&&!second.id.equals(first.id)&&
                    world.npcLifecycle().applyDamage(
                        second.id,99,11611L).newlyDied,
                "second genuinely new NPC dies with B current");
            MonsterSpawnerPvmRuntime.FinalizeResult settledB=
                installed.runtime().finalizeIfOwned(second);
            check(settledB!=null&&settledB.sourceWorld==world&&
                    settledB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    settledB.settlement!=null&&
                    settledB.settlement.groundItems.size()==1&&
                    dmm.snapshot(USER).objective.progress==2L&&
                    world.pvmRecords().receiptCount()==2&&
                    installed.runtime().finalizeIfOwned(first).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "second genuine terminal advances B-carried objective 1/3 to 2/3");

            int coins=world.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&USER.equals(item.owner))
                .mapToInt(item->item.amount).sum();
            check(coins==2,"two independent real settlements");
            System.out.println(
                "G1895_DMM_SAME_WORLD_REPLACEMENT_LIFETIME_CHARACTERIZED_PASS"+
                " actualNpcDeathsTwo=true"+
                " aProgressOneBeforeExit=true"+
                " bInheritedSameWorldProgressOne=true"+
                " bActivateDidNotReset=true"+
                " staleAExactWriteRejected=true"+
                " bContinuedProgressTwo=true"+
                " g1ReceiptsTwo=true"+
                " groundCoinsTwo=true"+
                " lifecyclePolicyUndecided=true"+
                " crossWorldDurabilityUnknown=true"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(generationA>=0L&&world.players().owns(a,generationA))
                world.unregisterPlayer(a,generationA);
            if(b!=null&&generationB>=0L&&
                world.players().owns(b,generationB))
                world.unregisterPlayer(b,generationB);
            world.close();
        }
    }

    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G18.9.5 "+reason);
    }

    private G1895DailyMoneyMakingSameWorldReplacementLifetimeCharacterizationTest(){}
}
