package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Collection Log presentation adapter.
 *
 * The outer interface root and integer selection-control transport are not
 * normalized by current exact evidence, so this adapter deliberately exposes
 * only the closed text and 120-slot item-container projection.
 */
final class CollectionLogPresentation {
    static final int COLLECTION_NAME_WIDGET=54414;
    static final int OBTAINED_COUNT_WIDGET=54415;
    static final int KILL_COUNT_WIDGET=54416;
    static final int RESULT_ITEMS_WIDGET=54418;
    static final int REWARD_HEADING_WIDGET=54419;
    static final int REWARD_DESCRIPTION_WIDGET=54420;
    static final int COLLECTION_SELECTION_CONTROL_WIDGET=54421;
    static final int CATEGORY_SELECTION_CONTROL_WIDGET=54422;
    static final int RESULT_SLOTS=120;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    static void publishSelectedCollection(
        ServerPacketWriter packets,
        CollectionLogApplicationService.CollectionSnapshot collection,
        String obtainedCountText,
        String killCountText,
        int[] itemIds,
        int[] quantities
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        Objects.requireNonNull(collection,"collection");

        ApplicationBus126Publisher.send(
            packets,
            COLLECTION_NAME_WIDGET,
            collection.name
        );
        ApplicationBus126Publisher.send(
            packets,
            OBTAINED_COUNT_WIDGET,
            wireText(
                obtainedCountText,
                "obtainedCountText"
            )
        );
        ApplicationBus126Publisher.send(
            packets,
            KILL_COUNT_WIDGET,
            wireText(
                killCountText,
                "killCountText"
            )
        );
        ApplicationBus126Publisher.send(
            packets,
            REWARD_HEADING_WIDGET,
            collection.completionRewardHeading
        );
        ApplicationBus126Publisher.send(
            packets,
            REWARD_DESCRIPTION_WIDGET,
            collection.completionRewardDescription
        );

        publishItems(
            packets,
            itemIds,
            quantities
        );
    }

    static void publishItems(
        ServerPacketWriter packets,
        int[] itemIds,
        int[] quantities
    )throws IOException{
        if(itemIds==null||
           quantities==null||
           itemIds.length!=RESULT_SLOTS||
           quantities.length!=RESULT_SLOTS)
            throw new IllegalArgumentException(
                "Collection Log result grid requires "+
                RESULT_SLOTS+
                " slots"
            );

        Objects.requireNonNull(packets,"packets")
            .varShort(
                53,
                BootstrapPackets.itemContainer53(
                    RESULT_ITEMS_WIDGET,
                    itemIds,
                    quantities
                )
            );
    }

    private static String wireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        if(value.indexOf('\n')>=0||
           value.indexOf('\r')>=0)
            throw new IllegalArgumentException(
                field+" contains line terminator"
            );

        return value;
    }

    private CollectionLogPresentation(){}
}
