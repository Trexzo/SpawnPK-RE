package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 PK Ratings application adapter.
 *
 * Owns only the recovered S2C250 subtype-16 row grammar and exact C2S185
 * application-widget normalization. Rating formulas, ordering/reset policy,
 * row-target semantics, rewards and persistence remain server-owned.
 */
final class PkRatingsPresentation {
    static final int APPLICATION_SUBTYPE=16;
    static final int RATINGS_ROOT=40403;
    static final int SELECTION_ROOT=61000;
    static final int RATINGS_TAB_WIDGET=32017;
    static final int DAILY_PK_WIDGET=61002;
    static final int TOURNAMENT_PK_WIDGET=61005;
    static final int FIRST_ROW_WIDGET=40405;
    static final int LAST_ROW_WIDGET=40454;
    static final int MAX_ROWS=50;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    enum InputKind {
        OPEN_RATINGS_TAB,
        SELECT_ROW,
        NAVIGATE
    }

    static final class Input {
        final InputKind kind;
        final int rowIndex;
        final PkRatingsService.Navigation navigation;

        private Input(
            InputKind kind,
            int rowIndex,
            PkRatingsService.Navigation navigation
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.rowIndex=rowIndex;
            this.navigation=navigation;
        }

        static Input openRatingsTab(){
            return new Input(
                InputKind.OPEN_RATINGS_TAB,
                -1,
                null
            );
        }

        static Input selectRow(int rowIndex){
            return new Input(
                InputKind.SELECT_ROW,
                checkedRowIndex(rowIndex),
                null
            );
        }

        static Input navigate(
            PkRatingsService.Navigation navigation
        ){
            return new Input(
                InputKind.NAVIGATE,
                -1,
                Objects.requireNonNull(
                    navigation,
                    "navigation"
                )
            );
        }
    }

    static Input resolveWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        if(widgetId==RATINGS_TAB_WIDGET)
            return Input.openRatingsTab();

        if(widgetId==DAILY_PK_WIDGET)
            return Input.navigate(
                PkRatingsService.Navigation.DAILY_PK
            );

        if(widgetId==TOURNAMENT_PK_WIDGET)
            return Input.navigate(
                PkRatingsService.Navigation.TOURNAMENT_PK
            );

        if(widgetId>=FIRST_ROW_WIDGET&&
           widgetId<=LAST_ROW_WIDGET)
            return Input.selectRow(
                widgetId-FIRST_ROW_WIDGET
            );

        return null;
    }

    static String selectableRowKey(
        PkRatingsService.Snapshot snapshot,
        int rowIndex
    ){
        Objects.requireNonNull(snapshot,"snapshot");
        int checked=checkedRowIndex(rowIndex);

        if(checked>=snapshot.rows.size())
            throw new IllegalStateException(
                "PK Ratings row not present index="+
                checked+
                " rows="+snapshot.rows.size()
            );

        PkRatingsService.Row row=
            snapshot.rows.get(checked);

        if(!row.selectable)
            throw new IllegalStateException(
                "PK Ratings row not selectable index="+
                checked+
                " rowKey="+row.rowKey
            );

        return row.rowKey;
    }

    static void rebuild(
        ServerPacketWriter packets,
        PkRatingsService.Snapshot snapshot,
        int fontIndex
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        Objects.requireNonNull(snapshot,"snapshot");
        checkedFontIndex(fontIndex);

        if(snapshot.rows.size()>MAX_ROWS)
            throw new IllegalArgumentException(
                "PK Ratings rows="+
                snapshot.rows.size()+
                " max="+MAX_ROWS
            );

        clear(packets);

        for(PkRatingsService.Row row:snapshot.rows)
            append(
                packets,
                fontIndex,
                row.selectable,
                row.displayText
            );
    }

    static void clear(
        ServerPacketWriter packets
    )throws IOException{
        ApplicationPacket250Writer.send(
            Objects.requireNonNull(packets,"packets"),
            APPLICATION_SUBTYPE,
            clearBody()
        );
    }

    static void append(
        ServerPacketWriter packets,
        int fontIndex,
        boolean selectable,
        String text
    )throws IOException{
        ApplicationPacket250Writer.send(
            Objects.requireNonNull(packets,"packets"),
            APPLICATION_SUBTYPE,
            appendBody(
                fontIndex,
                selectable,
                text
            )
        );
    }

    static void update(
        ServerPacketWriter packets,
        int rowIndex,
        String text
    )throws IOException{
        ApplicationPacket250Writer.send(
            Objects.requireNonNull(packets,"packets"),
            APPLICATION_SUBTYPE,
            updateBody(
                rowIndex,
                text
            )
        );
    }

    static byte[] clearBody(){
        return checkedBody(
            ApplicationPacket250Writer
                .payload()
                .u8(0)
                .bytes()
        );
    }

    static byte[] appendBody(
        int fontIndex,
        boolean selectable,
        String text
    ){
        String checked=
            wireText(
                text,
                "text"
            );

        return checkedBody(
            ApplicationPacket250Writer
                .payload()
                .u8(1)
                .u8(
                    checkedFontIndex(
                        fontIndex
                    )
                )
                .u8(selectable?1:0)
                .stringNl(checked)
                .bytes()
        );
    }

    static byte[] updateBody(
        int rowIndex,
        String text
    ){
        String checked=
            wireText(
                text,
                "text"
            );

        return checkedBody(
            ApplicationPacket250Writer
                .payload()
                .u8(2)
                .u16(
                    checkedRowIndex(
                        rowIndex
                    )
                )
                .stringNl(checked)
                .bytes()
        );
    }

    static int rowWidget(int rowIndex){
        return FIRST_ROW_WIDGET+
            checkedRowIndex(rowIndex);
    }

    private static byte[] checkedBody(
        byte[] body
    ){
        /*
         * Reuse the canonical S2C250 VAR_BYTE size fence without duplicating
         * packet framing. The returned value remains the subtype-local body.
         */
        ApplicationPacket250Writer.encode(
            APPLICATION_SUBTYPE,
            body
        );
        return body;
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

    private static int checkedFontIndex(
        int fontIndex
    ){
        if(fontIndex<0||fontIndex>255)
            throw new IllegalArgumentException(
                "fontIndex="+fontIndex
            );
        return fontIndex;
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

    private PkRatingsPresentation(){}
}
