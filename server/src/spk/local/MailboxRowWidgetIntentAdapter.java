package spk.local;

import java.util.Objects;

/**
 * Exact pinned-v308 native inbox row intent.
 *
 * The recovered type-1 widget menu action 315 emits C2S185/u16_be.
 * Row i uses widget 32026+(4*i), 0 <= i < 35.
 * This adapter does not open the Mailbox root, mark messages read,
 * delete envelopes or authorize any attachment settlement.
 */
final class MailboxRowWidgetIntentAdapter {
    static final int FIRST_ROW_WIDGET=32026;
    static final int ROW_WIDGET_STRIDE=4;
    static final int VISIBLE_ROWS=35;
    static final int LAST_ROW_WIDGET=
        FIRST_ROW_WIDGET+(VISIBLE_ROWS-1)*ROW_WIDGET_STRIDE;
    static final int EXACT_OPCODE=185;
    static final String AUTHORITY="EXACT_CURRENT_CLIENT_V308";

    /** Returns -1 for an unrelated widget, preserving existing routing. */
    static int resolveIfRow(WidgetActionClientRequest request){
        WidgetActionClientRequest checked=
            Objects.requireNonNull(request,"request");
        int widget=checked.widgetId();
        if(widget<FIRST_ROW_WIDGET||widget>LAST_ROW_WIDGET)
            return -1;

        int offset=widget-FIRST_ROW_WIDGET;
        if(offset%ROW_WIDGET_STRIDE!=0)
            return -1;

        ClientRequestMetadata metadata=
            Objects.requireNonNull(checked.metadata(),"metadata");
        if(metadata.opcode!=EXACT_OPCODE||
           metadata.provenance!=
               ClientRequestProvenance.EXACT_CURRENT_CLIENT)
            throw new IllegalArgumentException(
                "Mailbox row click must originate from exact v308 C2S185"
            );

        int row=offset/ROW_WIDGET_STRIDE;
        if(row<0||row>=VISIBLE_ROWS)
            throw new IllegalArgumentException(
                "Mailbox client row out of range"
            );
        return row;
    }

    static int requireRow(WidgetActionClientRequest request){
        int row=resolveIfRow(request);
        if(row<0)
            throw new IllegalArgumentException(
                "not an exact Mailbox inbox row widget"
            );
        return row;
    }

    private MailboxRowWidgetIntentAdapter(){}
}
