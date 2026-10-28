package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * P5.29 owner-scoped attack phase smoke:
 *  1. pure deterministic transition validation;
 *  2. actual canonical NPC+runes+queue joint source fixture,
 *     same-thread reentry and failures before/after queue admission.
 *
 * Nothing here enables actual LocalSession or authentic spell timing.
 */
public final class PlayableWindStrikeAttackLifecycleTest {
    private static final int[] SEED={401,402,403,404};

    private PlayableWindStrikeAttackLifecycleTest(){}

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(
            "P529_WIND_STRIKE_ATTACK_LIFECYCLE "+reason);
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

    private static void add(WorldPlayer owner,int id,int count)
        throws IOException{
        String result=owner.bank().spawnItem(
            id,count,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{411,412,413,414})));
        require(result!=null&&result.contains("OK"),
            "fixture resource seed failed");
    }

    private static SpellTargetRequest npc(int scene){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
            scene,-1,-1,-1,-1,-1);
    }

    private static void phases()throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long gen=world.registerPlayer(player,"p529-phase-model");
        long current=gen;
        try{
            LocalLabWindStrikeAttackLifecycle lifecycle=
                player.localLabWindStrikeAttackLifecycle();
            require(lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.READY,
                "newly bound lifecycle must be READY");
            LocalLabWindStrikeAttackLifecycle.Admission one=
                lifecycle.begin(world,gen,0);
            require(one.ticket!=null&&
                lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.VALIDATING,
                "first ticket did not enter VALIDATING");

            LocalLabWindStrikeAttackLifecycle.Admission nested=
                lifecycle.begin(world,gen,0);
            require("REJECTED_WIND_STRIKE_ATTACK_IN_FLIGHT".equals(
                    nested.denial)&&nested.ticket==null,
                "reentrant attack admitted under same owner");
            try{
                lifecycle.packetsAdmitted(one.ticket);
                throw new AssertionError(
                    "VALIDATING illegally skipped PUBLISHING");
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "P529_WIND_STRIKE_PHASE_TRANSITION_REQUIRED"),
                    "wrong rejected state transition");
            }

            lifecycle.exit(one.ticket);
            require(lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.READY,
                "aborted validation poisoned recovery");
            LocalLabWindStrikeAttackLifecycle.Admission two=
                lifecycle.begin(world,gen,0);
            lifecycle.publishing(two.ticket);
            require(lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.PUBLISHING,
                "publishing transition absent");
            lifecycle.exit(two.ticket);
            require(lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.READY,
                "prequeue publication denial should be retryable");

            LocalLabWindStrikeAttackLifecycle.Admission three=
                lifecycle.begin(world,gen,0);
            lifecycle.publishing(three.ticket);
            lifecycle.packetsAdmitted(three.ticket);
            require(lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.PACKETS_ADMITTED,
                "committed native queue phase not recorded");
            lifecycle.exit(three.ticket);
            require(lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.FAULTED,
                "postqueue incomplete semantic transition not poisoned");
            require("REJECTED_WIND_STRIKE_ATTACK_FAULTED".equals(
                    lifecycle.begin(world,gen,1).denial),
                "uncertain postqueue owner replay admitted");

            require(world.unregisterPlayer(player,gen),
                "phase owner unregister rejected");
            current=world.registerPlayer(player,"p529-phase-model");
            require(current!=gen,
                "World re-registration did not rotate generation");
            LocalLabWindStrikeAttackLifecycle.Admission fresh=
                lifecycle.begin(world,current,0);
            require(fresh.ticket!=null&&
                lifecycle.phase(world,current)==
                    LocalLabWindStrikeAttackLifecycle.Phase.VALIDATING,
                "new World generation inherited retired poison");
            lifecycle.publishing(fresh.ticket);
            lifecycle.packetsAdmitted(fresh.ticket);
            lifecycle.hitCommitted(fresh.ticket);
            lifecycle.exit(fresh.ticket);
            require(lifecycle.phase(world,current)==
                    LocalLabWindStrikeAttackLifecycle.Phase.RECOVERING,
                "committed phase failed to transition to recovery");
            // Retired ticket cannot reset or complete the new owner.
            lifecycle.exit(three.ticket);
            require(lifecycle.phase(world,current)==
                    LocalLabWindStrikeAttackLifecycle.Phase.RECOVERING,
                "stale ticket changed fresh owner phase");
        }finally{
            if(world.players().owns(player,current))
                world.unregisterPlayer(player,current);
            if(!world.closed())world.close();
        }
    }

    private static void integrated()throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long gen=world.registerPlayer(
            player,"p529-integrated");
        long current=gen;
        NpcRegistry view=new NpcRegistry(
            new DevAuthorityWorkbench(),
            world.petNpcs(),player.id());
        ServerPacketWriter relay=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{421,422,423,424}));
        WorldNpc target=null;
        boolean relayRegistered=false;

        try{
            SharedNpcWorldRelay.register(
                relay,world,player,view,player.movement());
            relayRegistered=true;
            target=world.npcs().spawn(
                100,3088,3495,0);
            world.npcLifecycle().register(
                target,2,"CUSTOM_LOCALLAB_P529_TWO_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,target);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity presented=view.canonical(target.id);
            require(presented!=null&&
                target.id.equals(presented.canonicalId()),
                "test NPC not canonically presented");

            SpellTargetRequest request=npc(
                presented.sceneIndex);
            add(player,556,2);
            add(player,558,2);
            LocalLabWindStrikeAttackLifecycle lifecycle=
                player.localLabWindStrikeAttackLifecycle();

            // A failed validation-to-publication callback must abort
            // without any native S2C bytes or consumed action slot.
            LocalLabWindStrikeNpcSettlement before=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->gen,view,
                    ()->{throw new IllegalStateException(
                        "P529_INJECT_BEFORE_QUEUE");},()->{});
            Capture beforeWire=new Capture();
            try{
                before.settle(request,beforeWire.writer);
                throw new AssertionError(
                    "prequeue injected failure did not propagate");
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "P529_INJECT_BEFORE_QUEUE"),
                    "unexpected prequeue exception");
            }
            require(lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.READY&&
                beforeWire.drain().length==0&&
                player.bank().inventoryCount(556)==2&&
                player.bank().inventoryCount(558)==2&&
                world.npcLifecycle().get(target.id).hitpoints==2,
                "prequeue failure was not fully retryable");

            LocalLabWindStrikeNpcSettlement nested=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->gen,view);
            final String[] nestedResult={null};
            final Capture nestedWire=new Capture();
            LocalLabWindStrikeNpcSettlement outer=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->gen,view,
                    ()->{
                        try{
                            nestedResult[0]=nested.settle(
                                request,nestedWire.writer);
                        }catch(IOException error){
                            throw new IllegalStateException(error);
                        }
                    },
                    ()->{});
            Capture outerWire=new Capture();
            String first=outer.settle(
                request,outerWire.writer);
            require("REJECTED_WIND_STRIKE_ATTACK_IN_FLIGHT".equals(
                    nestedResult[0])&&
                nestedWire.drain().length==0&&
                first.startsWith("LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                outerWire.queue.queuedPackets()==1&&
                outerWire.drain().length>0&&
                player.bank().inventoryCount(556)==1&&
                player.bank().inventoryCount(558)==1&&
                world.npcLifecycle().get(target.id).hitpoints==1&&
                lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.RECOVERING,
                "same-thread nested callback duplicated or lost outer hit");

            world.clock().advance();

            // Intentionally abort AFTER native output queue admission
            // and BEFORE the HP/rune semantic write to prove fail-closed
            // poisoning rather than an unsafe replay opportunity.
            LocalLabWindStrikeNpcSettlement after=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->gen,view,()->{},
                    ()->{throw new IllegalStateException(
                        "P529_INJECT_AFTER_QUEUE");});
            Capture afterWire=new Capture();
            try{
                after.settle(request,afterWire.writer);
                throw new AssertionError(
                    "postqueue injected failure did not propagate");
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "P529_INJECT_AFTER_QUEUE"),
                    "unexpected postqueue failure");
            }

            require(lifecycle.phase(world,gen)==
                    LocalLabWindStrikeAttackLifecycle.Phase.FAULTED&&
                afterWire.queue.queuedPackets()==1&&
                afterWire.queue.queuedBytes()>0&&
                world.npcLifecycle().get(target.id).hitpoints==1&&
                player.bank().inventoryCount(556)==1&&
                player.bank().inventoryCount(558)==1,
                "postqueue semantic fault not fenced against replay");

            Capture denied=new Capture();
            String replay=nested.settle(
                request,denied.writer);
            require("REJECTED_WIND_STRIKE_ATTACK_FAULTED".equals(
                    replay)&&
                denied.drain().length==0&&
                world.npcLifecycle().get(target.id).hitpoints==1&&
                player.bank().inventoryCount(556)==1,
                "faulted owner permitted double hit/rune spend");

            // The test does NOT drain the intentionally unaccounted
            // native output; a real runtime would have to terminate
            // or reconcile the session and durable state.
            require(world.unregisterPlayer(player,gen),
                "faulted owner detach rejected");
            current=world.registerPlayer(
                player,"p529-integrated");
            final long freshGen=current;
            LocalLabWindStrikeNpcSettlement fresh=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->freshGen,view);
            Capture newWire=new Capture();
            require(fresh.settle(request,newWire.writer).contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                newWire.drain().length>0&&
                world.npcLifecycle().get(target.id).dead()&&
                player.bank().inventoryCount(556)==0&&
                player.bank().inventoryCount(558)==0&&
                lifecycle.phase(world,current)==
                    LocalLabWindStrikeAttackLifecycle.Phase.RECOVERING,
                "fresh generation inherited uncertain in-JVM phase");

            System.out.println(
                "P529_WIND_STRIKE_ATTACK_LIFECYCLE_PASS"+
                " ownerScopedStateMachine=true"+
                " sourcePhaseTransitionsValidated=true"+
                " sameThreadReentryBlocked=true"+
                " beforeQueueFailureRetryable=true"+
                " postQueueFailurePoisoned=true"+
                " noDoubleRuneOrNpcHpOnFault=true"+
                " exactOwnerGenerationIsolation=true"+
                " realProjectileAndNativeGuiUnverified=true");
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

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        phases();
        integrated();
    }
}
