package spk.local;

import java.io.IOException;

/**
 * PvP client presentation using only already-proven transports.
 *
 * The exact current player hitsplat/HP-bar mask has not been certified in this
 * LocalLab line. Do not invent it. The defender receives an authoritative HP
 * skill update through exact packet 134 while packet-81 continues to relay the
 * attacker's presentation to remote viewers.
 */
final class PlayerCombatPresentationAdapter {
    static final String HIT_SPLAT_AUTHORITY=
        "UNRESOLVED_CLIENT_PACKET_AUTHORITY";

    static final class Result {
        final boolean attackerPresentationPublished;
        final boolean defenderHpPublished;
        final String hitsplatAuthority;

        Result(
            boolean attackerPresentationPublished,
            boolean defenderHpPublished,
            String hitsplatAuthority
        ){
            this.attackerPresentationPublished=
                attackerPresentationPublished;
            this.defenderHpPublished=
                defenderHpPublished;
            this.hitsplatAuthority=
                hitsplatAuthority;
        }

        @Override public String toString(){
            return "PlayerCombatPresentation{attacker81="+
                attackerPresentationPublished+
                ",defenderHp134="+
                defenderHpPublished+
                ",hitsplat="+
                hitsplatAuthority+"}";
        }
    }

    Result publishAttackAndHp(
        int animation,
        int targetValue,
        WorldPlayer target,
        ServerPacketWriter attackerWriter
    )throws IOException{
        if(target==null)
            throw new NullPointerException("target");
        if(attackerWriter==null)
            throw new NullPointerException(
                "attackerWriter"
            );

        if(animation>=0){
            attackerWriter.varShort(
                81,
                CombatSync.player81AnimationAndInteraction(
                    animation,
                    targetValue
                )
            );
        }else{
            attackerWriter.varShort(
                81,
                CombatSync.player81InteractionOnly(
                    targetValue
                )
            );
        }

        boolean hpPublished=false;
        ServerPacketWriter targetWriter=
            Player81WorldSync.writerFor(
                target
            );

        if(targetWriter!=null){
            PlayerState state=
                target.playerState();

            targetWriter.fixed(
                134,
                BootstrapPackets.skill134(
                    PlayerState.HITPOINTS,
                    state.xp(
                        PlayerState.HITPOINTS
                    ),
                    state.currentLevel(
                        PlayerState.HITPOINTS
                    )
                )
            );
            hpPublished=true;
        }

        return new Result(
            true,
            hpPublished,
            HIT_SPLAT_AUTHORITY
        );
    }
}
