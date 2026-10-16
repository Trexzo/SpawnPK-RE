package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.1.7/18: generation sampling is another callback between the
 * entry-time viewer-scene check and dead-NPC retry. A retired selected
 * projection must not reach retry even for an otherwise live player.
 * An unrelated retired projection must not block the current target.
 */
public final class G1917DeadNpcSceneRetiredDuringGenerationTest {
    static void run(boolean retireSelected)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,retireSelected?
                    "g1917-retire-dead-scene":
                    "g1918-retire-other-scene");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int def=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            WorldNpc target=world.npcs().spawn(
                def,3088,3495,0);
            world.npcLifecycle().register(
                target,20,"CUSTOM_LOCALLAB_G1917_DEAD_SCENE");

            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),
                player.id());
            NpcEntity clicked=registry.spawnMirroredNpc(
                def,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(clicked!=null,"selected projection must be present");
            clicked.bindCanonicalId(target.id);
            NpcEntity other=registry.spawnMirroredNpc(
                def,3088,3496,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(other!=null&&other!=clicked&&
                    other.sceneIndex!=clicked.sceneIndex,
                "independent other projection registered");
            NpcActionClientRequest packet=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        clicked.sceneIndex,
                        new int[]{1931,1932,1933,1934});
            check(packet.action().opcode==72&&
                    packet.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT&&
                    registry.scene(clicked.sceneIndex)==clicked,
                "decoded current-client NPC attack matches selected view");

            NpcLifecycleService.DamageResult dead=
                world.npcLifecycle().applyDamage(target.id,20,1917L);
            check(dead.newlyDied&&
                    world.npcLifecycle().get(target.id).dead(),
                "canonical target is genuinely dead");
            final long savedGeneration=generation;
            final NpcEntity retire=retireSelected?clicked:other;
            final int[] reads={0},retries={0};
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->{
                        reads[0]++;
                        registry.retireMirroredNpcLocal(
                            retire.sceneIndex);
                        check(registry.scene(retire.sceneIndex)==null,
                            "generation supplier retired requested projection");
                        return savedGeneration;
                    },
                    player.equipment(),player.combatStyles(),
                    registry,(npc,g)->{},
                    npc->{},
                    npc->{
                        check(npc==target,
                            "callback target remains canonical dead NPC");
                        retries[0]++;
                    });
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result result=
                handler.handle(
                    packet.action(),clicked,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(out));
            check(reads[0]==1&&
                    result!=null&&
                    result.status==(retireSelected?
                        LocalCanonicalNpcAttackHandler.Status.STALE_SCENE:
                        LocalCanonicalNpcAttackHandler.Status.TARGET_DEAD)&&
                    result.acceptedHit==null&&
                    result.appliedDamage==0&&
                    retries[0]==(retireSelected?0:1)&&
                    out.size()==0&&
                    handler.nextAllowedAttackTick()==0L&&
                    world.players().owns(player,generation)&&
                    world.npcLifecycle().get(target.id).dead()&&
                    world.pvmRecords().receiptCount()==0&&
                    world.groundItems().snapshot().isEmpty(),
                "only an exact still-visible dead NPC can reach retry");
            check(registry.scene(clicked.sceneIndex)==
                    (retireSelected?null:clicked)&&
                    registry.scene(other.sceneIndex)==
                    (retireSelected?other:null),
                "expected selected/other scene identity after sampling");
        }finally{
            if(generation>=0L&&
                world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(true);
        System.out.println(
            "G1917_DEAD_NPC_SCENE_RETIRED_DURING_GENERATION_PASS"+
            " realIsaacDecodedOpcode72=true"+
            " selectedProjectionRetiredDuringGenerationSampling=true"+
            " deadFinalizerRetryDenied=true"+
            " noNewReward=true"+
            " originalServerKillAttributionUnknown=true");
    }

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G19.1.7 "+why);
    }

    private G1917DeadNpcSceneRetiredDuringGenerationTest(){}
}
