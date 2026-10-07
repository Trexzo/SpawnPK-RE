package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Exact-v308 Login Reward presentation boundary.
 *
 * This class owns only client-visible root/container projection. Reward
 * generation, cadence, streak, claim authorization/transport and settlement
 * remain unknown server authority.
 */
final class LoginRewardPresentation {
    static final int ROOT=50600;
    static final int ITEM_CONTAINER_WIDGET=50615;
    static final int SLOT_COUNT=20;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        ).fixed(
            97,
            BootstrapPackets.interface97(
                ROOT
            )
        );
    }

    static void openEmpty(
        ServerPacketWriter packets
    )throws IOException{
        ServerPacketWriter checked=
            Objects.requireNonNull(
                packets,
                "packets"
            );

        open(checked);

        int[] itemIds=
            new int[SLOT_COUNT];
        int[] quantities=
            new int[SLOT_COUNT];

        Arrays.fill(
            itemIds,
            -1
        );

        publishContainer(
            checked,
            itemIds,
            quantities
        );
    }

    static void publishContainer(
        ServerPacketWriter packets,
        int[] itemIds,
        int[] quantities
    )throws IOException{
        if(itemIds==null||
           quantities==null||
           itemIds.length!=SLOT_COUNT||
           quantities.length!=SLOT_COUNT)
            throw new IllegalArgumentException(
                "Login Reward container requires "+
                SLOT_COUNT+
                " slots"
            );

        Objects.requireNonNull(
            packets,
            "packets"
        ).varShort(
            53,
            BootstrapPackets.itemContainer53(
                ITEM_CONTAINER_WIDGET,
                itemIds,
                quantities
            )
        );
    }

    private LoginRewardPresentation(){}
}
