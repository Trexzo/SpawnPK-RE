package spk.local;

/**
 * G18.9.0 — a settled event from World A cannot be passed to an
 * injected dispatcher with World B: this must fail before running ANY
 * G4/G14/G15/G16/G18 callback, objective change or report append.
 * Two real independently registered players share one normalized name.
 */
public final class G1890TerminalDispatcherWrongWorldDeliveryVetoTest {
    private static final String USER="g1890-terminal-realm";

    public static void main(String[] args)throws Exception{
        World a=World.isolatedForTest(60_000L);
        World b=World.isolatedForTest(60_000L);
        WorldPlayer ownerA=new WorldPlayer();
        WorldPlayer ownerB=new WorldPlayer();
        long genA=-1L;
        long genB=-1L;
        try{
            genA=a.registerPlayer(ownerA,USER);
            genB=b.registerPlayer(ownerB,USER);
            LocalLabTaskScrollRuntime taskA=a.localLabTaskScrolls();
            LocalLabTaskScrollRuntime taskB=b.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult assignedA=
                taskA.assign(USER,a.clock().tick());
            LocalLabTaskScrollRuntime.AssignmentResult assignedB=
                taskB.assign(USER,b.clock().tick());
            check(assignedA!=null&&assignedA.assignedNow&&
                    assignedB!=null&&assignedB.assignedNow&&
                    taskA.active(USER).objective.progress==0L&&
                    taskB.active(USER).objective.progress==0L,
                "both Worlds have independent real G15 objectives");

            LocalLabTerminalStageIsolation dispatcherA=
                new LocalLabTerminalStageIsolation();
            LocalLabTerminalStageIsolation dispatcherB=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime installedA=
                LocalLabMonsterSpawnerProvisioning.create(a,dispatcherA);
            LocalMonsterSpawnerActivationRuntime installedB=
                LocalLabMonsterSpawnerProvisioning.create(b,dispatcherB);

            MonsterSpawnerService service=installedA.service();
            service.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            service.selectRow(USER,0);
            service.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                installedA.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(a.npcLifecycle().applyDamage(
                    npc.id,99,7001L).newlyDied,
                "one genuine World A NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult event=
                installedA.runtime().finalizeIfOwned(npc);
            check(event!=null&&
                    event.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    event.settlement!=null&&
                    event.settlement.groundItems.size()==1&&
                    event.settlement.groundItems.get(0).settledAmount==1&&
                    a.pvmRecords().receiptCount()==1&&
                    taskA.active(USER).objective.progress==1L&&
                    taskB.active(USER).objective.progress==0L,
                "only World A objective was advanced by actual observer");

            long first=dispatcherA.diagnosticSnapshot().observedCount;
            long other=dispatcherB.diagnosticSnapshot().observedCount;
            check(first==1L&&other==0L,
                "separate dispatcher histories before attempted misuse");

            boolean wrongWorldRefused=false;
            try{
                dispatcherA.deliver(b,event);
            }catch(IllegalStateException expected){
                wrongWorldRefused=
                    "TERMINAL_DIAGNOSTICS_WORLD_MISMATCH"
                        .equals(expected.getMessage());
            }
            check(wrongWorldRefused&&
                    dispatcherA.diagnosticSnapshot().observedCount==1L&&
                    dispatcherB.diagnosticSnapshot().observedCount==0L&&
                    taskA.active(USER).objective.progress==1L&&
                    taskB.active(USER).objective.progress==0L&&
                    b.pvmRecords().receiptCount()==0,
                "wrong World rejected before dispatch, credit or journal");

            boolean unboundRefused=false;
            LocalLabTerminalStageIsolation unbound=
                new LocalLabTerminalStageIsolation();
            try{
                unbound.deliver(a,event);
            }catch(IllegalStateException expected){
                unboundRefused=
                    "TERMINAL_DIAGNOSTICS_WORLD_MISMATCH"
                        .equals(expected.getMessage());
            }
            check(unboundRefused&&
                    unbound.diagnosticSnapshot().observedCount==0L&&
                    taskA.active(USER).objective.progress==1L,
                "unbound dispatcher may not replay old terminal");

            check(installedA.runtime().finalizeIfOwned(npc).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    a.pvmRecords().receiptCount()==1&&
                    b.pvmRecords().receiptCount()==0,
                "old genuine NPC never re-finalized or credited in B");

            System.out.println(
                "G1890_TERMINAL_DISPATCHER_WRONG_WORLD_DELIVERY_VETO_PASS"+
                " genuineWorldADeathAndSettlement=true"+
                " sameAccountTwoIndependentWorlds=true"+
                " originalG15ProgressOne=true"+
                " otherG15ProgressZero=true"+
                " wrongWorldCallbackNotInvoked=true"+
                " unboundDispatcherCannotReplay=true"+
                " originalG1ReceiptOne=true"+
                " noDurableRewardAck=true"+
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

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError("G18.9.0 "+message);
    }

    private G1890TerminalDispatcherWrongWorldDeliveryVetoTest(){}
}
