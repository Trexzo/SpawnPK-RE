package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.0.7 / issue #2558: actual ISAAC-framed current client NPC Attack
 * opcode72 accepted for player B, terminal source comes from canonical
 * World, but CUSTOM_LOCALLAB Monster Spawner currently credits its
 * preselected spawn recipient A. This is evidence of a LOCAL mismatch,
 * NOT proof of original SpawnPK last-hit/reward authority.
 */
public final class G1907DecodedLethalHitVsSpawnerRecipientCharacterizationTest {
    private static final String USER_A="g1907-owner";
    private static final String USER_B="g1907-attacker";

    static final class Evidence {
        final boolean sameAttackerAndSpawnerRecipient;
        final int acceptedDamage;
        final boolean acceptedLethal;
        final long spawnerRecipientG1Kills;
        final long separateAttackerG1Kills;
        final int g1Receipts;
        final int coinSettlements;
        final boolean exactAttackerWitness;
        final boolean sourceWorldMatched;
        final boolean spawnRecipientOriginMatched;

        Evidence(
            boolean sameAttackerAndSpawnerRecipient,
            int acceptedDamage,
            boolean acceptedLethal,
            long spawnerRecipientG1Kills,
            long separateAttackerG1Kills,
            int g1Receipts,
            int coinSettlements,
            boolean exactAttackerWitness,
            boolean sourceWorldMatched,
            boolean spawnRecipientOriginMatched
        ){
            this.sameAttackerAndSpawnerRecipient=
                sameAttackerAndSpawnerRecipient;
            this.acceptedDamage=acceptedDamage;
            this.acceptedLethal=acceptedLethal;
            this.spawnerRecipientG1Kills=spawnerRecipientG1Kills;
            this.separateAttackerG1Kills=separateAttackerG1Kills;
            this.g1Receipts=g1Receipts;
            this.coinSettlements=coinSettlements;
            this.exactAttackerWitness=exactAttackerWitness;
            this.sourceWorldMatched=sourceWorldMatched;
            this.spawnRecipientOriginMatched=spawnRecipientOriginMatched;
        }
    }

