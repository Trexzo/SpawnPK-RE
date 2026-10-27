package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * P5.10: the live Quick ON service must reject DEAD and contradictory
 * ALIVE/zero-HP before applying selected prayer state or packets.
 */
public final class PlayableQuickPrayerOnZeroHpTest {
    private static final int[] SEED={141,142,143,144};
    private static final int THICK_SKIN=5609;

    private PlayableQuickPrayerOnZeroHpTest(){}

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(
            "P510_QUICK_ON_ZERO_HP "+message);
    }

    private static final class Capture {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        final OutboundPacketQueue queue=new OutboundPacketQueue(4096);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));
        byte[] drain()throws IOException{
            queue.drainTo(bytes,1<<20);
            return bytes.toByteArray();
        }
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,"p510-quick-on");
        try{
            QuickPrayerSelectionService selection=
                new QuickPrayerSelectionService(
                    player,"LOCAL_LAB_POLICY_P510_ON_LIFECYCLE");
            selection.beginEdit();
            selection.toggleDraft("normal:thick_skin");
            selection.confirmEdit();

            LocalQuickPrayerActivationService on=
                new LocalQuickPrayerActivationService(
                    player,selection);
            LocalQuickPrayerDeactivationService off=
                new LocalQuickPrayerDeactivationService(player);
            Capture alive=new Capture();
            require(on.activate(alive.writer).startsWith(
                "QUICK_PRAYERS_ON")&&
                player.prayers().activeWidget(THICK_SKIN)&&
                player.prayers().activeCount()==1&&
                alive.drain().length>0,
                "baseline Quick ON");

            Capture disable=new Capture();
            require(off.deactivate(disable.writer).startsWith(
                "QUICK_PRAYERS_OFF")&&
                player.prayers().activeCount()==0&&
                disable.drain().length>0,
                "baseline OFF between ON attempts");

            PlayerLifecycleService lifecycle=
                new PlayerLifecycleService(player);
            PlayerLifecycleService.DamageResult dead=
                lifecycle.applyDamage(500,20L,"P510_LETHAL");
            require(dead.died&&player.lifecycle().dead()&&
                player.playerState().currentLevel(
                    PlayerState.HITPOINTS)==0,
                "lethal fixture");

            Capture denied=new Capture();
            require("REJECTED_PLAYER_DEAD".equals(
                    on.activate(denied.writer))&&
                denied.drain().length==0&&
                player.prayers().activeCount()==0,
                "DEAD Quick ON accepted");

            require(lifecycle.tick(24L)==
                PlayerLifecycleService.TickResult.NONE&&
                player.lifecycle().dead(),
                "respawn earlier than 25");
            Capture preRespawn=new Capture();
            require("REJECTED_PLAYER_DEAD".equals(
                    on.activate(preRespawn.writer))&&
                preRespawn.drain().length==0,
                "pre-respawn Quick ON accepted");

            require(lifecycle.tick(25L)==
                PlayerLifecycleService.TickResult.RESPAWNED&&
                player.lifecycle().alive()&&
                player.playerState().currentLevel(
                    PlayerState.HITPOINTS)==99,
                "expected tick25 respawn");

            Capture restored=new Capture();
            require(on.activate(restored.writer).startsWith(
                "QUICK_PRAYERS_ON")&&
                player.prayers().activeWidget(THICK_SKIN)&&
                restored.drain().length>0,
                "Quick ON after respawn");

            Capture cleared=new Capture();
            require(off.deactivate(cleared.writer).startsWith(
                "QUICK_PRAYERS_OFF")&&
                player.prayers().activeCount()==0&&
                cleared.drain().length>0,
                "post-respawn OFF did not restore clean state");

            // setCurrentLevel(HP,0) clamps to 1; use actual HP damage
            // without lifecycle.markDead to test the inconsistent input.
            require(player.playerState()
                    .applyHitpointsDamage(99)==99&&
                player.playerState().currentLevel(
                    PlayerState.HITPOINTS)==0&&
                player.lifecycle().alive(),
                "ALIVE with HP0 fixture not constructed");
            Capture zeroHp=new Capture();
            require("REJECTED_PLAYER_DEAD".equals(
                    on.activate(zeroHp.writer))&&
                zeroHp.drain().length==0&&
                !player.prayers().activeWidget(THICK_SKIN)&&
                player.prayers().activeCount()==0,
                "ALIVE/zero-HP Quick ON admission bypass");

            System.out.println(
                "P510_QUICK_ON_ZERO_HP_PASS"+
                " aliveOnAndOff=true"+
                " deadOnZeroBytes=true"+
                " beforeRespawnDenied=true"+
                " respawnOnRestored=true"+
                " aliveZeroHpDenied=true"+
                " noPartialPrayerState=true"+
                " exactV308ClientGuiNotTested=true"+
                " prayerDrainAndCombatEffectsNotClaimed=true");
        }finally{
            if(player.registered())
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }
}
