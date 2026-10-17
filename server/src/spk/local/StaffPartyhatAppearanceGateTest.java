package spk.local;

import java.util.Arrays;

/**
 * Server-side packet-81 publication regression for the eight exact-v308
 * staff-partyhat item gates recovered in Issue #271.
 *
 * This test deliberately does not assign original SpawnPK named-rank -> aC
 * values. That policy remains UNKNOWN_SERVER_AUTHORITY. It proves only the
 * server-owned publication boundary required for the native client branches:
 * worn hats occupy br[0]/HEAD, cosmetic Override occupies bs instead, and the
 * semantic appearance-role field remains independent.
 */
public final class StaffPartyhatAppearanceGateTest {
    private static final int[] STAFF_PARTYHATS = {
        22130, // Tevins partyhat
        22131, // Admin partyhat
        22132, // Owner partyhat
        22133, // Wealthy partyhat
        23480, // Support partyhat
        23481, // Mod partyhat
        23482, // Super mod partyhat
        23483  // Grand mod partyhat
    };

    private static final int SEMANTIC_ROLE_PROBE = 45;

    public static void main(String[] args) throws Exception {
        require(STAFF_PARTYHATS.length == 8, "staff partyhat census");

        for (int itemId : STAFF_PARTYHATS) {
            wornHead(itemId);
            cosmeticOverride(itemId);
        }

        System.out.println(
            "STAFF_PARTYHAT_APPEARANCE_GATE_PASS " +
            "items=8 " +
            "wornHead=true " +
            "overrideBs=true " +
            "semanticAcPreserved=true " +
            "namedSpawnPkMappingInvented=false"
        );
    }

    private static void wornHead(int itemId) throws Exception {
        int[] worn = new int[12];
        Arrays.fill(worn, -1);
        worn[EquipmentSlot.HEAD.appearanceIndex] = itemId;

        PlayerState player = new PlayerState();
        AppearanceProjection projection = readAppearanceProjection(
            BootstrapPackets.appearanceBlock(
                "partyhat:worn:" + itemId,
                worn,
                player,
                null,
                SEMANTIC_ROLE_PROBE
            )
        );

        require(
            projection.appearanceRole == SEMANTIC_ROLE_PROBE,
            "worn semantic aC item=" + itemId
        );
        require(
            projection.head == 512 + itemId,
            "worn HEAD/br[0] item=" + itemId +
            " actual=" + projection.head
        );
        require(
            projection.extraItem == -1,
            "worn item leaked into cosmetic bs item=" + itemId +
            " extra=" + projection.extraItem
        );
    }

    private static void cosmeticOverride(int itemId) throws Exception {
        int[] worn = new int[12];
        Arrays.fill(worn, -1);

        PlayerState player = new PlayerState();
        player.cosmetic().set(itemId);
        player.syncEquipmentPresentation(new EquipmentState());

        AppearanceProjection projection = readAppearanceProjection(
            BootstrapPackets.appearanceBlock(
                "partyhat:override:" + itemId,
                worn,
                player,
                null,
                SEMANTIC_ROLE_PROBE
            )
        );

        require(
            projection.appearanceRole == SEMANTIC_ROLE_PROBE,
            "override semantic aC item=" + itemId
        );
        require(
            projection.head != 512 + itemId,
            "Override falsely activated HEAD/br[0] item=" + itemId
        );
        require(
            projection.extraItem == itemId,
            "Override did not publish cosmetic bs item=" + itemId +
            " extra=" + projection.extraItem
        );
    }

    private static AppearanceProjection readAppearanceProjection(byte[] data) {
        int appearanceRole = (short) readU16(data, 5);
        int offset = 7;
        int head = 0;

        for (int slot = 0; slot < 12; slot++) {
            int high = data[offset++] & 255;
            int value = 0;
            if (high != 0) {
                value = (high << 8) | (data[offset++] & 255);
            }
            if (slot == EquipmentSlot.HEAD.appearanceIndex) {
                head = value;
            }
        }

        int extraFlag = data[offset++] & 255;
        int extraItem = extraFlag == 0 ? -1 : readU16(data, offset);

        return new AppearanceProjection(
            appearanceRole,
            head,
            extraItem
        );
    }

    private static int readU16(byte[] data, int offset) {
        return ((data[offset] & 255) << 8) |
            (data[offset + 1] & 255);
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static final class AppearanceProjection {
        final int appearanceRole;
        final int head;
        final int extraItem;

        AppearanceProjection(
            int appearanceRole,
            int head,
            int extraItem
        ) {
            this.appearanceRole = appearanceRole;
            this.head = head;
            this.extraItem = extraItem;
        }
    }

    private StaffPartyhatAppearanceGateTest() {}
}
