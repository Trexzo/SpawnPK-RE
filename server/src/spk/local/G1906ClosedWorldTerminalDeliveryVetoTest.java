package spk.local;

/**
 * G19.0.6: a real FINALIZED event from the ordinary default-off
 * Monster Spawner path is still an unclaimed Java result object.
 * After closing its producing World, even an already-bound opt-in
 * diagnostic dispatcher must refuse it BEFORE stage callbacks,
 * before one-shot claim, and before report/counter mutation.
 */
public final class G1906ClosedWorldTerminalDeliveryVetoTest {
    private static final String USER="g1906-closed-delivery";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        boolean closed=false;
        try{
            generation=world.registerPlayer(owner,USER);
            LocalLabTaskScrollRuntime g15=world.localLabTaskScrolls();
            check(g15.assign(USER,world.clock().tick()).assignedNow,
                "genuine G15 objective active before terminal");
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            WorldNpc npc=installed.runtime().spawnAndBind(
                USER,USER,3088,3495,0).spawn.combat.spawn.npc;
            check(world.npcLifecycle().applyDamage(
                    npc.id,99,15501L).newlyDied,
                "real default-off installed NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult canonical=
                installed.runtime().finalizeIfOwned(npc);
            check(canonical!=null&&canonical.sourceWorld==world&&
                    canonical.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    canonical.finalization!=null&&
                    canonical.settlement!=null&&
                    canonical.settlement.groundItems.size()==1&&
                    world.pvmRecords().receiptCount()==1&&
                    g15.active(USER).objective.progress==1L,
                "default sequential installed observer finalized G15/G1 once");

            // This dispatcher is separate, bound to the CORRECT World,
            // but has never claimed canonical. Older code would accept
            // this event after close as another diagnostic dispatch.
            LocalLabTerminalStageIsolation dispatcher=
                new LocalLabTerminalStageIsolation();
            dispatcher.bindTo(world);
            check(dispatcher.diagnosticSnapshot().observedCount==0L,
                "new diagnostic handle starts without claimed events");
            check(world.unregisterPlayer(owner,generation),
                "World owner unregisters");
            world.close();
            closed=true;
            check(world.closed(),"producing World actually closed");

            boolean refused=false;
            try{
                dispatcher.deliver(world,canonical);
            }catch(IllegalStateException failure){
                refused="TERMINAL_DIAGNOSTICS_WORLD_CLOSED"
                    .equals(failure.getMessage());
            }
            boolean repeatRefused=false;
            try{
                dispatcher.deliver(world,canonical);
            }catch(IllegalStateException failure){
                repeatRefused="TERMINAL_DIAGNOSTICS_WORLD_CLOSED"
                    .equals(failure.getMessage());
            }
            LocalLabTerminalStageIsolation.DiagnosticSnapshot result=
                dispatcher.diagnosticSnapshot();
            check(refused&&repeatRefused&&
                    result.observedCount==0L&&
                    result.evictedCount==0L&&
                    result.recentReports.isEmpty()&&
                    result.stage(LocalLabTerminalStageIsolation.Stage.G15)
                        .returned==0L&&
                    result.stage(LocalLabTerminalStageIsolation.Stage.G15)
                        .skipped==0L,
                "postclose delivery refused before any report/rollup");
            System.out.println(
                "G1906_CLOSED_WORLD_TERMINAL_DELIVERY_VETO_PASS"+
                " authenticFinalizedEvent=true"+
                " producingWorldClosed=true"+
                " previouslyBoundDispatcherRefusesDelivery=true"+
                " repeatedPostcloseDeliveryRefused=true"+
                " noReportOrStageTotalsAdded=true"+
                " originalG1ReceiptOne=true"+
                " noReplayOrGrant=true"+
                " durableAck=false"+
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
        if(!value)throw new AssertionError("G19.0.6 "+reason);
    }

    private G1906ClosedWorldTerminalDeliveryVetoTest(){}
}
