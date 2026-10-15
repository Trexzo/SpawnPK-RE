package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.1.3: the real viewer-local NPC scene can retire AFTER the
 * request's initial identity check, inside the pre-resolution seam.
 * No canonical damage should be admitted once the exact projection
 * no longer belongs to this NpcRegistry.
 *
 * Only entry/post-hook narrowing, not atomic synchronization with
 * every subsequent scene removal or original SpawnPK server proof.
 */
public final class G1913ProjectionRetiredBeforeDamageAdmissionTest {
    static void run(boolean retireSelected)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer attacker=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                attacker,retireSelected?
                    "g1913-scene-race":"g1914-scene-control");
            attacker.movement().restoreAccountState(
                false,100,3087,3495,0);
            int def=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            WorldNpc target=world.npcs().spawn(
                def,3088,3495,0);
            world.npcLifecycle().register(
                target,20,"CUSTOM_LOCALLAB_G1913_SCENE_DRIFT");

            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),attacker.id());
            NpcEntity selected=registry.spawnMirroredNpc(
                def,3088,3495,null,attacker.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(selected!=null,"selected NPC must publish");
            selected.bindCanonicalId(target.id);

            // A second owned viewer scene is used for the positive
            // control, demonstrating that a different retirement does
            // not veto the still-current selected projection.
            NpcEntity other=registry.spawnMirroredNpc(
                def,3088,3496,null,attacker.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(other!=null&&other!=selected&&
                    other.sceneIndex!=selected.sceneIndex,
                "second independent viewer projection must publish");

            NpcActionClientRequest request=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        selected.sceneIndex,
                        new int[]{1915,1916,1917,1918});
            check(request.action().opcode==72&&
                    request.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT&&
                    registry.scene(selected.sceneIndex)==selected,
                "exact current-client attack matches an active scene");

            final long capturedGeneration=generation;
            final int[] callbackCalls={0};
            final NpcEntity toRetire=retireSelected?selected:other;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,attacker,()->capturedGeneration,
                    attacker.equipment(),attacker.combatStyles(),
                    registry,
                    (npc,expectedGeneration)->{
                        check(npc==target&&
                                expectedGeneration==capturedGeneration,
                            "hook uses canonical target and exact generation");
                        callbackCalls[0]++;
                        registry.retireMirroredNpcLocal(
                            toRetire.sceneIndex);
                        check(registry.scene(toRetire.sceneIndex)==null,
                            "projection removed in pre-resolution window");
                    });

            ByteArrayOutputStream bytes=
                new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result result=
                handler.handle(
                    request.action(),selected,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(bytes));

            check(callbackCalls[0]==1,
                "real pre-resolution admission hook invoked once");
            if(retireSelected){
                check(result.status==
                        LocalCanonicalNpcAttackHandler.Status.STALE_SCENE&&
                        result.acceptedHit==null&&
                        result.appliedDamage==0&&
                        bytes.size()==0&&
                        registry.scene(selected.sceneIndex)==null&&
                        world.npcLifecycle().get(target.id).hitpoints==20&&
                        handler.nextAllowedAttackTick()==0L&&
                        world.pvmRecords().receiptCount()==0,
                    "selected projection retirement must veto HP and packet");
            }else{
                check(result.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                        result.acceptedHit!=null&&
                        result.acceptedHit.attacker==attacker&&
                        result.acceptedHit.generation==generation&&
                        result.appliedDamage==10&&
                        result.hitpointsAfter==10&&
                        bytes.size()>0&&
                        registry.scene(selected.sceneIndex)==selected&&
                        registry.scene(other.sceneIndex)==null&&
                        world.npcLifecycle().get(target.id).hitpoints==10&&
                        world.pvmRecords().receiptCount()==0,
                    "unrelated projection retirement must preserve valid hit");
            }
        }finally{
            if(generation>=0L&&
                world.players().owns(attacker,generation))
                world.unregisterPlayer(attacker,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(true);
        System.out.println(
            "G1913_PROJECTION_RETIRED_BEFORE_DAMAGE_ADMISSION_PASS"+
            " genuineDecodedOpcode72=true"+
            " selectedProjectionRetiredDuringResolutionSeam=true"+
            " postHookIdentityCheckVeto=true"+
            " canonicalHpPreserved=true"+
            " noCadenceOrHitPacket=true"+
            " noOriginalServerPolicyClaim=true");
    }

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G19.1.3 "+why);
    }

    private G1913ProjectionRetiredBeforeDamageAdmissionTest(){}
}
