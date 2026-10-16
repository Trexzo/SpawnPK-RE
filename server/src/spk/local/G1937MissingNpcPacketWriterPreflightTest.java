package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * G19.3.7/38: a caller error (null packet writer) must never commit
 * authoritative NPC damage or cooldown and then throw at presentation.
 */
public final class G1937MissingNpcPacketWriterPreflightTest {
    static void run(boolean lethal)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,lethal?"g1938-null-writer-lethal":
                    "g1937-null-writer-nonlethal");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int def=G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
            WorldNpc npc=world.npcs().spawn(def,3088,3495,0);
            int initialHp=lethal?10:20;
            world.npcLifecycle().register(
                npc,initialHp,"CUSTOM_LOCALLAB_G1937_WRITER_PREFLIGHT");
            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            NpcEntity view=registry.spawnMirroredNpc(
                def,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(view!=null&&registry.scene(view.sceneIndex)==view,
                "exact scene projection");
            view.bindCanonicalId(npc.id);
            NpcActionClientRequest request=
                G1903DecodedOpcode72AcceptedHitWitnessTest.decodeExactAttack(
                    view.sceneIndex,new int[]{1937,1938,1939,1940});
            check(request.action().opcode==72&&
                    request.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "genuine independently decoded canonical opcode72");

            AtomicInteger beforeResolutionCount=new AtomicInteger();
            AtomicInteger finalizationCount=new AtomicInteger();
            final long ownerGeneration=generation;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->ownerGeneration,
                    player.equipment(),player.combatStyles(),registry,
                    (target,expectedGeneration)->{
                        check(target==npc&&
                                expectedGeneration==ownerGeneration,
                            "resolution callback owner/source match");
                        beforeResolutionCount.incrementAndGet();
                    },
                    target->{
                        check(target==npc,"lethal finalizer target");
                        finalizationCount.incrementAndGet();
                    });

            boolean missingWriterRejected=false;
            try{
                handler.handle(request.action(),view,null);
            }catch(NullPointerException expected){
                missingWriterRejected=
                    "writer".equals(expected.getMessage());
                if(!missingWriterRejected)throw expected;
            }

            NpcLifecycleService.Snapshot afterPreflight=
                world.npcLifecycle().get(npc.id);
            check(missingWriterRejected&&
                    afterPreflight!=null&&
                    afterPreflight.hitpoints==initialHp&&
                    !afterPreflight.dead()&&
                    handler.nextAllowedAttackTick()==0L&&
                    beforeResolutionCount.get()==0&&
                    finalizationCount.get()==0&&
                    world.pvmRecords().receiptCount()==0,
                "null writer cannot mutate HP, cooldown or callbacks");

            // A valid request on the same World tick is still admissible.
            // Rejecting the null writer must not poison or consume cadence.
            ByteArrayOutputStream mask=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result valid=
                handler.handle(
                    request.action(),view,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(mask));
            NpcLifecycleService.Snapshot afterValid=
                world.npcLifecycle().get(npc.id);
            check(valid.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                    valid.appliedDamage==10&&
                    valid.acceptedHit!=null&&
                    valid.acceptedHit.attacker==player&&
                    valid.acceptedHit.generation==generation&&
                    afterValid!=null&&
                    afterValid.hitpoints==initialHp-10&&
                    afterValid.dead()==lethal&&
                    valid.newlyDied==lethal&&
                    mask.size()>0&&
                    beforeResolutionCount.get()==1&&
                    finalizationCount.get()==(lethal?1:0)&&
                    handler.nextAllowedAttackTick()>world.clock().tick()&&
                    world.pvmRecords().receiptCount()==0,
                "valid writer permits exactly one owned hit and expected lethal callback");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(false);
        System.out.println(
            "G1937_MISSING_NPC_PACKET_WRITER_PREFLIGHT_PASS"+
            " exactDecodedOpcode72=true"+
            " nullWriterFailsBeforeDamage=true"+
            " zeroPreResolutionCallbacks=true"+
            " unchangedHPAndCadence=true"+
            " sameTickValidRetryHitsOnce=true");
    }

    private static void check(boolean ok,String detail){
        if(!ok)throw new AssertionError("G19.3.7/38 "+detail);
    }

    private G1937MissingNpcPacketWriterPreflightTest(){}
}
