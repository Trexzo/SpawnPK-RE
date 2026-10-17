package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live LocalLab shell for exact-v308 Donor Panel.
 *
 * Navigation identities are exact client authority. Payment, entitlements,
 * shop policy, promotion policy and teleport destinations remain unowned.
 */
final class LocalDonorPanelUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DONOR_PANEL_FAIL_CLOSED";

    static final String PROMOTION_PROGRESS_TEXT=
        "No LocalLab donation campaign configured";
    static final String NEXT_PROMOTION_TEXT=
        "Actions disabled until donor policy is configured";

    static final class Result {
        final String status;
        final DonorPanelPresentation.Intent intent;
        final boolean succeeded;

        Result(
            String status,
            DonorPanelPresentation.Intent intent,
            boolean succeeded
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.intent=
                Objects.requireNonNull(
                    intent,
                    "intent"
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

        DonorPanelPresentation.open(
            packets
        );
        DonorPanelPresentation.publishPromotionText(
            packets,
            PROMOTION_PROGRESS_TEXT,
            NEXT_PROMOTION_TEXT
        );
        open=true;
    }

    synchronized Result handle(
        DonorPanelPresentation.Intent intent
    ){
        DonorPanelPresentation.Intent checked=
            Objects.requireNonNull(
                intent,
                "intent"
            );

        if(!open)
            return new Result(
                "CLOSED_UI_NOOP",
                checked,
                false
            );

        return new Result(
            "DISABLED_NO_GAMEPLAY_AUTHORITY",
            checked,
            false
        );
    }

    synchronized Result handleLive(
        DonorPanelPresentation.Intent intent
    ){
        DonorPanelPresentation.Intent checked=
            Objects.requireNonNull(
                intent,
                "intent"
            );

        if(!open)
            return new Result(
                "CLOSED_UI_NOOP",
                checked,
                false
            );

        if(checked==
                DonorPanelPresentation
                    .Intent.DONATE_FOR_REWARDS)
            return new Result(
                "NAVIGATE_DONATION_CART",
                checked,
                true
            );

        return new Result(
            "DISABLED_NO_GAMEPLAY_AUTHORITY",
            checked,
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
