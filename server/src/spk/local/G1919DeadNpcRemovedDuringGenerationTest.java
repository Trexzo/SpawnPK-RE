package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.1.9–G19.2.2: dead-target retry must not trust canonical
 * ownership or NPC lifecycle observed BEFORE generation sampling.
 * Negative selected-NPC removal versus positive unrelated-NPC
 * removal in one actual isolated World. LOCAL evidence only.
 */
public final class G1919DeadNpcRemovedDuringGenerationTest {
    static final int REMOVE_SELECTED_NPC=0;
    static final int REMOVE_OTHER_NPC=1;
    static final int REMOVE_SELECTED_LIFECYCLE=2;
    static final int REMOVE_OTHER_LIFECYCLE=3;
    static final int RESTART_SELECTED_LIFECYCLE=4;
    static final int RESTART_OTHER_LIFECYCLE=5;
    static final int REDEAD_SELECTED_SAME_TICK=6;
    static final int REDEAD_OTHER_SAME_TICK=7;

    static void run(int mode)throws Exception{
        if(mode<0||mode>7)
            throw new IllegalArgumentException("mode");
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,"g1919-generation-target-"+mode);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int def=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            WorldNpc target=world.npcs().spawn(
                def,3088,3495,0);
            WorldNpc other=world.npcs().spawn(
                def,3088,3496,0);
            world.npcLifecycle().register(
                target,20,"CUSTOM_LOCALLAB_G1919_SELECTED");
            world.npcLifecycle().register(
                other,20,"CUSTOM_LOCALLAB_G1919_UNRELATED");
            check(!target.id.equals(other.id),
                "two independent canonical NPCs");

            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),
                player.id());
            NpcEntity scene=registry.spawnMirroredNpc(
                def,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(scene!=null&&registry.scene(scene.sceneIndex)==scene,
                "selected NPC is actually registered in viewer scene");
            scene.bindCanonicalId(target.id);
            NpcActionClientRequest packet=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        scene.sceneIndex,
                        new int[]{1941,1942,1943,1944});
            check(packet.action().opcode==72&&
                    packet.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "actual ISAAC current-client packet decoded");

            NpcLifecycleService.DamageResult death=
                world.npcLifecycle().applyDamage(target.id,20,1919L);
            check(death.newlyDied&&
                    world.npcLifecycle().get(target.id).dead(),
                "selected NPC was truly canonical dead before handler");
            final NpcLifecycleService.Snapshot originalDeath=
                world.npcLifecycle().get(target.id);
            if(mode==REDEAD_OTHER_SAME_TICK){
                NpcLifecycleService.DamageResult otherDeath=
                    world.npcLifecycle().applyDamage(other.id,20,1919L);
                check(otherDeath.newlyDied,
                    "unrelated NPC must also start dead same tick");
            }
            final NpcLifecycleService.Snapshot originalOtherDeath=
                mode==REDEAD_OTHER_SAME_TICK
                    ?world.npcLifecycle().get(other.id):null;
            final long exactGeneration=generation;
            final int[] supplierCalls={0},callbackCalls={0};
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->{
                        supplierCalls[0]++;
                        boolean removed;
                        switch(mode){
                            case REMOVE_SELECTED_NPC:
                                removed=world.npcs().remove(target.id);
                                break;
                            case REMOVE_OTHER_NPC:
                                removed=world.npcs().remove(other.id);
                                break;
                            case REMOVE_SELECTED_LIFECYCLE:
                                removed=world.npcLifecycle()
                                    .unregisterExact(target);
                                break;
                            case REMOVE_OTHER_LIFECYCLE:
                                removed=world.npcLifecycle()
                                    .unregisterExact(other);
                                break;
                            case RESTART_SELECTED_LIFECYCLE:
                                removed=world.npcLifecycle()
                                    .unregisterExact(target);
                                if(removed)
                                    world.npcLifecycle().register(
                                        target,20,
                                        "CUSTOM_LOCALLAB_G1923_RESTARTED");
                                break;
                            case RESTART_OTHER_LIFECYCLE:
                                removed=world.npcLifecycle()
                                    .unregisterExact(other);
                                if(removed)
                                    world.npcLifecycle().register(
                                        other,20,
                                        "CUSTOM_LOCALLAB_G1924_RESTARTED");
                                break;
                            case REDEAD_SELECTED_SAME_TICK:
                            case REDEAD_OTHER_SAME_TICK:
                                WorldNpc toRestart=
                                    mode==REDEAD_SELECTED_SAME_TICK
                                        ?target:other;
                                NpcLifecycleService.Snapshot initial=
                                    mode==REDEAD_SELECTED_SAME_TICK
                                        ?originalDeath:originalOtherDeath;
                                removed=world.npcLifecycle()
                                    .unregisterExact(toRestart);
                                if(removed){
                                    world.npcLifecycle().register(
                                        toRestart,20,
                                        mode==REDEAD_SELECTED_SAME_TICK
                                          ?"CUSTOM_LOCALLAB_G1919_SELECTED"
                                          :"CUSTOM_LOCALLAB_G1919_UNRELATED");
                                    NpcLifecycleService.DamageResult
                                        again=world.npcLifecycle().applyDamage(
                                            toRestart.id,20,1919L);
                                    NpcLifecycleService.Snapshot newer=
                                        world.npcLifecycle().get(toRestart.id);
                                    check(again.newlyDied&&
                                        newer!=null&&newer.dead()&&
                                        newer.deathTick==initial.deathTick&&
                                        newer.maxHitpoints==initial.maxHitpoints&&
                                        newer.hitpoints==initial.hitpoints&&
                                        newer.sourceAuthority.equals(
                                            initial.sourceAuthority)&&
                                        newer.registrationIdentity!=
                                            initial.registrationIdentity,
                                        "new death matches every public value but not registration");
                                }
                                break;
                            default: throw new AssertionError(mode);
                        }
                        check(removed,
                            "generation supplier must actually remove owner");
                        return exactGeneration;
                    },
                    player.equipment(),player.combatStyles(),
                    registry,(npc,g)->{},
                    npc->{},
                    npc->{
                        check(npc==target,
                            "retry callback can only refer to clicked target");
                        callbackCalls[0]++;
                    });

            ByteArrayOutputStream out=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result result=
                handler.handle(
                    packet.action(),scene,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(out));

            boolean selectedRemoved=
                mode==REMOVE_SELECTED_NPC||
                mode==REMOVE_SELECTED_LIFECYCLE||
                mode==RESTART_SELECTED_LIFECYCLE||
                mode==REDEAD_SELECTED_SAME_TICK;
            LocalCanonicalNpcAttackHandler.Status expected=
                mode==REMOVE_SELECTED_NPC
                    ?LocalCanonicalNpcAttackHandler.Status.STALE_CANONICAL
                    :mode==REMOVE_SELECTED_LIFECYCLE
                        ?LocalCanonicalNpcAttackHandler.Status.LIFECYCLE_MISSING
                        :mode==RESTART_SELECTED_LIFECYCLE||
                         mode==REDEAD_SELECTED_SAME_TICK
                            ?LocalCanonicalNpcAttackHandler.Status.STALE_LIFECYCLE
                            :LocalCanonicalNpcAttackHandler.Status.TARGET_DEAD;
            check(supplierCalls[0]==1&&result!=null&&
                    result.status==expected&&
                    result.acceptedHit==null&&
                    result.appliedDamage==0&&
                    callbackCalls[0]==(selectedRemoved?0:1)&&
                    out.size()==0&&
                    handler.nextAllowedAttackTick()==0L&&
                    world.players().owns(player,generation)&&
                    world.pvmRecords().receiptCount()==0&&
                    world.groundItems().snapshot().isEmpty(),
                "dead NPC callback gated on exact canonical and lifecycle");
            check(registry.scene(scene.sceneIndex)==scene,
                "scene was never detached, proving independent canonical veto");
            if(mode==REMOVE_SELECTED_NPC)
                check(world.npcs().byId(target.id)==null,
                    "selected canonical NPC really removed");
            else
                check(world.npcs().byId(target.id)==target,
                    "selected canonical NPC still retained");
            if(mode==REMOVE_SELECTED_LIFECYCLE)
                check(world.npcLifecycle().get(target.id)==null,
                    "selected lifecycle really removed");
            else if(mode==RESTART_SELECTED_LIFECYCLE)
                check(world.npcLifecycle().get(target.id)!=null&&
                        world.npcLifecycle().get(target.id).alive(),
                    "selected lifecycle restarted alive with new epoch");
            else
                check(world.npcLifecycle().get(target.id).dead(),
                    "selected lifecycle still dead");
        }finally{
            if(generation>=0L&&
                world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(REMOVE_SELECTED_NPC);
        System.out.println(
            "G1919_DEAD_NPC_REMOVED_DURING_GENERATION_PASS"+
            " decodedCurrentClientOpcode72=true"+
            " selectedCanonicalRemoved=true"+
            " noRetryCallback=true"+
            " noNewReward=true"+
            " originalSpawnpkAttributionUnknown=true");
    }

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G19.1.9 "+reason);
    }

    private G1919DeadNpcRemovedDuringGenerationTest(){}
}
