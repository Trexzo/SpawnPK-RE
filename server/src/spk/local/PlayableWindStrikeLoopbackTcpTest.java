package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * P5.27 localhost TCP, post-login wire acceptance for explicit LocalLab
 * Wind Strike. Uses the ACTUAL ClientPacketProbe ISAAC opcode decoder
 * and native S2C queue/writer packet frames, not just its static decoder.
 *
 * Deliberately bypasses login, LocalSession socket orchestration,
 * native client GUI and original combat rules. The handler is explicitly
 * opt-in, which the ordinary LocalSession constructor is NOT.
 */
public final class PlayableWindStrikeLoopbackTcpTest {
    private static final int[] C2S_SEED={311,312,313,314};
    private static final int[] S2C_SEED={321,322,323,324};
    private static final int[] RELAY_SEED={331,332,333,334};

    private PlayableWindStrikeLoopbackTcpTest(){}

    private static void require(boolean valid,String why){
        if(!valid)throw new AssertionError(
            "P527_LOOPBACK_TCP_WIND_STRIKE "+why);
    }

    private static void add(WorldPlayer player,int item,int amount)
        throws Exception{
        String result=player.bank().spawnItem(
            item,amount,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{341,342,343,344})));
        require(result!=null&&result.contains("OK"),
            "fixture resource setup rejected "+result);
    }

    /**
     * Client-side native C2S stream: ISAAC opcode + exact fixed body.
     * 185 (post-login initial widget=912) then three NPC spell casts.
     */
    private static byte[] clientStream(int sceneIndex){
        ByteArrayOutputStream raw=new ByteArrayOutputStream();
        IsaacCipher cipher=new IsaacCipher(C2S_SEED.clone());
        raw.write((185+cipher.nextInt())&255);
        raw.write(912>>>8);
        raw.write(912&255);
        for(int i=0;i<3;i++){
            raw.write((131+cipher.nextInt())&255);
            raw.write((sceneIndex+128)&255);
            raw.write((sceneIndex>>>8)&255);
            raw.write(1152>>>8);
            raw.write((1152+128)&255);
        }
        return raw.toByteArray();
    }

    private static SpellTargetClientRequest takeNativeCast(
        ClientPacketProbe probe
    )throws Exception{
        require(probe.isAligned(),
            "real TCP packet stream already lost alignment");
        require(probe.readNextKnownPacket(),
            "post-login TCP opcode131 not decoded");
        require(probe.typedRequestCount()==1,
            "unexpected count in decoded TCP typed queue");
        ClientRequest next=probe.takeTypedRequest();
        require(next instanceof SpellTargetClientRequest,
            "wrong inbound typed request from socket");
        SpellTargetClientRequest spell=
            (SpellTargetClientRequest)next;
        require(spell.metadata().opcode==131&&
            "PINNED_CLIENT_SPELL_ON_NPC_WRITER".equals(
                spell.metadata().source),
            "pinned NPC opcode131 metadata absent after socket");
        return spell;
    }

    private static void receiveTwoNativeFrames(
        InputStream clientInput,
        IsaacCipher s2c
    )throws Exception{
        for(int expected:new int[]{53,65}){
            int encoded=clientInput.read();
            require(encoded>=0,
                "TCP stream ended before native S2C packet");
            int actual=(encoded-s2c.nextInt())&255;
            int high=clientInput.read();
            int low=clientInput.read();
            require(high>=0&&low>=0,
                "truncated native varshort length");
            int len=(high<<8)|low;
            require(actual==expected,
                "native S2C opcode mismatch expected="+expected+
                " got="+actual);
            require(len>0&&len<4096,
                "invalid native packet body size "+len);
            byte[] body=Binary.readExactly(clientInput,len);
            require(body.length==len,
                "truncated TCP native packet body");
        }
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI args");
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(
            player,"p527-loopback-native");
        NpcRegistry scene=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),player.id());
        ServerPacketWriter relay=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(RELAY_SEED.clone()));
        boolean relayRegistered=false;
        WorldNpc canonical=null;
        try{
            SharedNpcWorldRelay.register(
                relay,world,player,scene,player.movement());
            relayRegistered=true;
            canonical=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                canonical,2,"CUSTOM_LOCALLAB_P527_TWO_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,canonical);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity visible=scene.canonical(canonical.id);
            require(visible!=null&&
                canonical.id.equals(visible.canonicalId()),
                "canonical NPC not visible in source viewer");

            add(player,556,2);
            add(player,558,2);

            LocalSpellTargetHandler optedIn=
                new LocalSpellTargetHandler(
                    world,player,()->generation,
                    scene,new CombatEngine(),
                    new LocalLabWindStrikeNpcSettlement(
                        world,player,()->generation,scene));
            OutboundPacketQueue outgoing=
                new OutboundPacketQueue(4096);
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    outgoing,new IsaacCipher(S2C_SEED.clone()));

            InetAddress loopback=InetAddress.getLoopbackAddress();
            try(ServerSocket listener=
                    new ServerSocket(0,1,loopback)){
                listener.setSoTimeout(5000);
                try(Socket client=
                        new Socket(loopback,listener.getLocalPort());
                    Socket server=listener.accept()){
                    client.setSoTimeout(5000);
                    server.setSoTimeout(5000);

                    // These are real TCP socket endpoints. They share
                    // no Java byte array or embedded InputStream shim.
                    OutputStream clientOutput=client.getOutputStream();
                    clientOutput.write(
                        clientStream(visible.sceneIndex));
                    clientOutput.flush();

                    ClientPacketProbe probe=
                        new ClientPacketProbe(
                            server.getInputStream(),
                            new IsaacCipher(C2S_SEED.clone()),
                            "[P527_TCP] ");
                    require(probe.readFirst185()==185&&
                        probe.isAligned()&&
                        probe.decodedCount()==1,
                        "post-login native opcode185 handshake failed");
                    IsaacCipher clientS2c=
                        new IsaacCipher(S2C_SEED.clone());
                    InputStream clientInput=client.getInputStream();

                    String first=optedIn.handleTyped(
                        takeNativeCast(probe),writer);
                    require(first.contains(
                            "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                        player.bank().inventoryCount(556)==1&&
                        player.bank().inventoryCount(558)==1&&
                        world.npcLifecycle().get(
                            canonical.id).hitpoints==1,
                        "first real socket spell not jointly settled");
                    require(outgoing.queuedPackets()==1,
                        "native S2C inventory/hit not one atomic batch");
                    require(outgoing.drainTo(
                            server.getOutputStream(),4096)>0&&
                        outgoing.queuedBytes()==0,
                        "native S2C queue could not flush to TCP");
                    receiveTwoNativeFrames(clientInput,clientS2c);
                    String last=player.magic().last();

                    // A second original-framed opcode131 on the same
                    // live TCP connection and same World tick must
                    // neither spend/hit nor publish a second response.
                    String duplicate=optedIn.handleTyped(
                        takeNativeCast(probe),writer);
                    require(duplicate.contains(
                            "REJECTED_SAME_TICK_DUPLICATE")&&
                        outgoing.queuedBytes()==0&&
                        player.bank().inventoryCount(556)==1&&
                        player.bank().inventoryCount(558)==1&&
                        world.npcLifecycle().get(
                            canonical.id).hitpoints==1&&
                        last.equals(player.magic().last()),
                        "same-TCP duplicate spent, hit or published");

                    // Simulate next authoritative World tick; still
                    // same TCP session and continuing both ISAAC ciphers.
                    world.clock().advance();
                    String second=optedIn.handleTyped(
                        takeNativeCast(probe),writer);
                    require(second.contains(
                            "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                        second.contains("newlyDead=true")&&
                        player.bank().inventoryCount(556)==0&&
                        player.bank().inventoryCount(558)==0&&
                        world.npcLifecycle().get(
                            canonical.id).dead(),
                        "second TCP spell did not apply lethal hit");
                    require(outgoing.queuedPackets()==1,
                        "second native hit not one batch");
                    require(outgoing.drainTo(
                            server.getOutputStream(),4096)>0,
                        "second S2C native queue not written to socket");
                    receiveTwoNativeFrames(clientInput,clientS2c);

                    require(probe.isAligned()&&
                        probe.decodedCount()==4&&
                        probe.typedRequestCount()==0&&
                        outgoing.queuedBytes()==0,
                        "native TCP decoder/writer not aligned after casts");
                }
            }

            System.out.println(
                "P527_LOOPBACK_TCP_WIND_STRIKE_PASS"+
                " actualLoopbackSockets=true"+
                " clientISAACOpcode185And131=true"+
                " typedHandlerWithExactMetadata=true"+
                " serverISAACOpcode53And65OverTcp=true"+
                " queueAdmissionAndNpcHpRuneCostTogether=true"+
                " sameTickDuplicateNoNativeBytes=true"+
                " nextTickLethalAndBothCiphersRemainAligned=true"+
                " originalLoginAndGraphicalGuiNotExercised=true"+
                " originalSpellDamageTimingXpLootUnverified=true");
        }finally{
            if(canonical!=null)
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,canonical.id);
            if(relayRegistered)
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
