package spk.local;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable semantic Clan Wars configuration derived from exact-current client vocabulary. */
final class ClanWarDefinition {
    enum SpellRule {
        ALL_SPELLBOOKS,
        STANDARD_SPELLS,
        BINDING_ONLY,
        DISABLED
    }

    enum PrayerRule {
        ALL_ALLOWED,
        STANDARD_PRAYERS,
        DISABLED
    }

    enum WeaponRule {
        ALLOWED,
        NO_STAFF_OF_THE_DEAD,
        DISABLED
    }

    enum VictoryMode {
        KILL_EM_ALL,
        LAST_TEAM_STANDING,
        KILL_TARGET
    }

    enum Arena {
        WASTELAND,
        PLATEAU,
        SYLVAN_GLADE,
        FORSAKEN_QUARRY,
        TURRETS,
        CLAN_CUP_ARENA,
        GHASTLY_SWAMP,
        NORTHLEACH_QUELL,
        GRIDLOCK,
        ETHEREAL
    }

    enum AdvancedRule {
        IGNORE_FREEZING,
        PJ_TIMER,
        SINGLE_SPELLS,
        EDGE_PVP_MODE
    }

    private static final Set<Integer> EXACT_VISIBLE_KILL_TARGETS =
        Collections.unmodifiableSet(new java.util.LinkedHashSet<Integer>(
            java.util.Arrays.asList(25, 50, 100, 200, 500)
        ));

    private final SpellRule spellRule;
    private final PrayerRule prayerRule;
    private final WeaponRule weaponRule;
    private final VictoryMode victoryMode;
    private final Integer killTarget;
    private final Arena arena;
    private final Set<AdvancedRule> advancedRules;
    private final ClanEvidenceAuthority authority;

    ClanWarDefinition(
        SpellRule spellRule,
        PrayerRule prayerRule,
        WeaponRule weaponRule,
        VictoryMode victoryMode,
        Integer killTarget,
        Arena arena,
        Set<AdvancedRule> advancedRules,
        ClanEvidenceAuthority authority
    ) {
        this.spellRule = Objects.requireNonNull(spellRule, "spellRule");
        this.prayerRule = Objects.requireNonNull(prayerRule, "prayerRule");
        this.weaponRule = Objects.requireNonNull(weaponRule, "weaponRule");
        this.victoryMode = Objects.requireNonNull(victoryMode, "victoryMode");
        this.arena = Objects.requireNonNull(arena, "arena");
        this.authority = Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(advancedRules, "advancedRules");

        if (victoryMode == VictoryMode.KILL_TARGET) {
            if (killTarget == null || !EXACT_VISIBLE_KILL_TARGETS.contains(killTarget)) {
                throw new IllegalArgumentException("Kill target is not part of the exact-current visible configuration vocabulary");
            }
            this.killTarget = killTarget;
        } else {
            if (killTarget != null) {
                throw new IllegalArgumentException("Kill target must be absent for non-target victory modes");
            }
            this.killTarget = null;
        }

        if (advancedRules.isEmpty()) {
            this.advancedRules = Collections.emptySet();
        } else {
            this.advancedRules = Collections.unmodifiableSet(EnumSet.copyOf(advancedRules));
        }
    }

    SpellRule spellRule() { return spellRule; }
    PrayerRule prayerRule() { return prayerRule; }
    WeaponRule weaponRule() { return weaponRule; }
    VictoryMode victoryMode() { return victoryMode; }
    Integer killTarget() { return killTarget; }
    Arena arena() { return arena; }
    Set<AdvancedRule> advancedRules() { return advancedRules; }
    ClanEvidenceAuthority authority() { return authority; }

    static Set<Integer> exactVisibleKillTargets() {
        return EXACT_VISIBLE_KILL_TARGETS;
    }
}
