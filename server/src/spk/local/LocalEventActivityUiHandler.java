package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live LocalLab shell for the exact-v308 Event Activity Viewer.
 *
 * It publishes a truthful empty viewer only. No EventActivityService or quota
 * policy state is created.
 */
final class LocalEventActivityUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_EVENT_ACTIVITY_EMPTY_VIEW";

    void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        EventActivityPresentation.open(
            packets
        );
        EventActivityPresentation.clear(
            packets
        );
        EventActivityPresentation.render(
            packets
        );
    }
}
