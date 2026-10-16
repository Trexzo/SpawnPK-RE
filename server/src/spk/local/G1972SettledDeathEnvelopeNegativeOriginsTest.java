package spk.local;

/**
 * G19.7.2: settled-death envelope distinguishes actual old origin,
 * same-name rebound, disconnected original, unregistered-at-spawn,
 * and a distinct World without mutating established grant policy.
 */
public final class G1972SettledDeathEnvelopeNegativeOriginsTest {
    public static void main(String[] args)throws Exception{
        G1961SameNameRecipientRebindObservationTest.run(false);
        G1959DisconnectedRecipientStageTraceTest.run(false);
        G1959DisconnectedRecipientStageTraceTest.run(true);
        missingAtSpawn();
        System.out.println(
            "G1972_SETTLED_DEATH_ENVELOPE_NEGATIVE_ORIGINS_PASS"+
            " sameNameRebound=true"+
            " noCurrentRecipient=true"+
            " missingSpawnOrigin=true"+
            " foreignWorldRejected=true"+
            " originalNameKeyedCreditPreserved=true"+
            " noReplayOrGrantAuthority=true");
    }

    private static void missingAtSpawn()throws Exception {
        World world=World.isolatedForTest(60_000L);
        final String owner="g1972-unregistered-session-owner";
        final String recipient="g1972-never-registered-recipient";
        try{
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world,null);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                owner,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(owner,0);
            spawner.activate(owner,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                installed.runtime().spawnAndBind(
                    owner,recipient,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(npc!=null&&spawned.runtime.recipientOrigin==null,
                "recipient was never registered at spawn");
            check(world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick()).newlyDied,
                "real canonical lethal NPC");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                installed.runtime().finalizeIfOwned(npc);
            check(terminal!=null&&terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null,
                "actual finalization plus canonical ground settlement");
            LocalLabSettledDeathEnvelope envelope=
                LocalLabSettledDeathEnvelope.observe(world,terminal);
            check(envelope.originStatus==
                    LocalLabSettledDeathEnvelope.OriginStatus.MISSING_SPAWN_ORIGIN&&
                    envelope.spawnRecipientGeneration==-1L&&
                    envelope.observedCurrentGeneration==-1L&&
                    !envelope.spawnRecipientStillCurrent()&&
                    envelope.npcId.equals(npc.id),
                "missing original spawn witness cannot become a current owner claim");
            LocalLabSequentialTerminalStageTrace.Snapshot trace=
                installed.sequentialTerminalDiagnosticSnapshot();
            check(trace.observed==1L&&trace.recent.size()==1&&
                    trace.recent.get(0).originStatus==envelope.originStatus&&
                    trace.originCount(
                        LocalLabSettledDeathEnvelope.OriginStatus.MISSING_SPAWN_ORIGIN)
                        ==1L&&
                    trace.originCount(
                        LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT)
                        ==0L,
                "installed observer retains exact missing-spawn origin aggregate");
            LocalLabTerminalDeliveryAssessment assessment=
                LocalLabTerminalDeliveryAssessment.from(trace.recent.get(0));
            check(assessment.originStatus==envelope.originStatus&&
                    !assessment.hasDurableGrantAcknowledgement()&&
                    !assessment.safeToAutomaticallyReplay(),
                "missing spawn origin cannot fabricate applied credit");
            for(LocalLabSequentialTerminalStageTrace.Stage stage:
                    LocalLabSequentialTerminalStageTrace.Stage.values())
                check(trace.recent.get(0).outcome(stage)==
                    LocalLabSequentialTerminalStageTrace.Outcome
                        .SKIPPED_NO_CURRENT_RECIPIENT,
                    "absent player does not execute consumer "+stage);
            GroundItem drop=world.groundItems().findOwned(
                995,3088,3495,0,recipient);
            check(drop!=null&&drop.amount==1&&
                    world.pvmRecords().receiptCount()==0&&
                    installed.runtime().finalizeIfOwned(npc).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "ground settlement once without creating recipient reward or replay");
        }finally{
            world.close();
        }
    }

    private static void check(boolean value,String message){
        if(!value)throw new AssertionError("G19.7.2 "+message);
    }
    private G1972SettledDeathEnvelopeNegativeOriginsTest(){}
}
