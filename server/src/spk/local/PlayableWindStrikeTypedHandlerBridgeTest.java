package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.25: typed SpellTargetRequest -> LocalSpellTargetHandler ->
 * opt-in isolated LocalLab joint Wind Strike settlement.
 * Production constructor stays inactive. This is source-only and
 * does not simulate a real TCP client packet or graphical client.
 */
public final class PlayableWindStrikeTypedHandlerBridgeTest {
    private static final int[] SEED={301,302,303,304};

    private PlayableWindStrikeTypedHandlerBridgeTest(){}

    private static void require(boolean good,String reason){
        if(!good)throw new AssertionError(
            "P525_TYPED_WIND_STRIKE_HANDLER "+reason);
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

    private static void add(WorldPlayer player,int id,int qty)
        throws IOException{
        String result=player.bank().spawnItem(
            id,qty,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{31,32,33,34})));
        require(result!=null&&result.contains("OK"),
            "fixture resource seed failed "+result);
    }

    private static SpellTargetRequest target(int scene){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
            scene,-1,-1,-1,-1,-1);
    }

    private static void assertInventoryAndHit(byte[] frames){
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        int pos=0;
        for(int opcode:new int[]{53,65}){
            require(pos+3<=frames.length,"missing frame");
            int decoded=((frames[pos++]&255)-cipher.nextInt())&255;
            int size=((frames[pos++]&255)<<8)|
                (frames[pos++]&255);
            require(decoded==opcode,
                "wrong native S2C opcode "+decoded+" expected "+opcode);
            require(size>0&&pos+size<=frames.length,
                "truncated native packet body");
            pos+=size;
        }
        require(pos==frames.length,"unexpected extra packet");
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");

        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(
            player,"p525-typed-bridge");
        NpcRegistry scene=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),player.id());
        ServerPacketWriter relay=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{41,42,43,44}));
        WorldNpc canonical=null;
        boolean viewerRegistered=false;
        try{
            SharedNpcWorldRelay.register(
                relay,world,player,scene,player.movement());
            viewerRegistered=true;
            canonical=world.npcs().spawn(
                100,3088,3495,0);
            world.npcLifecycle().register(
                canonical,2,"CUSTOM_LOCALLAB_P525_TWO_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,canonical);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity presented=scene.canonical(canonical.id);
            require(presented!=null&&
                presented.canonicalId().equals(canonical.id),
                "canonical viewer scene missing");

            add(player,556,2);
            add(player,558,2);
            SpellTargetRequest spell=target(presented.sceneIndex);

            // Reproduce the pinned original-client NPC spell writer:
            // scene index LE-A then spell widget BE-A, exactly 4 bytes.
            int sceneIndex=presented.sceneIndex;
            int widget=LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET;
            byte[] nativeBody=new byte[]{
                (byte)(((sceneIndex&255)+128)&255),
                (byte)((sceneIndex>>>8)&255),
                (byte)((widget>>>8)&255),
                (byte)(((widget&255)+128)&255)
            };
            SpellTargetRequest decoded=
                ClientPacketProbe.decodeSpellTarget(131,nativeBody);
            require(decoded.opcode==131&&
                decoded.kind==SpellTargetRequest.Kind.NPC&&
                decoded.targetIndex==sceneIndex&&
                decoded.spellWidget==widget,
                "pinned opcode131 decoded wrong native client bytes");
            SpellTargetClientRequest nativeSpell=
                new SpellTargetClientRequest(
                    decoded,ClientRequestMetadata.exactCurrent(
                        131,
                        "FIXED4_NPC_LE_A_SPELL_BE_A",
                        "PINNED_CLIENT_SPELL_ON_NPC_WRITER"));
            try{
                ClientPacketProbe.decodeSpellTarget(
                    131,new byte[]{0,0,0});
                throw new AssertionError(
                    "truncated native opcode131 accepted");
            }catch(IllegalArgumentException expected){
                require(expected.getMessage().contains("opcode131 len="),
                    "truncated spell denied for unrelated reason");
            }

            // The unchanged LocalSession production constructor must
            // not route source-only rune and HP settlement.
            LocalSpellTargetHandler productionShape=
                new LocalSpellTargetHandler(
                    world,player,()->generation,scene,
                    new CombatEngine());
            Capture defaultWriter=new Capture(4096);
            String unchanged=productionShape.handleTyped(
                nativeSpell,defaultWriter.writer);
            require(unchanged.contains(
                    "TARGET_NPC_VISIBLE def=100")&&
                !unchanged.contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                defaultWriter.drain().length==0&&
                player.bank().inventoryCount(556)==2&&
                player.bank().inventoryCount(558)==2&&
                world.npcLifecycle().get(canonical.id)
                    .hitpoints==2,
                "default production constructor unexpectedly committed fixture");

            LocalLabWindStrikeNpcSettlement isolated=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->generation,scene);

            // An opt-in fixture is never portable to another canonical
            // owner or a different per-session viewer registry.
            try{
                new LocalSpellTargetHandler(
                    world,new WorldPlayer(),()->generation,scene,
                    new CombatEngine(),isolated);
                throw new AssertionError(
                    "wrong-player settlement injection admitted");
            }catch(IllegalArgumentException expected){
                require(expected.getMessage().contains(
                    "P525_ISOLATED_WIND_STRIKE_BINDING_MISMATCH"),
                    "wrong-player binding failed for unrelated reason");
            }
            try{
                new LocalSpellTargetHandler(
                    world,player,()->generation,new NpcRegistry(),
                    new CombatEngine(),isolated);
                throw new AssertionError(
                    "wrong-viewer settlement injection admitted");
            }catch(IllegalArgumentException expected){
                require(expected.getMessage().contains(
                    "P525_ISOLATED_WIND_STRIKE_BINDING_MISMATCH"),
                    "wrong-viewer binding failed for unrelated reason");
            }

            LocalSpellTargetHandler explicit=
                new LocalSpellTargetHandler(
                    world,player,()->generation,scene,
                    new CombatEngine(),isolated);

            // A manually assembled semantic request lacks original
            // transport evidence, so cannot trigger the isolated hit.
            Capture noTransport=new Capture(4096);
            String manual=explicit.handle(spell,noTransport.writer);
            require(!manual.contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                noTransport.drain().length==0&&
                player.bank().inventoryCount(556)==2&&
                player.bank().inventoryCount(558)==2&&
                world.npcLifecycle().get(canonical.id).hitpoints==2,
                "synthetic request bypassed native provenance gate");

            // Even typed wrappers must present exact-current opcode,
            // schema and source. A fabricated/wrong writer name or
            // packet id cannot authorize the opt-in source fixture.
            SpellTargetClientRequest fakeSchema=
                new SpellTargetClientRequest(
                    decoded,ClientRequestMetadata.exactCurrent(
                        131,"FIXED4_NPC_UNREVIEWED_SCHEMA",
                        "PINNED_CLIENT_SPELL_ON_NPC_WRITER"));
            Capture fakeWriter=new Capture(4096);
            require(!explicit.handleTyped(fakeSchema,fakeWriter.writer)
                    .contains("LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                fakeWriter.drain().length==0&&
                player.bank().inventoryCount(556)==2&&
                world.npcLifecycle().get(canonical.id).hitpoints==2,
                "wrong opcode131 schema authorized fixture hit");

            SpellTargetClientRequest fakeSource=
                new SpellTargetClientRequest(
                    decoded,ClientRequestMetadata.exactCurrent(
                        131,"FIXED4_NPC_LE_A_SPELL_BE_A",
                        "SYNTHETIC_UNREVIEWED_WRITER"));
            Capture sourceWriter=new Capture(4096);
            require(!explicit.handleTyped(fakeSource,sourceWriter.writer)
                    .contains("LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                sourceWriter.drain().length==0&&
                player.bank().inventoryCount(556)==2&&
                world.npcLifecycle().get(canonical.id).hitpoints==2,
                "wrong opcode131 writer source authorized fixture");

            SpellTargetClientRequest fakeOpcode=
                new SpellTargetClientRequest(
                    decoded,ClientRequestMetadata.exactCurrent(
                        249,"FIXED4_NPC_LE_A_SPELL_BE_A",
                        "PINNED_CLIENT_SPELL_ON_NPC_WRITER"));
            Capture fakeId=new Capture(4096);
            require(!explicit.handleTyped(fakeOpcode,fakeId.writer)
                    .contains("LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                fakeId.drain().length==0&&
                player.bank().inventoryCount(556)==2&&
                world.npcLifecycle().get(canonical.id).hitpoints==2,
                "wrong packet id authorized source-only fixture");

            String before=player.magic().last();

            Capture saturated=new Capture(1024);
            byte[] sentinel=new byte[1024];
            Arrays.fill(sentinel,(byte)0x37);
            saturated.queue.offer(sentinel);
            require(saturated.queue.queuedBytes()==1024,
                "failed to fill outbound queue");
            String denied=explicit.handleTyped(nativeSpell,saturated.writer);
            require(denied.contains(
                    "REJECTED_WRITER_BACKPRESSURE_RETRYABLE")&&
                Arrays.equals(sentinel,saturated.drain())&&
                before.equals(player.magic().last())&&
                player.bank().inventoryCount(556)==2&&
                player.bank().inventoryCount(558)==2&&
                world.npcLifecycle().get(canonical.id).hitpoints==2,
                "backpressure changed state through typed handler");

            Capture accepted=new Capture(4096);
            String first=explicit.handleTyped(nativeSpell,accepted.writer);
            require(first.contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                player.magic().last().startsWith(
                    "TARGET_SETTLED_CUSTOM_LOCALLAB_WIND_STRIKE")&&
                player.bank().inventoryCount(556)==1&&
                player.bank().inventoryCount(558)==1&&
                world.npcLifecycle().get(canonical.id).hitpoints==1,
                "typed handler did not commit exactly one joint spell");
            assertInventoryAndHit(accepted.drain());
            String committedLast=player.magic().last();

            // A new handler and new settlement wrapper may not bypass
            // the canonical WorldPlayer's shared once-per-tick guard.
            LocalSpellTargetHandler sibling=
                new LocalSpellTargetHandler(
                    world,player,()->generation,scene,
                    new CombatEngine(),
                    new LocalLabWindStrikeNpcSettlement(
                        world,player,()->generation,scene));
            Capture replay=new Capture(4096);
            String twice=sibling.handleTyped(nativeSpell,replay.writer);
            require(twice.contains("REJECTED_SAME_TICK_DUPLICATE")&&
                replay.drain().length==0&&
                committedLast.equals(player.magic().last())&&
                player.bank().inventoryCount(556)==1&&
                world.npcLifecycle().get(canonical.id).hitpoints==1,
                "sibling handler double-hit or double-spent same tick");

            world.clock().advance();
            Capture second=new Capture(4096);
            String lethal=sibling.handleTyped(nativeSpell,second.writer);
            require(lethal.contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                lethal.contains("newlyDead=true")&&
                world.npcLifecycle().get(canonical.id).dead()&&
                player.bank().inventoryCount(556)==0&&
                player.bank().inventoryCount(558)==0,
                "second tick failed exact lethal rune/HP commit");
            assertInventoryAndHit(second.drain());

            world.clock().advance();
            Capture dead=new Capture(4096);
            String deniedDead=explicit.handleTyped(nativeSpell,dead.writer);
            require(deniedDead.contains(
                    "REJECTED_CANONICAL_NPC_NOT_ALIVE")&&
                dead.drain().length==0&&
                player.bank().inventoryCount(556)==0&&
                player.magic().last().startsWith(
                    "TARGET_SETTLED_CUSTOM_LOCALLAB_WIND_STRIKE"),
                "dead NPC admitted typed spell");

            require(world.unregisterPlayer(player,generation),
                "World user unregister failed");
            Capture stale=new Capture(4096);
            String detached=explicit.handleTyped(nativeSpell,stale.writer);
            require(detached.contains(
                    "REJECTED_STALE_CASTER_WORLD_OWNER")&&
                stale.drain().length==0,
                "detached caster admitted typed spell");

            System.out.println(
                "P525_TYPED_WIND_STRIKE_HANDLER_PASS"+
                " productionConstructorUnchanged=true"+
                " explicitSourceTestBridge=true"+
                " exactNativeOpcode131Decoded=true"+
                " wrongProvenanceAndSyntheticRequestDenied=true"+
                " foreignOwnerAndViewerBindingDenied=true"+
                " nativeOpcode53And65=true"+
                " queueBackpressureNoPhantomCast=true"+
                " magicLastOnlyAfterCommit=true"+
                " siblingDuplicateBlocked=true"+
                " lethalNpcAndStaleWorldRejected=true"+
                " noNativeTcpGuiOrRealDamageProof=true");
        }finally{
            if(canonical!=null)
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,canonical.id);
            if(viewerRegistered)
                SharedNpcWorldRelay.unregister(relay);
            if(canonical!=null){
                world.npcLifecycle().unregisterExact(canonical);
                world.npcs().remove(canonical.id);
            }
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }
}
