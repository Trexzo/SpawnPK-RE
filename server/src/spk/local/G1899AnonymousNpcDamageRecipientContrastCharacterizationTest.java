package spk.local;

/**
 * G18.9.9 / #2558: CHARACTERIZATION, not original kill-credit policy.
 * Genuine NPC damage is currently applyDamage(npcId,amount,tick):
 * no attacker player/generation is supplied. The receipt belongs to
 * the spawner's recipient name even when a second live player is
 * present and has independent eligible G15/G18 objectives.
 *
 * This does NOT claim that B attacked the NPC. In particular, there
 * is no B-associated attack or damage record to make that claim.
 */
public final class G1899AnonymousNpcDamageRecipientContrastCharacterizationTest {
    private static final String RECIPIENT="g1899-recipient";
    private static final String OTHER="g1899-other-player";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer playerA=new WorldPlayer();
        WorldPlayer playerB=new WorldPlayer();
        long genA=-1L, genB=-1L;
        try{
            genA=world.registerPlayer(playerA,RECIPIENT);
            genB=world.registerPlayer(playerB,OTHER);
            LocalLabTaskScrollRuntime g15=world.localLabTaskScrolls();
            LocalLabDailyMoneyMakingRuntime g18=
                world.localLabDailyMoneyMaking();
            check(g15.assign(RECIPIENT,world.clock().tick()).assignedNow&&
                    g15.assign(OTHER,world.clock().tick()).assignedNow,
                "real G15 objectives for both registered players");
            check(g18.activateEasyIfCurrent(
                    RECIPIENT,playerA,genA).activatedNow&&
                    g18.activateEasyIfCurrent(
                    OTHER,playerB,genB).activatedNow,
                "real EASY objectives for two separate usernames");

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                RECIPIENT,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(RECIPIENT,0);
            spawner.activate(RECIPIENT,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                installed.runtime().spawnAndBind(
                    RECIPIENT,RECIPIENT,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(spawned.runtime.recipientOrigin!=null&&
                    spawned.runtime.recipientOrigin.player==playerA&&
                    spawned.runtime.recipientOrigin.generation==genA,
                "spawn witness names A, not a player damage contributor");

            NpcLifecycleService.Snapshot hp=
                world.npcLifecycle().get(npc.id);
            check(hp!=null&&hp.alive()&&hp.hitpoints>0,
                "genuine live canonical NPC has positive hitpoints");
            NpcLifecycleService.DamageResult nonterminal=
                world.npcLifecycle().applyDamage(
                    npc.id,Math.max(0,hp.hitpoints-1),21001L);
            check(nonterminal!=null&&
                    nonterminal.npcId.equals(npc.id)&&
                    nonterminal.hitpointsAfter==1&&
                    !nonterminal.newlyDied&&
                    nonterminal.sourceAuthority.equals(hp.sourceAuthority),
                "nonterminal anonymous damage changes only NPC HP");

            // The actual signature above contains NO WorldPlayer or
            // generation. This final unit is also anonymous: neither
            // call provides evidence for last-hit or contributor credit.
            NpcLifecycleService.DamageResult terminalDamage=
                world.npcLifecycle().applyDamage(npc.id,1,21002L);
            check(terminalDamage!=null&&
                    terminalDamage.npcId.equals(npc.id)&&
                    terminalDamage.hitpointsAfter==0&&
                    terminalDamage.newlyDied&&
                    terminalDamage.sourceAuthority.equals(hp.sourceAuthority),
                "genuine lethal damage still exposes lifecycle, not damager authority");

            MonsterSpawnerPvmRuntime.FinalizeResult finalized=
                installed.runtime().finalizeIfOwned(npc);
            check(finalized!=null&&finalized.sourceWorld==world&&
                    finalized.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    finalized.settlement!=null&&
                    finalized.settlement.groundItems.size()==1&&
                    finalized.settlement.groundItems.get(0).settledAmount==1&&
                    RECIPIENT.equals(finalized.settlement.recipientRef)&&
                    finalized.recipientOrigin!=null&&
                    finalized.recipientOrigin.player==playerA,
                "real settled event points at A's preselected recipient");

            check(g15.active(RECIPIENT).objective.progress==1L&&
                    g15.active(OTHER).objective.progress==0L&&
                    g18.snapshot(RECIPIENT).objective.progress==1L&&
                    g18.snapshot(OTHER).objective.progress==0L&&
                    world.pvmRecords().receiptCount()==1&&
                    world.pvmRecords().snapshot(playerA).kills==1L&&
                    world.pvmRecords().snapshot(playerB).kills==0L&&
                    installed.runtime().finalizeIfOwned(npc).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "A receives configured LocalLab recipient credit; B remains 0");

            System.out.println(
                "G1899_ANONYMOUS_DAMAGE_RECIPIENT_CONTRAST_CHARACTERIZED_PASS"+
                " twoRealRegisteredPlayers=true"+
                " twoPlayerObjectiveStates=true"+
                " genuineTwoPhaseNpcDamage=true"+
                " damageApiHasNoAttackerArgument=true"+
                " damageSourceAuthorityIsNpcLifecycleAuthority=true"+
                " recipientASelectedAtSpawn=true"+
                " onlyAG15G18AndG1Credited=true"+
                " bContributionNotEstablished=true"+
                " groundSettlementOnce=true"+
                " originalSpawnpkKillPolicyUnknown=true");
        }finally{
            if(genA>=0L&&world.players().owns(playerA,genA))
                world.unregisterPlayer(playerA,genA);
            if(genB>=0L&&world.players().owns(playerB,genB))
                world.unregisterPlayer(playerB,genB);
            world.close();
        }
    }

    private static void check(boolean accepted,String reason){
        if(!accepted)throw new AssertionError("G18.9.9 "+reason);
    }

    private G1899AnonymousNpcDamageRecipientContrastCharacterizationTest(){}
}
