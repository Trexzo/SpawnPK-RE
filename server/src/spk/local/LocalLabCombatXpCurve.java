package spk.local;

/**
 * Explicit LocalLab combat XP curve used by the G1 playable slice.
 *
 * This is gameplay policy, not a claim about recovered original SpawnPK
 * progression. The generated level-99 threshold is fenced against the
 * repository's existing PlayerState.XP_99 constant.
 */
final class LocalLabCombatXpCurve
    implements CombatSkillProgressionService.LevelCurve {

    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G1_COMBAT_XP_CURVE_V1";

    private static final int MAX_LEVEL=99;
    private static final int[] THRESHOLDS=
        buildThresholds();

    static{
        if(THRESHOLDS[MAX_LEVEL]!=
                PlayerState.XP_99)
            throw new ExceptionInInitializerError(
                "LocalLab combat XP curve drifted level99="+
                THRESHOLDS[MAX_LEVEL]+
                " expected="+
                PlayerState.XP_99
            );
    }

    @Override public int maxLevel(){
        return MAX_LEVEL;
    }

    @Override public int minimumXpForLevel(
        int level
    ){
        if(level<1||level>MAX_LEVEL)
            throw new IllegalArgumentException(
                "level="+level+
                " expected=1.."+
                MAX_LEVEL
            );

        return THRESHOLDS[level];
    }

    @Override public String authority(){
        return AUTHORITY;
    }

    private static int[] buildThresholds(){
        int[] thresholds=
            new int[MAX_LEVEL+1];
        long points=0L;
        thresholds[1]=0;

        for(int level=1;
            level<MAX_LEVEL;
            level++){
            double growth=
                Math.pow(
                    2.0d,
                    level/7.0d
                );

            long contribution=
                (long)Math.floor(
                    level+
                    300.0d*growth
                );

            points=
                Math.addExact(
                    points,
                    contribution
                );

            long threshold=
                points/4L;

            if(threshold>Integer.MAX_VALUE)
                throw new IllegalStateException(
                    "XP threshold overflow level="+
                    (level+1)
                );

            thresholds[level+1]=
                (int)threshold;
        }

        return thresholds;
    }
}
