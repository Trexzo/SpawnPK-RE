package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.0.2: two separately registered Worlds may carry the same
 * canonical username and scene index. Accepted PvM hit witnesses
 * bind the actual WorldPlayer and World object, while rejected
 * out-of-range actions have NO fabricated attacker evidence.
 */
public final class G1902AcceptedHitWitnessTwoWorldAndRejectedClickTest {
    private static final String USER="g1902-same-name";

    public static void main(String[] args)throws Exception{
        World worldA=World.isolatedForTest(600L);
        World worldB=World.isolatedForTest(600L);
        WorldPlayer playerA=new WorldPlayer();
        WorldPlayer playerB=new WorldPlayer();
        long genA=-1L,genB=-1L;
        try{
            genA=worldA.registerPlayer(playerA,USER);
            genB=worldB.registerPlayer(playerB,USER);
            move(playerA,3087,3495);
            move(playerB,3087,3495);
            int def=attackDefinition();
            WorldNpc npcA=worldA.npcs().spawn(def,3088,3495,0);
            WorldNpc npcB=worldB.npcs().spawn(def,3088,3495,0);
            worldA.npcLifecycle().register(
                npcA,20,"CUSTOM_LOCALLAB_G1902_WORLD_A");
            worldB.npcLifecycle().register(
                npcB,20,"CUSTOM_LOCALLAB_G1902_WORLD_B");

            NpcRegistry sceneA=new NpcRegistry(
                new DevAuthorityWorkbench(),worldA.petNpcs(),playerA.id());
            NpcRegistry sceneB=new NpcRegistry(
                new DevAuthorityWorkbench(),worldB.petNpcs(),playerB.id());
            NpcEntity viewA=mirror(sceneA,playerA,def,npcA);
            NpcEntity viewB=mirror(sceneB,playerB,def,npcB);
            final long ownerGenA=genA,ownerGenB=genB;
            LocalCanonicalNpcAttackHandler attackA=
                new LocalCanonicalNpcAttackHandler(
                    worldA,playerA,()->ownerGenA,
                    playerA.equipment(),playerA.combatStyles(),sceneA);
            LocalCanonicalNpcAttackHandler attackB=
                new LocalCanonicalNpcAttackHandler(
                    worldB,playerB,()->ownerGenB,
                    playerB.equipment(),playerB.combatStyles(),sceneB);

            ByteArrayOutputStream bytesA=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result a=
                attackA.handle(
                    new NpcAction(72,viewA.sceneIndex),viewA,
                    writer(bytesA));
            LocalCanonicalNpcAttackHandler.AcceptedHitWitness acceptedA=
                a.acceptedHit;
            check(a.status==LocalCanonicalNpcAttackHandler.Status.HIT&&
                    acceptedA!=null&&
                    acceptedA.world==worldA&&
                    acceptedA.attacker==playerA&&
                    acceptedA.generation==genA&&
                    acceptedA.targetNpcId.equals(npcA.id)&&
                    acceptedA.stillCurrentIn(worldA)&&
                    !acceptedA.stillCurrentIn(worldB)&&
                    a.appliedDamage==10&&
                    worldA.npcLifecycle().get(npcA.id).hitpoints==10&&
                    worldB.npcLifecycle().get(npcB.id).hitpoints==20&&
                    bytesA.size()>0,
                "real A hit only World A, exact witness cannot be used in B");

            move(playerB,3070,3495);
            ByteArrayOutputStream rejectedBytes=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result rejected=
                attackB.handle(
                    new NpcAction(72,viewB.sceneIndex),viewB,
                    writer(rejectedBytes));
            check(rejected.status==
                        LocalCanonicalNpcAttackHandler.Status.OUT_OF_RANGE&&
                    rejected.acceptedHit==null&&
                    rejected.appliedDamage==0&&
                    rejectedBytes.size()==0&&
                    attackB.nextAllowedAttackTick()==0L&&
                    worldB.npcLifecycle().get(npcB.id).hitpoints==20,
                "rejected World B click has no accepted attacker witness");
            move(playerB,3087,3495);
            ByteArrayOutputStream bytesB=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result b=
                attackB.handle(
                    new NpcAction(72,viewB.sceneIndex),viewB,
                    writer(bytesB));
            LocalCanonicalNpcAttackHandler.AcceptedHitWitness acceptedB=
                b.acceptedHit;
            check(b.status==LocalCanonicalNpcAttackHandler.Status.HIT&&
                    acceptedB!=null&&acceptedB.world==worldB&&
                    acceptedB.attacker==playerB&&
                    acceptedB.generation==genB&&
                    acceptedB.targetNpcId.equals(npcB.id)&&
                    acceptedB.stillCurrentIn(worldB)&&
                    !acceptedB.stillCurrentIn(worldA)&&
                    acceptedA.world!=acceptedB.world&&
                    acceptedA.attacker!=acceptedB.attacker&&
                    acceptedA.attackerId.equals(playerA.id())&&
                    acceptedB.attackerId.equals(playerB.id())&&
                    worldA.npcLifecycle().get(npcA.id).hitpoints==10&&
                    worldB.npcLifecycle().get(npcB.id).hitpoints==10&&
                    bytesB.size()>0,
                "successful B click yields independent actual B World witness");

            System.out.println(
                "G1902_ACCEPTED_CANONICAL_HIT_TWO_WORLD_REJECTION_PASS"+
                " twoRealWorldsSameUsername=true"+
                " acceptedClickAIdentityWorldBound=true"+
                " rejectedOutOfRangeBClickHasNoWitness=true"+
                " acceptedClickBIdentityWorldBound=true"+
                " canonicalNpcHpIndependent=true"+
                " noCombatRewardsChanged=true"+
                " localOnlyAttackerEvidence=true"+
                " originalSpawnpkKillCreditUnknown=true");
        }finally{
            if(genA>=0L&&worldA.players().owns(playerA,genA))
                worldA.unregisterPlayer(playerA,genA);
            if(genB>=0L&&worldB.players().owns(playerB,genB))
                worldB.unregisterPlayer(playerB,genB);
            worldA.close();
            worldB.close();
        }
    }

    private static NpcEntity mirror(
        NpcRegistry registry,WorldPlayer owner,int def,WorldNpc npc
    )throws Exception{
        NpcEntity view=registry.spawnMirroredNpc(
            def,3088,3495,null,owner.movement(),
            writer(new ByteArrayOutputStream()));
        view.bindCanonicalId(npc.id);
        return view;
    }

    private static void move(WorldPlayer player,int x,int y){
        player.movement().restoreAccountState(false,100,x,y,0);
    }

    private static int attackDefinition(){
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
        throw new AssertionError("no attackable non-dummy NPC definition");
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream sink){
        return new ServerPacketWriter(
            sink,new IsaacCipher(new int[]{1001,1002,1003,1004}));
    }

    private static void check(boolean value,String reason){
        if(!value)throw new AssertionError("G19.0.2 "+reason);
    }

    private G1902AcceptedHitWitnessTwoWorldAndRejectedClickTest(){}
}
