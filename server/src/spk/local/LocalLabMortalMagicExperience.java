package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * R46: optional CUSTOM_LOCALLAB per-published-damage Magic XP for only
 * the R45 rune-free Air Surge against its R6 mortal practice NPC.
 *
 * Not an inferred SpawnPK Magic XP rate, current-level curve or combat rule.
 * The feature is disabled unless all THREE private fixture flags are set.
 */
final class LocalLabMortalMagicExperience {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_AIR_SURGE_MAGIC_XP_PER_PUBLISHED_DAMAGE_V1";
    private static final String XP_FLAG="spk.local.r46MortalMagicXp";

    static boolean enabled(){
        return LocalLabMortalNpcFixture.enabled() &&
            Boolean.getBoolean("spk.local.r45MortalMagic") &&
            Boolean.getBoolean(XP_FLAG);
    }

    /**
     * Check arithmetic before NPC damage or drop changes, so overflow cannot
     * leave a confirmed lethal death without its optional XP award.
     * The caller is the authoritative World execution context.
     */
    static void preflight(int damage,WorldPlayer owner){
        if(!enabled())return;
        Objects.requireNonNull(owner,"owner");
        if(damage<=0)
            throw new IllegalArgumentException("nonpositive custom magic damage");
        synchronized(owner.mutationLock()){
            Math.addExact(owner.playerState().xp(PlayerState.MAGIC),damage);
        }
    }

    /** Invoked exactly once, after canonical NPC hit/death publication. */
    static String awardPublishedDamage(
        int damage,
        NpcEntity npc,
        WorldPlayer owner,
        ServerPacketWriter writer
    )throws IOException{
        if(!enabled())return null;
        if(damage<=0||npc==null||!npc.localLabMortalFixture()||
           npc.definitionId!=LocalLabMortalNpcFixture.NPC_DEFINITION)
            return "R46_MAGIC_XP_SKIPPED reason=NONMORTAL_OR_NO_PUBLISHED_DAMAGE";
        Objects.requireNonNull(owner,"owner");
        Objects.requireNonNull(writer,"writer");

        final int before,after,current;
        synchronized(owner.mutationLock()){
            PlayerState state=owner.playerState();
            before=state.xp(PlayerState.MAGIC);
            after=Math.addExact(before,damage);
            current=state.currentLevel(PlayerState.MAGIC);
            state.setXp(PlayerState.MAGIC,after);
        }
        // Never write to network while holding the canonical mutation lock.
        writer.fixed(
            134,
            BootstrapPackets.skill134(PlayerState.MAGIC,after,current)
        );
        return "R46_MAGIC_XP_GRANTED"+
            " npcScene="+npc.sceneIndex+
            " skill=MAGIC damage="+damage+
            " before="+before+" after="+after+
            " currentLevel="+current+
            " authority="+AUTHORITY+
            " originalSpawnpkXpRate=false";
    }

    private LocalLabMortalMagicExperience(){}
}
