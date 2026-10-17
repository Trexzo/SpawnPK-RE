package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * G19.3.9/40: a packet-65 IOException after committed HP must not make
 * lethal finalization dependent on another network click.
 */
public final class G1939LethalNpcPacketIoFinalizationTest {
    static void run(boolean lethal,boolean terminalFailure)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,terminalFailure?"g1940-mask-finalizer-errors":
                    lethal?"g1939-mask-lethal-io":"g1939-mask-nonlethal-io");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int def=G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
            WorldNpc npc=world.npcs().spawn(def,3088,3495,0);
            int initialHp=lethal?10:20;
            world.npcLifecycle().register(
                npc,initialHp,"CUSTOM_LOCALLAB_G1939_MASK_IO");
            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            NpcEntity view=registry.spawnMirroredNpc(
                def,3088,3495,null,player.movement(),
                G1911CanonicalNpcSceneObjectIdentityFenceTest
                    .writer(new ByteArrayOutputStream()));
            check(view!=null&&registry.scene(view.sceneIndex)==view,
                "canonical viewer projection");
            view.bindCanonicalId(npc.id);
            NpcActionClientRequest decoded=
                G1903DecodedOpcode72AcceptedHitWitnessTest.decodeExactAttack(
                    view.sceneIndex,new int[]{1939,1940,1941,1942});
            check(decoded.action().opcode==72&&
                    decoded.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "exact current-client opcode72");
            final long ownerGeneration=generation;
            AtomicInteger hooks=new AtomicInteger();
            AtomicInteger terminalCalls=new AtomicInteger();
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->ownerGeneration,
                    player.equipment(),player.combatStyles(),registry,
                    (target,expectedGeneration)->{
                        check(target==npc&&
                                expectedGeneration==ownerGeneration,
                            "exact owner and target pre-resolution");
                        hooks.incrementAndGet();
                    },
                    target->{
                        check(target==npc,"lethal finalizer target");
                        terminalCalls.incrementAndGet();
                        if(terminalFailure)
                            throw new IllegalStateException(
                                "G1940_SYNTHETIC_TERMINAL_FAILURE");
                    });

            // A real writer instance that was already terminated. Its
            // varShort() throws the checked IOException after HP commits.
            ServerPacketWriter broken=
                G1911CanonicalNpcSceneObjectIdentityFenceTest.writer(
                    new ByteArrayOutputStream());
            broken.markTerminal();
            Throwable observed=null;
            try{
                handler.handle(decoded.action(),view,broken);
            }catch(IOException|RuntimeException failure){
                observed=failure;
            }
            NpcLifecycleService.Snapshot after=
                world.npcLifecycle().get(npc.id);
            check(observed!=null&&
                    after!=null&&
                    after.hitpoints==initialHp-10&&
                    after.dead()==lethal&&
                    hooks.get()==1&&
                    terminalCalls.get()==(lethal?1:0)&&
                    handler.nextAllowedAttackTick()>world.clock().tick()&&
                    world.pvmRecords().receiptCount()==0,
                "writer failure cannot rollback committed damage or skip lethal finalizer");

            if(terminalFailure){
                check(observed instanceof IllegalStateException&&
                        "G1940_SYNTHETIC_TERMINAL_FAILURE"
                            .equals(observed.getMessage())&&
                        observed.getSuppressed().length==1&&
                        observed.getSuppressed()[0] instanceof IOException&&
                        String.valueOf(observed.getSuppressed()[0].getMessage())
                            .contains("server packet writer terminal"),
                    "terminal failure propagated with original mask I/O suppressed");
            }else{
                check(observed instanceof IOException&&
                        String.valueOf(observed.getMessage()).contains(
                            "server packet writer terminal")&&
                        observed.getSuppressed().length==0,
                    "original checked packet failure remains observable");
            }

            ByteArrayOutputStream retryBytes=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result retry=
                handler.handle(
                    decoded.action(),view,
                    G1911CanonicalNpcSceneObjectIdentityFenceTest
                        .writer(retryBytes));
            check(retry.status==
                    (lethal
                        ?LocalCanonicalNpcAttackHandler.Status.TARGET_DEAD
                        :LocalCanonicalNpcAttackHandler.Status.CADENCE_BLOCKED)&&
                    retry.appliedDamage==0&&
                    retry.acceptedHit==null&&
                    retryBytes.size()==0&&
                    hooks.get()==1&&
                    terminalCalls.get()==(lethal?1:0)&&
                    world.npcLifecycle().get(npc.id).hitpoints==
                        initialHp-10,
                "replayed immediate click cannot apply damage again");

            if(!lethal){
                for(int elapsed=0;elapsed<12;elapsed++)
                    world.clock().advance();
                ByteArrayOutputStream goodMask=new ByteArrayOutputStream();
                LocalCanonicalNpcAttackHandler.Result later=
                    handler.handle(
                        decoded.action(),view,
                        G1911CanonicalNpcSceneObjectIdentityFenceTest
                            .writer(goodMask));
                check(later.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                        later.appliedDamage==10&&
                        later.newlyDied&&
                        later.acceptedHit!=null&&
                        goodMask.size()>0&&
                        hooks.get()==2&&terminalCalls.get()==1&&
                        world.npcLifecycle().get(npc.id).dead(),
                    "post-cooldown legitimate packet can terminalize NPC");
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
            "G1939_LETHAL_NPC_PACKET_IO_FINALIZATION_PASS"+
            " realTerminatedPacketWriter=true"+
            " postDamageIOExceptionPreserved=true"+
            " lethalCallbackAttemptedOnce=true"+
            " sameTickReplayDoesNotDuplicateHP=true"+
            " postCooldownValidNonlethalControl=true");
    }

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G19.3.9/40 "+reason);
    }

    private G1939LethalNpcPacketIoFinalizationTest(){}
}
