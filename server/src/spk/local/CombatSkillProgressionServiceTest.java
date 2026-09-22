package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

public final class CombatSkillProgressionServiceTest {
    private static final String AUTHORITY =
        "CUSTOM_LOCALLAB_TEST_XP_CURVE";

    public static void main(String[] args) {
        WorldPlayer owner =
            new WorldPlayer();
        PlayerState player =
            owner.playerState();

        MutableCurve curve =
            new MutableCurve(
                AUTHORITY,
                0,
                100,
                250,
                500
            );

        CombatSkillProgressionService service =
            new CombatSkillProgressionService(
                owner,
                curve
            );

        semanticSkillCoverage();
        curveSnapshotIsStable(
            service,
            curve
        );
        attackAwardAndLevelUp(
            service,
            player
        );
        currentLevelPreserved(
            service,
            player
        );
        deadHitpointsNotResurrected(
            service,
            player
        );
        maxLevelAndThreshold(
            service,
            player
        );
        overflowIsAtomic(
            service,
            player
        );
        invalidAwardIsAtomic(
            service,
            player
        );
        invalidCurvesRejected();
        boundaryGuard();

        System.out.println(
            "COMBAT_SKILL_PROGRESSION_PASS " +
            "canonicalPlayerStateXp=true " +
            "semanticSkills=7 " +
            "callerLevelCurve=true " +
            "strictCurveValidation=true " +
            "xpAward=true " +
            "levelGainDerived=true " +
            "currentLevelPreserved=true " +
            "deadHitpointsNotResurrected=true " +
            "overflowAtomic=true " +
            "maxLevel=true " +
            "nextThreshold=true " +
            "curveAuthorityExplicit=true " +
            "hardcodedXpTable=false " +
            "persistenceOwned=false " +
            "packet134Owned=false " +
            "protocolIndependent=true"
        );
    }

    private static void semanticSkillCoverage() {
        require(
            CombatSkillProgressionService
                .Skill.values()
                .length == 7,
            "semantic skill count"
        );

        require(
            Arrays.equals(
                CombatSkillProgressionService
                    .Skill.values(),
                new CombatSkillProgressionService
                    .Skill[]{
                    CombatSkillProgressionService
                        .Skill.ATTACK,
                    CombatSkillProgressionService
                        .Skill.DEFENCE,
                    CombatSkillProgressionService
                        .Skill.STRENGTH,
                    CombatSkillProgressionService
                        .Skill.HITPOINTS,
                    CombatSkillProgressionService
                        .Skill.RANGED,
                    CombatSkillProgressionService
                        .Skill.PRAYER,
                    CombatSkillProgressionService
                        .Skill.MAGIC
                }
            ),
            "semantic skill order"
        );
    }

    private static void curveSnapshotIsStable(
        CombatSkillProgressionService service,
        MutableCurve curve
    ) {
        require(
            service.maxLevel() == 4,
            "curve max level"
        );

        require(
            Arrays.equals(
                service.curveSnapshot(),
                new int[]{
                    0,
                    100,
                    250,
                    500
                }
            ),
            "curve snapshot"
        );

        require(
            AUTHORITY.equals(
                service.curveAuthority()
            ),
            "curve authority"
        );

        curve.thresholds[1] = 999999;

        require(
            service.minimumXpForLevel(2) ==
                100,
            "mutable caller curve changed live semantics"
        );
    }

