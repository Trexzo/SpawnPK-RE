package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.21 source-only fixed one-damage spell+runes+NPC settlement regression.
 * It does not claim original combat, a multi-viewer hit or crash durability.
 */
public final class PlayableWindStrikeNpcSettlementTest {
    private static final int[] SEED={291,292,293,294};
    private static final int AIR=556;
    private static final int MIND=558;

    private PlayableWindStrikeNpcSettlementTest(){}

    private static void require(boolean good,String reason){
        if(!good)throw new AssertionError(
            "P521_WIND_STRIKE_NPC_SETTLEMENT "+reason);
    }

    private static final class Capture {
        final OutboundPacketQueue queue;
        final ServerPacketWriter writer;
        Capture(int capacity){
            queue=new OutboundPacketQueue(capacity);
            writer=new ServerPacketWriter(
                queue,new IsaacCipher(SEED.clone()));
        }
        byte[] drain()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.toByteArray();
        }
    }

    private static SpellTargetRequest npc(int scene){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
            scene,-1,-1,-1,-1,-1);
    }

    private static void add(WorldPlayer player,int id,int count)
        throws IOException{
        String result=player.bank().spawnItem(
            id,count,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{11,12,13,14})));
        require(result!=null&&result.contains("OK"),
            "fixture item seed refused "+id+" "+result);
    }

    /** Verify exactly one native opcode53 + one native opcode65 in order. */
    private static void assertTwoPackets(byte[] wire){
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        int p=0;
        int[] expected={53,65};
        for(int want:expected){
            require(p+3<=wire.length,"incomplete packet framing");
            int op=((wire[p++]&255)-cipher.nextInt())&255;
            int len=((wire[p++]&255)<<8)|(wire[p++]&255);
            require(op==want,
                "unexpected opcode "+op+" instead of "+want);
            require(len>0&&p+len<=wire.length,
                "invalid packet length opcode="+op+" bytes="+len);
            p+=len;
        }
        require(p==wire.length,"unexpected extra packet bytes");
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI arguments");
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long first=world.registerPlayer(player,"p521-atomic-hit");
        NpcRegistry scene=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),player.id());
        ServerPacketWriter relay=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{21,22,23,24}));
        WorldNpc target=null;
        boolean relayRegistered=false;
        try{
            SharedNpcWorldRelay.register(
                relay,world,player,scene,player.movement());
            relayRegistered=true;
            target=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                target,3,"CUSTOM_LOCALLAB_P521_3HP_FIXTURE");
            SharedNpcWorldRelay.trackCanonicalNpc(world,target);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity visible=scene.canonical(target.id);
            require(visible!=null&&
                visible.canonicalId().equals(target.id),
                "actual World NPC not projected into viewer");

            LocalLabWindStrikeNpcSettlement spell=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->first,scene);
            SpellTargetRequest request=npc(visible.sceneIndex);

            String magicLast=player.magic().last();
            Capture noRunes=new Capture(4096);
            require(spell.settle(request,noRunes.writer).contains(
                    "REJECTED_MAGIC_REQUIREMENTS")&&
                noRunes.drain().length==0&&
                world.npcLifecycle().get(target.id).hitpoints==3&&
                player.magic().last().equals(magicLast),
                "missing resources touched hit, wire or magic");

            add(player,AIR,3);
            add(player,MIND,3);

            // P5.23: spatial authority is canonical WorldNpc.tile,
            // not a potentially stale viewer-local NpcEntity coordinate.
            // All geometry denials must occur before queue publication,
            // resource consumption, HP mutation or same-tick admission.
            world.npcs().move(target.id,3088,3495,1);
            Capture otherPlane=new Capture(4096);
            require("REJECTED_TARGET_DIFFERENT_PLANE".equals(
                    spell.settle(request,otherPlane.writer))&&
                otherPlane.drain().length==0&&
                player.bank().inventoryCount(AIR)==3&&
                player.bank().inventoryCount(MIND)==3&&
                world.npcLifecycle().get(target.id).hitpoints==3,
                "cross-plane NPC accepted a charged hit");
            world.npcs().move(target.id,3105,3495,0);
            Capture far=new Capture(4096);
            require("REJECTED_TARGET_OUT_OF_LAB_RANGE".equals(
                    spell.settle(request,far.writer))&&
                far.drain().length==0&&
                player.bank().inventoryCount(AIR)==3&&
                player.bank().inventoryCount(MIND)==3&&
                world.npcLifecycle().get(target.id).hitpoints==3,
                "far-away NPC accepted stale visible scene target");
            world.npcs().move(target.id,3400,3800,0);
            Capture outsideWindow=new Capture(4096);
            require("REJECTED_TARGET_OUTSIDE_LOADED_REGION".equals(
                    spell.settle(request,outsideWindow.writer))&&
                outsideWindow.drain().length==0&&
                player.bank().inventoryCount(AIR)==3&&
                player.bank().inventoryCount(MIND)==3&&
                world.npcLifecycle().get(target.id).hitpoints==3,
                "NPC outside loaded region admitted spell");
            world.npcs().move(target.id,3088,3495,0);

            Capture full=new Capture(1024);
            byte[] sentinel=new byte[1024];
            Arrays.fill(sentinel,(byte)0x5a);
            full.queue.offer(sentinel);
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE".equals(
                    spell.settle(request,full.writer))&&
                Arrays.equals(full.drain(),sentinel)&&
                player.bank().inventoryCount(AIR)==3&&
                player.bank().inventoryCount(MIND)==3&&
                world.npcLifecycle().get(target.id).hitpoints==3,
                "queue backpressure changed any semantic owner");

            // A nonretractable writer must be denied before any mutation.
            try{
                spell.settle(request,new ServerPacketWriter(
                    new ByteArrayOutputStream(),
                    new IsaacCipher(SEED.clone())));
                throw new AssertionError(
                    "nonretractable direct writer unexpectedly admitted");
            }catch(IOException expected){
                require(expected.getMessage().contains(
                    "RECIPE_DIRECT_WRITER_NONATOMIC_PUBLICATION_DENIED"),
                    "wrong direct writer rejection");
            }
            require(player.bank().inventoryCount(AIR)==3&&
                player.bank().inventoryCount(MIND)==3&&
                world.npcLifecycle().get(target.id).hitpoints==3,
                "direct writer rejected after changing resources/hp");

            Capture busy=new Capture(4096);
            busy.writer.beginBatch();
            try{
                require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE".equals(
                    spell.settle(request,busy.writer))&&
                    player.bank().inventoryCount(AIR)==3&&
                    world.npcLifecycle().get(target.id).hitpoints==3,
                    "busy writer mutated semantic hit");
            }finally{
                busy.writer.abortBatch();
            }
            require(busy.drain().length==0,
                "busy writer published bytes");

            // Inclusive 8-tile range boundary is explicit LocalLab
            // policy. Projection still reports the old near coordinate.
            // The result must come from the canonical WorldNpc position.
            world.npcs().move(target.id,3095,3495,0);
            Capture firstPacket=new Capture(4096);
            String firstHit=spell.settle(request,firstPacket.writer);
            require(firstHit.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                firstHit.contains("damage=1")&&
                firstHit.contains("npcHpAfter=2")&&
                player.bank().inventoryCount(AIR)==2&&
                player.bank().inventoryCount(MIND)==2&&
                world.npcLifecycle().get(target.id).hitpoints==2&&
                magicLast.equals(player.magic().last()),
                "first joint settlement did not commit exact runes and hp");
            assertTwoPackets(firstPacket.drain());
            world.npcs().move(target.id,3088,3495,0);

            // P5.22: multiple wrappers of the exact same WorldPlayer
            // must share the LocalLab once-per-tick semantic admission.
            LocalLabWindStrikeNpcSettlement secondWrapper=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->first,scene);
            Capture duplicate=new Capture(4096);
            require("REJECTED_SAME_TICK_DUPLICATE".equals(
                    secondWrapper.settle(request,duplicate.writer))&&
                duplicate.drain().length==0&&
                player.bank().inventoryCount(AIR)==2&&
                player.bank().inventoryCount(MIND)==2&&
                world.npcLifecycle().get(target.id).hitpoints==2,
                "different service wrapper replayed same-tick hit");

            Capture sameWrapper=new Capture(4096);
            require("REJECTED_SAME_TICK_DUPLICATE".equals(
                    spell.settle(request,sameWrapper.writer))&&
                sameWrapper.drain().length==0,
                "original wrapper missed shared tick ledger");

            world.clock().advance();
            Capture second=new Capture(4096);
            require(secondWrapper.settle(request,second.writer)
                    .contains("npcHpAfter=1")&&
                player.bank().inventoryCount(AIR)==1&&
                player.bank().inventoryCount(MIND)==1&&
                world.npcLifecycle().get(target.id).hitpoints==1,
                "second tick joint settlement inconsistent");
            assertTwoPackets(second.drain());

            Capture originalAfterSecond=new Capture(4096);
            require("REJECTED_SAME_TICK_DUPLICATE".equals(
                    spell.settle(request,originalAfterSecond.writer))&&
                originalAfterSecond.drain().length==0&&
                player.bank().inventoryCount(AIR)==1&&
                world.npcLifecycle().get(target.id).hitpoints==1,
                "original wrapper did not observe sibling's tick");

            world.clock().advance();
            Capture lethal=new Capture(4096);
            String finalHit=spell.settle(request,lethal.writer);
            NpcLifecycleService.Snapshot dead=
                world.npcLifecycle().get(target.id);
            require(finalHit.contains("newlyDead=true")&&
                dead!=null&&dead.dead()&&dead.hitpoints==0&&
                player.bank().inventoryCount(AIR)==0&&
                player.bank().inventoryCount(MIND)==0,
                "terminal one-damage settlement inconsistent");
            assertTwoPackets(lethal.drain());

            world.clock().advance();
            Capture afterDeath=new Capture(4096);
            require("REJECTED_NPC_NOT_ALIVE".equals(
                    spell.settle(request,afterDeath.writer))&&
                afterDeath.drain().length==0&&
                dead.registrationIdentity==
                    world.npcLifecycle().get(target.id)
                        .registrationIdentity&&
                player.magic().last().equals(magicLast),
                "dead NPC accepted another hit");

            require(world.unregisterPlayer(player,first),
                "World unregister refused");
            Capture detached=new Capture(4096);
            require("REJECTED_STALE_WORLD_OWNER".equals(
                    secondWrapper.settle(request,detached.writer))&&
                detached.drain().length==0,
                "old World generation accepted target hit");

            // The ledger must distinguish a new registration even for the
            // very same WorldPlayer object and numeric tick.
            long next=world.registerPlayer(player,"p521-atomic-hit");
            try{
                long settledTick=world.clock().tick()-1L;
                require(player.localLabWindStrikeSettledThisTick(
                        world,first,settledTick)&&
                    !player.localLabWindStrikeSettledThisTick(
                        world,next,settledTick),
                    "new World registration inherited retired tick");
                require("REJECTED_STALE_WORLD_OWNER".equals(
                        spell.settle(request,new Capture(4096).writer)),
                    "retired wrapper revived after same-player ABA");
            }finally{
                require(world.unregisterPlayer(player,next),
                    "new World generation cleanup failed");
            }

            System.out.println(
                "P521_WIND_STRIKE_NPC_SETTLEMENT_PASS"+
                " queueAtomicInventoryAndHitMask=true"+
                " fixedOneDamageAndRuneCost=true"+
                " realWorldNpcLethalLifecycle=true"+
                " queueFullAndBusyDirectWriterDenied=true"+
                " sameTickDuplicateDeniedAcrossWrappers=true"+
                " generationScopedTickLedger=true"+
                " canonicalWorldGeometryFence=true"+
                " distantAndOtherPlaneDenialsZeroPackets=true"+
                " afterDeathAndStaleWorldDenied=true"+
                " magicLastUnchangedAndNoXpLoot=true"+
                " productionLocalSessionNotConnected=true"+
                " nativeGuiAndDurableAtomicityUnproven=true");
        }finally{
            if(target!=null)
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,target.id);
            if(relayRegistered)
                SharedNpcWorldRelay.unregister(relay);
            if(target!=null){
                world.npcLifecycle().unregisterExact(target);
                world.npcs().remove(target.id);
            }
            if(world.players().owns(player,first))
                world.unregisterPlayer(player,first);
            if(!world.closed())world.close();
        }
    }
}
