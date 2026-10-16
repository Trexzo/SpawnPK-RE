package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * G19.3.1/32: actual two-thread requests against the same per-session
 * handler must not both pass the tick-cadence check before its HP write.
 * Genuine independently ISAAC-decoded opcode72 requests are used.
 */
public final class G1931ConcurrentSameTickNpcAttackFenceTest {
    static void run(boolean sameTarget)throws Exception{
        run(sameTarget,false);
    }

    static void run(
        boolean sameTarget,
        boolean separateHandlerInstances
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        CountDownLatch allowFirst=new CountDownLatch(1);
        Thread first=null,second=null;
        try{
            generation=world.registerPlayer(
                player,sameTarget?"g1931-double-click":"g1932-two-targets");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int def=G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
            WorldNpc npcA=world.npcs().spawn(def,3088,3495,0);
            WorldNpc npcB=world.npcs().spawn(def,3087,3496,0);
            world.npcLifecycle().register(
                npcA,20,"CUSTOM_LOCALLAB_G1931_A");
            world.npcLifecycle().register(
                npcB,20,"CUSTOM_LOCALLAB_G1932_B");
            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),
                player.id());
            NpcEntity a=registry.spawnMirroredNpc(
                def,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            NpcEntity b=registry.spawnMirroredNpc(
                def,3087,3496,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(a!=null&&b!=null&&a!=b&&
                    registry.scene(a.sceneIndex)==a&&
                    registry.scene(b.sceneIndex)==b,
                "two real distinct projections");
            a.bindCanonicalId(npcA.id);
            b.bindCanonicalId(npcB.id);

            NpcActionClientRequest reqA=
                G1903DecodedOpcode72AcceptedHitWitnessTest.decodeExactAttack(
                    a.sceneIndex,new int[]{1931,1932,1933,1934});
            NpcEntity secondView=sameTarget?a:b;
            NpcActionClientRequest reqSecond=
                G1903DecodedOpcode72AcceptedHitWitnessTest.decodeExactAttack(
                    secondView.sceneIndex,new int[]{2031,2032,2033,2034});
            check(reqA.action().opcode==72&&
                    reqSecond.action().opcode==72&&
                    reqA.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT&&
                    reqSecond.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "both incoming clicks use actual decoded client opcode72");

            final long capturedGeneration=generation;
            final AtomicInteger hookCount=new AtomicInteger();
            final CountDownLatch insideFirstHook=new CountDownLatch(1);
            final CountDownLatch secondStarted=new CountDownLatch(1);
            LocalCanonicalNpcAttackHandler.BeforeResolutionHook hook=
                    (target,expectedGeneration)->{
                        int nth=hookCount.incrementAndGet();
                        if(nth==1){
                            check(target==npcA&&
                                    expectedGeneration==capturedGeneration,
                                "first callback owns first target and generation");
                            insideFirstHook.countDown();
                            try{
                                if(!allowFirst.await(5,TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "G19.3.1 first admission release timeout");
                            }catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(
                                    "G19.3.1 parked attack interrupted",
                                    interrupted
                                );
                            }
                        }
                    };
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->capturedGeneration,
                    player.equipment(),player.combatStyles(),registry,hook);
            LocalCanonicalNpcAttackHandler otherHandler=
                separateHandlerInstances
                    ?new LocalCanonicalNpcAttackHandler(
                        world,player,()->capturedGeneration,
                        player.equipment(),player.combatStyles(),registry,hook)
                    :handler;
            check(!separateHandlerInstances||otherHandler!=handler,
                "second handler instance genuinely independent");
            AtomicReference<LocalCanonicalNpcAttackHandler.Result> rA=
                new AtomicReference<>();
            AtomicReference<LocalCanonicalNpcAttackHandler.Result> rSecond=
                new AtomicReference<>();
            AtomicReference<Throwable> errA=new AtomicReference<>();
            AtomicReference<Throwable> errSecond=new AtomicReference<>();
            ByteArrayOutputStream bytesA=new ByteArrayOutputStream();
            ByteArrayOutputStream bytesSecond=new ByteArrayOutputStream();
            first=new Thread(()->{
                try{
                    rA.set(handler.handle(
                        reqA.action(),a,
                        G1911CanonicalNpcSceneObjectIdentityFenceTest
                            .writer(bytesA)));
                }catch(Throwable error){errA.set(error);}
            },"g1931-first-NPC-attack");
            second=new Thread(()->{
                secondStarted.countDown();
                try{
                    rSecond.set(otherHandler.handle(
                        reqSecond.action(),secondView,
                        G1911CanonicalNpcSceneObjectIdentityFenceTest
                            .writer(bytesSecond)));
                }catch(Throwable error){errSecond.set(error);}
            },"g1931-concurrent-NPC-attack");
            first.start();
            check(insideFirstHook.await(5,TimeUnit.SECONDS),
                "first attack must park inside pre-resolution seam");
            second.start();
            check(secondStarted.await(5,TimeUnit.SECONDS),
                "second request actually launched");

            // The first thread is parked before resolution/HP mutation.
            // Under the per-handler guard a competing handle() cannot
            // reach its own hook or cadence check yet.
            boolean blocked=false;
            for(int attempt=0;attempt<600;attempt++){
                if(second.getState()==Thread.State.BLOCKED){
                    blocked=true;
                    break;
                }
                if(errA.get()!=null||errSecond.get()!=null)
                    break;
                Thread.sleep(5L);
            }
            check(blocked&&hookCount.get()==1&&
                    world.npcLifecycle().get(npcA.id).hitpoints==20&&
                    world.npcLifecycle().get(npcB.id).hitpoints==20,
                "second same-player attack waits while first owns cadence");
            allowFirst.countDown();
            first.join(5000L);
            second.join(5000L);
            check(!first.isAlive()&&!second.isAlive()&&
                    errA.get()==null&&errSecond.get()==null,
                "both request threads finish cleanly"+
                " first="+errA.get()+" second="+errSecond.get());
            check(rA.get()!=null&&rSecond.get()!=null&&
                    rA.get().status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                    rA.get().appliedDamage==10&&
                    rA.get().acceptedHit!=null&&
                    rSecond.get().status==
                        LocalCanonicalNpcAttackHandler.Status.CADENCE_BLOCKED&&
                    rSecond.get().appliedDamage==0&&
                    rSecond.get().acceptedHit==null&&
                    hookCount.get()==1&&
                    bytesA.size()>0&&bytesSecond.size()==0&&
                    world.npcLifecycle().get(npcA.id).hitpoints==10&&
                    world.npcLifecycle().get(npcB.id).hitpoints==20&&
                    handler.nextAllowedAttackTick()>world.clock().tick()&&
                    otherHandler.nextAllowedAttackTick()==
                        handler.nextAllowedAttackTick()&&
                    world.pvmRecords().receiptCount()==0,
                "exactly one hit admitted for same-tick concurrent clicks");

            // Legitimate later same-handler click must not be permanently
            // disabled by the concurrency fence or lose session state.
            for(int tick=0;tick<12;tick++)world.clock().advance();
            ByteArrayOutputStream laterMask=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result later=
                otherHandler.handle(
                    reqSecond.action(),secondView,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(laterMask));
            check(later.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                    later.appliedDamage==10&&
                    later.acceptedHit!=null&&
                    later.acceptedHit.attacker==player&&
                    later.acceptedHit.generation==generation&&
                    laterMask.size()>0&&
                    hookCount.get()==2&&
                    world.npcLifecycle().get(npcA.id).hitpoints==
                        (sameTarget?0:10)&&
                    world.npcLifecycle().get(npcB.id).hitpoints==
                        (sameTarget?20:10),
                "post-cadence legitimate attack still succeeds");
        }finally{
            allowFirst.countDown();
            if(first!=null&&first.isAlive())first.join(5000L);
            if(second!=null&&second.isAlive())second.join(5000L);
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(true);
        System.out.println(
            "G1931_CONCURRENT_SAME_TICK_NPC_ATTACK_FENCE_PASS"+
            " twoRealThreads=true"+
            " exactDecodedOpcode72=true"+
            " oneHitBeforeCooldown=true"+
            " secondSameTickCadenceBlocked=true"+
            " laterCurrentClickAccepted=true"+
            " noExtraReward=true");
    }

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError("G19.3.1 "+message);
    }

    private G1931ConcurrentSameTickNpcAttackFenceTest(){}
}
