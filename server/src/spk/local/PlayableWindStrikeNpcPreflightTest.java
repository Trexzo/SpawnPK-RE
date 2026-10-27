package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * P5.20: stable read-only joint World + canonical NPC + Wind Strike
 * resource readiness. This is NOT a cast and does not spend or hit.
 */
public final class PlayableWindStrikeNpcPreflightTest {
    private static final int[] SEED={281,282,283,284};

    private PlayableWindStrikeNpcPreflightTest(){}

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(
            "P520_WIND_STRIKE_NPC_PREFLIGHT "+why);
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream bytes
    ){
        return new ServerPacketWriter(
            bytes,new IsaacCipher(SEED.clone()));
    }

    private static void add(WorldPlayer player,int id,int qty)
        throws Exception{
        String result=player.bank().spawnItem(
            id,qty,writer(new ByteArrayOutputStream()));
        require(result!=null&&result.contains("OK"),
            "fixture rune seed failed id="+id+" result="+result);
    }

    private static SpellTargetRequest request(int index){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
            index,-1,-1,-1,-1,-1);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI args");
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(
            player,"p520-wind-strike");
        NpcRegistry scene=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),player.id());
        ServerPacketWriter relay=writer(
            new ByteArrayOutputStream());
        WorldNpc npc=null;
        boolean registered=false;
        try{
            SharedNpcWorldRelay.register(
                relay,world,player,scene,player.movement());
            registered=true;
            npc=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                npc,10,"CUSTOM_LOCALLAB_P520_NPC_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,npc);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity visible=scene.canonical(npc.id);
            require(visible!=null&&
                visible.canonicalId().equals(npc.id),
                "real canonical NPC not visible");

            LocalLabWindStrikeNpcPreflight probe=
                new LocalLabWindStrikeNpcPreflight(
                    world,player,()->generation,scene);
            SpellTargetRequest cast=request(visible.sceneIndex);
            String magicBefore=player.magic().last();
            int healthBefore=world.npcLifecycle().get(npc.id).hitpoints;

            String missing=probe.check(cast);
            require(missing.contains("REJECTED_MAGIC_REQUIREMENTS")&&
                magicBefore.equals(player.magic().last())&&
                healthBefore==world.npcLifecycle().get(
                    npc.id).hitpoints,
                "missing rune preflight changed magic or NPC HP");

            add(player,556,2);
            add(player,558,2);
            int airBefore=player.bank().inventoryCount(556);
            int mindBefore=player.bank().inventoryCount(558);
            String valid=probe.check(cast);
            String duplicate=probe.check(cast);
            require(valid.contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_READY")&&
                duplicate.contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_READY")&&
                magicBefore.equals(player.magic().last())&&
                player.bank().inventoryCount(556)==airBefore&&
                player.bank().inventoryCount(558)==mindBefore&&
                world.npcLifecycle().get(npc.id).hitpoints==
                    healthBefore,
                "successful repeated preview unexpectedly mutated state");

            String wrongSpell=probe.check(new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,19100,
                visible.sceneIndex,-1,-1,-1,-1,-1));
            require(wrongSpell.contains(
                    "REJECTED_NOT_CANONICAL_WIND_STRIKE_NPC_REQUEST")&&
                magicBefore.equals(player.magic().last()),
                "unreviewed spell admitted");

            world.npcLifecycle().applyDamageOwned(
                npc.id,50,20L);
            String dead=probe.check(cast);
            require(dead.contains("REJECTED_NPC_NOT_ALIVE")&&
                player.bank().inventoryCount(556)==airBefore&&
                player.bank().inventoryCount(558)==mindBefore&&
                magicBefore.equals(player.magic().last()),
                "dead target consumed runes or accepted preview");

            require(world.unregisterPlayer(player,generation),
                "World player unregister refused");
            require(probe.check(cast).contains(
                    "REJECTED_STALE_WORLD_OWNER"),
                "detached World user still previews NPC spell");

            System.out.println(
                "P520_WIND_STRIKE_NPC_PREFLIGHT_PASS"+
                " realWorldNpc=true"+
                " validReadOnlyAndRepeatable=true"+
                " missingRunesDenied=true"+
                " deadNpcDenied=true"+
                " staleWorldOwnerDenied=true"+
                " noRunesDamageOrMagicLastMutation=true"+
                " liveCombatAndNativeGuiUnverified=true");
        }finally{
            if(npc!=null)
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,npc.id);
            if(registered)
                SharedNpcWorldRelay.unregister(relay);
            if(npc!=null){
                world.npcLifecycle().unregisterExact(npc);
                world.npcs().remove(npc.id);
            }
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }
}
