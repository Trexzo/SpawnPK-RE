package spk.local;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * Bloodcore Lottery application bridge over the already-exact
 * LotteryPresentation and the existing protocol-independent LotteryService.
 *
 * This class does not create/settle entries, select winners, settle prizes or
 * assign meanings to the static 22844 / 250 / 10000 client values.
 */
final class BloodcoreLotteryAdapter {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    /**
     * Exact static client values retained only as opaque presentation evidence.
     * Their production semantic roles are not established by the client.
     */
    static final class OpaqueStaticEvidence {
        static final int ITEM_ID=22844;
        static final int VALUE_A=250;
        static final int VALUE_B=10000;
        static final int ITEM_WIDGET_A=61158;
        static final int ITEM_WIDGET_B=61159;
        static final boolean SEMANTICS_OWNED=false;

        private OpaqueStaticEvidence(){}
    }

    static final class EnterIntent {
        final LotteryService.Channel channel;
        final LotteryService.RoundSnapshot activeRound;

        private EnterIntent(
            LotteryService.RoundSnapshot activeRound
        ){
            this.channel=
                LotteryService.Channel.BLOODCORE;
            this.activeRound=activeRound;
        }
    }

    static final class HistoryRow {
        final int index;
        final int widgetId;
        final LotteryService.WinnerRecord record;

        private HistoryRow(
            int index,
            LotteryService.WinnerRecord record
        ){
            this.index=index;
            this.widgetId=
                LotteryPresentation.historyWidget(
                    LotteryService.Channel.BLOODCORE,
                    index
                );
            this.record=
                Objects.requireNonNull(
                    record,
                    "record"
                );

            if(record.channel!=
                    LotteryService.Channel.BLOODCORE)
                throw new IllegalArgumentException(
                    "non-Bloodcore winner record "+
                    record.channel
                );
        }
    }

    static final class HistoryView {
        final List<HistoryRow> rows;

        private HistoryView(
            Collection<HistoryRow> rows
        ){
            this.rows=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        rows
                    )
                );
        }
    }

    /**
     * Converts the exact Bloodcore entry click into a read-only semantic intent.
     *
     * Payment/entry settlement remains external. In particular this method never
     * calls LotteryService.confirmEntrySettled(...).
     */
    static EnterIntent resolveEnterIntent(
        LotteryService service,
        int activeRoot,
        int widgetId
    ){
        Objects.requireNonNull(
            service,
            "service"
        );

        checkedU16(
            activeRoot,
            "activeRoot"
        );
        checkedU16(
            widgetId,
            "widgetId"
        );

        if(activeRoot!=
                LotteryPresentation.BLOODCORE_ROOT)
            return null;

        LotteryService.Channel channel=
            LotteryPresentation
                .resolveEntryWidget(
                    widgetId
                );

        if(channel!=
                LotteryService.Channel.BLOODCORE)
            return null;

        return new EnterIntent(
            service.activeRound(
                LotteryService.Channel.BLOODCORE
            )
        );
    }

    /**
     * Projects the service-owned Bloodcore winner history without inventing
     * text ordering or winner-row formatting beyond the service's stable order.
     */
    static HistoryView projectHistory(
        LotteryService service
    ){
        Objects.requireNonNull(
            service,
            "service"
        );

        List<LotteryService.WinnerRecord>
            history=
                service.history(
                    LotteryService.Channel.BLOODCORE
                );

        int capacity=
            LotteryService.Channel.BLOODCORE
                .historyCapacity();

        if(history.size()>capacity)
            throw new IllegalStateException(
                "Bloodcore Lottery history exceeds exact client capacity: "+
                history.size()
            );

        ArrayList<HistoryRow> rows=
            new ArrayList<>(
                history.size()
            );

        for(int i=0;i<history.size();i++)
            rows.add(
                new HistoryRow(
                    i,
                    history.get(i)
                )
            );

        return new HistoryView(
            rows
        );
    }

    /**
     * Uses the already-established exact LotteryPresentation text route.
     * Row formatting remains caller policy because exact client evidence does not
     * recover the production winner-string grammar.
     */
    static void publishStatus(
        ServerPacketWriter packets,
        String countdownText,
        String participantText,
        HistoryView history,
        Function<
            LotteryService.WinnerRecord,
            String
        > formatter
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );
        Objects.requireNonNull(
            history,
            "history"
        );
        Objects.requireNonNull(
            formatter,
            "formatter"
        );

        ArrayList<String> rows=
            new ArrayList<>(
                history.rows.size()
            );

        for(HistoryRow row:
                history.rows){
            String text=
                formatter.apply(
                    row.record
                );

            if(text==null)
                throw new NullPointerException(
                    "formatter result"
                );

            rows.add(text);
        }

        LotteryPresentation.publishStatus(
            packets,
            LotteryService.Channel.BLOODCORE,
            countdownText,
            participantText,
            rows
        );
    }

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        LotteryPresentation.open(
            Objects.requireNonNull(
                packets,
                "packets"
            ),
            LotteryService.Channel.BLOODCORE
        );
    }

    private static void checkedU16(
        int value,
        String field
    ){
        if(value<0||
           value>0xffff)
            throw new IllegalArgumentException(
                field+"="+value
            );
    }

    private BloodcoreLotteryAdapter(){}
}
