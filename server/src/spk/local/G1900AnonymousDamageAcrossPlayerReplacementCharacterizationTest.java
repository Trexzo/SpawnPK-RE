package spk.local;

/**
 * G19.0.0 / #2558 — CHARACTERIZATION of a single NPC across
 * the A-spawn / A-nonlethal-damage / B-terminal name-reuse window.
 *
 * The lifecycle damage calls have no WorldPlayer attack source. This
 * test DOES NOT claim A or B landed a game-authorized attack, that
 * spawn origin is a verified damager, or that original SpawnPK
 * should credit either party. It records current CUSTOM_LOCALLAB.
 */
public final class G1900AnonymousDamageAcrossPlayerReplacementCharacterizationTest {
    private static final String USER="g1900-named-replacement";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long genA=-1L, genB=-1L;
        try{
            genA=world.registerPlayer(original,USER);
            LocalLabDailyMoneyMakingRuntime dmm=
                world.localLabDailyMoneyMaking();
            check(dmm.activateEasyIfCurrent(
                    USER,original,genA).activatedNow,
                "original A genuinely activates EASY objective");
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult start=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=start.spawn.combat.spawn.npc;
            check(start.runtime.recipientOrigin!=null&&
                    start.runtime.recipientOrigin.player==original&&
                    start.runtime.recipientOrigin.generation==genA,
                "original A present at canonical NPC creation");
            NpcLifecycleService.Snapshot initial=
                world.npcLifecycle().get(npc.id);
            check(initial!=null&&initial.hitpoints>0,
                "spawned genuine NPC has positive hitpoints");

            NpcLifecycleService.DamageResult early=
                world.npcLifecycle().applyDamage(
                    npc.id,Math.max(0,initial.hitpoints-1),22001L);
            check(early!=null&&early.hitpointsAfter==1&&
                    !early.newlyDied&&
                    early.sourceAuthority.equals(initial.sourceAuthority),
                "anonymous damage event before A logout retains live NPC");
            check(dmm.snapshot(USER).objective.progress==0L&&
                    world.pvmRecords().receiptCount()==0,
                "nonterminal damage did not credit or finalize");

            check(world.unregisterPlayer(original,genA),
                "original A exact-generation unregister");
            replacement=new WorldPlayer();
            genB=world.registerPlayer(replacement,USER);
            check(world.players().byName(USER)==replacement&&
                    replacement!=original&&
                    world.players().owns(replacement,genB)&&
                    !world.players().owns(original,genA),
                "B exact identity now current for same name");
            check(dmm.snapshot(USER).objective.progress==0L,
                "B inherits A's previously zero-progress active EASY state");
            LocalLabTaskScrollRuntime task=world.localLabTaskScrolls();
            check(task.assign(USER,world.clock().tick()).assignedNow,
                "B has independently activated valid G15 objective");

            NpcLifecycleService.DamageResult lethal=
                world.npcLifecycle().applyDamage(npc.id,1,22002L);
            check(lethal!=null&&lethal.newlyDied&&
                    lethal.hitpointsBefore==1&&
                    lethal.hitpointsAfter==0&&
                    lethal.sourceAuthority.equals(initial.sourceAuthority),
                "anonymous damage API has no current/replacement attacker witness");
            MonsterSpawnerPvmRuntime.FinalizeResult event=
                installed.runtime().finalizeIfOwned(npc);
            check(event!=null&&
                    event.sourceWorld==world&&
                    event.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    event.finalization!=null&&
                    event.finalization.deathTick==22002L&&
                    event.settlement!=null&&
                    event.settlement.groundItems.size()==1&&
                    event.settlement.groundItems.get(0).settledAmount==1&&
                    USER.equals(event.settlement.recipientRef)&&
                    event.recipientOrigin!=null&&
                    event.recipientOrigin.player==original,
                "one genuine settled NPC still points to original spawn witness");
            check(task.active(USER).objective.progress==1L&&
                    dmm.snapshot(USER).objective.progress==1L&&
                    world.pvmRecords().receiptCount()==1&&
                    world.pvmRecords().snapshot(replacement).kills==1L&&
                    installed.runtime().finalizeIfOwned(npc).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "current B gets name-based G15/G18/G1 despite A spawn origin");
            LocalLabDailyMoneyMakingRuntime.ProgressResult staleCredit=
                dmm.recordMonsterSpawnerFinalizationIfCurrent(
                    USER,original,genA,
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID,
                    22003L);
            check(staleCredit!=null&&!staleCredit.progressed&&
                    dmm.snapshot(USER).objective.progress==1L,
                "exact original A stale DMM API cannot add another credit");
            System.out.println(
                "G1900_ANONYMOUS_DAMAGE_REPLACEMENT_WINDOW_CHARACTERIZED_PASS"+
                " singleCanonicalNpc=true"+
                " aSpawnOriginWitness=true"+
                " anonymousDamageBeforeAndAfterOwnerSwap=true"+
                " twoGenuineNpcDamageCalls=true"+
                " noDamagerOrLastHitEvidenceInLifecycleApi=true"+
                " currentBNameCreditG1G15G18=true"+
                " originalAExactCreditStaleRefused=true"+
                " settledOnceNoReplay=true"+
                " attackerEntitlementUnknown=true"+
                " originalSpawnpkPolicyUnknown=true");
        }finally{
            if(genA>=0L&&world.players().owns(original,genA))
                world.unregisterPlayer(original,genA);
            if(replacement!=null&&genB>=0L&&
                world.players().owns(replacement,genB))
                world.unregisterPlayer(replacement,genB);
            world.close();
        }
    }

    private static void check(boolean good,String reason){
        if(!good)throw new AssertionError("G19.0.0 "+reason);
    }

    private G1900AnonymousDamageAcrossPlayerReplacementCharacterizationTest(){}
}
