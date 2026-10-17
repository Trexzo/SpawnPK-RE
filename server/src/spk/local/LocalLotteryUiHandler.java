package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

/**
 * Live LocalLab shell for the exact-v308 ordinary and Bloodcore Lottery roots.
 *
 * No LotteryService round is created because entry economics, round lifecycle,
 * draw cadence, RNG, prizes and refunds remain unknown server authority.
 */
final class LocalLotteryUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_LOTTERY_FAIL_CLOSED";

    static final String COUNTDOWN_TEXT=
        "No LocalLab round configured";
    static final String PARTICIPANT_TEXT=
        "Participants: 0";

    static final class Result {
        final String status;
        final LotteryService.Channel channel;
        final boolean succeeded;

        Result(
            String status,
            LotteryService.Channel channel,
            boolean succeeded
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.channel=
                Objects.requireNonNull(
                    channel,
                    "channel"
                );
            this.succeeded=succeeded;
        }
    }

    private LotteryService.Channel openChannel;

    synchronized void open(
        LotteryService.Channel channel,
        ServerPacketWriter packets
    )throws IOException{
        LotteryService.Channel checked=
            Objects.requireNonNull(
                channel,
                "channel"
            );
        Objects.requireNonNull(
            packets,
            "packets"
        );

        LotteryPresentation.open(
            packets,
            checked
        );
        LotteryPresentation.publishStatus(
            packets,
            checked,
            COUNTDOWN_TEXT,
            PARTICIPANT_TEXT,
            Collections.emptyList()
        );

        openChannel=checked;
    }

    synchronized Result handleEntry(
        LotteryService.Channel channel
    ){
        LotteryService.Channel checked=
            Objects.requireNonNull(
                channel,
                "channel"
            );

        if(openChannel==null)
            return new Result(
                "CLOSED_UI_NOOP",
                checked,
                false
            );

        if(openChannel!=checked)
            return new Result(
                "CHANNEL_MISMATCH_NOOP",
                checked,
                false
            );

        return new Result(
            "DISABLED_NO_GAMEPLAY_AUTHORITY",
            checked,
            false
        );
    }

    synchronized boolean close(){
        boolean wasOpen=
            openChannel!=null;
        openChannel=null;
        return wasOpen;
    }

    synchronized boolean isOpen(){
        return openChannel!=null;
    }

    synchronized LotteryService.Channel openChannel(){
        return openChannel;
    }
}
