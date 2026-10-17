package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.1.1 / #2558: a genuine decoded client NPC Attack cannot accept
 * a server-supplied NpcEntity that is not the precise active scene
 * projection, or a clicked object that disagrees with packet sceneIndex.
 * This is a LocalLab server-source admission rule, not original server
 * semantics or cryptographic replay defense.
 */
public final class G1911CanonicalNpcSceneObjectIdentityFenceTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,"g1911-scene-owner");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            WorldNpc npc=world.npcs().spawn(
                definition,3088,3495,0);
            world.npcLifecycle().register(
                npc,20,"CUSTOM_LOCALLAB_G1911_SCENE_IDENTITY");

            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),
                player.id());
            NpcEntity actual=registry.spawnMirroredNpc(
                definition,3088,3495,null,player.movement(),
                writer(new ByteArrayOutputStream()));
            check(actual!=null,"actual projection published");
            actual.bindCanonicalId(npc.id);
            check(registry.scene(actual.sceneIndex)==actual,
                "actual projection is active registry scene");

            NpcActionClientRequest decoded=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        actual.sceneIndex,
                        new int[]{1911,1912,1913,1914});
            check(decoded.metadata().provenance==
                    ClientRequestProvenance.EXACT_CURRENT_CLIENT&&
                    decoded.action().opcode==72,
                "authentic ISAAC client attack decoded");

            final long gen=generation;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->gen,
                    player.equipment(),player.combatStyles(),
                    registry);

            // A package-local caller could otherwise forge an alternate
            // NpcEntity with the same scene index/canonical ID.
            NpcEntity forged=new NpcEntity(
                actual.sceneIndex,definition,3088,3495);
            forged.bindCanonicalId(npc.id);
            check(forged!=actual&&
                    registry.scene(forged.sceneIndex)!=forged,
                "forged object is not the registered projection");
            ByteArrayOutputStream forgedOut=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result forgedResult=
                handler.handle(
                    decoded.action(),forged,writer(forgedOut));
            check(forgedResult.status==
                    LocalCanonicalNpcAttackHandler.Status.STALE_SCENE&&
                    forgedResult.acceptedHit==null&&
                    forgedResult.appliedDamage==0&&
                    forgedOut.size()==0&&
                    world.npcLifecycle().get(npc.id).hitpoints==20,
                "forged object vetoed before any canonical damage");

            NpcAction wrongSlot=new NpcAction(
                72,actual.sceneIndex-1);
            ByteArrayOutputStream mismatchedOut=
                new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result mismatched=
                handler.handle(
                    wrongSlot,actual,writer(mismatchedOut));
            check(mismatched.status==
                    LocalCanonicalNpcAttackHandler.Status.STALE_SCENE&&
                    mismatched.acceptedHit==null&&
                    mismatched.appliedDamage==0&&
                    mismatchedOut.size()==0&&
                    world.npcLifecycle().get(npc.id).hitpoints==20,
                "action/clicked scene index mismatch also vetoed");

            ByteArrayOutputStream validOut=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result valid=
                handler.handle(
                    decoded.action(),actual,writer(validOut));
            check(valid.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                    valid.appliedDamage==10&&
                    valid.hitpointsAfter==10&&
                    valid.acceptedHit!=null&&
                    valid.acceptedHit.attacker==player&&
                    valid.acceptedHit.generation==generation&&
                    validOut.size()>0&&
                    world.npcLifecycle().get(npc.id).hitpoints==10&&
                    world.pvmRecords().receiptCount()==0,
                "real current projection retains canonical hit semantics");

            System.out.println(
                "G1911_CANONICAL_NPC_SCENE_OBJECT_IDENTITY_FENCE_PASS"+
                " forgedProjectionNoDamage=true"+
                " actionIndexMismatchNoDamage=true"+
                " exactCurrentSceneAccepted=true"+
                " actualDecodedIsaacOpcode72=true"+
                " noRewardGrant=true"+
                " originalServerAttributionUnknown=true");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    static ServerPacketWriter writer(ByteArrayOutputStream sink){
        return G1903DecodedOpcode72AcceptedHitWitnessTest.writer(sink);
    }

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G19.1.1 "+why);
    }

    private G1911CanonicalNpcSceneObjectIdentityFenceTest(){}
}
