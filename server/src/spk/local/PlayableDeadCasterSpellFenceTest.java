package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * P5.6: real production WorldPlayer binding must deny all targeted
 * spells during DEAD_WAITING_RESPAWN before MagicState accepts a
 * request or combat/packet side effects occur.
 *
 * Source gameplay only, not an original GUI or concurrency/World epoch proof.
 */
public final class PlayableDeadCasterSpellFenceTest {
    private static final int[] SEED={91,92,93,94};

    private PlayableDeadCasterSpellFenceTest(){}

    private static void require(boolean value,String why){
        if(!value)throw new AssertionError(
            "P56_DEAD_CASTER_FENCE "+why);
    }

    private static SpellTargetRequest playerTarget(){
        return new SpellTargetRequest(
            249,SpellTargetRequest.Kind.PLAYER,
            19100,7,-1,-1,-1,-1,-1);
    }

    private static SpellTargetRequest npcTarget(){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            19100,103,-1,-1,-1,-1,-1);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        WorldPlayer player=new WorldPlayer();
        ByteArrayOutputStream published=new ByteArrayOutputStream();
        ServerPacketWriter packets=new ServerPacketWriter(
            published,new IsaacCipher(SEED.clone()));
        NpcRegistry registry=new NpcRegistry();
        CombatEngine combat=new CombatEngine();
        LocalSpellTargetHandler handler=new LocalSpellTargetHandler(
            player,registry,combat);
        require(player.playerState().currentLevel(
            PlayerState.HITPOINTS)==99,"initial HP");

        String alive=handler.handle(playerTarget(),packets);
        require(alive.contains(
            "ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Air surge")&&
            alive.contains("TARGET_PLAYER_ROUTED index=7")&&
            published.size()==0,
            "alive spell route baseline or unknown player effect drift");
        String previous=player.magic().last();
        require(previous.startsWith("TARGET_ACCEPT "),
            "accepted spell should set MagicState.last");

        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(player);
        PlayerLifecycleService.DamageResult dead=
            lifecycle.applyDamage(500,20L,"P56_LETHAL");
        require(dead.died&&
            player.lifecycle().dead()&&
            player.playerState().currentLevel(
                PlayerState.HITPOINTS)==0&&
            player.lifecycle().respawnTick()==25L,
            "lethal fixture");

        int bytes=published.size();
        String deadPlayer=handler.handle(playerTarget(),packets);
        String deadNpc=handler.handle(npcTarget(),packets);
        require(deadPlayer.contains("REJECTED_CASTER_DEAD")&&
            deadNpc.contains("REJECTED_CASTER_DEAD")&&
            !deadNpc.contains("TARGET_NPC_VISIBLE")&&
            !deadNpc.contains("magicFixtureHit")&&
            published.size()==bytes&&
            player.magic().last().equals(previous),
            "dead targeted spell admitted/changed state/published bytes");

        require(lifecycle.tick(24L)==
            PlayerLifecycleService.TickResult.NONE,
            "cast fixture respawned early");
        String early=handler.handle(playerTarget(),packets);
        require(early.contains("REJECTED_CASTER_DEAD")&&
            published.size()==bytes,
            "spell admitted before respawn deadline");

        require(lifecycle.tick(25L)==
            PlayerLifecycleService.TickResult.RESPAWNED&&
            player.lifecycle().alive()&&
            player.playerState().currentLevel(
                PlayerState.HITPOINTS)==99,
            "recovery at expected tick");

        String resumed=handler.handle(playerTarget(),packets);
        require(resumed.contains(
            "ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Air surge")&&
            resumed.contains("TARGET_PLAYER_ROUTED index=7")&&
            published.size()==bytes,
            "alive caster not restored after respawn");

        player.playerState().setCurrentLevel(
            PlayerState.HITPOINTS,0);
        String inconsistent=handler.handle(playerTarget(),packets);
        require(inconsistent.contains("REJECTED_CASTER_DEAD")&&
            published.size()==bytes,
            "zero HP with ALIVE lifecycle admitted spell");

        require(Arrays.equals(
            published.toByteArray(),new byte[0]),
            "no-op player-target spell unexpectedly wrote packets");

        System.out.println(
            "P56_PLAYABLE_DEAD_CASTER_FENCE_PASS"+
            " productionOwnerBound=true"+
            " aliveSpellAccepted=true"+
            " lethalDeadPlayerAndNpcSpellDenied=true"+
            " deadCastZeroBytes=true"+
            " magicLastUnchangedWhileDead=true"+
            " beforeRespawnDenied=true"+
            " postRespawnAccepted=true"+
            " zeroHpAliveDenied=true"+
            " sourceOnlyNoGUI=true"+
            " concurrentCastDeathFenceClaim=false"+
            " runeConsumptionAndSpellDamageClaim=false");
    }
}