    private static void attackAwardAndLevelUp(
        CombatSkillProgressionService service,
        PlayerState player
    ) {
        player.setXp(
            PlayerState.ATTACK,
            90
        );
        player.setCurrentLevel(
            PlayerState.ATTACK,
            17
        );

        CombatSkillProgressionService.Snapshot before =
            service.snapshot(
                CombatSkillProgressionService
                    .Skill.ATTACK
            );

        require(
            before.xp == 90 &&
            before.baseLevel == 1 &&
            before.currentLevel == 17 &&
            !before.maxLevel &&
            before.nextLevelXp()
                .isPresent() &&
            before.nextLevelXp()
                .getAsInt() == 100,
            "attack before snapshot"
        );

        CombatSkillProgressionService.AwardResult result =
            service.award(
                CombatSkillProgressionService
                    .Skill.ATTACK,
                20
            );

        require(
            result.awardedXp == 20 &&
            result.levelsGained == 1 &&
            result.levelledUp(),
            "attack award result"
        );

        require(
            result.before.xp == 90 &&
            result.after.xp == 110 &&
            result.before.baseLevel == 1 &&
            result.after.baseLevel == 2,
            "attack base-level transition"
        );

        require(
            player.xp(
                PlayerState.ATTACK
            ) == 110,
            "canonical PlayerState XP not updated"
        );

        require(
            player.currentLevel(
                PlayerState.ATTACK
            ) == 17,
            "attack current level changed"
        );
    }

    private static void currentLevelPreserved(
        CombatSkillProgressionService service,
        PlayerState player
    ) {
        player.setXp(
            PlayerState.RANGED,
            240
        );
        player.setCurrentLevel(
            PlayerState.RANGED,
            114
        );

        CombatSkillProgressionService.AwardResult result =
            service.award(
                CombatSkillProgressionService
                    .Skill.RANGED,
                20
            );

        require(
            result.before.baseLevel == 2 &&
            result.after.baseLevel == 3 &&
            result.before.currentLevel == 114 &&
            result.after.currentLevel == 114 &&
            player.currentLevel(
                PlayerState.RANGED
            ) == 114,
            "boosted current level not preserved"
        );
    }

    private static void deadHitpointsNotResurrected(
        CombatSkillProgressionService service,
        PlayerState player
    ) {
        player.setXp(
            PlayerState.HITPOINTS,
            90
        );
        player.restoreHitpointsDefault();
        player.applyHitpointsDamage(
            99
        );

        require(
            player.currentLevel(
                PlayerState.HITPOINTS
            ) == 0 &&
            !player.alive(),
            "dead HP setup"
        );

        CombatSkillProgressionService.AwardResult result =
            service.award(
                CombatSkillProgressionService
                    .Skill.HITPOINTS,
                20
            );

        require(
            result.after.baseLevel == 2 &&
            result.after.currentLevel == 0 &&
            player.currentLevel(
                PlayerState.HITPOINTS
            ) == 0 &&
            !player.alive(),
            "XP award resurrected dead hitpoints"
        );
    }

    private static void maxLevelAndThreshold(
        CombatSkillProgressionService service,
        PlayerState player
    ) {
        player.setXp(
            PlayerState.PRAYER,
            490
        );
        player.setCurrentLevel(
            PlayerState.PRAYER,
            42
        );

        CombatSkillProgressionService.Snapshot before =
            service.snapshot(
                CombatSkillProgressionService
                    .Skill.PRAYER
            );

        require(
            before.baseLevel == 3 &&
            before.nextLevelXp()
                .isPresent() &&
            before.nextLevelXp()
                .getAsInt() == 500,
            "next level threshold"
        );

        CombatSkillProgressionService.AwardResult result =
            service.award(
                CombatSkillProgressionService
                    .Skill.PRAYER,
                20
            );

        require(
            result.after.baseLevel == 4 &&
            result.after.maxLevel &&
            !result.after.nextLevelXp()
                .isPresent(),
            "max-level snapshot"
        );
    }

    private static void overflowIsAtomic(
        CombatSkillProgressionService service,
        PlayerState player
    ) {
        player.setXp(
            PlayerState.MAGIC,
            Integer.MAX_VALUE - 5
        );
        player.setCurrentLevel(
            PlayerState.MAGIC,
            109
        );

        CombatSkillProgressionService.Snapshot before =
            service.snapshot(
                CombatSkillProgressionService
                    .Skill.MAGIC
            );

        expect(
            ArithmeticException.class,
            () -> service.award(
                CombatSkillProgressionService
                    .Skill.MAGIC,
                10
            ),
            "XP overflow"
        );

        CombatSkillProgressionService.Snapshot after =
            service.snapshot(
                CombatSkillProgressionService
                    .Skill.MAGIC
            );

        require(
            before.xp == after.xp &&
            before.baseLevel ==
                after.baseLevel &&
            before.currentLevel ==
                after.currentLevel,
            "overflow mutated skill state"
        );
    }