    static Evidence run(boolean attackerIsSpawnerRecipient)
        throws Exception{
        String suffix=attackerIsSpawnerRecipient?"g1908":"g1907";
        String userA=suffix+"-owner";
        String userB=suffix+"-attacker";
        World world=World.isolatedForTest(600L);
        WorldPlayer owner=new WorldPlayer();
        WorldPlayer other=null;
        long ownerGeneration=-1L,otherGeneration=-1L;
        try{
            ownerGeneration=world.registerPlayer(owner,userA);
            WorldPlayer attacker;
            long generation;
            if(attackerIsSpawnerRecipient){
                attacker=owner;
                generation=ownerGeneration;
            }else{
                other=new WorldPlayer();
                otherGeneration=world.registerPlayer(other,userB);
                attacker=other;
                generation=otherGeneration;
            }
            attacker.movement().restoreAccountState(
                false,100,3087,3495,0);

            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                userA,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(userA,0);
            spawner.activate(userA,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                installed.runtime().spawnAndBind(
                    userA,userA,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(npc.definitionId==
                    LocalLabMonsterSpawnerProvisioning.NPC_DEFINITION_ID&&
                    spawned.runtime.recipientOrigin!=null&&
                    spawned.runtime.recipientOrigin.player==owner&&
                    spawned.runtime.recipientOrigin.generation==
                        ownerGeneration,
                "actual Monster Spawner NPC preserves A spawn recipient");

            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),attacker.id());
            NpcEntity scene=registry.spawnMirroredNpc(
                npc.definitionId,3088,3495,null,
                attacker.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            scene.bindCanonicalId(npc.id);
            NpcActionClientRequest request=
                G1903DecodedOpcode72AcceptedHitWitnessTest.decodeExactAttack(
                    scene.sceneIndex,
                    new int[]{5101,5102,5103,5104});
            check(request.action().opcode==72&&
                    request.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT&&
                    NpcInteractionRouter.resolve(
                        request.action(),scene).service==
                            NpcInteractionRouter.Service.ATTACK,
                "actual configured spawner NPC is current-client option2 Attack");

            final long exactGeneration=generation;
            final MonsterSpawnerPvmRuntime.FinalizeResult[] terminal={null};
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,attacker,()->exactGeneration,
                    attacker.equipment(),attacker.combatStyles(),registry,
                    (target,expectedGeneration)->{},
                    target->terminal[0]=
                        installed.runtime().finalizeIfOwned(target)
                );
            ByteArrayOutputStream hitBytes=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result hit=
                handler.handle(
                    request.action(),scene,
                    G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                        hitBytes));
            check(hit!=null&&hit.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                    hit.acceptedHit!=null&&
                    hit.acceptedHit.attacker==attacker&&
                    hit.acceptedHit.generation==generation&&
                    hit.acceptedHit.world==world&&
                    hit.acceptedHit.targetNpcId.equals(npc.id)&&
                    hit.acceptedHit.newlyDied&&
                    hit.acceptedHit.stillCurrentIn(world)&&
                    hit.newlyDied&&hit.hitpointsAfter==0&&
                    hitBytes.size()>0,
                "genuine decoded opcode72 lethal hit carries exact attacker witness");
            MonsterSpawnerPvmRuntime.FinalizeResult result=terminal[0];
            check(result!=null&&result.sourceWorld==world&&
                    result.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    result.recipientOrigin!=null&&
                    result.recipientOrigin.player==owner&&
                    result.settlement!=null&&
                    result.settlement.groundItems.size()==1&&
                    result.settlement.groundItems.get(0).settledAmount==1&&
                    installed.runtime().get(npc.id)==null,
                "authentic lethal click settled NPC once through spawner");

            long ownerKills=world.pvmRecords().snapshot(owner).kills;
            long otherKills=other==null?0L:
                world.pvmRecords().snapshot(other).kills;
            int receipts=world.pvmRecords().receiptCount();
            int coins=world.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&userA.equals(item.owner))
                .mapToInt(item->item.amount).sum();

            check(ownerKills==1L&&otherKills==0L&&
                    receipts==1&&coins==1&&
                    installed.runtime().finalizeIfOwned(npc).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "current LocalLab G1 credits spawn recipient even when B hit");
            return new Evidence(
                attackerIsSpawnerRecipient,
                hit.appliedDamage,hit.acceptedHit.newlyDied,
                ownerKills,otherKills,receipts,coins,
                hit.acceptedHit.attacker==attacker&&
                    hit.acceptedHit.world==world,
                result.sourceWorld==world,
                result.recipientOrigin.player==owner
            );
        }finally{
            if(ownerGeneration>=0L&&
                world.players().owns(owner,ownerGeneration))
                world.unregisterPlayer(owner,ownerGeneration);
            if(other!=null&&otherGeneration>=0L&&
                world.players().owns(other,otherGeneration))
                world.unregisterPlayer(other,otherGeneration);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        Evidence e=run(false);
        check(!e.sameAttackerAndSpawnerRecipient&&
                e.acceptedDamage==10&&e.acceptedLethal&&
                e.spawnerRecipientG1Kills==1L&&
                e.separateAttackerG1Kills==0L&&
                e.g1Receipts==1&&e.coinSettlements==1&&
                e.exactAttackerWitness&&e.sourceWorldMatched&&
                e.spawnRecipientOriginMatched,
            "real B-accepted lethal click coexists with A recipient credit");
        System.out.println(
            "G1907_DECODED_LETHAL_HIT_VS_SPAWNER_RECIPIENT_PASS"+
            " realIsaacOpcode72Accepted=true"+
            " serverWitnessAttackerB=true"+
            " spawnerRecipientA=true"+
            " g1CreditOnlyA=true"+
            " groundCoinOwnerA=true"+
            " actualNpcSettlementOne=true"+
            " originalSpawnpkKillPolicyUnknown=true"+
            " noRewardPolicyChange=true");
    }

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G19.0.7 "+reason);
    }

    private G1907DecodedLethalHitVsSpawnerRecipientCharacterizationTest(){}
}
