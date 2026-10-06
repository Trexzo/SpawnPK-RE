package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Session-boundary settlement for native Blood Slayer UI results.
 * Semantic mutation is already complete; durable save is requested before
 * advisory S2C253 feedback whenever the result carries a save reason.
 */
final class LocalBloodSlayerUiSettlement {
    @FunctionalInterface
    interface SaveAction {
        void save(String reason);
    }

    static LocalBloodSlayerUiHandler.Result settle(
        LocalBloodSlayerUiHandler.Result result,
        SaveAction save,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        LocalBloodSlayerUiHandler.Result checked=
            Objects.requireNonNull(
                result,
                "result"
            );
        SaveAction saveAction=
            Objects.requireNonNull(
                save,
                "save"
            );
        ServerPacketWriter packets=
            Objects.requireNonNull(
                writer,
                "writer"
            );

        if(checked.saveReason!=null)
            saveAction.save(
                checked.saveReason
            );

        new SocialChatPresentationPublisher(
            packets
        ).serverMessage(
            checked.clientMessage
        );

        System.out.println(
            (tag==null?"":tag)+
            "G4_BLOOD_SLAYER_UI_SETTLEMENT"+
            " persistenceBeforeFeedback="+
            (checked.saveReason!=null)+
            " status="+
            checked.status
        );

        return checked;
    }

    private LocalBloodSlayerUiSettlement(){}
}
