package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.2.7/28: immediate canonical damage must be bound to the exact
 * lifecycle registration captured before the resolution seam.
 * The selected canonical WorldNpc and viewer scene stay identical.
 */
public final class G1927LiveNpcLifecycleReplacedBeforeDamageTest {
    static void run(boolean replaceSelected)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,replaceSelected?
                    "g1927-selected-lifecycle":
                    "g1928-unrelated-lifecycle");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int def=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            WorldNpc selected=world.npcs().spawn(
                def,3088,3495,0);
            WorldNpc other=world.npcs().spawn(
                def,3088,3496,0);
            world.npcLifecycle().register(
                selected,20,"CUSTOM_LOCALLAB_G1927_SELECTED");
            world.npcLifecycle().register(
                other,20,"CUSTOM_LOCALLAB_G1928_UNRELATED");
            final NpcLifecycleService.Snapshot firstSelected=
                world.npcLifecycle().get(selected.id);
            final NpcLifecycleService.Snapshot firstOther=
                world.npcLifecycle().get(other.id);
            check(firstSelected.alive()&&firstOther.alive(),
                "both independent NPC lifecycle registrations alive");

            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),
                player.id());
            NpcEntity clicked=registry.spawnMirroredNpc(
                def,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(clicked!=null&&registry.scene(clicked.sceneIndex)==clicked,
                "exact current NPC projection created");
            clicked.bindCanonicalId(selected.id);
            NpcActionClientRequest request=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        clicked.sceneIndex,
                        new int[]{1927,1928,1929,1930});
            check(request.action().opcode==72&&
                    request.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "genuine ISAAC framed opcode72 decoded");

            final long capturedGeneration=generation;
            final int[] hookCalls={0};
            final WorldNpc toRestart=replaceSelected?selected:other;
            final NpcLifecycleService.Snapshot first=
                replaceSelected?firstSelected:firstOther;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->capturedGeneration,
                    player.equipment(),player.combatStyles(),
                    registry,(target,expectedGeneration)->{
                        check(target==selected&&
                                expectedGeneration==capturedGeneration,
                            "resolution seam owns exact selected NPC and generation");
                        hookCalls[0]++;
                        check(world.npcLifecycle().unregisterExact(toRestart),
                            "selected/unrelated NPC lifecycle removed in seam");
                        NpcLifecycleService.Snapshot replacement=
                            world.npcLifecycle().register(
                                toRestart,20,first.sourceAuthority);
                        check(replacement.registrationIdentity!=
                                first.registrationIdentity&&
                                replacement.alive()&&
                                replacement.hitpoints==first.hitpoints&&
                                replacement.maxHitpoints==first.maxHitpoints&&
                                replacement.sourceAuthority.equals(
                                    first.sourceAuthority)&&
                                world.npcs().byId(toRestart.id)==toRestart,
                            "new lifecycle has same public values but different identity");
                    });

            ByteArrayOutputStream output=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result outcome=
                handler.handle(
                    request.action(),clicked,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(output));
            NpcLifecycleService.Snapshot currentSelected=
                world.npcLifecycle().get(selected.id);
            NpcLifecycleService.Snapshot currentOther=
                world.npcLifecycle().get(other.id);
            check(hookCalls[0]==1&&
                    registry.scene(clicked.sceneIndex)==clicked&&
                    world.npcs().byId(selected.id)==selected&&
                    world.players().owns(player,generation)&&
                    currentSelected!=null&&currentOther!=null&&
                    world.pvmRecords().receiptCount()==0&&
                    world.groundItems().snapshot().isEmpty(),
                "canonical and scene owners remain current with no grant");
            if(replaceSelected){
                check(outcome.status==
                        LocalCanonicalNpcAttackHandler.Status.STALE_LIFECYCLE&&
                        outcome.acceptedHit==null&&
                        outcome.appliedDamage==0&&
                        currentSelected.hitpoints==20&&
                        currentSelected.registrationIdentity!=
                            firstSelected.registrationIdentity&&
                        output.size()==0&&
                        handler.nextAllowedAttackTick()==0L,
                    "same canonical object new lifecycle cannot receive stale hit");
            }else{
                check(outcome.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                        outcome.acceptedHit!=null&&
                        outcome.acceptedHit.attacker==player&&
                        outcome.acceptedHit.generation==generation&&
                        outcome.appliedDamage==10&&
                        currentSelected.hitpoints==10&&
                        currentSelected.registrationIdentity==
                            firstSelected.registrationIdentity&&
                        currentOther.registrationIdentity!=
                            firstOther.registrationIdentity&&
                        currentOther.hitpoints==20&&
                        output.size()>0,
                    "unrelated lifecycle replacement preserves accepted hit");
            }
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(true);
        System.out.println(
            "G1927_LIVE_NPC_LIFECYCLE_REPLACED_BEFORE_DAMAGE_PASS"+
            " genuineIsaacOpcode72=true"+
            " canonicalNpcAndSceneUnchanged=true"+
            " exactLifecycleOwnedDamageDenied=true"+
            " newLifecycleHpPreserved=true"+
            " noHitPacketCadenceOrReward=true");
    }

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G19.2.7 "+reason);
    }

    private G1927LiveNpcLifecycleReplacedBeforeDamageTest(){}
}
