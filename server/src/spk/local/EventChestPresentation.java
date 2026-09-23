package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Event Chest presentation/input adapter.
 *
 * Widget/container identity stays here. Semantic EventChestService entry keys
 * are intentionally not reinterpreted as cache item ids.
 */
final class EventChestPresentation {
    static final int ROOT=60600;
    static final int MAIN_GRID_WIDGET=60602;
    static final int EXCHANGE_WIDGET=60604;
    static final int SMALL_GRID_0_WIDGET=60611;
    static final int SMALL_GRID_1_WIDGET=60612;
    static final int SMALL_GRID_2_WIDGET=60613;
    static final int EVENT_HEADING_WIDGET=60616;
    static final int PROGRESS_TEXT_WIDGET=60625;
    static final int ENTER_NEXT_TIER_WIDGET=60626;
    static final int RESET_EVENT_ITEMS_WIDGET=60631;
    static final int MAIN_GRID_CAPACITY=175;
    static final int SMALL_GRID_CAPACITY=4;
    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    static EventChestService.Action resolveAction(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        switch(widgetId){
            case EXCHANGE_WIDGET:
                return EventChestService.Action.EXCHANGE;
            case ENTER_NEXT_TIER_WIDGET:
                return EventChestService.Action.ENTER_NEXT_TIER;
            case RESET_EVENT_ITEMS_WIDGET:
                return EventChestService.Action.RESET_EVENT_ITEMS;
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
                BootstrapPackets.interface97(ROOT)
            );
    }

    static void publishHeadingAndProgress(
        ServerPacketWriter packets,
        String headingText,
        String progressText
    )throws IOException{
        Objects.requireNonNull(packets,"packets");

        ApplicationBus126Publisher.send(
            packets,
            EVENT_HEADING_WIDGET,
            requireText(
                headingText,
                "headingText"
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
    }

    static void publishMainGrid(
        ServerPacketWriter packets,
        int[] itemIds,
        int[] quantities
    )throws IOException{
        publishExactContainer(
            packets,
            MAIN_GRID_WIDGET,
            itemIds,
            quantities,
            MAIN_GRID_CAPACITY,
            "main grid"
        );
    }

    static void publishSmallGrid(
        ServerPacketWriter packets,
        int gridIndex,
        int[] itemIds,
        int[] quantities
    )throws IOException{
        int widget;
        switch(gridIndex){
            case 0:
                widget=SMALL_GRID_0_WIDGET;
                break;
            case 1:
                widget=SMALL_GRID_1_WIDGET;
                break;
            case 2:
                widget=SMALL_GRID_2_WIDGET;
                break;
            default:
                throw new IllegalArgumentException(
                    "gridIndex="+gridIndex
                );
        }

        publishExactContainer(
            packets,
            widget,
            itemIds,
            quantities,
            SMALL_GRID_CAPACITY,
            "small grid "+gridIndex
        );
    }

    private static void publishExactContainer(
        ServerPacketWriter packets,
        int widget,
        int[] itemIds,
        int[] quantities,
        int expectedSlots,
        String label
    )throws IOException{
        if(itemIds==null||
           quantities==null||
           itemIds.length!=expectedSlots||
           quantities.length!=expectedSlots)
            throw new IllegalArgumentException(
                "Event Chest "+
                label+
                " requires "+
                expectedSlots+
                " slots"
            );

        Objects.requireNonNull(packets,"packets")
            .varShort(
                53,
                BootstrapPackets.itemContainer53(
                    widget,
                    itemIds,
                    quantities
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

    private EventChestPresentation(){}
}
