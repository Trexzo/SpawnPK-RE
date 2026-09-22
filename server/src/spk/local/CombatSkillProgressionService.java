package spk.local;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Protocol-independent XP -> base-level progression for PlayerState's existing
 * seven combat skills.
 *
 * XP remains owned by PlayerState. This service derives semantic base levels
 * from one immutable snapshot of a caller-supplied curve and deliberately does
 * not rewrite current/boosted/drained levels when XP changes.
 */
final class CombatSkillProgressionService {
    enum Skill {
        ATTACK(PlayerState.ATTACK),
        DEFENCE(PlayerState.DEFENCE),
        STRENGTH(PlayerState.STRENGTH),
        HITPOINTS(PlayerState.HITPOINTS),
        RANGED(PlayerState.RANGED),
        PRAYER(PlayerState.PRAYER),
        MAGIC(PlayerState.MAGIC);

        private final int playerStateIndex;

        Skill(int playerStateIndex) {
            this.playerStateIndex = playerStateIndex;
        }

        int playerStateIndex() {
            return playerStateIndex;
        }
    }

    interface LevelCurve {
        int maxLevel();
        int minimumXpForLevel(int level);
        String authority();
    }

    static final class Snapshot {
        final Skill skill;
        final int xp;
        final int baseLevel;
        final int currentLevel;
        final boolean maxLevel;
        final Integer nextLevelXp;
        final String curveAuthority;

        private Snapshot(
            Skill skill,
            int xp,
            int baseLevel,
            int currentLevel,
            boolean maxLevel,
            Integer nextLevelXp,
            String curveAuthority
        ) {
            this.skill = skill;
            this.xp = xp;
            this.baseLevel = baseLevel;
            this.currentLevel = currentLevel;
            this.maxLevel = maxLevel;
            this.nextLevelXp = nextLevelXp;
            this.curveAuthority = curveAuthority;
        }

        OptionalInt nextLevelXp() {
            return nextLevelXp == null
                ? OptionalInt.empty()
                : OptionalInt.of(nextLevelXp.intValue());
        }
    }

    static final class AwardResult {
        final int awardedXp;
        final int levelsGained;
        final Snapshot before;
        final Snapshot after;

        private AwardResult(
            int awardedXp,
            int levelsGained,
            Snapshot before,
            Snapshot after
        ) {
            this.awardedXp = awardedXp;
            this.levelsGained = levelsGained;
            this.before = before;
            this.after = after;
        }

        boolean levelledUp() {
            return levelsGained > 0;
        }
    }

    private final PlayerState player;
    private final int[] minimumXpByLevel;
    private final String curveAuthority;

    CombatSkillProgressionService(
        PlayerState player,
        LevelCurve curve
    ) {
        this.player =
            Objects.requireNonNull(
                player,
                "player"
            );

        Objects.requireNonNull(
            curve,
            "curve"
        );

        this.curveAuthority =
            requireGameplayAuthority(
                curve.authority()
            );

        int maxLevel =
            curve.maxLevel();

        if (maxLevel < 1 ||
            maxLevel > 255) {
            throw new IllegalArgumentException(
                "maxLevel=" +
                maxLevel +
                " expected=1..255"
            );
        }

        int[] snapshot =
            new int[maxLevel + 1];

        int previous = -1;

        for (int level = 1;
             level <= maxLevel;
             level++) {

            int threshold =
                curve.minimumXpForLevel(
                    level
                );

            if (threshold < 0) {
                throw new IllegalArgumentException(
                    "negative XP threshold level=" +
                    level +
                    " xp=" +
                    threshold
                );
            }

            if (level == 1 &&
                threshold != 0) {
                throw new IllegalArgumentException(
                    "level 1 XP must be 0 actual=" +
                    threshold
                );
            }

            if (level > 1 &&
                threshold <= previous) {
                throw new IllegalArgumentException(
                    "XP thresholds must increase strictly level=" +
                    level +
                    " previous=" +
                    previous +
                    " actual=" +
                    threshold
                );
            }

            snapshot[level] =
                threshold;
            previous =
                threshold;
        }

        this.minimumXpByLevel =
            snapshot;
    }

