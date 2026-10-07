package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Open-only LocalLab composition for exact-v308 Bloodcore Token Synthesis.
 *
 * No raw control dispatch or synthesis service is installed because the exact
 * unique server-bound widget inside each visible control family remains
 * unresolved.
 */
final class LocalBloodcoreSynthesisUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_BLOODCORE_SYNTHESIS_OPEN_ONLY";

    static final String STATUS_A=
        "No LocalLab synthesis recipe configured";
    static final String STATUS_B=
        "Controls disabled until transport authority is closed";

    void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        BloodcoreSynthesisPresentation.open(
            packets
        );
        BloodcoreSynthesisPresentation.publishStatus(
            packets,
            STATUS_A,
            STATUS_B
        );
    }
}
