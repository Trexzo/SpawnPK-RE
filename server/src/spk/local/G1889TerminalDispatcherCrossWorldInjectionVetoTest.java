package spk.local;

/**
 * G18.8.9: the package-local injection overload MUST NOT allow one
 * observational dispatcher to be installed in two separate Worlds.
 * Both successes use genuinely finalized NPCs and real G1 receipts.
 */
public final class G1889TerminalDispatcherCrossWorldInjectionVetoTest {
    private static final String USER="g1889-cross-world-injection";

    public static void main(String[] args)throws Exception{
        World a=World.isolatedForTest(60_000L);
        World b=World.isolatedForTest(60_000L);
        WorldPlayer playerA=new WorldPlayer();
        WorldPlayer playerB=new WorldPlayer();
        long genA=-1L;
        long genB=-1L;
        try{
            genA=a.registerPlayer(playerA,USER);
            genB=b.registerPlayer(playerB,USER);
            LocalLabTerminalStageIsolation shared=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime runtimeA=
                LocalLabMonsterSpawnerProvisioning.create(a,shared);
            shared.bindTo(a); // harmless identity-preserving repeat
            finalizeOne(runtimeA,a,6501L);
            LocalLabTerminalStageIsolation.DiagnosticSnapshot before=
                runtimeA.terminalDiagnosticSnapshot();
            check(before!=null&&before.observedCount==1L&&
                    a.pvmRecords().receiptCount()==1,
                "real first World has exactly one finalized report");

            boolean refused=false;
            try{
                LocalLabMonsterSpawnerProvisioning.create(b,shared);
            }catch(IllegalStateException expected){
                refused="TERMINAL_DIAGNOSTICS_WORLD_MISMATCH"
                    .equals(expected.getMessage());
            }
            check(refused,
                "same injected observer cannot provision second World");
            check(shared.diagnosticSnapshot().observedCount==1L&&
                    b.pvmRecords().receiptCount()==0,
                "second World refused before any event or shared mutation");

            LocalLabTerminalStageIsolation independent=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime runtimeB=
                LocalLabMonsterSpawnerProvisioning.create(b,independent);
            finalizeOne(runtimeB,b,6509L);
            check(a.pvmRecords().receiptCount()==1&&
                    b.pvmRecords().receiptCount()==1&&
                    runtimeA.terminalDiagnosticSnapshot()
                        .observedCount==1L&&
                    runtimeB.terminalDiagnosticSnapshot()
                        .observedCount==1L&&
                    before.recentReports.size()==1&&
                    shared!=independent&&
                    shared.diagnosticSnapshot().recentReports.size()==1,
                "two separately bound World observers never mix history");

            System.out.println(
                "G1889_TERMINAL_DISPATCHER_CROSS_WORLD_INJECTION_VETO_PASS"+
                " sameWorldRebindSafe=true"+
                " crossWorldInjectionRefusedBeforeGraphCreation=true"+
                " twoActualNpcFinalizations=true"+
                " g1ReceiptsOnePerWorld=true"+
                " diagnosticHistoriesSeparated=true"+
                " noDurableReplay=true"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(genA>=0L&&a.players().owns(playerA,genA))
                a.unregisterPlayer(playerA,genA);
            if(genB>=0L&&b.players().owns(playerB,genB))
                b.unregisterPlayer(playerB,genB);
            a.close();
            b.close();
        }
    }

    private static void finalizeOne(
        LocalMonsterSpawnerActivationRuntime installed,
        World world,
        long deathTick
    )throws Exception{
        MonsterSpawnerService service=installed.service();
        service.openSession(
            USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
        service.selectRow(USER,0);
        service.activate(USER,1);
        MonsterSpawnerPvmRuntime.SpawnResult spawned=
            installed.runtime().spawnAndBind(
                USER,USER,3088,3495,0);
        WorldNpc npc=spawned.spawn.combat.spawn.npc;
        check(world.npcLifecycle().applyDamage(
                npc.id,99,deathTick).newlyDied,
            "genuine lethal NPC damage");
        MonsterSpawnerPvmRuntime.FinalizeResult terminal=
            installed.runtime().finalizeIfOwned(npc);
        check(terminal!=null&&
                terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminal.settlement!=null&&
                terminal.settlement.groundItems.size()==1&&
                terminal.settlement.groundItems.get(0).settledAmount==1&&
                installed.runtime().get(npc.id)==null,
            "real World finalization and coin settlement");
    }

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError("G18.8.9 "+message);
    }

    private G1889TerminalDispatcherCrossWorldInjectionVetoTest(){}
}
