package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live LocalLab shell for exact-v308 Legendary Pet Fusion.
 *
 * The live projection is intentionally empty and never consults the
 * presentation's legacy static evidence defaults.
 */
final class LocalLegendaryPetFusionUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_LEGENDARY_PET_FUSION_FAIL_CLOSED";

    static final String AVAILABILITY_TEXT=
        "No LocalLab fusion offering configured";

    static final class Result {
        final String status;
        final LegendaryPetFusionPresentation.InputKind kind;
        final boolean succeeded;

        Result(
            String status,
            LegendaryPetFusionPresentation.InputKind kind,
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

        LegendaryPetFusionPresentation.open(
            packets
        );
        LegendaryPetFusionPresentation.publishEmpty(
            packets,
            AVAILABILITY_TEXT
        );
        open=true;
    }

    synchronized Result handle(
        LegendaryPetFusionPresentation.Input input
    ){
        LegendaryPetFusionPresentation.Input checked=
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

        if(checked.kind==
                LegendaryPetFusionPresentation
                    .InputKind.CLOSE){
            open=false;
            return new Result(
                "CLOSED",
                checked.kind,
                true
            );
        }

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
