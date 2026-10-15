package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.0.4: packet72's exact decoded scene index has no authenticated
 * attacker field; the old typed request cannot command the old
 * session after A unregisters. A fresh B session with the SAME
 * canonical name can submit its own valid option2 request and gets
 * a distinct accepted WorldPlayer/generation witness. This is NOT
 * evidence that the original SpawnPK server credits B for A's hit.
 */
public final class G1904DecodedNpcAttackOldSessionCannotRetargetTest {
    private static final String USER="g1904-typed-reuse";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=null;
        long genA=-1L,genB=-1L;
        try{
            genA=world.registerPlayer(a,USER);
            move(a);
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            WorldNpc target=world.npcs().spawn(
                definition,3088,3495,0);
            world.npcLifecycle().register(
                target,20,"CUSTOM_LOCALLAB_G1904_PACKET_SESSION_TEST");

            NpcRegistry registryA=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),a.id());
            NpcEntity viewA=mirror(
                registryA,a,definition,target);
            NpcActionClientRequest packetA=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        viewA.sceneIndex,new int[]{4101,4102,4103,4104});
            final long oldGeneration=genA;
            LocalCanonicalNpcAttackHandler handlerA=
                new LocalCanonicalNpcAttackHandler(
                    world,a,()->oldGeneration,
                    a.equipment(),a.combatStyles(),registryA);
            ByteArrayOutputStream firstMask=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result first=
                handlerA.handle(
                    packetA.action(),viewA,
                    G1903DecodedOpcode72AcceptedHitWitnessTest
                        .writer(firstMask));
            check(first.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                    first.acceptedHit!=null&&
                    first.acceptedHit.attacker==a&&
                    first.acceptedHit.generation==genA&&
                    first.acceptedHit.world==world&&
                    first.hitpointsAfter==10&&
                    firstMask.size()>0,
                "old A registered session legitimately accepted first decoded click");

            check(world.unregisterPlayer(a,genA),
                "old attacker A exact session unregister");
            b=new WorldPlayer();
            genB=world.registerPlayer(b,USER);
            move(b);
            check(world.players().byName(USER)==b&&
                    world.players().owns(b,genB)&&
                    !first.acceptedHit.stillCurrentIn(world),
                "new B same-name account does not become A's old evidence");

            ByteArrayOutputStream staleBytes=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result oldRequestAgain=
                handlerA.handle(
                    packetA.action(),viewA,
                    G1903DecodedOpcode72AcceptedHitWitnessTest
                        .writer(staleBytes));
            check(oldRequestAgain.status==
                        LocalCanonicalNpcAttackHandler.Status.STALE_PLAYER&&
                    oldRequestAgain.acceptedHit==null&&
                    staleBytes.size()==0&&
                    world.npcLifecycle().get(target.id).hitpoints==10,
                "previously decoded old-session packet cannot retarget stale A");

            NpcRegistry registryB=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),b.id());
            NpcEntity viewB=mirror(
                registryB,b,definition,target);
            NpcActionClientRequest packetB=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .decodeExactAttack(
                        viewB.sceneIndex,new int[]{4201,4202,4203,4204});
            check(packetB.metadata().provenance==
                        ClientRequestProvenance.EXACT_CURRENT_CLIENT&&
                    packetB.action().opcode==packetA.action().opcode&&
                    packetB.action().sceneIndex==viewB.sceneIndex,
                "new typed request is the same opcode72 protocol, no username field");
            final long newGeneration=genB;
            LocalCanonicalNpcAttackHandler handlerB=
                new LocalCanonicalNpcAttackHandler(
                    world,b,()->newGeneration,
                    b.equipment(),b.combatStyles(),registryB);
            ByteArrayOutputStream nextMask=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result acceptedB=
                handlerB.handle(
                    packetB.action(),viewB,
                    G1903DecodedOpcode72AcceptedHitWitnessTest
                        .writer(nextMask));
            check(acceptedB.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                    acceptedB.acceptedHit!=null&&
                    acceptedB.acceptedHit.attacker==b&&
                    acceptedB.acceptedHit.attacker!=first.acceptedHit.attacker&&
                    acceptedB.acceptedHit.world==world&&
                    acceptedB.acceptedHit.generation==genB&&
                    acceptedB.acceptedHit.stillCurrentIn(world)&&
                    acceptedB.newlyDied&&
                    acceptedB.hitpointsAfter==0&&
                    nextMask.size()>0&&
                    world.npcLifecycle().get(target.id).dead()&&
                    world.pvmRecords().receiptCount()==0,
                "new B session's separate decoded click has exact B witness, no reward");
            System.out.println(
                "G1904_DECODED_NPC_ATTACK_OLD_SESSION_FENCE_PASS"+
                " actualNpcOption2PacketA=true"+
                " originalAFirstHitAccepted=true"+
                " oldTypedRequestAfterLogoutRejectedWithoutWitness=true"+
                " realNewBPacketAcceptedLethal=true"+
                " attackerEvidenceFromServerSessionNotPayload=true"+
                " exactAAndBWorldPlayerDistinct=true"+
                " noAutomaticKillCredit=true"+
                " originalSpawnpkPolicyUnknown=true");
        }finally{
            if(genA>=0L&&world.players().owns(a,genA))
                world.unregisterPlayer(a,genA);
            if(b!=null&&genB>=0L&&world.players().owns(b,genB))
                world.unregisterPlayer(b,genB);
            world.close();
        }
    }

    private static NpcEntity mirror(
        NpcRegistry registry,WorldPlayer owner,int def,WorldNpc npc
    )throws Exception{
        NpcEntity view=registry.spawnMirroredNpc(
            def,3088,3495,null,owner.movement(),
            G1903DecodedOpcode72AcceptedHitWitnessTest
                .writer(new ByteArrayOutputStream()));
        view.bindCanonicalId(npc.id);
        return view;
    }

    private static void move(WorldPlayer player){
        player.movement().restoreAccountState(
            false,100,3087,3495,0);
    }

    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G19.0.4 "+reason);
    }

    private G1904DecodedNpcAttackOldSessionCannotRetargetTest(){}
}
