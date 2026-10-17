package spk.local;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

/**
 * Live LocalLab shell for the exact-v308 Unclaimed Reward Coffer.
 *
 * The shell publishes only an empty exact container and rejects bulk movement.
 * It deliberately creates no semantic coffer/reward-delivery state.
 */
final class LocalUnclaimedRewardCofferUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_UNCLAIMED_COFFER_FAIL_CLOSED";

    static final class Result {
        final String status;
        final UnclaimedRewardCofferService.Destination destination;
        final boolean succeeded;

        Result(
            String status,
            UnclaimedRewardCofferService.Destination destination,
            boolean succeeded
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.destination=
                Objects.requireNonNull(
                    destination,
                    "destination"
                );
            this.succeeded=succeeded;
        }
    }

    private final WorldPlayer player;
    private boolean open;
    private UnclaimedRewardCofferPresentation.Projection projection;

    LocalUnclaimedRewardCofferUiHandler(
        WorldPlayer player
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
    }

    synchronized UnclaimedRewardCofferPresentation.Projection open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        String owner=
            player.username();

        if(owner==null||
           owner.trim().isEmpty())
            throw new IllegalStateException(
                "Unclaimed Reward Coffer requires registered WorldPlayer"
            );

        projection=
            new UnclaimedRewardCofferPresentation.Projection(
                owner,
                Collections.emptyList()
            );

        UnclaimedRewardCofferPresentation.open(
            packets
        );
        UnclaimedRewardCofferPresentation.publishContainer(
            packets,
            projection
        );

        open=true;
        return projection;
    }

    synchronized Result handleBulk(
        UnclaimedRewardCofferPresentation.BulkIntent intent
    ){
        UnclaimedRewardCofferPresentation.BulkIntent checked=
            Objects.requireNonNull(
                intent,
                "intent"
            );

        if(!open)
            return new Result(
                "CLOSED_UI_NOOP",
                checked.destination,
                false
            );

        return new Result(
            "DISABLED_NO_GAMEPLAY_AUTHORITY",
            checked.destination,
            false
        );
    }

    synchronized boolean close(){
        boolean wasOpen=open;
        open=false;
        projection=null;
        return wasOpen;
    }

    synchronized boolean isOpen(){
        return open;
    }

    synchronized UnclaimedRewardCofferPresentation.Projection projection(){
        return projection;
    }
}
