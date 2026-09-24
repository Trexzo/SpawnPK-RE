package spk.content.api;

import java.util.Set;

/**
 * Safe player-domain facade available to content.
 *
 * No socket, packet buffer, mutable registry or internal WorldPlayer reference is
 * exposed through this interface.
 */
public interface ContentPlayer {
    int skillLevel(ContentSkill skill);
    int skillExperience(ContentSkill skill);

    Set<ContentSkill> restoreCombatSkillsAndSpecial();
    void clearTimedStatuses();
    void setRunEnergy(int value);
    Set<ContentSkill> syncMaintainedPetEffects();
    boolean maintainedPetEffectActive();

    /**
     * Grant an item by semantic item identity and amount.
     *
     * Exact inventory/container publication stays runtime-owned. Alternate
     * content runtimes fail closed unless they explicitly support this
     * capability.
     */
    default String grantItem(
        int itemId,
        int amount
    ){
        throw new UnsupportedOperationException(
            "item grant unavailable"
        );
    }

    int runEnergy();
    int specialEnergy();
    int poison();
    int venom();
    int sicken();
}
