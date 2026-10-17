package spk.local;

/**
 * G18.8.1: a real terminal is still settled after its sole
 * WorldPlayer unregisters. Experimental stage isolation observes
 * that no current player exists and never redirects a callback,
 * generates a record, or replays a canonical death.
 *
 * This is not proof of original SpawnPK recipient/attacker policy.
 */
public final class G1881OptinTerminalNoLiveRecipientSkipsStagesTest {
    private static final String USER="g1881-no-live-recipient";

    public static void main(String[] args)throws Exception {
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(original,USER);
            LocalLabTerminalStageIsolation isolation=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world,isolation);
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
                    spawned.runtime.recipientOrigin.generation==generation,
                "real NPC captures original live generation");

            check(world.unregisterPlayer(original,generation),
                "owner genuinely leaves World before death");
            check(world.players().byName(USER)==null,
                "no terminal-time name match");
            check(world.npcLifecycle().applyDamage(
                    npc.id,99,2201L).newlyDied,
                "real NPC genuinely dies after owner departure");
            MonsterSpawnerPvmRuntime.FinalizeResult done=
                installed.runtime().finalizeIfOwned(npc);
            check(done!=null&&
                    done.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    done.settlement!=null&&
                    done.settlement.groundItems.size()==1&&
                    done.settlement.groundItems.get(0).settledAmount==1&&
                    installed.runtime().get(npc.id)==null&&
                    world.pvmRecords().receiptCount()==0&&
                    world.pvmRecords().snapshot(original).kills==0L,
                "drops settled but no G1 recipient record");

            check(isolation.history().size()==1,
                "one diagnostic record for one real terminal");
            LocalLabTerminalStageIsolation.Report report=
                isolation.history().get(0);
            check(report.npcId.equals(npc.id)&&
                    report.deathTick==2201L&&
                    report.recipientRef.equals(USER)&&
                    report.observedGeneration==-1L&&
                    !report.spawnOriginMatchedAtDispatch&&
                    report.outcomes.size()==5,
                "report identifies missing recipient without forwarding");
            for(LocalLabTerminalStageIsolation.Stage stage:
                    LocalLabTerminalStageIsolation.Stage.values()){
                check(report.outcome(stage)==
                        LocalLabTerminalStageIsolation.Outcome
                            .SKIPPED_NO_CURRENT_RECIPIENT,
                    "every progression stage skipped absent recipient "+stage);
            }

            MonsterSpawnerPvmRuntime.FinalizeResult duplicate=
                installed.runtime().finalizeIfOwned(npc);
            check(duplicate.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    isolation.history().size()==1&&
                    world.pvmRecords().receiptCount()==0,
                "no duplicate terminal report or G1 credit");

            System.out.println(
                "G1881_NO_CURRENT_RECIPIENT_SKIPS_ALL_ISOLATED_STAGES_PASS"+
                " realNpcDeath=true"+
                " dropSettlementOnce=true"+
                " g1ReceiptCountZero=true"+
                " fiveStagesSkipped=true"+
                " diagnosticReportOnce=true"+
                " originalSpawnpkPolicy=false"+
                " durableOutbox=false");
        }finally{
            if(generation>=0L&&world.players().owns(original,generation))
                world.unregisterPlayer(original,generation);
            world.close();
        }
    }

    private static void check(boolean success,String reason){
        if(!success)throw new AssertionError("G18.8.1 "+reason);
    }

    private G1881OptinTerminalNoLiveRecipientSkipsStagesTest(){}
}
