package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Boss Teleportation Network presentation/input adapter.
 *
 * Raw widget ids, row order and generic packet-compatible presentation stay at
 * this boundary. Destination coordinates, requirements and drop mechanics stay
 * server-owned.
 */
final class BossTeleportPresentation {
    static final int ROOT=18616;
    static final int FIRST_ROW_WIDGET=60412;
    static final int LAST_ROW_WIDGET=60424;
    static final int TELEPORT_WIDGET=60448;
    static final int VIEW_FULL_DROP_TABLE_WIDGET=39873;
    static final int NAME_WIDGET=60405;
    static final int DESCRIPTION_WIDGET=60407;
    static final int DROP_PREVIEW_WIDGET=60447;
    static final int MAX_ROWS=13;
    static final int DROP_PREVIEW_SLOTS=12;
    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    enum InputKind {
        SELECT_ROW,
        TELEPORT,
        VIEW_FULL_DROP_TABLE
    }

    static final class Input {
        final InputKind kind;
        final int rowIndex;

        private Input(InputKind kind,int rowIndex){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.rowIndex=rowIndex;
        }

        static Input selectRow(int rowIndex){
            return new Input(
                InputKind.SELECT_ROW,
                checkedRowIndex(rowIndex)
            );
        }

        static Input teleport(){
            return new Input(InputKind.TELEPORT,-1);
        }

        static Input viewFullDropTable(){
            return new Input(
                InputKind.VIEW_FULL_DROP_TABLE,
                -1
            );
        }
    }

    static Input resolveWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        if(widgetId>=FIRST_ROW_WIDGET&&
           widgetId<=LAST_ROW_WIDGET)
            return Input.selectRow(
                widgetId-FIRST_ROW_WIDGET
            );

        if(widgetId==TELEPORT_WIDGET)
            return Input.teleport();

        if(widgetId==
                VIEW_FULL_DROP_TABLE_WIDGET)
            return Input.viewFullDropTable();

        return null;
    }

    static String rowBossKey(
        BossTeleportService.Snapshot snapshot,
        int rowIndex
    ){
        Objects.requireNonNull(snapshot,"snapshot");
        int checked=checkedRowIndex(rowIndex);

        if(checked>=snapshot.entries.size())
            throw new IllegalStateException(
                "Boss Teleport row not populated index="+
                checked+
                " rows="+snapshot.entries.size()
            );

        return snapshot.entries
            .get(checked)
            .bossKey;
    }

    static String selectedFullDropTableKey(
        BossTeleportService.PlayerSnapshot player
    ){
        Objects.requireNonNull(player,"player");

        if(!player.hasSelection())
            throw new IllegalStateException(
                "Boss Teleport selection missing"
            );

        if(player.selectedFullDropTableKey==null)
            throw new IllegalStateException(
                "Boss Teleport full drop table unavailable boss="+
                player.selectedBossKey
            );

        return player.selectedFullDropTableKey;
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

    static void publishSelectionText(
        ServerPacketWriter packets,
        BossTeleportService.Entry entry
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        Objects.requireNonNull(entry,"entry");

        ApplicationBus126Publisher.send(
            packets,
            NAME_WIDGET,
            entry.displayName
        );
        ApplicationBus126Publisher.send(
            packets,
            DESCRIPTION_WIDGET,
            entry.description
        );
    }

    static void publishDropPreview(
        ServerPacketWriter packets,
        int[] itemIds,
        int[] quantities
    )throws IOException{
        if(itemIds==null||
           quantities==null||
           itemIds.length!=DROP_PREVIEW_SLOTS||
           quantities.length!=DROP_PREVIEW_SLOTS)
            throw new IllegalArgumentException(
                "Boss Teleport drop preview requires "+
                DROP_PREVIEW_SLOTS+
                " slots"
            );

        Objects.requireNonNull(packets,"packets");

        packets.varShort(
            53,
            BootstrapPackets.itemContainer53(
                DROP_PREVIEW_WIDGET,
                itemIds,
                quantities
            )
        );
    }

    static int rowWidget(int rowIndex){
        return FIRST_ROW_WIDGET+
            checkedRowIndex(rowIndex);
    }

    private static int checkedRowIndex(
        int rowIndex
    ){
        if(rowIndex<0||rowIndex>=MAX_ROWS)
            throw new IllegalArgumentException(
                "rowIndex="+rowIndex
            );
        return rowIndex;
    }

    private BossTeleportPresentation(){}
}
