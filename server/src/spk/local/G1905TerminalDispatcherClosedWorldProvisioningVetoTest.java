package spk.local;

/**
 * G19.0.5: package-local test/extension injection may never install
 * a terminal diagnostic dispatcher into a World already closed.
 * This check is independent of the dispatcher identity check.
 */
public final class G1905TerminalDispatcherClosedWorldProvisioningVetoTest {
    private static final String USER="g1905-closed-provision";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        boolean closed=false;
        try{
            generation=world.registerPlayer(owner,USER);
            LocalLabTerminalStageIsolation installedDispatcher=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(
                    world,installedDispatcher);
            MonsterSpawnerService service=installed.service();
            service.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            service.selectRow(USER,0);
            service.activate(USER,1);
            WorldNpc npc=installed.runtime().spawnAndBind(
                USER,USER,3088,3495,0).spawn.combat.spawn.npc;
            check(world.npcLifecycle().applyDamage(
                    npc.id,99,14601L).newlyDied,
                "real preclose NPC must die");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                installed.runtime().finalizeIfOwned(npc);
            check(terminal!=null&&
                    terminal.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&
                    terminal.settlement.groundItems.size()==1&&
                    installedDispatcher.diagnosticSnapshot()
                        .observedCount==1L&&
                    world.pvmRecords().receiptCount()==1,
                "preclose installed dispatcher finalizes exactly once");

            check(world.unregisterPlayer(owner,generation),
                "original current owner leaves");
            world.close();
            closed=true;
            check(world.closed(),"actual World close completed");

            boolean oldInstanceRefused=false;
            try{
                installedDispatcher.bindTo(world);
            }catch(IllegalStateException failure){
                oldInstanceRefused=
                    "TERMINAL_DIAGNOSTICS_WORLD_CLOSED"
                        .equals(failure.getMessage());
            }
            LocalLabTerminalStageIsolation unbound=
                new LocalLabTerminalStageIsolation();
            boolean newInstanceRefused=false;
            try{
                unbound.bindTo(world);
            }catch(IllegalStateException failure){
                newInstanceRefused=
                    "TERMINAL_DIAGNOSTICS_WORLD_CLOSED"
                        .equals(failure.getMessage());
            }
            LocalLabTerminalStageIsolation injected=
                new LocalLabTerminalStageIsolation();
            boolean factoryRefused=false;
            try{
                LocalLabMonsterSpawnerProvisioning.create(world,injected);
            }catch(IllegalStateException failure){
                factoryRefused=
                    "TERMINAL_DIAGNOSTICS_WORLD_CLOSED"
                        .equals(failure.getMessage());
            }
            check(oldInstanceRefused&&newInstanceRefused&&factoryRefused&&
                    installedDispatcher.diagnosticSnapshot()
                        .observedCount==1L&&
                    unbound.diagnosticSnapshot().observedCount==0L&&
                    injected.diagnosticSnapshot().observedCount==0L,
                "closed World refuses old/new injected observers, no extra reports");

            System.out.println(
                "G1905_CLOSED_WORLD_TERMINAL_PROVISIONING_VETO_PASS"+
                " actualNpcDeathBeforeClose=true"+
                " g1ReceiptOneBeforeClose=true"+
                " closedWorldOldRebindDenied=true"+
                " closedWorldFreshBindingDenied=true"+
                " closedWorldFactoryInjectionDenied=true"+
                " noPostcloseStageReports=true"+
                " noPolicyResetOrGrant=true"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(!closed){
                if(generation>=0L&&world.players().owns(owner,generation))
                    world.unregisterPlayer(owner,generation);
                world.close();
            }
        }
    }

    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G19.0.5 "+reason);
    }

    private G1905TerminalDispatcherClosedWorldProvisioningVetoTest(){}
}
