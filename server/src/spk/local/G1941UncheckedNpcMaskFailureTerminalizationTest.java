package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * G19.4.1/42: unchecked packet publication failure after committed damage
 * must not strand newly dead canonical NPCs without terminalization.
 */
public final class G1941UncheckedNpcMaskFailureTerminalizationTest {
    static void run(boolean lethal,boolean terminalAlsoFails)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            String name=terminalAlsoFails?"g1942-runtime-dual":
                lethal?"g1941-runtime-lethal":"g1941-runtime-live";
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int def=G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
            WorldNpc npc=world.npcs().spawn(def,3088,3495,0);
            int originalHp=lethal?10:20;
            world.npcLifecycle().register(
                npc,originalHp,"CUSTOM_LOCALLAB_G1941_RUNTIME_MASK");
            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            NpcEntity view=registry.spawnMirroredNpc(
                def,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest.writer(
                    new ByteArrayOutputStream()));
            check(view!=null&&registry.scene(view.sceneIndex)==view,
                "exact canonical viewer projection");
            view.bindCanonicalId(npc.id);
            NpcActionClientRequest req=
                G1903DecodedOpcode72AcceptedHitWitnessTest.decodeExactAttack(
                    view.sceneIndex,new int[]{1941,1942,1943,1944});
            check(req.action().opcode==72&&
                    req.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "actual ISAAC decoded client Attack slot");

            final long expectedGeneration=generation;
            AtomicInteger attempts=new AtomicInteger();
            AtomicInteger terminalCalls=new AtomicInteger();
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->expectedGeneration,
                    player.equipment(),player.combatStyles(),registry,
                    (target,ownerGeneration)->{
                        check(target==npc&&
                                ownerGeneration==expectedGeneration,
                            "exact resolved NPC and player generation");
                        attempts.incrementAndGet();
                    },
                    target->{
                        check(target==npc,"terminal target");
                        terminalCalls.incrementAndGet();
                        if(terminalAlsoFails)
                            throw new IllegalStateException(
                                "G1942_TERMINAL_CALLBACK_FAILED");
                    });
            // Genuine packet writer and ISAAC state, with a backing
            // OutputStream that raises an unchecked exception on flush.
            // It is NOT a null writer and reaches the post-damage seam.
            OutputStream crashingSink=new OutputStream(){
                @Override public void write(int value){
                    throw new IllegalStateException(
                        "G1941_MASK_OUTPUT_RUNTIME_FAILURE");
                }
                @Override public void write(
                    byte[] bytes,int offset,int length
                ){
                    throw new IllegalStateException(
                        "G1941_MASK_OUTPUT_RUNTIME_FAILURE");
                }
            };
            ServerPacketWriter failingWriter=
                new ServerPacketWriter(
                    crashingSink,
                    new IsaacCipher(new int[]{3105,3106,3107,3108})
                );
            Throwable observed=null;
            try{
                handler.handle(req.action(),view,failingWriter);
            }catch(RuntimeException failure){
                observed=failure;
            }
            NpcLifecycleService.Snapshot state=
                world.npcLifecycle().get(npc.id);
            check(state!=null&&
                    state.hitpoints==originalHp-10&&
                    state.dead()==lethal&&
                    attempts.get()==1&&
                    terminalCalls.get()==(lethal?1:0)&&
                    handler.nextAllowedAttackTick()>world.clock().tick()&&
                    world.pvmRecords().receiptCount()==0,
                "HP/cooldown committed, terminal attempted as required");

            if(terminalAlsoFails){
                check(observed instanceof IllegalStateException&&
                        "G1942_TERMINAL_CALLBACK_FAILED"
                            .equals(observed.getMessage())&&
                        observed.getSuppressed().length==1&&
                        observed.getSuppressed()[0] instanceof
                            IllegalStateException&&
                        "G1941_MASK_OUTPUT_RUNTIME_FAILURE".equals(
                            observed.getSuppressed()[0].getMessage()),
                    "terminal error primary with exact unchecked output error suppressed");
            }else{
                check(observed instanceof IllegalStateException&&
                        "G1941_MASK_OUTPUT_RUNTIME_FAILURE".equals(
                            observed.getMessage())&&
                        observed.getSuppressed().length==0,
                    "original unchecked transport exception not swallowed");
            }

            ByteArrayOutputStream duplicateMask=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result duplicate=
                handler.handle(
                    req.action(),view,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest.writer(
                        duplicateMask));
            check(duplicate.status==
                    (lethal
                        ?LocalCanonicalNpcAttackHandler.Status.TARGET_DEAD
                        :LocalCanonicalNpcAttackHandler.Status.CADENCE_BLOCKED)&&
                    duplicate.appliedDamage==0&&
                    duplicate.acceptedHit==null&&
                    attempts.get()==1&&
                    terminalCalls.get()==(lethal?1:0)&&
                    duplicateMask.size()==0&&
                    world.npcLifecycle().get(npc.id).hitpoints==
                        originalHp-10,
                "same-tick duplicate request cannot land another hit");

            if(!lethal){
                for(int tick=0;tick<12;tick++)world.clock().advance();
                ByteArrayOutputStream nextMask=new ByteArrayOutputStream();
                LocalCanonicalNpcAttackHandler.Result next=
                    handler.handle(
                        req.action(),view,
                        G1911CanonicalNpcSceneObjectIdentityFenceTest.writer(
                            nextMask));
                check(next.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                        next.appliedDamage==10&&
                        next.newlyDied&&next.acceptedHit!=null&&
                        nextMask.size()>0&&
                        terminalCalls.get()==1&&
                        world.npcLifecycle().get(npc.id).dead(),
                    "legitimate later click still terminalizes exactly once");
            }
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(false,false);
        run(true,false);
        System.out.println(
            "G1941_UNCHECKED_NPC_MASK_FAILURE_TERMINALIZATION_PASS"+
            " actualDecodedOpcode72=true"+
            " checkedWriterObjectRealRuntimeFailure=true"+
            " nonlethalAndLethalDamageCommitted=true"+
            " lethalTerminalCallbackAttempted=true"+
            " exactExceptionPreserved=true"+
            " laterLegitimateHitAllowed=true");
    }

    private static void check(boolean ok,String detail){
        if(!ok)throw new AssertionError("G19.4.1/42 "+detail);
    }

    private G1941UncheckedNpcMaskFailureTerminalizationTest(){}
}
