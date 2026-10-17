package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-current Adventure book presentation boundary.
 *
 * The native v308 client proves the BEGIN_ADVENTURE_BOOK target-1 control and
 * subtype-22 reset operations. This adapter deliberately publishes no
 * objectives, rewards, chapter policy or interface97 root.
 */
final class AdventureBookPresentation {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT_V308_ADVENTURE_BOOK";
    static final int APPLICATION_SUBTYPE=22;

    static void openEmpty(
        ServerPacketWriter writer
    )throws IOException{
        ServerPacketWriter checked=
            Objects.requireNonNull(
                writer,
                "writer"
            );

        ApplicationControl126Service.adventureBook(
            checked
        );
        ApplicationUiService.chapterReset(
            checked
        );
        ApplicationUiService.chapterSecondaryReset(
            checked
        );
    }

    private AdventureBookPresentation(){}
}