    synchronized Snapshot snapshot(
        Skill skill
    ) {
        return snapshotInternal(
            requireSkill(
                skill
            )
        );
    }

    synchronized AwardResult award(
        Skill skill,
        int amount
    ) {
        Skill checked =
            requireSkill(
                skill
            );

        if (amount <= 0) {
            throw new IllegalArgumentException(
                "XP award must be positive amount=" +
                amount
            );
        }

        Snapshot before =
            snapshotInternal(
                checked
            );

        final int afterXp;

        try {
            afterXp =
                Math.addExact(
                    before.xp,
                    amount
                );
        } catch (
            ArithmeticException overflow
        ) {
            throw new ArithmeticException(
                "XP overflow skill=" +
                checked +
                " before=" +
                before.xp +
                " amount=" +
                amount
            );
        }

        int afterBase =
            baseLevelForXp(
                afterXp
            );

        int levelsGained =
            afterBase -
            before.baseLevel;

        player.setXp(
            checked.playerStateIndex(),
            afterXp
        );

        Snapshot after =
            snapshotInternal(
                checked
            );

        if (after.xp != afterXp ||
            after.baseLevel != afterBase) {
            throw new IllegalStateException(
                "PlayerState XP write did not project expected progression"
            );
        }

        if (after.currentLevel !=
                before.currentLevel) {
            throw new IllegalStateException(
                "XP award unexpectedly changed current level skill=" +
                checked
            );
        }

        return new AwardResult(
            amount,
            levelsGained,
            before,
            after
        );
    }

    int maxLevel() {
        return minimumXpByLevel.length - 1;
    }

    String curveAuthority() {
        return curveAuthority;
    }

    int minimumXpForLevel(
        int level
    ) {
        if (level < 1 ||
            level > maxLevel()) {
            throw new IllegalArgumentException(
                "level=" +
                level +
                " expected=1.." +
                maxLevel()
            );
        }

        return minimumXpByLevel[level];
    }

    int[] curveSnapshot() {
        return Arrays.copyOfRange(
            minimumXpByLevel,
            1,
            minimumXpByLevel.length
        );
    }

    private Snapshot snapshotInternal(
        Skill skill
    ) {
        int index =
            skill.playerStateIndex();

        int xp =
            player.xp(
                index
            );

        if (xp < 0) {
            throw new IllegalStateException(
                "PlayerState exposed negative XP skill=" +
                skill +
                " xp=" +
                xp
            );
        }

        int base =
            baseLevelForXp(
                xp
            );

        boolean max =
            base == maxLevel();

        Integer next =
            max
                ? null
                : Integer.valueOf(
                    minimumXpByLevel[
                        base + 1
                    ]
                );

        return new Snapshot(
            skill,
            xp,
            base,
            player.currentLevel(
                index
            ),
            max,
            next,
            curveAuthority
        );
    }

    private int baseLevelForXp(
        int xp
    ) {
        int low = 1;
        int high = maxLevel();

        while (low <= high) {
            int middle =
                (low + high) >>> 1;

            if (minimumXpByLevel[middle] <=
                    xp) {
                low =
                    middle + 1;
            } else {
                high =
                    middle - 1;
            }
        }

        return Math.max(
            1,
            high
        );
    }

    private static Skill requireSkill(
        Skill skill
    ) {
        return Objects.requireNonNull(
            skill,
            "skill"
        );
    }

    private static String requireGameplayAuthority(
        String value
    ) {
        if (value == null) {
            throw new NullPointerException(
                "curveAuthority"
            );
        }

        String clean =
            value.trim();

        if (clean.isEmpty()) {
            throw new IllegalArgumentException(
                "curveAuthority blank"
            );
        }

        if ("EXACT_CURRENT_CLIENT".equals(
                clean) ||
            "UNKNOWN_SERVER_AUTHORITY".equals(
                clean)) {
            throw new IllegalArgumentException(
                "client/unknown authority cannot define XP curve actual=" +
                clean
            );
        }

        return clean;
    }
}
