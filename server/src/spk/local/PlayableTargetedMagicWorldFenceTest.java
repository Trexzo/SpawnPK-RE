package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * P5.18: targeted spell validation and MagicState mutation require
 * continuous current World+player-generation ownership, not just a
 * preflight that can race detach or re-register.
 *
 * This does NOT simulate actual combat, rune spending, graphical v308,
 * cross-JVM ownership, or durable account settlement.
 */
public final class PlayableTargetedMagicWorldFenceTest {
    private static final int SPELL=19100;
    private static final int[] SEED={261,262,263,264};

    private PlayableTargetedMagicWorldFenceTest(){}

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(
            "P518_TARGET_MAGIC_WORLD_FENCE "+why);
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
        WorldPlayer player=new WorldPlayer();
        long first=world.registerPlayer(player,"p518-target-owner");
        long current=first;
        try{
            NpcRegistry view=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),player.id());
            ByteArrayOutputStream wire=new ByteArrayOutputStream();
            ServerPacketWriter packets=writer(wire);
            LocalSpellTargetHandler old=
                new LocalSpellTargetHandler(
                    world,player,()->first,view,
                    new CombatEngine());

            String live=old.handle(playerTarget(),packets);
            require(live.contains(
                    "TARGET_PLAYER_ROUTED index=7")&&
                player.magic().last().startsWith("TARGET_ACCEPT")&&
                wire.size()==0,
                "live player-target routing not preserved");
            String acceptedLast=player.magic().last();

            require(world.unregisterPlayer(player,first),
                "first-generation unregister refused");

            // A detached session must not alter MagicState or publish bytes.
            String detached=old.handle(playerTarget(),packets);
            require(detached.contains(
                    "REJECTED_STALE_CASTER_WORLD_OWNER")&&
                acceptedLast.equals(player.magic().last())&&
                wire.size()==0,
                "detached owner mutated targeted Magic");

            // Re-registering this exact same Java object is not permission
            // for a retired LocalSession's old generation to cast (ABA).
            current=world.registerPlayer(
                player,"p518-target-owner");
            require(current!=first,"World did not rotate generation");
            String aba=old.handle(playerTarget(),packets);
            require(aba.contains(
                    "REJECTED_STALE_CASTER_WORLD_OWNER")&&
                acceptedLast.equals(player.magic().last())&&
                wire.size()==0,
                "old World generation accepted after ABA");

            final long freshGeneration=current;
            LocalSpellTargetHandler fresh=
                new LocalSpellTargetHandler(
                    world,player,()->freshGeneration,
                    view,new CombatEngine());
            String freshCast=fresh.handle(playerTarget(),packets);
            require(freshCast.contains(
                    "TARGET_PLAYER_ROUTED index=7")&&
                wire.size()==0,
                "fresh-generation targeting rejected");

            // Controlled generation-supplier handoff: detach after a
            // spell starts but before its World ownership admission.
            CountDownLatch readEntered=new CountDownLatch(1);
            CountDownLatch allowRead=new CountDownLatch(1);
            AtomicReference<String> racedResponse=
                new AtomicReference<>();
            AtomicReference<Throwable> racedFailure=
                new AtomicReference<>();
            LocalSpellTargetHandler raced=
                new LocalSpellTargetHandler(
                    world,player,()->{
                        readEntered.countDown();
                        try{
                            if(!allowRead.await(
                                    5L,TimeUnit.SECONDS))
                                throw new IllegalStateException(
                                    "P518 generation gate timeout");
                        }catch(InterruptedException interrupted){
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(
                                interrupted);
                        }
                        return freshGeneration;
                    },view,new CombatEngine());
            Thread attempt=new Thread(()->{
                try{
                    racedResponse.set(raced.handle(
                        playerTarget(),packets));
                }catch(Throwable problem){
                    racedFailure.set(problem);
                }
            },"p518-in-flight-cast");
            attempt.start();
            try{
                require(readEntered.await(5L,TimeUnit.SECONDS),
                    "concurrent cast did not enter generation read");
                require(world.unregisterPlayer(
                        player,freshGeneration),
                    "concurrent owner detach refused");
            }finally{
                allowRead.countDown();
            }
            attempt.join(5000L);
            require(!attempt.isAlive()&&
                racedFailure.get()==null&&
                racedResponse.get()!=null&&
                racedResponse.get().contains(
                    "REJECTED_STALE_CASTER_WORLD_OWNER")&&
                acceptedLast.equals(player.magic().last())&&
                wire.size()==0,
                "in-flight revoked spell was admitted");
            current=-1L;

            // A closed World cannot validate an otherwise intact
            // local owner/session (no synthetic re-registration).
            world.close();
            String closed=fresh.handle(playerTarget(),packets);
            require(closed.contains(
                    "REJECTED_STALE_CASTER_WORLD_OWNER")&&
                acceptedLast.equals(player.magic().last())&&
                wire.size()==0,
                "closed World admitted targeted Magic");

            System.out.println(
                "P518_TARGET_MAGIC_WORLD_FENCE_PASS"+
                " continuousWorldGenerationFence=true"+
                " detachedAndAbaDenied=true"+
                " inFlightDetachDenied=true"+
                " freshGenerationWorks=true"+
                " closedWorldDenied=true"+
                " noPhantomMagicLastMutation=true"+
                " zeroPacketOnDenial=true"+
                " actualPvmRuneDamageAndGuiUnverified=true");
        }finally{
            if(!world.closed()){
                if(current>=0&&world.players().owns(player,current))
                    world.unregisterPlayer(player,current);
                world.close();
            }
        }
    }
}
