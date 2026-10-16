package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * G19.3.5/36: a WorldPlayer monitor serializes other threads, but Java
 * synchronized permits SAME-THREAD recursion from the pre-resolution hook.
 * A nested handler must never land another hit before outer cooldown commit.
 */
public final class G1935ReentrantNpcAttackFenceTest {
    static void run(boolean throwFirstHook)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,throwFirstHook?"g1936-reentry-failure":"g1935-reentry");
            player.movement().restoreAccountState(false,100,3087,3495,0);
            int def=G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
            WorldNpc npc=world.npcs().spawn(def,3088,3495,0);
            world.npcLifecycle().register(
                npc,30,"CUSTOM_LOCALLAB_G1935_REENTRANT");
            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            NpcEntity view=registry.spawnMirroredNpc(
                def,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(view!=null&&registry.scene(view.sceneIndex)==view,
                "valid exact viewer scene");
            view.bindCanonicalId(npc.id);
            NpcActionClientRequest request=
                G1903DecodedOpcode72AcceptedHitWitnessTest.decodeExactAttack(
                    view.sceneIndex,new int[]{1935,1936,1937,1938});
            check(request.action().opcode==72&&
                    request.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "actual client opcode72 request");

            final long currentGeneration=generation;
            final AtomicInteger hookCalls=new AtomicInteger();
            final AtomicReference<LocalCanonicalNpcAttackHandler.Result>
                nested=new AtomicReference<>();
            final ByteArrayOutputStream nestedBytes=
                new ByteArrayOutputStream();
            final LocalCanonicalNpcAttackHandler[] other={null};
            LocalCanonicalNpcAttackHandler.BeforeResolutionHook hook=
                (target,expectedGeneration)->{
                    check(target==npc&&
                            expectedGeneration==currentGeneration,
                        "hook received exact canonical target/owner");
                    if(hookCalls.incrementAndGet()!=1)return;
                    if(throwFirstHook)
                        throw new IllegalStateException(
                            "G1936_INJECTED_BEFORE_RESOLUTION_FAILURE");
                    try{
                        nested.set(other[0].handle(
                            request.action(),view,
                            G1911CanonicalNpcSceneObjectIdentityFenceTest
                                .writer(nestedBytes)));
                    }catch(java.io.IOException failure){
                        throw new IllegalStateException(
                            "nested packet writer failed",failure);
                    }
                };
            LocalCanonicalNpcAttackHandler primary=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->currentGeneration,
                    player.equipment(),player.combatStyles(),registry,hook);
            other[0]=new LocalCanonicalNpcAttackHandler(
                world,player,()->currentGeneration,
                player.equipment(),player.combatStyles(),registry,hook);
            check(primary!=other[0],"two separate handler objects");

            ByteArrayOutputStream firstMask=new ByteArrayOutputStream();
            if(throwFirstHook){
                boolean injected=false;
                try{
                    primary.handle(
                        request.action(),view,
                        G1911CanonicalNpcSceneObjectIdentityFenceTest
                            .writer(firstMask));
                }catch(IllegalStateException expected){
                    injected="G1936_INJECTED_BEFORE_RESOLUTION_FAILURE"
                        .equals(expected.getMessage());
                    if(!injected)throw expected;
                }
                check(injected&&hookCalls.get()==1&&
                        firstMask.size()==0&&
                        world.npcLifecycle().get(npc.id).hitpoints==30&&
                        primary.nextAllowedAttackTick()==0L,
                    "failed hook neither damaged NPC nor published cooldown");

                ByteArrayOutputStream retryMask=new ByteArrayOutputStream();
                LocalCanonicalNpcAttackHandler.Result retry=
                    other[0].handle(
                        request.action(),view,
                        G1911CanonicalNpcSceneObjectIdentityFenceTest
                            .writer(retryMask));
                check(retry.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                        retry.appliedDamage==10&&
                        retry.acceptedHit!=null&&
                        retryMask.size()>0&&
                        world.npcLifecycle().get(npc.id).hitpoints==20&&
                        hookCalls.get()==2&&
                        other[0].nextAllowedAttackTick()>world.clock().tick(),
                    "callback exception released in-flight owner for real retry");
            }else{
                LocalCanonicalNpcAttackHandler.Result outer=
                    primary.handle(
                        request.action(),view,
                        G1911CanonicalNpcSceneObjectIdentityFenceTest
                            .writer(firstMask));
                check(outer.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                        outer.appliedDamage==10&&
                        outer.acceptedHit!=null&&
                        nested.get()!=null&&
                        nested.get().status==
                            LocalCanonicalNpcAttackHandler.Status.CADENCE_BLOCKED&&
                        nested.get().appliedDamage==0&&
                        nested.get().acceptedHit==null&&
                        nestedBytes.size()==0&&
                        firstMask.size()>0&&
                        hookCalls.get()==1&&
                        world.npcLifecycle().get(npc.id).hitpoints==20&&
                        primary.nextAllowedAttackTick()>world.clock().tick(),
                    "inner same-thread handler rejected; outer hits exactly once");

                for(int elapsed=0;elapsed<12;elapsed++)
                    world.clock().advance();
                ByteArrayOutputStream laterMask=new ByteArrayOutputStream();
                LocalCanonicalNpcAttackHandler.Result later=
                    other[0].handle(
                        request.action(),view,
                        G1911CanonicalNpcSceneObjectIdentityFenceTest
                            .writer(laterMask));
                check(later.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                        later.appliedDamage==10&&
                        later.acceptedHit!=null&&
                        laterMask.size()>0&&
                        hookCalls.get()==2&&
                        world.npcLifecycle().get(npc.id).hitpoints==10,
                    "later nonreentrant click through other handler is allowed");
            }
            check(world.pvmRecords().receiptCount()==0,
                "no inferred rewards or duplicate progression receipts");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(false);
        System.out.println(
            "G1935_REENTRANT_NPC_ATTACK_FENCE_PASS"+
            " exactDecodedOpcode72=true"+
            " twoIndependentHandlers=true"+
            " nestedSameThreadRejected=true"+
            " outerExactlyOneHit=true"+
            " laterLegitimateHit=true");
    }

    private static void check(boolean ok,String detail){
        if(!ok)throw new AssertionError("G19.3.5/36 "+detail);
    }

    private G1935ReentrantNpcAttackFenceTest(){}
}
