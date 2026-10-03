package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 normal Duel presentation/input adapter.
 *
 * Owns only recovered roots, widget-action normalization, contextual
 * reconstruction of the >u16 load-last action, and exact challenge suffixes.
 * Staking, rule policy, escrow, arena behavior, restrictions, timeout,
 * disconnect handling and persistence remain server authority.
 */
final class NormalDuelPresentation {
    static final int SELECTOR_ROOT=25754;
    static final int RULES_ROOT=6575;

    static final int STANDARD_WIDGET=25759;
    static final int WHIP_WIDGET=25760;
    static final int WHIP_DDS_WIDGET=25761;
    static final int INVITE_WIDGET=25763;
    static final int CLOSE_WIDGET=65418;

    static final int LOAD_LAST_SPRITE_WIDGET=68439;
    static final int LOAD_LAST_CLIENT_WIDGET=68440;
    static final int LOAD_LAST_WIRE_WIDGET=
        LOAD_LAST_CLIENT_WIDGET&0xffff;

    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final String STANDARD_CHALLENGE_SUFFIX=
        ":duelreq:";
    static final String WHIP_CHALLENGE_SUFFIX=
        ":whipduelreq:";
    static final String WHIP_DDS_CHALLENGE_SUFFIX=
        ":whipddsreq:";

    enum DuelMode {
        STANDARD,
        WHIP_ONLY,
        WHIP_DDS_ONLY
    }

    enum InputKind {
        SELECT_MODE,
        INVITE,
        CLOSE,
        LOAD_LAST_RULES
    }

    static final class Input {
        final InputKind kind;
        final DuelMode mode;

        private Input(
            InputKind kind,
            DuelMode mode
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.mode=mode;
        }

        static Input mode(DuelMode mode){
            return new Input(
                InputKind.SELECT_MODE,
                Objects.requireNonNull(mode,"mode")
            );
        }

        static Input simple(InputKind kind){
            if(kind==InputKind.SELECT_MODE)
                throw new IllegalArgumentException(
                    "SELECT_MODE requires mode"
                );
            return new Input(kind,null);
        }
    }

    static Input resolveSelectorWidget(int widgetId){
        checkedU16(widgetId,"widgetId");

        switch(widgetId){
            case STANDARD_WIDGET:
                return Input.mode(DuelMode.STANDARD);
            case WHIP_WIDGET:
                return Input.mode(DuelMode.WHIP_ONLY);
            case WHIP_DDS_WIDGET:
                return Input.mode(DuelMode.WHIP_DDS_ONLY);
            case INVITE_WIDGET:
                return Input.simple(InputKind.INVITE);
            case CLOSE_WIDGET:
                return Input.simple(InputKind.CLOSE);
            default:
                return null;
        }
    }

    static Input resolveRulesWireWidget(
        int wireWidgetId,
        int activeRoot
    ){
        checkedU16(wireWidgetId,"wireWidgetId");
        checkedU16(activeRoot,"activeRoot");

        if(activeRoot!=RULES_ROOT)
            return null;

        return wireWidgetId==LOAD_LAST_WIRE_WIDGET
            ?Input.simple(InputKind.LOAD_LAST_RULES)
            :null;
    }

    static String challengeSuffix(DuelMode mode){
        switch(Objects.requireNonNull(mode,"mode")){
            case STANDARD:
                return STANDARD_CHALLENGE_SUFFIX;
            case WHIP_ONLY:
                return WHIP_CHALLENGE_SUFFIX;
            case WHIP_DDS_ONLY:
                return WHIP_DDS_CHALLENGE_SUFFIX;
            default:
                throw new AssertionError(mode);
        }
    }

    static void openSelector(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(
                    SELECTOR_ROOT
                )
            );
    }

    static void openRules(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(
                    RULES_ROOT
                )
            );
    }

    private static void checkedU16(
        int value,
        String field
    ){
        if(value<0||value>0xffff)
            throw new IllegalArgumentException(
                field+"="+value
            );
    }

    private NormalDuelPresentation(){}
}
