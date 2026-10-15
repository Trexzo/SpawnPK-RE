package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.0.9 / issue #2558: a stale, previously authenticated handler must
 * never initiate dead-NPC finalization retry when its session was replaced.
 * It decodes genuine current-client opcode72 before testing an already-dead
 * canonical NPC. This checks LOCAL callback admission only; it cannot prove
 * original SpawnPK server's reward recipient or replay behavior.
 */
public final class G1909StaleDeadNpcRetrySessionFenceTest {
    private static final String USER="g1909-dead-retry-owner";

    static void run(boolean tryStaleHandler)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer original=new WorldPlayer();
        WorldPlayer replacement=null;
        long originalGeneration=-1L,replacementGeneration=-1L;
        try{
            originalGeneration=world.registerPlayer(original,USER);
            original.movement().restoreAccountState(
                false,100,3087,3495,0);
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest.attackDefinition();
            WorldNpc target=world.npcs().spawn(
                definition,3088,3495,0);
            world.npcLifecycle().register(
                target,20,"CUSTOM_LOCALLAB_G1909_DEAD_RETRY_TEST");

            NpcRegistry oldRegistry=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),original.id());
            NpcEntity oldView=oldRegistry.spawnMirroredNpc(
                definition,3088,3495,null,original.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            oldView.bindCanonicalId(target.id);
            NpcActionClientRequest oldPacket=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        oldView.sceneIndex,new int[]{1909,1910,1911,1912});
            check(oldPacket.action().opcode==72&&
                    oldPacket.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "real ISAAC-framed old-session packet was decoded");

            final long capturedOld=originalGeneration;
            final int[] staleRetryCalls={0};
            LocalCanonicalNpcAttackHandler oldHandler=
                new LocalCanonicalNpcAttackHandler(
                    world,original,()->capturedOld,
                    original.equipment(),original.combatStyles(),
                    oldRegistry,
                    (npc,generation)->{},
                    npc->{},
                    npc->{staleRetryCalls[0]++;});

            NpcLifecycleService.DamageResult died=
                world.npcLifecycle().applyDamage(
                    target.id,20,1909L);
            check(died.newlyDied&&
                    world.npcLifecycle().get(target.id).dead(),
                "canonical target must already be dead before retry");

            check(world.unregisterPlayer(
                    original,originalGeneration),
                "exact previous session removed");
            replacement=new WorldPlayer();
            replacementGeneration=world.registerPlayer(
                replacement,USER);
            replacement.movement().restoreAccountState(
                false,100,3087,3495,0);
            check(world.players().byName(USER)==replacement&&
                    world.players().owns(
                        replacement,replacementGeneration)&&
                    !world.players().owns(
                        original,originalGeneration),
                "replacement is current despite same username");

            if(tryStaleHandler){
                ByteArrayOutputStream stalePackets=
                    new ByteArrayOutputStream();
                LocalCanonicalNpcAttackHandler.Result stale=
                    oldHandler.handle(
                        oldPacket.action(),oldView,
                        G1903DecodedOpcode72AcceptedHitWitnessTest
                            .writer(stalePackets));
                check(stale!=null&&
                        stale.status==
                            LocalCanonicalNpcAttackHandler.Status.STALE_PLAYER&&
                        stale.acceptedHit==null&&
                        stale.appliedDamage==0&&
                        staleRetryCalls[0]==0&&
                        stalePackets.size()==0&&
                        world.npcLifecycle().get(target.id).dead(),
                    "stale session must be rejected before dead retry callback");
            }

            NpcRegistry newRegistry=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),replacement.id());
            NpcEntity newView=newRegistry.spawnMirroredNpc(
                definition,3088,3495,null,replacement.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            newView.bindCanonicalId(target.id);
            NpcActionClientRequest newPacket=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        newView.sceneIndex,new int[]{1913,1914,1915,1916});
            final long capturedReplacement=replacementGeneration;
            final int[] currentRetryCalls={0};
            LocalCanonicalNpcAttackHandler currentHandler=
                new LocalCanonicalNpcAttackHandler(
                    world,replacement,()->capturedReplacement,
                    replacement.equipment(),replacement.combatStyles(),
                    newRegistry,
                    (npc,generation)->{},
                    npc->{},
                    npc->{
                        check(npc==target,
                            "current callback receives exact canonical NPC");
                        currentRetryCalls[0]++;
                    });
            ByteArrayOutputStream currentPackets=
                new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result current=
                currentHandler.handle(
                    newPacket.action(),newView,
                    G1903DecodedOpcode72AcceptedHitWitnessTest
                        .writer(currentPackets));
            check(current!=null&&
                    current.status==
                        LocalCanonicalNpcAttackHandler.Status.TARGET_DEAD&&
                    current.acceptedHit==null&&
                    current.appliedDamage==0&&
                    currentRetryCalls[0]==1&&
                    currentPackets.size()==0&&
                    staleRetryCalls[0]==0&&
                    world.npcLifecycle().get(target.id).dead()&&
                    world.pvmRecords().receiptCount()==0&&
                    world.groundItems().snapshot().isEmpty(),
                "current replacement may retry dead finalizer without damage/reward");
        }finally{
            if(originalGeneration>=0L&&
                world.players().owns(original,originalGeneration))
                world.unregisterPlayer(original,originalGeneration);
            if(replacement!=null&&replacementGeneration>=0L&&
                world.players().owns(replacement,replacementGeneration))
                world.unregisterPlayer(replacement,replacementGeneration);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(true);
        System.out.println(
            "G1909_STALE_DEAD_NPC_RETRY_SESSION_FENCE_PASS"+
            " decodedCurrentClientOpcode72=true"+
            " sameNameGenerationReplacement=true"+
            " priorSessionDeadRetryCalls=0"+
            " currentSessionDeadRetryCalls=1"+
            " noRewardGrant=true"+
            " originalSpawnpkKillPolicyUnknown=true");
    }

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G19.0.9 "+why);
    }

    private G1909StaleDeadNpcRetrySessionFenceTest(){}
}
