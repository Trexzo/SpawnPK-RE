package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Blood Pool Store presentation boundary.
 *
 * The client proves only a reset + repeated slot-record append lifecycle:
 * target-1 RESET_BLOOD_POOL_SHOP_SLOTS followed by target-37 records.
 *
 * Target-37 field meanings are not recovered yet, so append records remain
 * explicitly opaque presentation payloads. This class owns no shop economics,
 * stock, eligibility, persistence, or purchase policy.
 */
final class BloodPoolShopPresentation {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";
    static final String SLOT_FIELD_AUTHORITY=
        "UNKNOWN_CLIENT_PRESENTATION_DETAIL";

    private final ServerPacketWriter packets;

    BloodPoolShopPresentation(
        ServerPacketWriter packets
    ){
        this.packets=
            Objects.requireNonNull(
                packets,
                "packets"
            );
    }

    void resetSlots()throws IOException{
        ApplicationControl126Service
            .bloodPoolResetSlots(
                packets
            );
    }

    void appendOpaqueSlotRecord(
        String exactRecord
    )throws IOException{
        ApplicationControl126Service
            .bloodPoolAppendOpaqueSlot(
                packets,
                exactRecord
            );
    }
}
