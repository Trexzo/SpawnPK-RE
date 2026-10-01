package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Exact-v308 Monster Spawner presentation/input adapter.
 *
 * Owns only the proven root, visible toggle, 22 selectable row addresses and
 * S2C126-compatible text channels. NPC catalog/mapping, spawn policy, x5
 * counter semantics, ownership/limits and persistence remain server authority.
 *
 * Widgets 41017/41018 exist in the exact client class but are not attached to
 * root 41000 by the static builder; they are deliberately not actionable here.
 */
final class MonsterSpawnerPresentation {
    static final int ROOT=41000;
    static final int TOGGLE_WIDGET=41007;
    static final int X5_PRESENTATION_WIDGET=41016;
    static final int UNATTACHED_DISTANCED_WIDGET=41017;
    static final int UNATTACHED_X3_WIDGET=41018;
    static final int SELECTED_NPC_TEXT_WIDGET=41019;
    static final int SCROLL_ROOT=41020;
    static final int FIRST_ROW_WIDGET=41021;
    static final int LAST_ROW_WIDGET=41042;
    static final int ROWS=
        LAST_ROW_WIDGET-FIRST_ROW_WIDGET+1;
    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum InputKind {
        TOGGLE,
        SELECT_ROW
    }

    static final class Input {
        final InputKind kind;
        final int rowIndex;

        private Input(
            InputKind kind,
            int rowIndex
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.rowIndex=rowIndex;
        }

        static Input toggle(){
            return new Input(InputKind.TOGGLE,-1);
        }

        static Input selectRow(int rowIndex){
            return new Input(
                InputKind.SELECT_ROW,
                checkedRow(rowIndex)
            );
        }
    }

    static Input resolveWidget(int widgetId){
        checkedWidget(widgetId);

        if(widgetId==TOGGLE_WIDGET)
            return Input.toggle();

        if(widgetId>=FIRST_ROW_WIDGET&&
           widgetId<=LAST_ROW_WIDGET)
            return Input.selectRow(
                widgetId-FIRST_ROW_WIDGET
            );

        /*
         * 41017/41018 intentionally fall through: exact static construction
         * does not attach them to root 41000.
         */
        return null;
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

    static String prepareSelectedNpcName(
        String npcName
    ){
        String clean=requireText(
            npcName,
            "npcName"
        );

        String payload=
            "You have selected: @yel@"+
            clean;

        int bodyLength=
            payload.getBytes(
                StandardCharsets.ISO_8859_1
            ).length+3;

        if(bodyLength>65535)
            throw new IllegalArgumentException(
                "selected NPC text payload too large: "+
                bodyLength
            );

        return clean;
    }

    static void publishSelectedNpcText(
        ServerPacketWriter packets,
        String npcName
    )throws IOException{
        Objects.requireNonNull(packets,"packets");

        String clean=
            prepareSelectedNpcName(
                npcName
            );

        ApplicationBus126Publisher.send(
            packets,
            SELECTED_NPC_TEXT_WIDGET,
            "You have selected: @yel@"+clean
        );
    }

    static void publishRowText(
        ServerPacketWriter packets,
        int rowIndex,
        String text
    )throws IOException{
        Objects.requireNonNull(packets,"packets");

        ApplicationBus126Publisher.send(
            packets,
            rowWidget(rowIndex),
            requireText(text,"text")
        );
    }

    static int rowWidget(int rowIndex){
        return FIRST_ROW_WIDGET+
            checkedRow(rowIndex);
    }

    private static int checkedRow(int rowIndex){
        if(rowIndex<0||rowIndex>=ROWS)
            throw new IllegalArgumentException(
                "rowIndex="+rowIndex
            );
        return rowIndex;
    }

    private static void checkedWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
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

    private MonsterSpawnerPresentation(){}
}
