package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live LocalLab composition for the exact Blood Fountain hub and its exact
 * Blood-shard-salvaging navigation target.
 *
 * Only navigation is implemented. Target mechanics remain fail-closed.
 */
final class LocalBloodFountainUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_BLOOD_FOUNTAIN_NAV";

    static final String SALVAGE_STATUS=
        "No LocalLab salvage recipe configured";

    enum Surface {
        HUB,
        SALVAGE
    }

    static final class HubResult {
        final String status;
        final BloodFountainHubService.Intent intent;
        final boolean navigated;

        HubResult(
            String status,
            BloodFountainHubService.Intent intent,
            boolean navigated
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
            this.navigated=navigated;
        }
    }

    static final class SalvageResult {
        final String status;
        final BloodShardSalvagePresentation.Intent intent;
        final boolean succeeded;

        SalvageResult(
            String status,
            BloodShardSalvagePresentation.Intent intent,
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

    private Surface surface;

    synchronized void openHub(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        BloodFountainHubPresentation.open(
            packets
        );
        surface=Surface.HUB;
    }

    synchronized HubResult handleHubIntent(
        BloodFountainHubService.Intent intent,
        ServerPacketWriter packets
    )throws IOException{
        BloodFountainHubService.Intent checked=
            Objects.requireNonNull(
                intent,
                "intent"
            );
        Objects.requireNonNull(
            packets,
            "packets"
        );

        if(surface==null)
            return new HubResult(
                "CLOSED_UI_NOOP",
                checked,
                false
            );

        if(surface!=Surface.HUB)
            return new HubResult(
                "WRONG_SURFACE_NOOP",
                checked,
                false
            );

        if(checked!=
                BloodFountainHubService
                    .Intent.BLOOD_SHARD_SALVAGING)
            return new HubResult(
                "DISABLED_NO_RUNTIME_COMPOSITION",
                checked,
                false
            );

        BloodShardSalvagePresentation.open(
            packets
        );
        BloodShardSalvagePresentation.publishStatus(
            packets,
            SALVAGE_STATUS
        );
        surface=Surface.SALVAGE;

        return new HubResult(
            "NAVIGATED_TO_SALVAGE",
            checked,
            true
        );
    }

    synchronized SalvageResult handleSalvage(
        BloodShardSalvagePresentation.Intent intent
    ){
        BloodShardSalvagePresentation.Intent checked=
            Objects.requireNonNull(
                intent,
                "intent"
            );

        if(surface==null)
            return new SalvageResult(
                "CLOSED_UI_NOOP",
                checked,
                false
            );

        if(surface!=Surface.SALVAGE)
            return new SalvageResult(
                "WRONG_SURFACE_NOOP",
                checked,
                false
            );

        return new SalvageResult(
            "DISABLED_NO_GAMEPLAY_AUTHORITY",
            checked,
            false
        );
    }

    synchronized boolean close(){
        boolean wasOpen=
            surface!=null;
        surface=null;
        return wasOpen;
    }

    synchronized boolean isOpen(){
        return surface!=null;
    }

    synchronized Surface surface(){
        return surface;
    }
}
