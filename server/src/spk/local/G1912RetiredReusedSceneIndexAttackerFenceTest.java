package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.1.2: a retired mirror remains a Java object after viewer-local
 * scene slot reuse. The combat handler must never treat that detached
 * object as live, even when a new scene projection uses the same index.
 * Wire requests contain only the scene index: this DOES NOT establish
 * that an old wire click can be distinguished from a new click after
 * the same slot is reused; it validates server-owned object identity.
 */
public final class G1912RetiredReusedSceneIndexAttackerFenceTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,"g1912-recycled-scene-owner");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            WorldNpc oldNpc=world.npcs().spawn(
                definition,3088,3495,0);
            WorldNpc newNpc=world.npcs().spawn(
                definition,3088,3496,0);
            world.npcLifecycle().register(
                oldNpc,20,"CUSTOM_LOCALLAB_G1912_OLD_NPC");
            world.npcLifecycle().register(
                newNpc,20,"CUSTOM_LOCALLAB_G1912_NEW_NPC");

            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),
                player.id());
            NpcEntity oldView=registry.spawnMirroredNpc(
                definition,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest.writer(
                    new ByteArrayOutputStream()));
            check(oldView!=null,"old NPC projected");
            oldView.bindCanonicalId(oldNpc.id);
            final int reusedSlot=oldView.sceneIndex;
            NpcActionClientRequest staleDecoded=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        reusedSlot,new int[]{2011,2012,2013,2014});
            check(registry.scene(reusedSlot)==oldView,
                "old view was genuinely active before retirement");

            registry.retireMirroredNpcLocal(reusedSlot);
            check(registry.scene(reusedSlot)==null,
                "old projection actually detached");

            player.movement().restoreAccountState(
                false,100,3087,3496,0);
            NpcEntity fresh=registry.spawnMirroredNpc(
                definition,3088,3496,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest.writer(
                    new ByteArrayOutputStream()));
            check(fresh!=null,"fresh NPC projection published");
            fresh.bindCanonicalId(newNpc.id);
            check(fresh.sceneIndex==reusedSlot&&
                    fresh!=oldView&&
                    registry.scene(reusedSlot)==fresh&&
                    !oldNpc.id.equals(newNpc.id),
                "same scene slot reused by a different exact object/NPC");

            final long gen=generation;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->gen,
                    player.equipment(),player.combatStyles(),
                    registry);
            ByteArrayOutputStream staleMask=
                new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result rejected=
                handler.handle(
                    staleDecoded.action(),oldView,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(staleMask));
            check(rejected.status==
                    LocalCanonicalNpcAttackHandler.Status.STALE_SCENE&&
                    rejected.acceptedHit==null&&
                    rejected.appliedDamage==0&&
                    staleMask.size()==0&&
                    world.npcLifecycle().get(oldNpc.id).hitpoints==20&&
                    world.npcLifecycle().get(newNpc.id).hitpoints==20,
                "retired object cannot hit old canonical NPC via reused scene");

            // Separate positive control on the new projection. Both
            // decoded packets share the same numeric index; only the
            // server-owned clicked object distinguishes the calls.
            NpcActionClientRequest currentDecoded=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        fresh.sceneIndex,new int[]{2111,2112,2113,2114});
            ByteArrayOutputStream currentMask=
                new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result accepted=
                handler.handle(
                    currentDecoded.action(),fresh,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(currentMask));
            check(accepted.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                    accepted.acceptedHit!=null&&
                    accepted.acceptedHit.targetNpcId.equals(newNpc.id)&&
                    accepted.acceptedHit.attacker==player&&
                    accepted.appliedDamage==10&&
                    accepted.hitpointsAfter==10&&
                    currentMask.size()>0&&
                    world.npcLifecycle().get(oldNpc.id).hitpoints==20&&
                    world.npcLifecycle().get(newNpc.id).hitpoints==10&&
                    world.pvmRecords().receiptCount()==0,
                "fresh scene can hit only new canonical target");

            System.out.println(
                "G1912_RETIRED_REUSED_SCENE_INDEX_ATTACKER_FENCE_PASS"+
                " previousAndNewSceneSlotEqual=true"+
                " retiredObjectRejectedNoDamage=true"+
                " currentProjectionAccepted=true"+
                " oldCanonicalNpcHpIntact=true"+
                " originalWireReplayIndistinguishable=true"+
                " originalServerAttributionUnknown=true");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G19.1.2 "+why);
    }

    private G1912RetiredReusedSceneIndexAttackerFenceTest(){}
}
