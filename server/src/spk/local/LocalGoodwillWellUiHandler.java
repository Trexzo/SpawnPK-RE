package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live LocalLab shell for the exact-v308 Well of Good Will surface.
 *
 * No GoodwillWellService campaign is created here because the exact client does
 * not prove contribution assets, goal units/values, rewards, duration or reset
 * policy. The native presentation is exposed truthfully and Donate fails closed.
 */
final class LocalGoodwillWellUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_GOODWILL_FAIL_CLOSED";

    static final String CONTRIBUTION_TEXT=
        "Contribution asset: not configured";
    static final String PROGRESS_TEXT=
        "No LocalLab campaign configured";
    static final String SERVER_REWARD_TEXT=
        "Server reward: not configured";
    static final String INDIVIDUAL_REWARD_TEXT=
        "Individual reward: not configured";

    static final class Result {
        final String status;
        final GoodwillWellPresentation.InputKind kind;
        final boolean succeeded;

        Result(
            String status,
            GoodwillWellPresentation.InputKind kind,
            boolean succeeded
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.kind=
                Objects.requireNonNull(
                    kind,
                    "kind"
                );
            this.succeeded=succeeded;
        }
    }

    private boolean open;

    synchronized void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        GoodwillWellPresentation.open(
            packets
        );
        GoodwillWellPresentation.publish(
            packets,
            CONTRIBUTION_TEXT,
            PROGRESS_TEXT,
            SERVER_REWARD_TEXT,
            INDIVIDUAL_REWARD_TEXT
        );

        open=true;
    }

    synchronized Result handle(
        GoodwillWellPresentation.Input input
    ){
        GoodwillWellPresentation.Input checked=
            Objects.requireNonNull(
                input,
                "input"
            );

        if(!open)
            return new Result(
                "CLOSED_UI_NOOP",
                checked.kind,
                false
            );

        return new Result(
            "DISABLED_NO_GAMEPLAY_AUTHORITY",
            checked.kind,
            false
        );
    }

    synchronized boolean close(){
        boolean wasOpen=open;
        open=false;
        return wasOpen;
    }

    synchronized boolean isOpen(){
        return open;
    }
}
