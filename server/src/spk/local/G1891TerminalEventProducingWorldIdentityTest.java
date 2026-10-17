package spk.local;

/**
 * G18.9.1 — binding the dispatcher alone cannot reject a settled
 * World A event delivered to World B's correctly bound dispatcher.
 * The canonical terminal must carry its source World object identity.
 */
public final class G1891TerminalEventProducingWorldIdentityTest {
    private static final String USER="g1891-terminal-event-world";

    public static void main(String[] args)throws Exception{
        World a=World.isolatedForTest(60_000L);
        World b=World.isolatedForTest(60_000L);
        WorldPlayer playerA=new WorldPlayer();
        WorldPlayer playerB=new WorldPlayer();
        long genA=-1L, genB=-1L;
        try{
            genA=a.registerPlayer(playerA,USER);
            genB=b.registerPlayer(playerB,USER);
            LocalLabTaskScrollRuntime taskA=a.localLabTaskScrolls();
            LocalLabTaskScrollRuntime taskB=b.localLabTaskScrolls();
            check(taskA.assign(USER,a.clock().tick()).assignedNow&&
                    taskB.assign(USER,b.clock().tick()).assignedNow,
                "two independent genuinely activated G15 tasks");

            LocalLabTerminalStageIsolation dispatcherA=
                new LocalLabTerminalStageIsolation();
            LocalLabTerminalStageIsolation dispatcherB=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime installedA=
                LocalLabMonsterSpawnerProvisioning.create(a,dispatcherA);
            LocalMonsterSpawnerActivationRuntime installedB=
                LocalLabMonsterSpawnerProvisioning.create(b,dispatcherB);

            WorldNpc npcA=spawn(installedA);
            check(a.npcLifecycle().applyDamage(
                    npcA.id,99,8101L).newlyDied,
                "real World A NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult eventA=
                installedA.runtime().finalizeIfOwned(npcA);
            check(eventA!=null&&eventA.sourceWorld==a&&
                    eventA.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    eventA.settlement!=null&&
                    eventA.settlement.groundItems.size()==1&&
                    taskA.active(USER).objective.progress==1L&&
                    taskB.active(USER).objective.progress==0L&&
                    a.pvmRecords().receiptCount()==1&&
                    b.pvmRecords().receiptCount()==0,
                "genuine terminal carries World A provenance only");

            boolean refused=false;
            try{
                // B dispatcher is correctly B-bound and recipient name
                // matches. This is the bypass left by G18.9.0.
                dispatcherB.deliver(b,eventA);
            }catch(IllegalStateException expected){
                refused="TERMINAL_EVENT_SOURCE_WORLD_MISMATCH"
                    .equals(expected.getMessage());
            }
            check(refused&&
                    taskA.active(USER).objective.progress==1L&&
                    taskB.active(USER).objective.progress==0L&&
                    dispatcherA.diagnosticSnapshot().observedCount==1L&&
                    dispatcherB.diagnosticSnapshot().observedCount==0L&&
                    a.pvmRecords().receiptCount()==1&&
                    b.pvmRecords().receiptCount()==0,
                "wrong producer rejected before B callback/report/record");

            WorldNpc npcB=spawn(installedB);
            check(b.npcLifecycle().applyDamage(
                    npcB.id,99,8111L).newlyDied,
                "real independent World B NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult eventB=
                installedB.runtime().finalizeIfOwned(npcB);
            check(eventB!=null&&eventB.sourceWorld==b&&
                    eventB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    eventB.settlement!=null&&
                    eventB.settlement.groundItems.size()==1&&
                    taskA.active(USER).objective.progress==1L&&
                    taskB.active(USER).objective.progress==1L&&
                    a.pvmRecords().receiptCount()==1&&
                    b.pvmRecords().receiptCount()==1&&
                    dispatcherA.diagnosticSnapshot().observedCount==1L&&
                    dispatcherB.diagnosticSnapshot().observedCount==1L,
                "each proper producing World accepts only its own terminal");

            check(installedA.runtime().finalizeIfOwned(npcA).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    installedB.runtime().finalizeIfOwned(npcB).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "neither already-settled NPC re-finalizes");
            System.out.println(
                "G1891_TERMINAL_EVENT_PRODUCING_WORLD_IDENTITY_PASS"+
                " canonicalResultCarriesExactWorldObject=true"+
                " correctlyBoundOtherWorldDispatcherRejectsEvent=true"+
                " twoRealNpcDeathsAndSettlements=true"+
                " g15ProgressOnePerWorld=true"+
                " g1ReceiptOnePerWorld=true"+
                " diagnosticReportsOnePerWorld=true"+
                " noCrossWorldDuplicate=true"+
                " noDurableRewardAck=true"+
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

    private static WorldNpc spawn(
        LocalMonsterSpawnerActivationRuntime installed
    )throws Exception{
        MonsterSpawnerService service=installed.service();
        service.openSession(
            USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
        service.selectRow(USER,0);
        service.activate(USER,1);
        return installed.runtime().spawnAndBind(
            USER,USER,3088,3495,0).spawn.combat.spawn.npc;
    }

    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G18.9.1 "+reason);
    }

    private G1891TerminalEventProducingWorldIdentityTest(){}
}
