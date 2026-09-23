package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Blood Slayer selector/presentation adapter.
 *
 * Raw widgets stay at this boundary. Task catalogues, assignment, progress,
 * points/rewards and persistence remain owned by semantic/server policy.
 */
final class BloodSlayerPresentation {
    static final int ROOT=54100;
    static final int MONSTER_HUNTER_WIDGET=54109;
    static final int BOSS_HUNTER_WIDGET=54110;
    static final int BOUNTY_HUNTER_WIDGET=54111;
    static final int SLAUGHTER_WIDGET=54112;
    static final int GET_TASK_WIDGET=54113;
    static final int BLOOD_SLAYER_POINTS_WIDGET=54118;
    static final int SLAYER_POINTS_WIDGET=54119;
    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    enum InputKind {
        SELECT_MODE,
        REQUEST_TASK
    }

    static final class Input {
        final InputKind kind;
        final BloodSlayerModeService.Mode mode;

        private Input(
            InputKind kind,
            BloodSlayerModeService.Mode mode
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.mode=mode;
        }

        static Input select(
            BloodSlayerModeService.Mode mode
        ){
            return new Input(
                InputKind.SELECT_MODE,
                Objects.requireNonNull(
                    mode,
                    "mode"
                )
            );
        }

        static Input requestTask(){
            return new Input(
                InputKind.REQUEST_TASK,
                null
            );
        }
    }

    static Input resolveWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        switch(widgetId){
            case MONSTER_HUNTER_WIDGET:
                return Input.select(
                    BloodSlayerModeService
                        .Mode
                        .MONSTER_HUNTER_PVM
                );
            case BOSS_HUNTER_WIDGET:
                return Input.select(
                    BloodSlayerModeService
                        .Mode
                        .BOSS_HUNTER_PVM
                );
            case BOUNTY_HUNTER_WIDGET:
                return Input.select(
                    BloodSlayerModeService
                        .Mode
                        .BOUNTY_HUNTER_PK
                );
            case SLAUGHTER_WIDGET:
                return Input.select(
                    BloodSlayerModeService
                        .Mode
                        .SLAUGHTER_PK
                );
            case GET_TASK_WIDGET:
                return Input.requestTask();
            default:
                return null;
        }
    }

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(
                    ROOT
                )
            );
    }

    /**
     * Publish caller-authored point text through the exact compatible text
     * channels. Numeric point policy/formatting deliberately remains outside
     * this adapter.
     */
    static void publishPointTexts(
        ServerPacketWriter packets,
        String bloodSlayerText,
        String slayerText
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        Objects.requireNonNull(
            bloodSlayerText,
            "bloodSlayerText"
        );
        Objects.requireNonNull(
            slayerText,
            "slayerText"
        );

        ApplicationBus126Publisher.send(
            packets,
            BLOOD_SLAYER_POINTS_WIDGET,
            bloodSlayerText
        );
        ApplicationBus126Publisher.send(
            packets,
            SLAYER_POINTS_WIDGET,
            slayerText
        );
    }

    private BloodSlayerPresentation(){}
}
