package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Blood Shard Salvaging Kit transport/presentation adapter.
 *
 * The exact input widget and its five remove-option transports are closed.
 * Input capacity, recipe/yield policy and the semantic role of the second item
 * surface remain unproven and are deliberately not invented here.
 */
final class BloodShardSalvagePresentation {
    static final int ROOT=18546;
    static final int INPUT_ITEM_WIDGET=60012;
    static final int SECOND_ITEM_WIDGET=60013;
    static final int SALVAGE_WIDGET=60014;
    static final int STATUS_WIDGET=60018;
    static final int GUIDE_WIDGET=60019;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    enum Intent {
        SALVAGE,
        READ_GUIDE
    }

    enum RemoveMode {
        ONE(145),
        FIVE(117),
        TEN(43),
        ALL(129),
        X(135);

        private final int opcode;

        RemoveMode(int opcode){
            this.opcode=opcode;
        }

        int opcode(){
            return opcode;
        }

        static RemoveMode fromOpcode(int opcode){
            for(RemoveMode mode:values())
                if(mode.opcode==opcode)
                    return mode;
            return null;
        }
    }

    static final class RemoveIntent {
        final int slot;
        final int itemId;
        final RemoveMode mode;

        RemoveIntent(
            int slot,
            int itemId,
            RemoveMode mode
        ){
            if(slot<0)
                throw new IllegalArgumentException(
                    "slot="+slot
                );
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId="+itemId
                );

            this.slot=slot;
            this.itemId=itemId;
            this.mode=Objects.requireNonNull(mode,"mode");
        }
    }

    static Intent resolveWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        if(widgetId==SALVAGE_WIDGET)
            return Intent.SALVAGE;

        if(widgetId==GUIDE_WIDGET)
            return Intent.READ_GUIDE;

        return null;
    }

    static RemoveIntent resolveRemoveAction(
        ItemContainerAction action
    ){
        Objects.requireNonNull(action,"action");

        if(action.widgetId!=INPUT_ITEM_WIDGET)
            return null;

        RemoveMode mode=
            RemoveMode.fromOpcode(action.opcode);

        if(mode==null)
            return null;

        return new RemoveIntent(
            action.slot,
            action.itemId,
            mode
        );
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

    static void publishStatus(
        ServerPacketWriter packets,
        String statusText
    )throws IOException{
        Objects.requireNonNull(packets,"packets");

        ApplicationBus126Publisher.send(
            packets,
            STATUS_WIDGET,
            wireText(
                statusText,
                "statusText"
            )
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

    private BloodShardSalvagePresentation(){}
}
