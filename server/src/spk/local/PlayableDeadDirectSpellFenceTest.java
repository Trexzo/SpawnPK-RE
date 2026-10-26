package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * P5.7: production-bound direct spell widget route, including all
 * three home teleports, must fail closed during DEAD/HP0 without
 * granting the LocalSession teleport token or changing MagicState.
 * Source-only, not original client graphical or concurrent cast proof.
 */
public final class PlayableDeadDirectSpellFenceTest {
    private static final int[] SEED={151,152,153,154};
    private PlayableDeadDirectSpellFenceTest(){}

    private static void require(boolean ok,String what){
        if(!ok)throw new AssertionError(
            "P57_DEAD_DIRECT_SPELL "+what);
    }
    private static final class Capture {
        final ByteArrayOutputStream wire=new ByteArrayOutputStream();
        final ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(SEED.clone()));
    }
    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected CLI args");
        WorldPlayer player=new WorldPlayer();
        LocalGameplayWidgetHandler gameplay=
            new LocalGameplayWidgetHandler(player);
        Capture capture=new Capture();

        // A direct Home Teleport is accepted and exposes its token.
        String baseline=gameplay.handle(1195,capture.writer);
        require(baseline.contains(
            "ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Home Teleport")&&
            gameplay.consumeAcceptedHomeTeleport()&&
            !gameplay.consumeAcceptedHomeTeleport(),
            "living Home Teleport token is not exactly once");

        // Prepare ordinary resource-requiring direct Magic.
        player.bank().spawnItem(557,2,capture.writer);
        player.bank().spawnItem(555,2,capture.writer);
        player.bank().spawnItem(561,1,capture.writer);
        String normal=gameplay.handle(1159,capture.writer);
        require(normal.contains(
            "ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Bones to bananas"),
            "living direct spell baseline");
        String acceptedLast=player.magic().last();
        require(acceptedLast.contains("DIRECT_ACCEPT"),
            "accepted direct spell did not update last");

        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);
        PlayerLifecycleService.DamageResult hit=
            lifecycle.applyDamage(500,20L,"P57_LETHAL");
        require(hit.died&&
            player.lifecycle().dead()&&
            player.playerState().currentLevel(
                PlayerState.HITPOINTS)==0,
            "lethal damage state");

        int bytes=capture.wire.size();
        for(int widget:new int[]{1195,1159,12856,30000}){
            String result=gameplay.handle(widget,capture.writer);
            require(result.contains("REJECTED_CASTER_DEAD")&&
                !gameplay.consumeAcceptedHomeTeleport()&&
                player.magic().last().equals(acceptedLast)&&
                capture.wire.size()==bytes,
                "dead direct spell or teleport accepted widget="+widget);
        }

        require(lifecycle.tick(24L)==
            PlayerLifecycleService.TickResult.NONE,
            "respawn allowed early");
        String stillDead=gameplay.handle(1195,capture.writer);
        require(stillDead.contains("REJECTED_CASTER_DEAD")&&
            !gameplay.consumeAcceptedHomeTeleport()&&
            capture.wire.size()==bytes,
            "pre-respawn Home Teleport");

        require(lifecycle.tick(25L)==
            PlayerLifecycleService.TickResult.RESPAWNED&&
            player.playerState().currentLevel(
                PlayerState.HITPOINTS)==99&&
            player.lifecycle().alive(),
            "expected respawn");
        String live=gameplay.handle(1195,capture.writer);
        require(live.contains(
            "ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Home Teleport")&&
            gameplay.consumeAcceptedHomeTeleport()&&
            !gameplay.consumeAcceptedHomeTeleport(),
            "after-respawn Home Teleport not restored");

        // Fail closed if a broken lifecycle marks ALIVE but HP is zero.
        require(player.playerState().applyHitpointsDamage(99)==99&&
            player.playerState().currentLevel(PlayerState.HITPOINTS)==0&&
            player.lifecycle().alive(),
            "explicit inconsistent ALIVE/zero-HP fixture");
        bytes=capture.wire.size();
        String inconsistent=gameplay.handle(1195,capture.writer);
        require(inconsistent.contains("REJECTED_CASTER_DEAD")&&
            !gameplay.consumeAcceptedHomeTeleport()&&
            capture.wire.size()==bytes,
            "zero-HP ALIVE teleport admitted");

        // Unrelated IDs must still flow to other UI routes.
        require(gameplay.handle(999999,capture.writer)==null&&
            !gameplay.consumeAcceptedHomeTeleport(),
            "unrelated widget was swallowed");

        System.out.println(
            "P57_DEAD_DIRECT_SPELL_FENCE_PASS"+
            " productionWorldPlayerBound=true"+
            " normalSpellAccepted=true"+
            " deadHome1195Ancient12856Lunar30000Denied=true"+
            " deadResourceSpell1159Denied=true"+
            " deadNoTeleportToken=true"+
            " deadNoPacketOrLastMutation=true"+
            " beforeRespawnDenied=true"+
            " postRespawnTokenRestored=true"+
            " zeroHpAliveRejected=true"+
            " unrelatedUiFallthrough=true"+
            " originalGuiAndConcurrentDeathRaceUnverified=true");
    }
}
