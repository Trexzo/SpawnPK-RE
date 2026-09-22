package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class CombatLevelServiceTest {
    private static final String CURVE_AUTHORITY =
        "CUSTOM_LOCALLAB_COMBAT_LEVEL_CURVE";
    private static final String FORMULA_AUTHORITY =
        "CUSTOM_LOCALLAB_COMBAT_LEVEL_FORMULA";

    public static void main(String[] args) {
        PlayerState player =
            new PlayerState();

        CombatSkillProgressionService progression =
            new CombatSkillProgressionService(
                player,
                new TestCurve()
            );

        configureBaseLevels(
            player
        );

        AtomicReference<CombatLevelService.BaseLevels>
            captured =
                new AtomicReference<>();

        CombatLevelService service =
            new CombatLevelService(
                progression,
                levels -> {
                    captured.set(
                        levels
                    );

                    return levels.attack +
                        levels.defence +
                        levels.strength +
                        levels.hitpoints +
                        levels.ranged +
                        levels.prayer +
                        levels.magic;
                },
                FORMULA_AUTHORITY
            );

        int[] xpBefore =
            xpSnapshot(
                player
            );
        int[] currentBefore =
            currentSnapshot(
                player
            );

        CombatLevelService.Snapshot result =
            service.calculate();

        CombatLevelService.BaseLevels levels =
            captured.get();

        require(
            levels != null,
            "formula did not receive levels"
        );

        require(
            levels.attack == 2 &&
            levels.defence == 3 &&
            levels.strength == 4 &&
            levels.hitpoints == 2 &&
            levels.ranged == 3 &&
            levels.prayer == 1 &&
            levels.magic == 4,
            "base levels not derived from XP"
        );

        require(
            levels.level(
                CombatSkillProgressionService
                    .Skill.ATTACK
            ) == 2 &&
            levels.level(
                CombatSkillProgressionService
                    .Skill.MAGIC
            ) == 4,
            "semantic skill lookup"
        );

        require(
            result.baseLevels == levels &&
            result.combatLevel == 19 &&
            FORMULA_AUTHORITY.equals(
                result.formulaAuthority
            ) &&
            FORMULA_AUTHORITY.equals(
                service.formulaAuthority()
            ),
            "combat level result"
        );

        require(
            arraysEqual(
                xpBefore,
                xpSnapshot(
                    player
                )
            ),
            "combat-level calculation mutated XP"
        );

        require(
            arraysEqual(
                currentBefore,
                currentSnapshot(
                    player
                )
            ),
            "combat-level calculation mutated current levels"
        );

        require(
            player.currentLevel(
                PlayerState.ATTACK
            ) == 99 &&
            player.currentLevel(
                PlayerState.RANGED
            ) == 114 &&
            levels.attack == 2 &&
            levels.ranged == 3,
            "current boosts/defaults leaked into base-level calculation"
        );

        invalidFormulaResultRejected(
            progression
        );
        invalidAuthorityRejected(
            progression
        );
        boundaryGuard();

        System.out.println(
            "COMBAT_LEVEL_SERVICE_PASS " +
            "baseLevelsFromXp=true " +
            "currentBoostsIgnored=true " +
            "sevenSkills=true " +
            "callerFormula=true " +
            "formulaAuthorityExplicit=true " +
            "resultRange1to255=true " +
            "invalidFormulaResultRejected=true " +
            "playerStateUnmodified=true " +
            "hardcoded126Owned=false " +
            "packet81Owned=false " +
            "protocolIndependent=true"
        );
    }

    private static void configureBaseLevels(
        PlayerState player
    ) {
        player.setXp(
            PlayerState.ATTACK,
            120
        );
        player.setXp(
            PlayerState.DEFENCE,
            275
        );
        player.setXp(
            PlayerState.STRENGTH,
            600
        );
        player.setXp(
            PlayerState.HITPOINTS,
            110
        );
        player.setXp(
            PlayerState.RANGED,
            300
        );
        player.setXp(
            PlayerState.PRAYER,
            50
        );
        player.setXp(
            PlayerState.MAGIC,
            800
        );

        /*
         * Deliberately unrelated current values. The combat formula must read
         * derived base levels, not these boosted/default/drained values.
         */
        player.setCurrentLevel(
            PlayerState.ATTACK,
            99
        );
        player.setCurrentLevel(
            PlayerState.DEFENCE,
            1
        );
        player.setCurrentLevel(
            PlayerState.STRENGTH,
            120
        );
        player.setCurrentLevel(
            PlayerState.HITPOINTS,
            17
        );
        player.setCurrentLevel(
            PlayerState.RANGED,
            114
        );
        player.setCurrentLevel(
            PlayerState.PRAYER,
            7
        );
        player.setCurrentLevel(
            PlayerState.MAGIC,
            109
        );
    }

    private static void invalidFormulaResultRejected(
        CombatSkillProgressionService progression
    ) {
        CombatLevelService zero =
            new CombatLevelService(
                progression,
                levels -> 0,
                FORMULA_AUTHORITY
            );

        expect(
            IllegalStateException.class,
            zero::calculate,
            "zero combat level"
        );

        CombatLevelService tooHigh =
            new CombatLevelService(
                progression,
                levels -> 256,
                FORMULA_AUTHORITY
            );

        expect(
            IllegalStateException.class,
            tooHigh::calculate,
            "combat level >255"
        );
    }

    private static void invalidAuthorityRejected(
        CombatSkillProgressionService progression
    ) {
        expect(
            IllegalArgumentException.class,
            () -> new CombatLevelService(
                progression,
                levels -> 126,
                "EXACT_CURRENT_CLIENT"
            ),
            "client authority formula"
        );

        expect(
            IllegalArgumentException.class,
            () -> new CombatLevelService(
                progression,
                levels -> 126,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority formula"
        );
    }

    private static int[] xpSnapshot(
        PlayerState player
    ) {
        int[] out =
            new int[
                PlayerState.COMBAT_SKILL_COUNT
            ];

        for (int i = 0;
             i < out.length;
             i++) {
            out[i] =
                player.xp(i);
        }

        return out;
    }

    private static int[] currentSnapshot(
        PlayerState player
    ) {
        int[] out =
            new int[
                PlayerState.COMBAT_SKILL_COUNT
            ];

        for (int i = 0;
             i < out.length;
             i++) {
            out[i] =
                player.currentLevel(i);
        }

        return out;
    }

    private static boolean arraysEqual(
        int[] left,
        int[] right
    ) {
        if (left.length != right.length) {
            return false;
        }

        for (int i = 0;
             i < left.length;
             i++) {
            if (left[i] != right[i]) {
                return false;
            }
        }

        return true;
    }

    private static void boundaryGuard() {
        for (Class<?> type :
                new Class<?>[]{
                    CombatLevelService.class,
                    CombatLevelService
                        .BaseLevels.class,
                    CombatLevelService
                        .Snapshot.class
                }) {
            for (Field field :
                    type.getDeclaredFields()) {
                String haystack =
                    (
                        field.getName() +
                        " " +
                        field.getType().getName()
                    ).toLowerCase(
                        Locale.ROOT
                    );

                for (String forbidden :
                        new String[]{
                            "packet",
                            "opcode",
                            "widget",
                            "clientindex",
                            "wilderness",
                            "matchmaking",
                            "reward"
                        }) {
                    require(
                        !haystack.contains(
                            forbidden
                        ),
                        "unowned identity leaked through " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }

        for (Method method :
                CombatLevelService.class
                    .getDeclaredMethods()) {
            String name =
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            for (String forbidden :
                    new String[]{
                        "packet",
                        "publish",
                        "persist",
                        "reward",
                        "restrict",
                        "wilderness"
                    }) {
                require(
                    !name.contains(
                        forbidden
                    ),
                    "unowned behavior leaked through " +
                    method.getName()
                );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ) {
        try {
            action.run();
        } catch (
            Throwable failure
        ) {
            if (type.isInstance(
                    failure)) {
                return;
            }

            throw new AssertionError(
                label +
                " wrong failure " +
                failure,
                failure
            );
        }

        throw new AssertionError(
            label +
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ) {
        if (!condition) {
            throw new AssertionError(
                label
            );
        }
    }

    private static final class TestCurve
        implements CombatSkillProgressionService.LevelCurve {

        private static final int[] THRESHOLDS = {
            0,
            100,
            250,
            500
        };

        @Override public int maxLevel() {
            return THRESHOLDS.length;
        }

        @Override public int minimumXpForLevel(
            int level
        ) {
            return THRESHOLDS[
                level - 1
            ];
        }

        @Override public String authority() {
            return CURVE_AUTHORITY;
        }
    }

    private CombatLevelServiceTest() {}
}
