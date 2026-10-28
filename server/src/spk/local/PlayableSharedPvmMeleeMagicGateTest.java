package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * P5.30: shared LocalLab PvM action reservation, independent from the
 * existing melee and magic locks. No original server-style arbitration
 * or native GUI is inferred from these source-only tests.
 */
public final class PlayableSharedPvmMeleeMagicGateTest {
    private static final int[] SEED={431,432,433,434};

    private PlayableSharedPvmMeleeMagicGateTest(){}

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(
            "P530_SHARED_PVM_MELEE_MAGIC "+reason);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=
            new OutboundPacketQueue(4096);
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,new IsaacCipher(SEED.clone()));
        byte[] drain()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.toByteArray();
        }
    }

    private static void add(WorldPlayer player,int id,int qty)
        throws Exception{
        String result=player.bank().spawnItem(
            id,qty,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{441,442,443,444})));
        require(result!=null&&result.contains("OK"),
            "rune fixture item seed failed");
    }

    private static SpellTargetRequest spell(int scene){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
            scene,-1,-1,-1,-1,-1);
    }

    private static void pureTransitionTest()throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer owner=new WorldPlayer();
        long first=world.registerPlayer(owner,"p530-pure");
        long current=first;
        try{
            LocalLabSharedPvmActionGate gate=
                owner.localLabSharedPvmActionGate();

            require(gate.begin(
                    world,first,
                    LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,0)
                    .ticket==null,
                "default should remain inactive");
            require(owner.installLocalLabSharedPvmAdmission(
                    world,first),
                "exact World owner refused trial");
            try{
                owner.installLocalLabSharedPvmAdmission(world,first);
                throw new AssertionError(
                    "duplicate install accepted");
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "P530_SHARED_PVM_ALREADY_INSTALLED"),
                    "unexpected duplicate install error");
            }

            LocalLabSharedPvmActionGate.Admission magic=
                gate.begin(
                    world,first,
                    LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,0);
            require(magic.ticket!=null,
                "first magic action not admitted");
            require("REJECTED_SHARED_PVM_ACTION_IN_FLIGHT".equals(
                    gate.begin(
                        world,first,
                        LocalLabSharedPvmActionGate.Kind
                            .CANONICAL_NPC_BASIC,0).denial),
                "nested melee could overtake magic");
            gate.exit(magic.ticket);

            LocalLabSharedPvmActionGate.Admission melee=
                gate.begin(
                    world,first,
                    LocalLabSharedPvmActionGate.Kind
                        .CANONICAL_NPC_BASIC,0);
            require(melee.ticket!=null,
                "aborted magic unexpectedly burned owner slot");
            gate.mayHaveCommitted(melee.ticket);
            gate.committed(melee.ticket);
            gate.exit(melee.ticket);

            require("REJECTED_SHARED_PVM_ACTION_SAME_TICK".equals(
                    gate.begin(
                        world,first,
                        LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,0)
                        .denial),
                "successful melee did not block same tick magic");
            LocalLabSharedPvmActionGate.Admission followingTick=
                gate.begin(
                    world,first,
                    LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,1);
            require(followingTick.ticket!=null,
                "next tick should be ready in one-tick trial");
            gate.exit(followingTick.ticket);

            // Clear the active probe explicitly; an incomplete attempt
            // before HP publication must not consume an attack.
            LocalLabSharedPvmActionGate gate2=
                new LocalLabSharedPvmActionGate();
            gate2.install(world,first);
            LocalLabSharedPvmActionGate.Admission uncertain=
                gate2.begin(
                    world,first,
                    LocalLabSharedPvmActionGate.Kind
                        .CANONICAL_NPC_BASIC,1);
            gate2.mayHaveCommitted(uncertain.ticket);
            gate2.exit(uncertain.ticket);
            require(gate2.faulted(world,first)&&
                "REJECTED_SHARED_PVM_ACTION_FAULTED".equals(
                    gate2.begin(
                        world,first,
                        LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,2)
                        .denial),
                "uncertain melee HP operation was not fenced");
            // A retired generation must not clear a current fault.
            require(world.unregisterPlayer(owner,first),
                "source owner detach refused");
            current=world.registerPlayer(owner,"p530-pure");
            require(current!=first,
                "owner generation did not rotate");
            require(owner.installLocalLabSharedPvmAdmission(
                    world,current),
                "fresh owner cannot enable independent trial");
            require(gate.begin(
                    world,current,
                    LocalLabSharedPvmActionGate.Kind
                        .CANONICAL_NPC_BASIC,0).ticket!=null,
                "fresh generation inherited previous tick");
            require(gate.begin(
                    world,first,
                    LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,0)
                    .ticket==null,
                "retired generation mutated fresh owner gate");
        }finally{
            if(world.players().owns(owner,current))
                world.unregisterPlayer(owner,current);
            if(!world.closed())world.close();
        }
    }

    private static void realWindStrikeGateTest()throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long gen=world.registerPlayer(
            player,"p530-integrated");
        long current=gen;
        NpcRegistry scene=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),player.id());
        ServerPacketWriter relay=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{451,452,453,454}));
        boolean relayRegistered=false;
        WorldNpc npc=null;
        try{
            SharedNpcWorldRelay.register(
                relay,world,player,scene,player.movement());
            relayRegistered=true;
            npc=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                npc,2,"CUSTOM_LOCALLAB_P530_TWO_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,npc);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity shown=scene.canonical(npc.id);
            require(shown!=null&&
                shown.canonicalId().equals(npc.id),
                "canonical scene projection absent");

            add(player,556,2);
            add(player,558,2);
            require(player.installLocalLabSharedPvmAdmission(
                    world,gen),
                "current player could not enable source test");
            LocalLabSharedPvmActionGate gate=
                player.localLabSharedPvmActionGate();

            LocalLabWindStrikeNpcSettlement spell=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->gen,scene);
            SpellTargetRequest request=spell(shown.sceneIndex);

            // Simulate an in-flight melee transaction from another
            // handler. The *real* spell settlement must reject before
            // HP, inventory, MagicState or outbound packet mutation.
            LocalLabSharedPvmActionGate.Admission melee=
                gate.begin(
                    world,gen,
                    LocalLabSharedPvmActionGate.Kind
                        .CANONICAL_NPC_BASIC,world.clock().tick());
            require(melee.ticket!=null,
                "melee trial did not reserve shared slot");
            Capture blocked=new Capture();
            String denied=spell.settle(
                request,blocked.writer);
            require("REJECTED_SHARED_PVM_ACTION_IN_FLIGHT".equals(
                    denied)&&blocked.drain().length==0&&
                world.npcLifecycle().get(npc.id).hitpoints==2&&
                player.bank().inventoryCount(556)==2&&
                player.bank().inventoryCount(558)==2,
                "in-flight melee reservation did not block real spell");
            gate.exit(melee.ticket);

            // An uncommitted melee slot is retryable for Wind Strike
            // during the exact same World tick.
            Capture first=new Capture();
            String hit=spell.settle(request,first.writer);
            require(hit.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                first.queue.queuedPackets()==1&&
                first.drain().length>0&&
                world.npcLifecycle().get(npc.id).hitpoints==1&&
                player.bank().inventoryCount(556)==1&&
                player.bank().inventoryCount(558)==1,
                "released melee reservation denied legitimate spell");

            require("REJECTED_SHARED_PVM_ACTION_SAME_TICK".equals(
                    gate.begin(
                        world,gen,
                        LocalLabSharedPvmActionGate.Kind
                            .CANONICAL_NPC_BASIC,
                        world.clock().tick()).denial),
                "committed magic did not block same-tick melee");

            world.clock().advance();
            LocalLabSharedPvmActionGate.Admission nextMelee=
                gate.begin(
                    world,gen,
                    LocalLabSharedPvmActionGate.Kind
                        .CANONICAL_NPC_BASIC,world.clock().tick());
            require(nextMelee.ticket!=null,
                "next tick melee reservation refused");
            Capture blockedOther=new Capture();
            String other=spell.settle(
                request,blockedOther.writer);
            require("REJECTED_SHARED_PVM_ACTION_IN_FLIGHT".equals(
                    other)&&blockedOther.drain().length==0,
                "concurrent melee did not suppress next-tick magic");
            gate.exit(nextMelee.ticket);

            Capture second=new Capture();
            String lethal=spell.settle(request,second.writer);
            require(lethal.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                lethal.contains("newlyDead=true")&&
                second.drain().length>0&&
                world.npcLifecycle().get(npc.id).dead()&&
                player.bank().inventoryCount(556)==0&&
                player.bank().inventoryCount(558)==0,
                "second cast failed on released next-tick melee slot");

            // Retired owner re-registers on the SAME current tick.
            // Shared admission is opt-in afresh, not automatically
            // inherited by the new WorldPlayer generation.
            require(world.unregisterPlayer(player,gen),
                "current player detach refused");
            current=world.registerPlayer(player,"p530-integrated");
            require(!gate.enabled(world,current)&&
                gate.begin(
                    world,current,
                    LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,
                    world.clock().tick()).ticket==null,
                "new owner silently inherited old source trial");
            require(player.installLocalLabSharedPvmAdmission(
                    world,current)&&
                gate.begin(
                    world,current,
                    LocalLabSharedPvmActionGate.Kind
                        .CANONICAL_NPC_BASIC,
                    world.clock().tick()).ticket!=null,
                "new owner registration could not activate own trial");

            System.out.println(
                "P530_SHARED_PVM_MELEE_MAGIC_PASS"+
                " defaultOff=true"+
                " exactWorldPlayerGeneration=true"+
                " noCrossMonitorWait=true"+
                " inFlightCrossStyleDenied=true"+
                " sameTickCrossStyleHitDenied=true"+
                " abandonedReservationRetryable=true"+
                " uncertainMeleeDamageFaultsClosed=true"+
                " realWindStrikeRupeAndHpCommit=true"+
                " originalCombatAndNativeGuiNotProven=true");
        }finally{
            if(npc!=null)
                SharedNpcWorldRelay.untrackCanonicalNpc(
                    world,npc.id);
            if(relayRegistered)
                SharedNpcWorldRelay.unregister(relay);
            if(npc!=null){
                world.npcLifecycle().unregisterExact(npc);
                world.npcs().remove(npc.id);
            }
            if(world.players().owns(player,current))
                world.unregisterPlayer(player,current);
            if(!world.closed())world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        pureTransitionTest();
        realWindStrikeGateTest();
    }
}
