package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 Bloodcore Token Synthesis presentation/transport adapter.
 *
 * The control families and item-option transports are closed, but the exact
 * unique server-bound member inside each multi-widget control family is not
 * promoted here. The second item surface also remains semantically unassigned.
 */
final class BloodcoreSynthesisPresentation {
    static final int ROOT=61078;
    static final int STATUS_WIDGET_A=61096;
    static final int STATUS_WIDGET_B=61097;
    static final int INPUT_ITEM_WIDGET=61099;
    static final int SECOND_ITEM_WIDGET=61100;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    static final class ControlFamily {
        final BloodcoreSynthesisService.Control control;
        final int buttonWidgetA;
        final int buttonWidgetB;
        final int hoverWidget;

        ControlFamily(
            BloodcoreSynthesisService.Control control,
            int buttonWidgetA,
            int buttonWidgetB,
            int hoverWidget
        ){
            this.control=Objects.requireNonNull(control,"control");
            this.buttonWidgetA=buttonWidgetA;
            this.buttonWidgetB=buttonWidgetB;
            this.hoverWidget=hoverWidget;
        }
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

    private static final List<ControlFamily>
        CONTROLS=
            Collections.unmodifiableList(
                Arrays.asList(
                    new ControlFamily(
                        BloodcoreSynthesisService.Control.START,
                        61081,61082,61083
                    ),
                    new ControlFamily(
                        BloodcoreSynthesisService.Control.SHOP,
                        61086,61087,61088
                    ),
                    new ControlFamily(
                        BloodcoreSynthesisService.Control.GUIDE,
                        61091,61092,61093
                    ),
                    new ControlFamily(
                        BloodcoreSynthesisService.Control.LOTTO,
                        61101,61102,61103
                    )
                )
            );

    static List<ControlFamily> controls(){
        return CONTROLS;
    }

    static ControlFamily control(
        BloodcoreSynthesisService.Control control
    ){
        BloodcoreSynthesisService.Control checked=
            Objects.requireNonNull(control,"control");

        for(ControlFamily family:CONTROLS)
            if(family.control==checked)
                return family;

        throw new IllegalArgumentException(
            "Bloodcore Synthesis control missing "+
            checked
        );
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
        String textA,
        String textB
    )throws IOException{
        Objects.requireNonNull(packets,"packets");

        ApplicationBus126Publisher.send(
            packets,
            STATUS_WIDGET_A,
            wireText(textA,"textA")
        );
        ApplicationBus126Publisher.send(
            packets,
            STATUS_WIDGET_B,
            wireText(textB,"textB")
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

    private BloodcoreSynthesisPresentation(){}
}
