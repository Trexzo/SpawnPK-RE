package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 ordinary + Bloodcore Lottery presentation/input adapter.
 *
 * The adapter owns only the two native roots, entry actions and exact
 * countdown/participant/history text surfaces. Entry economics, item meanings,
 * draw scheduling, RNG, prizes and refunds remain caller/server authority.
 */
final class LotteryPresentation {
    static final int ORDINARY_ROOT=52000;
    static final int ORDINARY_COUNTDOWN_WIDGET=52005;
    static final int ORDINARY_PARTICIPANTS_WIDGET=52006;
    static final int ORDINARY_ENTRY_WIDGET=52010;
    static final int ORDINARY_HISTORY_FIRST=52014;
    static final int ORDINARY_HISTORY_LAST=52019;

    static final int BLOODCORE_ROOT=61150;
    static final int BLOODCORE_COUNTDOWN_WIDGET=61156;
    static final int BLOODCORE_PARTICIPANTS_WIDGET=61157;
    static final int BLOODCORE_ENTRY_WIDGET=61196;
    static final int BLOODCORE_HISTORY_FIRST=61161;
    static final int BLOODCORE_HISTORY_LAST=61195;

    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    static LotteryService.Channel resolveEntryWidget(
        int widgetId
    ){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        if(widgetId==ORDINARY_ENTRY_WIDGET)
            return LotteryService.Channel.ORDINARY;

        if(widgetId==BLOODCORE_ENTRY_WIDGET)
            return LotteryService.Channel.BLOODCORE;

        return null;
    }

    static int root(
        LotteryService.Channel channel
    ){
        switch(requireChannel(channel)){
            case ORDINARY:
                return ORDINARY_ROOT;
            case BLOODCORE:
                return BLOODCORE_ROOT;
            default:
                throw new AssertionError(channel);
        }
    }

    static int historyWidget(
        LotteryService.Channel channel,
        int index
    ){
        LotteryService.Channel checked=
            requireChannel(channel);

        if(index<0||
           index>=checked.historyCapacity())
            throw new IllegalArgumentException(
                "history index="+index+
                " channel="+checked
            );

        return checked==LotteryService.Channel.ORDINARY
            ?ORDINARY_HISTORY_FIRST+index
            :BLOODCORE_HISTORY_FIRST+index;
    }

    static void open(
        ServerPacketWriter packets,
        LotteryService.Channel channel
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(
                    root(channel)
                )
            );
    }

    static void publishStatus(
        ServerPacketWriter packets,
        LotteryService.Channel channel,
        String countdownText,
        String participantText,
        List<String> historyRows
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        LotteryService.Channel checked=
            requireChannel(channel);
        Objects.requireNonNull(
            historyRows,
            "historyRows"
        );

        if(historyRows.size()>
                checked.historyCapacity())
            throw new IllegalArgumentException(
                "Lottery history rows="+
                historyRows.size()+
                " capacity="+
                checked.historyCapacity()+
                " channel="+checked
            );

        ApplicationBus126Publisher.send(
            packets,
            checked==LotteryService.Channel.ORDINARY
                ?ORDINARY_COUNTDOWN_WIDGET
                :BLOODCORE_COUNTDOWN_WIDGET,
            wireText(
                countdownText,
                "countdownText"
            )
        );

        ApplicationBus126Publisher.send(
            packets,
            checked==LotteryService.Channel.ORDINARY
                ?ORDINARY_PARTICIPANTS_WIDGET
                :BLOODCORE_PARTICIPANTS_WIDGET,
            wireText(
                participantText,
                "participantText"
            )
        );

        for(int i=0;
            i<checked.historyCapacity();
            i++){
            String text=
                i<historyRows.size()
                    ?wireText(
                        historyRows.get(i),
                        "historyRows["+i+"]"
                    )
                    :"";

            ApplicationBus126Publisher.send(
                packets,
                historyWidget(checked,i),
                text
            );
        }
    }

    private static LotteryService.Channel
        requireChannel(
            LotteryService.Channel channel
        ){
        return Objects.requireNonNull(
            channel,
            "channel"
        );
    }

    private static String wireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);

            if(ch=='\n'||ch=='\r')
                throw new IllegalArgumentException(
                    field+" contains line terminator"
                );

            if(ch>0xff)
                throw new IllegalArgumentException(
                    field+
                    " not ISO-8859-1 at index="+
                    i
                );
        }

        return value;
    }

    private LotteryPresentation(){}
}