    private static void invalidAwardIsAtomic(
        CombatSkillProgressionService service,
        PlayerState player
    ) {
        player.setXp(
            PlayerState.DEFENCE,
            100
        );
        player.setCurrentLevel(
            PlayerState.DEFENCE,
            88
        );

        CombatSkillProgressionService.Snapshot before =
            service.snapshot(
                CombatSkillProgressionService
                    .Skill.DEFENCE
            );

        expect(
            IllegalArgumentException.class,
            () -> service.award(
                CombatSkillProgressionService
                    .Skill.DEFENCE,
                0
            ),
            "zero XP award"
        );

        CombatSkillProgressionService.Snapshot after =
            service.snapshot(
                CombatSkillProgressionService
                    .Skill.DEFENCE
            );

        require(
            before.xp == after.xp &&
            before.currentLevel ==
                after.currentLevel,
            "invalid award mutated state"
        );
    }

    private static void invalidCurvesRejected() {
        WorldPlayer owner =
            new WorldPlayer();

        expect(
            IllegalArgumentException.class,
            () -> new CombatSkillProgressionService(
                owner,
                new MutableCurve(
                    AUTHORITY
                )
            ),
            "zero max-level curve"
        );

        expect(
            IllegalArgumentException.class,
            () -> new CombatSkillProgressionService(
                owner,
                new MutableCurve(
                    AUTHORITY,
                    1,
                    100
                )
            ),
            "level one threshold"
        );

        expect(
            IllegalArgumentException.class,
            () -> new CombatSkillProgressionService(
                owner,
                new MutableCurve(
                    AUTHORITY,
                    0,
                    100,
                    100
                )
            ),
            "non-increasing curve"
        );

        expect(
            IllegalArgumentException.class,
            () -> new CombatSkillProgressionService(
                owner,
                new MutableCurve(
                    "EXACT_CURRENT_CLIENT",
                    0,
                    100
                )
            ),
            "client authority used as XP curve"
        );

        expect(
            IllegalArgumentException.class,
            () -> new CombatSkillProgressionService(
                owner,
                new MutableCurve(
                    "UNKNOWN_SERVER_AUTHORITY",
                    0,
                    100
                )
            ),
            "unknown authority used as XP curve"
        );
    }

    private static void boundaryGuard() {
        for (Class<?> type :
                new Class<?>[]{
                    CombatSkillProgressionService.class,
                    CombatSkillProgressionService
                        .Snapshot.class,
                    CombatSkillProgressionService
                        .AwardResult.class
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
                            "isaac",
                            "reward",
                            "drop",
                            "loot"
                        }) {
                    require(
                        !haystack.contains(
                            forbidden
                        ),
                        "protocol/effect identity leaked through " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }

        for (Method method :
                CombatSkillProgressionService.class
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
                        "drop",
                        "heal",
                        "restorecurrent"
                    }) {
                require(
                    !name.contains(
                        forbidden
                    ),
                    "owned behavior leaked through method " +
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

    private static final class MutableCurve
        implements CombatSkillProgressionService.LevelCurve {

        private final String authority;
        private final int[] thresholds;

        MutableCurve(
            String authority,
            int... thresholds
        ) {
            this.authority =
                authority;
            this.thresholds =
                thresholds;
        }

        @Override public int maxLevel() {
            return thresholds.length;
        }

        @Override public int minimumXpForLevel(
            int level
        ) {
            return thresholds[
                level - 1
            ];
        }

        @Override public String authority() {
            return authority;
        }
    }

    private CombatSkillProgressionServiceTest() {}
}
