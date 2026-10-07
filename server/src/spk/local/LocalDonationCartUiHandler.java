package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live LocalLab shell for exact-v308 Donation Shopping Cart.
 *
 * The live projection contains no products and all commerce actions fail
 * closed. No semantic cart/service state is created.
 */
final class LocalDonationCartUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DONATION_CART_ZERO_FAIL_CLOSED";

    static final class Result {
        final String status;
        final DonationCartPresentation.Input input;
        final boolean succeeded;

        Result(
            String status,
            DonationCartPresentation.Input input,
            boolean succeeded
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.input=
                Objects.requireNonNull(
                    input,
                    "input"
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

        DonationCartPresentation.open(
            packets
        );
        DonationCartPresentation.publishZeroQuantities(
            packets
        );
        open=true;
    }

    synchronized Result handle(
        DonationCartPresentation.Input input
    ){
        DonationCartPresentation.Input checked=
            Objects.requireNonNull(
                input,
                "input"
            );

        if(!open)
            return new Result(
                "CLOSED_UI_NOOP",
                checked,
                false
            );

        return new Result(
            "DISABLED_NO_COMMERCE_AUTHORITY",
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
