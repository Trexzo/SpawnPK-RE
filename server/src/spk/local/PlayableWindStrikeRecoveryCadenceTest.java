package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.28: opt-in source-only recovery admission shared across all Wind
 * Strike service wrappers, scoped to World + WorldPlayer generation.
 * This is a custom three-tick test, NOT original SpawnPK spell cadence.
 */
public final class PlayableWindStrikeRecoveryCadenceTest {
    private static final int[] SEED={351,352,353,354};
    private static final int AIR=556;
    private static final int MIND=558;

    private PlayableWindStrikeRecoveryCadenceTest(){}

    private static void require(boolean yes,String reason){
        if(!yes)throw new AssertionError(
            "P528_PLAYER_WIND_STRIKE_RECOVERY "+reason);
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
        String done=player.bank().spawnItem(
            id,qty,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{361,362,363,364})));
        require(done!=null&&done.contains("OK"),
            "fixture resource seed failed id="+id);
    }

    private static SpellTargetRequest npc(int scene){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
            scene,-1,-1,-1,-1,-1);
    }

    private static void checkDeny(
        LocalLabWindStrikeNpcSettlement cast,
        SpellTargetRequest request,
        String expected,
        World world,WorldNpc npc,WorldPlayer player,
        int expectedHp,int expectedRunes
    )throws Exception{
        String before=player.magic().last();
        Capture denied=new Capture(4096);
        String status=cast.settle(request,denied.writer);
        require(status.equals(expected)&&
            denied.queue.queuedBytes()==0&&
            denied.drain().length==0&&
            world.npcLifecycle().get(npc.id).hitpoints==expectedHp&&
            player.bank().inventoryCount(AIR)==expectedRunes&&
            player.bank().inventoryCount(MIND)==expectedRunes&&
            before.equals(player.magic().last()),
            "incorrect denied recovery "+status+" want="+expected);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(
            player,"p528-recovery");
        long current=generation;
        NpcRegistry scene=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),player.id());
        ServerPacketWriter relay=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{371,372,373,374}));
        boolean relayRegistered=false;
        WorldNpc target=null;
        try{
            SharedNpcWorldRelay.register(
                relay,world,player,scene,player.movement());
            relayRegistered=true;
            target=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                target,4,"CUSTOM_LOCALLAB_P528_FOUR_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,target);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity visible=scene.canonical(target.id);
            require(visible!=null&&
                target.id.equals(visible.canonicalId()),
                "canonical NPC not scene-projected");
            SpellTargetRequest request=npc(visible.sceneIndex);
            add(player,AIR,4);
            add(player,MIND,4);

            require(player.installLocalLabWindStrikeRecovery(
                    world,generation,3),
                "current World owner policy install refused");
            try{
                player.installLocalLabWindStrikeRecovery(
                    world,generation,2);
                throw new AssertionError(
                    "second same-generation policy install admitted");
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "P528_RECOVERY_POLICY_ALREADY_INSTALLED"),
                    "wrong policy reconfiguration denial");
            }
            LocalLabWindStrikeNpcSettlement first=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->generation,scene);
            LocalLabWindStrikeNpcSettlement sibling=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->generation,scene);

            Capture firstWire=new Capture(4096);
            require(first.settle(request,firstWire.writer).contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                firstWire.drain().length>0&&
                world.npcLifecycle().get(target.id).hitpoints==3&&
                player.bank().inventoryCount(AIR)==3,
                "initial local recovery-enabled hit not settled");

            checkDeny(sibling,request,"REJECTED_SAME_TICK_DUPLICATE",
                world,target,player,3,3);
            world.clock().advance();
            checkDeny(first,request,"REJECTED_LOCAL_LAB_SPELL_RECOVERY",
                world,target,player,3,3);
            world.clock().advance();
            checkDeny(sibling,request,"REJECTED_LOCAL_LAB_SPELL_RECOVERY",
                world,target,player,3,3);

            world.clock().advance();
            // Tick is ready; writer backpressure must NOT consume the
            // shared recovery or future admission for any wrapper.
            Capture full=new Capture(1024);
            byte[] sentinel=new byte[1024];
            Arrays.fill(sentinel,(byte)0x41);
            full.queue.offer(sentinel);
            String blocked=first.settle(request,full.writer);
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE".equals(
                    blocked)&&
                Arrays.equals(full.drain(),sentinel)&&
                world.npcLifecycle().get(target.id).hitpoints==3&&
                player.bank().inventoryCount(AIR)==3,
                "backpressure burned recovery or changed HP/runes");

            Capture retry=new Capture(4096);
            require(sibling.settle(request,retry.writer).contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                retry.drain().length>0&&
                world.npcLifecycle().get(target.id).hitpoints==2&&
                player.bank().inventoryCount(AIR)==2&&
                player.bank().inventoryCount(MIND)==2,
                "sibling could not settle on same ready tick after retry");

            try{
                player.installLocalLabWindStrikeRecovery(
                    world,generation,3);
                throw new AssertionError(
                    "post-commit policy replacement admitted");
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "P528_RECOVERY_POLICY_ALREADY_INSTALLED"),
                    "unexpected policy replacement message");
            }
            require("REJECTED_WORLD_TICK_REGRESSION".equals(
                    player.localLabWindStrikeRecoveryRejection(
                        world,generation,world.clock().tick()-1)),
                "regressed World tick not fail-closed");

            world.clock().advance();
            checkDeny(first,request,"REJECTED_LOCAL_LAB_SPELL_RECOVERY",
                world,target,player,2,2);
            world.clock().advance();
            checkDeny(sibling,request,"REJECTED_LOCAL_LAB_SPELL_RECOVERY",
                world,target,player,2,2);

            world.clock().advance();
            Capture third=new Capture(4096);
            require(first.settle(request,third.writer).contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                third.drain().length>0&&
                world.npcLifecycle().get(target.id).hitpoints==1&&
                player.bank().inventoryCount(AIR)==1,
                "third-tick source recovery did not open");

            require(world.unregisterPlayer(player,generation),
                "old player registration did not detach");
            checkDeny(first,request,"REJECTED_STALE_WORLD_OWNER",
                world,target,player,1,1);
            current=world.registerPlayer(player,"p528-recovery");
            require(current!=generation,
                "new canonical player generation was not rotated");
            // A new owner generation must not inherit the retired
            // three-tick policy or old tick admission ledger.
            final long next=current;
            require(player.installLocalLabWindStrikeRecovery(
                    world,next,2),
                "fresh generation could not install independent policy");
            Capture fresh=new Capture(4096);
            LocalLabWindStrikeNpcSettlement newlyAttached=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->next,scene);
            require(newlyAttached.settle(request,fresh.writer).contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                fresh.drain().length>0&&
                world.npcLifecycle().get(target.id).dead()&&
                player.bank().inventoryCount(AIR)==0,
                "new owner inherited retired recovery state");

            System.out.println(
                "P528_PLAYER_WIND_STRIKE_RECOVERY_PASS"+
                " customSourceRecoveryTicks=3"+
                " playerOwnedAcrossWrappers=true"+
                " rejectedCastDoesNotConsumeRecovery=true"+
                " queueBackpressureRetrySameTick=true"+
                " tickRegressionFailClosed=true"+
                " generationABAIsolation=true"+
                " originalSpawnpkCadenceAndGuiNotProven=true");
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
            if(world.players().owns(player,current))
                world.unregisterPlayer(player,current);
            if(!world.closed())world.close();
        }
    }
}
