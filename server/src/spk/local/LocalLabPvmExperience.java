package spk.local;

import java.io.IOException;

/**
 * R8 custom LocalLab damage-XP projection.
 *
 * Only the explicit R6 + R7 + R8 test profile may award XP. This is not a
 * reconstructed SpawnPK XP rate, level curve, or combat style server formula.
 *
 * Awards a flat XP amount equal to one actually published, positive damage
 * value to the exact single skill named by the current client combat style.
 * Controlled/multi-skill styles remain disabled, rather than guessing an XP
 * split. Current levels are unchanged (XP-only experimental progression).
 */
final class LocalLabPvmExperience {
    static final String AUTHORITY="CUSTOM_LOCALLAB_XP_PER_PUBLISHED_DAMAGE_V1";
    private static final String XP_FLAG="spk.local.r8PvmXp";

    static boolean enabled(){
        return LocalLabMortalNpcFixture.lifecycleEnabled()
            &&Boolean.getBoolean(XP_FLAG);
    }

    static String awardPublishedDamage(
        int damage,
        NpcEntity target,
        CombatStyleRepository.Style style,
        WorldPlayer owner,
        ServerPacketWriter writer
    )throws IOException{
        if(!enabled()||damage<=0||target==null||
           !target.localLabMortalFixture())
            return null;

        if(style==null||
           style.trainedSkills==null||
           style.trainedSkills.length!=1)
            return "R8_PVM_XP_SKIPPED reason=UNRESOLVED_OR_CONTROLLED_STYLE";

        final int skill=skillIndex(style.trainedSkills[0]);
        if(skill<0)
            return "R8_PVM_XP_SKIPPED reason=UNKNOWN_TRAINED_SKILL";

        if(owner==null||writer==null)
            throw new NullPointerException("owner/writer");

        final PlayerState player=owner.playerState();
        final int before,after,current;
        synchronized(owner.mutationLock()){
            before=player.xp(skill);
            after=Math.addExact(before,damage);
            current=player.currentLevel(skill);
            // Exactly one XP write after all arithmetic checks. No HP/level
            // mutation, no implicit rewards, no original-server formula.
            player.setXp(skill,after);
            writer.fixed(
                134,
                BootstrapPackets.skill134(skill,after,current)
            );
        }

        return "R8_PVM_XP_GRANTED targetScene="+target.sceneIndex+
            " skill="+style.trainedSkills[0]+
            " damage="+damage+
            " before="+before+
            " after="+after+
            " currentLevel="+current+
            " authority="+AUTHORITY+
            " originalSpawnpkXpRate=false";
    }

    private static int skillIndex(String name){
        if("ATTACK".equals(name))return PlayerState.ATTACK;
        if("DEFENCE".equals(name))return PlayerState.DEFENCE;
        if("STRENGTH".equals(name))return PlayerState.STRENGTH;
        if("HITPOINTS".equals(name))return PlayerState.HITPOINTS;
        if("RANGED".equals(name))return PlayerState.RANGED;
        if("PRAYER".equals(name))return PlayerState.PRAYER;
        if("MAGIC".equals(name))return PlayerState.MAGIC;
        return -1;
    }

    private LocalLabPvmExperience(){}
}
