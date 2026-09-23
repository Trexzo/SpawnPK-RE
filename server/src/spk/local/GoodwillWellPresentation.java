package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Well of Good Will presentation/input adapter.
 *
 * The adapter owns only the recovered root/action/text channels. Contribution
 * assets, goal units, reward economics, durations and reset policy remain
 * caller/server authority.
 */
final class GoodwillWellPresentation {
    static final int ROOT=51150;
    static final int CONTRIBUTION_ITEM_TEXT_WIDGET=51154;
    static final int PROGRESS_TEXT_WIDGET=51158;
    static final int SERVER_REWARD_TEXT_WIDGET=51162;
    static final int DONATE_WIDGET=51163;
    static final int INDIVIDUAL_REWARD_TEXT_WIDGET=51169;
    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    enum InputKind {
        DONATE
    }

    static final class Input {
        final InputKind kind;

        private Input(InputKind kind){
            this.kind=Objects.requireNonNull(kind,"kind");
        }
    }

    static Input resolveWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        return widgetId==DONATE_WIDGET
            ?new Input(InputKind.DONATE)
            :null;
    }

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(ROOT)
            );
    }

    static void publish(
        ServerPacketWriter packets,
        String contributionItemText,
        String progressText,
        String serverRewardText,
        String individualRewardText
    )throws IOException{
        Objects.requireNonNull(packets,"packets");

        ApplicationBus126Publisher.send(
            packets,
            CONTRIBUTION_ITEM_TEXT_WIDGET,
            requireText(
                contributionItemText,
                "contributionItemText"
            )
        );
        ApplicationBus126Publisher.send(
            packets,
            PROGRESS_TEXT_WIDGET,
            requireText(
                progressText,
                "progressText"
            )
        );
        ApplicationBus126Publisher.send(
            packets,
            SERVER_REWARD_TEXT_WIDGET,
            requireText(
                serverRewardText,
                "serverRewardText"
            )
        );
        ApplicationBus126Publisher.send(
            packets,
            INDIVIDUAL_REWARD_TEXT_WIDGET,
            requireText(
                individualRewardText,
                "individualRewardText"
            )
        );
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        if(clean.indexOf('\n')>=0||
           clean.indexOf('\r')>=0)
            throw new IllegalArgumentException(
                field+" contains line terminator"
            );

        return clean;
    }

    private GoodwillWellPresentation(){}
}
