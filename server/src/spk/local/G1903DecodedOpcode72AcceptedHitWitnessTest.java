package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * G19.0.3: a real ISAAC-framed exact client opcode72 (NPC Attack
 * option2) is decoded into a typed NpcAction request before reaching
 * canonical PvM click admission. The packet carries scene index,
 * NOT a player ID: accepted WorldPlayer/generation comes from the
 * server's exact LocalCanonicalNpcAttackHandler session.
 */
public final class G1903DecodedOpcode72AcceptedHitWitnessTest {
    private static final String USER="g1903-packet72-owner";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(owner,USER);
            owner.movement().restoreAccountState(
                false,100,3087,3495,0);
            int definition=attackDefinition();
            WorldNpc npc=world.npcs().spawn(
                definition,3088,3495,0);
            world.npcLifecycle().register(
                npc,20,"CUSTOM_LOCALLAB_G1903_TYPED_PACKET_TEST");
            NpcRegistry registry=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),owner.id());
            NpcEntity view=registry.spawnMirroredNpc(
                definition,3088,3495,null,owner.movement(),
                writer(new ByteArrayOutputStream()));
            view.bindCanonicalId(npc.id);

            final int[] seed={3101,3102,3103,3104};
            NpcActionClientRequest typed=decodeExactAttack(
                view.sceneIndex,seed);
            check(typed.action().opcode==72&&
                    typed.action().sceneIndex==view.sceneIndex&&
                    typed.metadata().opcode==72&&
                    "FIXED2_NPC_SCENE_INDEX_BE_A".equals(
                        typed.metadata().schema)&&
                    "PINNED_CLIENT_NPC_OPTION_2_ATTACK_WRITER".equals(
                        typed.metadata().source)&&
                    typed.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT,
                "exact current-client wire opcode72 becomes typed scene request");

            final long exactGeneration=generation;
            LocalCanonicalNpcAttackHandler handler=
                new LocalCanonicalNpcAttackHandler(
                    world,owner,()->exactGeneration,
                    owner.equipment(),owner.combatStyles(),registry);
            ByteArrayOutputStream mask=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result result=
                handler.handle(
                    typed.action(),view,writer(mask));
            LocalCanonicalNpcAttackHandler.AcceptedHitWitness witness=
                result.acceptedHit;
            check(result!=null&&
                    result.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                    result.appliedDamage==10&&
                    result.hitpointsAfter==10&&
                    !result.newlyDied&&mask.size()>0&&
                    world.npcLifecycle().get(npc.id).hitpoints==10&&
                    witness!=null&&
                    witness.world==world&&
                    witness.attacker==owner&&
                    witness.generation==generation&&
                    witness.attackerId.equals(owner.id())&&
                    witness.targetNpcId.equals(npc.id)&&
                    witness.appliedDamage==10&&
                    witness.stillCurrentIn(world),
                "real decoded opcode72 mutates canonical NPC with exact server session witness");

            check(world.pvmRecords().receiptCount()==0,
                "nonlethal decoded click alone cannot grant a kill receipt");

            System.out.println(
                "G1903_DECODED_OPCODE72_ACCEPTED_HIT_WITNESS_PASS"+
                " realIsaacFramedPacket=true"+
                " currentClientOption2ExactMetadata=true"+
                " decodedSceneIndexMatchesCanonicalView=true"+
                " actualCanonicalNpcDamageTen=true"+
                " exactCurrentWorldPlayerAndGenerationWitness=true"+
                " hitPacketPublished=true"+
                " packetCarriesNoPlayerId=true"+
                " originalKillCreditUnknown=true");
        }finally{
            if(generation>=0L&&world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            world.close();
        }
    }

    static NpcActionClientRequest decodeExactAttack(
        int sceneIndex,int[] seed
    )throws Exception{
        if(sceneIndex<0||sceneIndex>65535)
            throw new IllegalArgumentException("sceneIndex");
        IsaacCipher encoder=new IsaacCipher(seed.clone());
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        wire.write((72+encoder.nextInt())&255);
        wire.write((sceneIndex>>>8)&255);
        wire.write((sceneIndex+128)&255);
        ClientPacketProbe probe=new ClientPacketProbe(
            new ByteArrayInputStream(wire.toByteArray()),
            new IsaacCipher(seed.clone()),"[g1903-typed-attack] ");
        check(probe.readNextKnownPacket()&&probe.isAligned(),
            "packet72 ISAAC/body codec remained aligned");
        ClientRequest request=probe.takeTypedRequest();
        check(request instanceof NpcActionClientRequest&&
                probe.takeTypedRequest()==null,
            "exactly one typed NPC request decoded");
        return (NpcActionClientRequest)request;
    }

    static int attackDefinition(){
        for(int id=0;id<16384;id++){
            NpcEntity probe;
            try{probe=new NpcEntity(100,id,3088,3495);}
            catch(IllegalArgumentException ignored){continue;}
            if(NpcInteractionRouter.resolve(
                    new NpcAction(72,probe.sceneIndex),probe).service==
                    NpcInteractionRouter.Service.ATTACK&&
                    !CombatTargetRepository.isCombatDummy(id))
                return id;
        }
        throw new AssertionError("no canonical option2 attack definition");
    }

    static ServerPacketWriter writer(ByteArrayOutputStream out){
        return new ServerPacketWriter(
            out,new IsaacCipher(new int[]{3105,3106,3107,3108}));
    }

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G19.0.3 "+why);
    }

    private G1903DecodedOpcode72AcceptedHitWitnessTest(){}
}
