package spk.local;

import java.util.Arrays;

/**
 * The seven player-appearance animation ids carried by packet 81, in the exact
 * order consumed by the pinned client:
 * stand, stand-turn, walk, turn-180, turn-90-CW, turn-90-CCW, run.
 *
 * A profile always contains seven concrete wire values. weaponSpecificMask marks
 * which fields have weapon/family-specific evidence rather than inheriting the
 * conservative human baseline. This prevents a partial recovery from being
 * mislabeled as a complete weapon animation set.
 */
final class EquipmentPoseProfile {
    static final int FIELD_COUNT = 7;
    static final int ALL_FIELDS_MASK = (1 << FIELD_COUNT) - 1;

    static final int STAND = 0;
    static final int STAND_TURN = 1;
    static final int WALK = 2;
    static final int TURN_180 = 3;
    static final int TURN_90_CW = 4;
    static final int TURN_90_CCW = 5;
    static final int RUN = 6;

    static final EquipmentPoseProfile DEFAULT_HUMAN = new EquipmentPoseProfile(
        "DEFAULT_HUMAN",
        808, 823, 819, 820, 821, 822, 824,
        0,
        "PINNED_CLIENT_BASELINE"
    );

    /**
     * Production-backed SpawnPK scythe-family movement/stance profile.
     *
     * v5.1 live-disproved raw OSRS id 8057. v5.1.1 then recovered the correct
     * current-client local stand id 15692 and the user visually certified it.
     * The later passive production appearance probe completed the family vector:
     * Blood Reaper Scythe 23202 and Scythe of Shadowrend 27485 were observed with
     * stand/stand-turn/walk/turn180/turn90CW/turn90CCW/run =
     * 15692/823/1146/820/821/822/1210.  v5.2 therefore no longer labels walk/run
     * as generic fallbacks.
     */
    static final EquipmentPoseProfile SCYTHE_SPAWNPK_FAMILY = new EquipmentPoseProfile(
        "SCYTHE_SPAWNPK_FAMILY",
        15692, 823, 1146, 820, 821, 822, 1210,
        ALL_FIELDS_MASK,
        "LIVE_VISUAL_LOCAL15692+PRODUCTION_PASSIVE_SCYTHE_FAMILY_POSE7_23202_27485"
    );



    /** Production-observed whip/tentacle family: 4151, 24093, 25000. */
    static final EquipmentPoseProfile WHIP_FAMILY = new EquipmentPoseProfile(
        "WHIP_FAMILY", 808, 823, 1660, 820, 821, 822, 1661,
        ALL_FIELDS_MASK,
        "V902_PRODUCTION_POSE7_CONSENSUS_4151_24093_25000"
    );

    /** Production-observed staff/wand/trident/sceptre family. */
    static final EquipmentPoseProfile STAFF_MAGIC_FAMILY = new EquipmentPoseProfile(
        "STAFF_MAGIC_FAMILY", 809, 823, 1146, 820, 821, 822, 1210,
        ALL_FIELDS_MASK,
        "V902_PRODUCTION_POSE7_CONSENSUS_STAFF_WAND_TRIDENT"
    );

    /** Production-observed ordinary maul family: Granite/Barrelchest/Primal. */
    static final EquipmentPoseProfile MAUL_HEAVY_FAMILY = new EquipmentPoseProfile(
        "MAUL_HEAVY_FAMILY", 1662, 823, 1663, 820, 821, 822, 1664,
        ALL_FIELDS_MASK,
        "V902_PRODUCTION_POSE7_CONSENSUS_4153_7808_12848_16425"
    );

    /** Production-observed 2H/godsword family: AGS, Primal 2H, Swift blade (t). */
    static final EquipmentPoseProfile TWO_HANDED_SWORD_FAMILY = new EquipmentPoseProfile(
        "TWO_HANDED_SWORD_FAMILY", 7053, 823, 7052, 7044, 7044, 7044, 7043,
        ALL_FIELDS_MASK,
        "V902_PRODUCTION_POSE7_CONSENSUS_11694_16909_22121"
    );

    final String name;
    final int stand;
    final int standTurn;
    final int walk;
    final int turn180;
    final int turn90CW;
    final int turn90CCW;
    final int run;
    final int weaponSpecificMask;
    final String evidence;

    EquipmentPoseProfile(String name,
                         int stand, int standTurn, int walk,
                         int turn180, int turn90CW, int turn90CCW, int run,
                         int weaponSpecificMask, String evidence) {
        this.name = name;
        this.stand = checked(stand);
        this.standTurn = checked(standTurn);
        this.walk = checked(walk);
        this.turn180 = checked(turn180);
        this.turn90CW = checked(turn90CW);
        this.turn90CCW = checked(turn90CCW);
        this.run = checked(run);
        this.weaponSpecificMask = weaponSpecificMask & ALL_FIELDS_MASK;
        this.evidence = evidence == null ? "" : evidence;
    }

    int[] toArray() {
        return new int[]{stand, standTurn, walk, turn180, turn90CW, turn90CCW, run};
    }

    boolean hasWeaponSpecificField(int field) {
        if (field < 0 || field >= FIELD_COUNT) return false;
        return (weaponSpecificMask & (1 << field)) != 0;
    }

    boolean weaponSpecificComplete() {
        return weaponSpecificMask == ALL_FIELDS_MASK;
    }

    private static int checked(int v) {
        if (v < -1 || v > 0xffff) throw new IllegalArgumentException("animation=" + v);
        return v;
    }

    @Override public String toString() {
        return name + Arrays.toString(toArray())
            + " weaponSpecificMask=0x" + Integer.toHexString(weaponSpecificMask)
            + " complete=" + weaponSpecificComplete();
    }
}
