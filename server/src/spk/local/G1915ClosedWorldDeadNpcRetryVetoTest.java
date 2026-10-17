package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.1.5/16: a canonical dead NPC is present in a real World.
 * Closing that World must veto the click's potentially state-mutating
 * terminal retry callback, while the exact current open World still
 * permits its existing retry behavior. This is LOCAL authority only.
 */
public final class G1915ClosedWorldDeadNpcRetryVetoTest {
    static void run(boolean closeBeforeClick)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,closeBeforeClick?
                    "g1915-closed-world":"g1916-open-world");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            WorldNpc target=world.npcs().spawn(
                definition,3088,3495,0);
            world.npcLifecycle().register(
                target,20,"CUSTOM_LOCALLAB_G1915_DEAD_WORLD");
            NpcRegistry npcs=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),
                player.id());
            NpcEntity scene=npcs.spawnMirroredNpc(
                definition,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(scene!=null&&npcs.scene(scene.sceneIndex)==scene,
                "real viewer projection is current");
            scene.bindCanonicalId(target.id);
            NpcActionClientRequest packet=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        scene.sceneIndex,new int[]{1921,1922,1923,1924});
            check(packet.action().opcode==72&&
                    packet.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "real current-client ISAAC opcode72 decoded");
            NpcLifecycleService.DamageResult died=
                world.npcLifecycle().applyDamage(
                    target.id,20,1915L);
            check(died.newlyDied&&
                    world.npcLifecycle().get(target.id).dead(),
                "real canonical NPC already dead");

            final int[] generationReads={0};
            final int[] retryCalls={0};
            final long exactGeneration=generation;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->{
                        generationReads[0]++;
                        return exactGeneration;
                    },
                    player.equipment(),player.combatStyles(),
                    npcs,(npc,expectedGeneration)->{},
                    npc->{},
                    npc->{
                        check(npc==target,
                            "terminal retry receives exact target");
                        retryCalls[0]++;
                    });

            if(closeBeforeClick){
                check(world.players().owns(player,generation),
                    "exact session valid before World.close");
                world.close();
                check(world.closed(),"actual World.close completed");
            }else{
                check(!world.closed()&&
                        world.players().owns(player,generation),
                    "open World and exact current player");
            }

            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result result=
                handler.handle(
                    packet.action(),scene,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(bytes));
            check(result!=null&&
                    result.status==(closeBeforeClick?
                        LocalCanonicalNpcAttackHandler.Status.WORLD_CLOSED:
                        LocalCanonicalNpcAttackHandler.Status.TARGET_DEAD)&&
                    result.acceptedHit==null&&
                    result.appliedDamage==0&&
                    bytes.size()==0&&
                    retryCalls[0]==(closeBeforeClick?0:1)&&
                    generationReads[0]==(closeBeforeClick?0:1)&&
                    handler.nextAllowedAttackTick()==0L&&
                    world.pvmRecords().receiptCount()==0,
                "World-close admission gate preserves open-only callback");
            if(!closeBeforeClick)
                check(world.npcLifecycle().get(target.id).dead()&&
                        world.groundItems().snapshot().isEmpty(),
                    "open World retry made no new damage or rewards");
        }finally{
            if(!world.closed()&&generation>=0L&&
                world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(true);
        System.out.println(
            "G1915_CLOSED_WORLD_DEAD_NPC_RETRY_VETO_PASS"+
            " realClosedWorld=true"+
            " realDecodedOpcode72=true"+
            " retryCallbackCallsZero=true"+
            " generationSupplierCallsZero=true"+
            " noNewHitOrReward=true"+
            " originalSpawnpkServerPolicyUnknown=true");
    }

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G19.1.5 "+reason);
    }

    private G1915ClosedWorldDeadNpcRetryVetoTest(){}
}
