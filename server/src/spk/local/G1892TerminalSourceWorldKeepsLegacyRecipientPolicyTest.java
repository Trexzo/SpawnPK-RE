package spk.local;

/**
 * G18.9.2: event World provenance is NOT an assertion that the
 * original spawn-time WorldPlayer gets the kill. This regression
 * preserves explicit CUSTOM_LOCALLAB terminal-time name policy
 * within one World while refusing cross-World event injection.
 */
public final class G1892TerminalSourceWorldKeepsLegacyRecipientPolicyTest {
    private static final String USER="g1892-same-world-replacement";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long genA=-1L, genB=-1L;
        try{
            genA=world.registerPlayer(original,USER);
            LocalLabTerminalStageIsolation dispatcher=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(
                    world,dispatcher);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);

            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(spawned.runtime.recipientOrigin!=null&&
                    spawned.runtime.recipientOrigin.player==original&&
                    spawned.runtime.recipientOrigin.generation==genA,
                "canonical NPC retains original A spawn-time witness");

            check(world.unregisterPlayer(original,genA),
                "A unregisters before NPC death");
            replacement=new WorldPlayer();
            genB=world.registerPlayer(replacement,USER);
            check(world.players().byName(USER)==replacement&&
                    original!=replacement&&
                    world.players().owns(replacement,genB)&&
                    !world.players().owns(original,genA),
                "distinct current B player identity owns name in SAME World");

            LocalLabTaskScrollRuntime task=world.localLabTaskScrolls();
            LocalLabTaskScrollRuntime.AssignmentResult assigned=
                task.assign(USER,world.clock().tick());
            check(assigned!=null&&assigned.assignedNow&&
                    task.active(USER).objective.progress==0L,
                "B has genuine G15 zero-progress objective");

            check(world.npcLifecycle().applyDamage(
                    npc.id,99,9201L).newlyDied,
                "original A NPC genuinely dies while B logged in");
            MonsterSpawnerPvmRuntime.FinalizeResult event=
                installed.runtime().finalizeIfOwned(npc);
            LocalLabTerminalStageIsolation.DiagnosticSnapshot view=
                installed.terminalDiagnosticSnapshot();
            check(event!=null&&event.sourceWorld==world&&
                    event.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    event.settlement!=null&&
                    event.settlement.groundItems.size()==1&&
                    event.settlement.groundItems.get(0).settledAmount==1&&
                    event.recipientOrigin!=null&&
                    event.recipientOrigin.player==original&&
                    view!=null&&view.observedCount==1L&&
                    view.recentReports.size()==1&&
                    !view.recentReports.get(0)
                        .spawnOriginMatchedAtDispatch&&
                    view.recentReports.get(0).outcome(
                        LocalLabTerminalStageIsolation.Stage.G15)==
                        LocalLabTerminalStageIsolation.Outcome.RETURNED&&
                    task.active(USER).objective.progress==1L&&
                    world.pvmRecords().receiptCount()==1&&
                    world.pvmRecords().snapshot(replacement).kills==1L,
                "same World B retains documented LocalLab name policy");

            check(installed.runtime().finalizeIfOwned(npc).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    world.pvmRecords().receiptCount()==1&&
                    dispatcher.diagnosticSnapshot().observedCount==1L,
                "one actual terminal and no replay");

            System.out.println(
                "G1892_SOURCE_WORLD_PRESERVES_LOCALLAB_REPLACEMENT_POLICY_PASS"+
                " originalSpawnWitnessA=true"+
                " replacementRecipientB=true"+
                " canonicalEventWorldIdentityPreserved=true"+
                " spawnOriginMatchFalse=true"+
                " bG15ProgressOne=true"+
                " bG1KillOne=true"+
                " actualNpcGroundSettlementOne=true"+
                " noOldNpcRefinalization=true"+
                " originalSpawnpkKillPolicyUnknown=true"+
                " durableOutbox=false");
        }finally{
            if(genA>=0L&&world.players().owns(original,genA))
                world.unregisterPlayer(original,genA);
            if(replacement!=null&&genB>=0L&&
                world.players().owns(replacement,genB))
                world.unregisterPlayer(replacement,genB);
            world.close();
        }
    }

    private static void check(boolean condition,String explanation){
        if(!condition)throw new AssertionError("G18.9.2 "+explanation);
    }

    private G1892TerminalSourceWorldKeepsLegacyRecipientPolicyTest(){}
}
