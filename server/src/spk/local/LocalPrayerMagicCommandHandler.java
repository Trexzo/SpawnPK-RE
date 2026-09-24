package spk.local;

import java.io.IOException;

/**
 * Runtime effect adapter for prayer presentation fixtures.
 *
 * Raw command parsing belongs to content. Exact packet publication remains
 * runtime-owned through PrayerState.
 */
final class LocalPrayerMagicCommandHandler {
    private final PrayerState prayers;

    LocalPrayerMagicCommandHandler(
        PrayerState prayers
    ){
        this.prayers=
            java.util.Objects.requireNonNull(
                prayers,
                "prayers"
            );
    }

    String prayerIcon(
        int icon,
        ServerPacketWriter serverPackets
    )throws IOException{
        return "V510_PRAYER_ICON result="+
            prayers.publishManualHeadIcon(
                icon,
                serverPackets
            );
    }
}
