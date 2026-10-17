package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * G19.4.5/46: a long synchronous admission callback can span many World
 * ticks. The next allowed click must be based on actual damage completion,
 * not a stale timestamp captured before the callback.
 */
public final class G1945DelayedResolutionWorldTickCadenceTest {
    static void run(boolean sameTarget)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,sameTarget?"g1945-late-same-target":
                    "g1946-late-different-target");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int def=G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
            WorldNpc a=world.npcs().spawn(def,3088,3495,0);
            WorldNpc b=world.npcs().spawn(def,3087,3496,0);
            world.npcLifecycle().register(
                a,30,"CUSTOM_LOCALLAB_G1945_NPC_A");
            world.npcLifecycle().register(
                b,30,"CUSTOM_LOCALLAB_G1946_NPC_B");
            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            NpcEntity viewA=registry.spawnMirroredNpc(
                def,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest.writer(
                    new ByteArrayOutputStream()));
            NpcEntity viewB=registry.spawnMirroredNpc(
                def,3087,3496,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest.writer(
                    new ByteArrayOutputStream()));
            check(viewA!=null&&viewB!=null&&viewA!=viewB&&
                    registry.scene(viewA.sceneIndex)==viewA&&
                    registry.scene(viewB.sceneIndex)==viewB,
                "two real exact scene projections");
            viewA.bindCanonicalId(a.id);
            viewB.bindCanonicalId(b.id);
            NpcEntity nextView=sameTarget?viewA:viewB;
            NpcActionClientRequest firstRequest=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(viewA.sceneIndex,
                        new int[]{1945,1946,1947,1948});
            NpcActionClientRequest nextRequest=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(nextView.sceneIndex,
                        new int[]{1955,1956,1957,1958});
            check(firstRequest.action().opcode==72&&
                    nextRequest.action().opcode==72&&
                    firstRequest.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT&&
                    nextRequest.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "both clicks genuinely ISAAC decoded");

            final long currentGeneration=generation;
            AtomicInteger callbacks=new AtomicInteger();
            long entryTick=world.clock().tick();
            LocalCanonicalNpcAttackHandler.BeforeResolutionHook hook=
                (npc,ownerGeneration)->{
                    check(ownerGeneration==currentGeneration,
                        "generation remains owned during callback");
                    if(callbacks.incrementAndGet()==1){
                        check(npc==a,"first admission target exact");
                        for(int tick=0;tick<12;tick++)
                            world.clock().advance();
                    }
                };
            LocalCanonicalNpcAttackHandler first=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->currentGeneration,
                    player.equipment(),player.combatStyles(),registry,hook);
            LocalCanonicalNpcAttackHandler second=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->currentGeneration,
                    player.equipment(),player.combatStyles(),registry,hook);
            check(first!=second,"two independently constructed handlers");
            ByteArrayOutputStream firstPacket=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result hit=
                first.handle(
                    firstRequest.action(),viewA,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(firstPacket));
            long afterCompletion=world.clock().tick();
            check(afterCompletion>=entryTick+12&&
                    hit.status==LocalCanonicalNpcAttackHandler.Status.HIT&&
                    hit.appliedDamage==10&&hit.acceptedHit!=null&&
                    firstPacket.size()>0&&
                    first.nextAllowedAttackTick()>afterCompletion&&
                    second.nextAllowedAttackTick()==
                        first.nextAllowedAttackTick()&&
                    world.npcLifecycle().get(a.id).hitpoints==20&&
                    world.npcLifecycle().get(b.id).hitpoints==30,
                "postdamage cooldown anchored to newest observed World tick");

            ByteArrayOutputStream blockedPacket=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result premature=
                second.handle(
                    nextRequest.action(),nextView,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(blockedPacket));
            check(premature.status==
                    LocalCanonicalNpcAttackHandler.Status.CADENCE_BLOCKED&&
                    premature.appliedDamage==0&&
                    premature.acceptedHit==null&&
                    blockedPacket.size()==0&&
                    callbacks.get()==1&&
                    world.npcLifecycle().get(a.id).hitpoints==20&&
                    world.npcLifecycle().get(b.id).hitpoints==30,
                "fresh same-tick second click cannot bypass stale start tick");

            for(int tick=0;tick<12;tick++)world.clock().advance();
            ByteArrayOutputStream laterPacket=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result later=
                second.handle(
                    nextRequest.action(),nextView,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(laterPacket));
            check(later.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                    later.appliedDamage==10&&later.acceptedHit!=null&&
                    laterPacket.size()>0&&callbacks.get()==2&&
                    world.npcLifecycle().get(a.id).hitpoints==
                        (sameTarget?10:20)&&
                    world.npcLifecycle().get(b.id).hitpoints==
                        (sameTarget?30:20)&&
                    world.pvmRecords().receiptCount()==0,
                "legitimate later attack succeeds on expected canonical target");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(true);
        System.out.println(
            "G1945_DELAYED_RESOLUTION_WORLD_TICK_CADENCE_PASS"+
            " exactDecodedOpcode72=true"+
            " twelveWorldTicksAdvancedInsideAdmission=true"+
            " sharedCooldownAnchoredAfterDamage=true"+
            " prematureSameTargetClickBlocked=true"+
            " laterSameTargetHitAllowed=true");
    }

    private static void check(boolean ok,String detail){
        if(!ok)throw new AssertionError("G19.4.5/46 "+detail);
    }

    private G1945DelayedResolutionWorldTickCadenceTest(){}
}
