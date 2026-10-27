package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * P5.13 actual World-owned NPC projection and lifecycle preflight for
 * production-bound targeted Magic. No rune cost or combat damage applied.
 */
public final class PlayableCanonicalSpellTargetOwnershipTest {
    private static final int[] SEED={181,182,183,184};
    private static final int SPELL=19100;

    private PlayableCanonicalSpellTargetOwnershipTest(){}

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(
            "P513_CANONICAL_SPELL_TARGET "+why);
    }

    private static SpellTargetRequest npc(int sceneIndex){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,SPELL,
            sceneIndex,-1,-1,-1,-1,-1);
    }

    private static SpellTargetRequest playerTarget(){
        return new SpellTargetRequest(
            249,SpellTargetRequest.Kind.PLAYER,SPELL,
            7,-1,-1,-1,-1,-1);
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream bytes
    ){
        return new ServerPacketWriter(
            bytes,new IsaacCipher(SEED.clone()));
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI args");
        World world=World.isolatedForTest(600L);
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(
            owner,"p513-spell-owner");
        NpcRegistry view=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),owner.id());
        ByteArrayOutputStream relayOut=
            new ByteArrayOutputStream();
        ServerPacketWriter relayWriter=writer(relayOut);
        boolean registered=false;
        WorldNpc npc=null;

        try{
            SharedNpcWorldRelay.register(
                relayWriter,world,owner,view,owner.movement());
            registered=true;

            // A genuinely World-registered NPC and matching local
            // viewer scene projection, not a synthetic unbound actor.
            npc=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                npc,10,"CUSTOM_LOCALLAB_P513_NPC_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,npc);
            SharedNpcWorldRelay.syncRemotePets(relayWriter);
            NpcEntity scene=view.canonical(npc.id);
            require(scene!=null&&
                scene.canonicalId().equals(npc.id)&&
                scene.definitionId==100,
                "canonical scene projection not established");

            ByteArrayOutputStream castWire=
                new ByteArrayOutputStream();
            ServerPacketWriter castWriter=writer(castWire);
            LocalSpellTargetHandler handler=
                new LocalSpellTargetHandler(
                    world,owner,()->generation,view,
                    new CombatEngine());

            String valid=handler.handle(
                npc(scene.sceneIndex),castWriter);
            require(valid.contains(
                    "ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Air surge")&&
                valid.contains("TARGET_NPC_VISIBLE def=100")&&
                valid.contains("UNIMPLEMENTED_SERVER_AUTHORITY")&&
                owner.magic().last().startsWith("TARGET_ACCEPT")&&
                castWire.size()==0,
                "valid canonical visible NPC target changed");
            String last=owner.magic().last();

            NpcLifecycleService.DamageResult fatal=
                world.npcLifecycle().applyDamageOwned(
                    npc.id,50,20L);
            require(fatal.newlyDied&&
                fatal.hitpointsAfter==0,
                "genuine World NPC lethal lifecycle");
            String dead=handler.handle(
                npc(scene.sceneIndex),castWriter);
            require(dead.contains(
                    "REJECTED_CANONICAL_NPC_NOT_ALIVE")&&
                owner.magic().last().equals(last)&&
                castWire.size()==0,
                "dead canonical NPC generated phantom accepted cast");

            // Keep the viewer's old projection until next relay tick:
            // unregistering the canonical NPC must immediately
            // invalidate its local scene handle.
            require(world.npcLifecycle().unregisterExact(npc),
                "NPC lifecycle unregister");
            require(world.npcs().remove(npc.id),
                "World NPC unregister");
            String removed=handler.handle(
                npc(scene.sceneIndex),castWriter);
            require(removed.contains(
                    "REJECTED_STALE_CANONICAL_NPC")&&
                owner.magic().last().equals(last)&&
                castWire.size()==0,
                "stale scene projection accepted removed World NPC");

            String stillPlayer=handler.handle(
                playerTarget(),castWriter);
            require(stillPlayer.contains(
                    "TARGET_PLAYER_ROUTED index=7")&&
                castWire.size()==0,
                "other existing target family changed");
            String playerLast=owner.magic().last();

            require(world.unregisterPlayer(owner,generation),
                "World player unregister");
            String staleOwner=handler.handle(
                playerTarget(),castWriter);
            require(staleOwner.contains(
                    "REJECTED_STALE_CASTER_WORLD_OWNER")&&
                owner.magic().last().equals(playerLast)&&
                castWire.size()==0,
                "detached World player accepted targeted Magic");

            System.out.println(
                "P513_CANONICAL_SPELL_TARGET_OWNER_PASS"+
                " realWorldNpcAndSceneProjection=true"+
                " validNpcRoutingPreserved=true"+
                " deadCanonicalNpcDenied=true"+
                " removedCanonicalNpcDenied=true"+
                " casterWorldGenerationDenied=true"+
                " noPhantomSpellLastMutation=true"+
                " zeroPacketOnAllRejections=true"+
                " productionLocalSessionBound=true"+
                " runeSpendingAndSpellDamageNotApplied=true"+
                " nativeClientGuiAndConcurrentDespawnNotProven=true");
        }finally{
            if(registered)
                SharedNpcWorldRelay.unregister(relayWriter);
            if(npc!=null){
                world.npcLifecycle().unregisterExact(npc);
                world.npcs().remove(npc.id);
            }
            if(world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            if(!world.closed())world.close();
        }
    }
}
