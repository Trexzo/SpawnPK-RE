package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * G19.0.1 / #2558: a canonical option-2 NPC attack accepted under
 * exact WorldPlayer/generation carries immutable immediate HIT evidence.
 * A stale original attacker cannot silently become a replacement B.
 * This is NOT original SpawnPK kill allocation or an applied reward.
 */
public final class G1901AcceptedHitWitnessExactPlayerReplacementTest {
    private static final String USER="g1901-click-identity";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer a=new WorldPlayer();
        WorldPlayer b=null;
        long generationA=-1L,generationB=-1L;
        try{
            generationA=world.registerPlayer(a,USER);
            position(a);
            int def=attackDefinition();
            WorldNpc npc=world.npcs().spawn(def,3088,3495,0);
            world.npcLifecycle().register(
                npc,20,"CUSTOM_LOCALLAB_G1901_ACCEPTED_ATTACKER_WITNESS");
            NpcRegistry registryA=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),a.id());
            NpcEntity viewA=view(registryA,a,def,npc);
            final long capturedGenerationA=generationA;
            LocalCanonicalNpcAttackHandler handlerA=
                new LocalCanonicalNpcAttackHandler(
                    world,a,()->capturedGenerationA,
                    a.equipment(),a.combatStyles(),registryA);

            ByteArrayOutputStream packetA=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result first=
                handlerA.handle(
                    new NpcAction(72,viewA.sceneIndex),viewA,
                    writer(packetA));
            LocalCanonicalNpcAttackHandler.AcceptedHitWitness wa=
                first.acceptedHit;
            check(first.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                    first.appliedDamage==10&&
                    first.hitpointsAfter==10&&
                    !first.newlyDied&&
                    wa!=null&&wa.world==world&&wa.attacker==a&&
                    wa.generation==generationA&&
                    wa.attackerId.equals(a.id())&&
                    wa.targetNpcId.equals(npc.id)&&
                    wa.appliedDamage==first.appliedDamage&&
                    !wa.newlyDied&&wa.stillCurrentIn(world)&&
                    packetA.size()>0,
                "first genuine accepted NPC click has exact owner witness");

            check(world.unregisterPlayer(a,generationA),
                "A leaves after genuinely accepted first NPC hit");
            b=new WorldPlayer();
            generationB=world.registerPlayer(b,USER);
            position(b);
            check(!wa.stillCurrentIn(world)&&
                    world.players().owns(b,generationB)&&
                    !world.players().owns(a,generationA),
                "A's immutable accepted attack becomes stale, not B");

            ByteArrayOutputStream rejectedBytes=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result stale=
                handlerA.handle(
                    new NpcAction(72,viewA.sceneIndex),viewA,
                    writer(rejectedBytes));
            check(stale.status==
                        LocalCanonicalNpcAttackHandler.Status.STALE_PLAYER&&
                    stale.acceptedHit==null&&rejectedBytes.size()==0&&
                    world.npcLifecycle().get(npc.id).hitpoints==10,
                "stale original NPC click has no witness or extra damage");

            NpcRegistry registryB=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),b.id());
            NpcEntity viewB=view(registryB,b,def,npc);
            final WorldPlayer currentB=b;
            final long genB=generationB;
            LocalCanonicalNpcAttackHandler handlerB=
                new LocalCanonicalNpcAttackHandler(
                    world,b,()->genB,
                    b.equipment(),b.combatStyles(),registryB);
            ByteArrayOutputStream packetB=new ByteArrayOutputStream();
            LocalCanonicalNpcAttackHandler.Result second=
                handlerB.handle(
                    new NpcAction(72,viewB.sceneIndex),viewB,
                    writer(packetB));
            LocalCanonicalNpcAttackHandler.AcceptedHitWitness wb=
                second.acceptedHit;
            check(second.status==
                        LocalCanonicalNpcAttackHandler.Status.HIT&&
                    second.newlyDied&&second.hitpointsAfter==0&&
                    wb!=null&&wb.world==world&&
                    wb.attacker==currentB&&wb.attacker!=wa.attacker&&
                    wb.generation==genB&&
                    wb.attackerId.equals(b.id())&&
                    wb.targetNpcId.equals(npc.id)&&
                    wb.newlyDied&&wb.stillCurrentIn(world)&&
                    !wa.stillCurrentIn(world)&&
                    packetB.size()>0&&
                    world.npcLifecycle().get(npc.id).dead(),
                "new B genuine lethal click has B witness, not recycled A");

            check(wa.attacker==a&&wa.generation==generationA&&
                    !wa.newlyDied&&wb.newlyDied&&
                    wa.world==wb.world,
                "accepted hit evidence is immutable across replacement");
            System.out.println(
                "G1901_ACCEPTED_CANONICAL_HIT_EXACT_PLAYER_WITNESS_PASS"+
                " realNpcHitBeforeAndAfterReplacement=true"+
                " playerAFirstHitAccepted=true"+
                " staleASecondClickRejectedNoWitness=true"+
                " exactBRealLethalHitWitness=true"+
                " capturedGenerationAndWorldBound=true"+
                " hitPacketPerAcceptedHit=true"+
                " noTerminalRewardsModified=true"+
                " originalSpawnpkKillAttributionUnknown=true");
        }finally{
            if(generationA>=0L&&world.players().owns(a,generationA))
                world.unregisterPlayer(a,generationA);
            if(b!=null&&generationB>=0L&&
                world.players().owns(b,generationB))
                world.unregisterPlayer(b,generationB);
            world.close();
        }
    }

    private static NpcEntity view(
        NpcRegistry registry,WorldPlayer owner,int definition,WorldNpc npc
    )throws Exception{
        NpcEntity mirror=registry.spawnMirroredNpc(
            definition,3088,3495,null,owner.movement(),
            writer(new ByteArrayOutputStream()));
        mirror.bindCanonicalId(npc.id);
        return mirror;
    }

    private static void position(WorldPlayer player){
        player.movement().restoreAccountState(
            false,100,3087,3495,0);
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
        throw new AssertionError("no canonical non-dummy attack definition");
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream target){
        return new ServerPacketWriter(
            target,new IsaacCipher(new int[]{103,104,105,106}));
    }

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G19.0.1 "+why);
    }

    private G1901AcceptedHitWitnessExactPlayerReplacementTest(){}
}
