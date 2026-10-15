package spk.local;

/**
 * G18.8.3: a post-settlement consumer callback returning normally
 * does NOT attest that an objective existed, credit was applied,
 * or a durable ACK was recorded. This uses a real spawned NPC,
 * the installed observer, and an opt-in memory-only report.
 */
public final class G1883TerminalReportReturnedNotCreditedTest {
    private static final String USER="g1883-returned-not-credited";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        try {
            generation=world.registerPlayer(owner,USER);
            check(world.localLabDailyMoneyMaking().snapshot(USER)==null&&
                    world.localLabTaskScrolls().active(USER)==null&&
                    world.localLabAdventures().objective(USER)==null,
                "G18/G15/G16 objectives never explicitly activated");

            LocalLabTerminalStageIsolation isolation=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world,isolation);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawn=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=spawn.spawn.combat.spawn.npc;
            check(world.npcLifecycle().applyDamage(
                    npc.id,99,2701L).newlyDied,
                "genuinely killed canonical NPC");

            MonsterSpawnerPvmRuntime.FinalizeResult result=
                installed.runtime().finalizeIfOwned(npc);
            check(result!=null&&
                    result.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    result.settlement!=null&&
                    result.settlement.groundItems.size()==1&&
                    result.settlement.groundItems.get(0)
                        .settledAmount==1&&
                    world.pvmRecords().receiptCount()==1&&
                    world.pvmRecords().snapshot(owner).kills==1L,
                "real G1 and ground settlement succeed");
            check(isolation.history().size()==1,
                "one diagnostic after one terminal");
            LocalLabTerminalStageIsolation.Report report=
                isolation.history().get(0);
            check(report.npcId.equals(npc.id)&&
                    report.deathTick==2701L&&
                    report.spawnOriginMatchedAtDispatch&&
                    report.outcome(
                        LocalLabTerminalStageIsolation.Stage.G15)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                    report.outcome(
                        LocalLabTerminalStageIsolation.Stage.G16)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                    report.outcome(
                        LocalLabTerminalStageIsolation.Stage.G18)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED,
                "callbacks returned normally without asserting awards");

            check(world.localLabDailyMoneyMaking().snapshot(USER)==null&&
                    world.localLabTaskScrolls().active(USER)==null&&
                    world.localLabAdventures().objective(USER)==null,
                "returned-stage diagnostics cannot mean G15/G16/G18 credit");
            check(installed.runtime().finalizeIfOwned(npc).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    isolation.history().size()==1,
                "old NPC cannot be reapplied from diagnostic history");

            System.out.println(
                "G1883_TERMINAL_STAGE_RETURNED_NOT_CREDITED_PASS"+
                " genuineNpcDeath=true"+
                " g1ReceiptOne=true"+
                " groundSettlementOne=true"+
                " g15ReturnedButNoObjective=true"+
                " g16ReturnedButNoObjective=true"+
                " g18ReturnedButNoObjective=true"+
                " diagnosticNotAnAck=true"+
                " durableOutbox=false"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(generation>=0L&&world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            world.close();
        }
    }

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G18.8.3 "+why);
    }

    private G1883TerminalReportReturnedNotCreditedTest(){}
}
