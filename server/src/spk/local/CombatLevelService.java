package spk.local;

import java.util.Objects;

/**
 * Protocol-independent semantic combat-level calculation.
 *
 * Reads derived base levels from CombatSkillProgressionService and delegates
 * the actual combat formula to caller-owned gameplay policy.
 */
final class CombatLevelService {
    static final class BaseLevels {
        final int attack;
        final int defence;
        final int strength;
        final int hitpoints;
        final int ranged;
        final int prayer;
        final int magic;

        private BaseLevels(
            int attack,
            int defence,
            int strength,
            int hitpoints,
            int ranged,
            int prayer,
            int magic
        ) {
            this.attack = attack;
            this.defence = defence;
            this.strength = strength;
            this.hitpoints = hitpoints;
            this.ranged = ranged;
            this.prayer = prayer;
            this.magic = magic;
        }

        int level(
            CombatSkillProgressionService.Skill skill
        ) {
            Objects.requireNonNull(
                skill,
                "skill"
            );

            switch (skill) {
                case ATTACK:
                    return attack;
                case DEFENCE:
                    return defence;
                case STRENGTH:
                    return strength;
                case HITPOINTS:
                    return hitpoints;
                case RANGED:
                    return ranged;
                case PRAYER:
                    return prayer;
                case MAGIC:
                    return magic;
                default:
                    throw new IllegalArgumentException(
                        "unsupported skill " +
                        skill
                    );
            }
        }
    }

    interface Formula {
        int calculate(
            BaseLevels levels
        );
    }

    static final class Snapshot {
        final BaseLevels baseLevels;
        final int combatLevel;
        final String formulaAuthority;

        private Snapshot(
            BaseLevels baseLevels,
            int combatLevel,
            String formulaAuthority
        ) {
            this.baseLevels =
                baseLevels;
            this.combatLevel =
                combatLevel;
            this.formulaAuthority =
                formulaAuthority;
        }
    }

    private final CombatSkillProgressionService
        progression;
    private final Formula formula;
    private final String formulaAuthority;

    CombatLevelService(
        CombatSkillProgressionService progression,
        Formula formula,
        String formulaAuthority
    ) {
        this.progression =
            Objects.requireNonNull(
                progression,
                "progression"
            );
        this.formula =
            Objects.requireNonNull(
                formula,
                "formula"
            );
        this.formulaAuthority =
            requireGameplayAuthority(
                formulaAuthority
            );
    }

    Snapshot calculate() {
        final BaseLevels levels;

        /*
         * CombatSkillProgressionService's state reads are synchronized on the
         * progression object. Hold that same monitor across all seven reads so
         * one calculation sees one coherent XP/base-level moment.
         */
        synchronized (progression) {
            levels =
                new BaseLevels(
                    baseLevel(
                        CombatSkillProgressionService
                            .Skill.ATTACK
                    ),
                    baseLevel(
                        CombatSkillProgressionService
                            .Skill.DEFENCE
                    ),
                    baseLevel(
                        CombatSkillProgressionService
                            .Skill.STRENGTH
                    ),
                    baseLevel(
                        CombatSkillProgressionService
                            .Skill.HITPOINTS
                    ),
                    baseLevel(
                        CombatSkillProgressionService
                            .Skill.RANGED
                    ),
                    baseLevel(
                        CombatSkillProgressionService
                            .Skill.PRAYER
                    ),
                    baseLevel(
                        CombatSkillProgressionService
                            .Skill.MAGIC
                    )
                );
        }

        int calculated =
            formula.calculate(
                levels
            );

        if (calculated < 1 ||
            calculated > 255) {
            throw new IllegalStateException(
                "combat level formula returned " +
                calculated +
                " expected=1..255"
            );
        }

        return new Snapshot(
            levels,
            calculated,
            formulaAuthority
        );
    }

    String formulaAuthority() {
        return formulaAuthority;
    }

    private int baseLevel(
        CombatSkillProgressionService.Skill skill
    ) {
        return progression
            .snapshot(skill)
            .baseLevel;
    }

    private static String requireGameplayAuthority(
        String value
    ) {
        if (value == null) {
            throw new NullPointerException(
                "formulaAuthority"
            );
        }

        String clean =
            value.trim();

        if (clean.isEmpty()) {
            throw new IllegalArgumentException(
                "formulaAuthority blank"
            );
        }

        if ("EXACT_CURRENT_CLIENT".equals(
                clean) ||
            "UNKNOWN_SERVER_AUTHORITY".equals(
                clean)) {
            throw new IllegalArgumentException(
                "client/unknown authority cannot define combat formula actual=" +
                clean
            );
        }

        return clean;
    }
}
