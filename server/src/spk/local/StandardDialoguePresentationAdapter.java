package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Exact-current standard dialogue presentation adapter.
 *
 * Raw roots/widgets remain package-internal transport evidence. Callers select
 * semantic presentation families and line/option counts only.
 */
final class StandardDialoguePresentationAdapter {
    static final int KEYBOARD_CONTINUE_WIDGET=4907;

    private static final int[] NPC_ROOTS={
        4882,4887,4893,4900
    };
    private static final int[] NPC_MODEL_WIDGETS={
        4883,4888,4894,4901
    };
    private static final int[] NPC_NAME_WIDGETS={
        4884,4889,4895,4902
    };
    private static final int[] NPC_LINE_START_WIDGETS={
        4885,4890,4896,4903
    };
    private static final int[] NPC_CONTINUE_WIDGETS={
        4886,4892,4899,4907
    };

    private static final int[] STATEMENT_ROOTS={
        356,359,363,368,374
    };
    private static final int[] STATEMENT_LINE_START_WIDGETS={
        357,360,364,369,375
    };
    private static final int[] STATEMENT_CONTINUE_WIDGETS={
        358,362,367,373,380
    };

    private static final int TWO_OPTION_ROOT=2459;
    private static final int TWO_OPTION_TITLE_WIDGET=2460;
    private static final int TWO_OPTION_FIRST_WIDGET=2461;
    private static final int TWO_OPTION_SECOND_WIDGET=2462;

    static void openNamedNpc(
        ServerPacketWriter packets,
        int npcDefinitionId,
        String speakerName,
        List<String> lines
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        int count=checkedCount(lines,1,4,"named NPC dialogue lines");
        String speaker=wireText(speakerName,"speakerName");

        packets.varShort(
            126,
            BootstrapPackets.widgetText126(
                NPC_NAME_WIDGETS[count-1],
                speaker
            )
        );

        for(int i=0;i<count;i++)
            packets.varShort(
                126,
                BootstrapPackets.widgetText126(
                    NPC_LINE_START_WIDGETS[count-1]+i,
                    wireText(
                        lines.get(i),
                        "lines["+i+"]"
                    )
                )
            );

        packets.fixed(
            75,
            BootstrapPackets.interfaceNpcHead75(
                npcDefinitionId,
                NPC_MODEL_WIDGETS[count-1]
            )
        );

        packets.fixed(
            164,
            BootstrapPackets.chatboxInterface164(
                NPC_ROOTS[count-1]
            )
        );
    }

    static void openStatement(
        ServerPacketWriter packets,
        List<String> lines
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        int count=checkedCount(lines,1,5,"statement lines");

        for(int i=0;i<count;i++)
            packets.varShort(
                126,
                BootstrapPackets.widgetText126(
                    STATEMENT_LINE_START_WIDGETS[count-1]+i,
                    wireText(
                        lines.get(i),
                        "lines["+i+"]"
                    )
                )
            );

        packets.fixed(
            164,
            BootstrapPackets.chatboxInterface164(
                STATEMENT_ROOTS[count-1]
            )
        );
    }

    static void openTwoOptions(
        ServerPacketWriter packets,
        String title,
        List<String> options
    )throws IOException{
        Objects.requireNonNull(packets,"packets");

        if(options==null||options.size()!=2)
            throw new IllegalArgumentException(
                "two-option dialogue requires exactly 2 options"
            );

        packets.varShort(
            126,
            BootstrapPackets.widgetText126(
                TWO_OPTION_TITLE_WIDGET,
                wireText(title,"title")
            )
        );

        packets.varShort(
            126,
            BootstrapPackets.widgetText126(
                TWO_OPTION_FIRST_WIDGET,
                wireText(options.get(0),"options[0]")
            )
        );

        packets.varShort(
            126,
            BootstrapPackets.widgetText126(
                TWO_OPTION_SECOND_WIDGET,
                wireText(options.get(1),"options[1]")
            )
        );

        packets.fixed(
            164,
            BootstrapPackets.chatboxInterface164(
                TWO_OPTION_ROOT
            )
        );
    }

    static void close(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                219,
                new byte[0]
            );
    }

    static boolean acceptsNamedNpcContinue(
        int lineCount,
        int widgetId
    ){
        int count=checkedRange(
            lineCount,
            1,
            4,
            "named NPC lineCount"
        );

        return widgetId==
                NPC_CONTINUE_WIDGETS[count-1]||
            widgetId==
                KEYBOARD_CONTINUE_WIDGET;
    }

    static int twoOptionIndexForWidget(
        int widgetId
    ){
        if(widgetId==TWO_OPTION_FIRST_WIDGET)
            return 1;
        if(widgetId==TWO_OPTION_SECOND_WIDGET)
            return 2;
        return 0;
    }

    static boolean isAugmentedOptionCloseWidget(
        int widgetId
    ){
        return widgetId==54195;
    }

    static int namedNpcRoot(int lineCount){
        return NPC_ROOTS[
            checkedRange(
                lineCount,
                1,
                4,
                "named NPC lineCount"
            )-1
        ];
    }

    static int namedNpcModelWidget(
        int lineCount
    ){
        return NPC_MODEL_WIDGETS[
            checkedRange(
                lineCount,
                1,
                4,
                "named NPC lineCount"
            )-1
        ];
    }

    static int namedNpcContinueWidget(
        int lineCount
    ){
        return NPC_CONTINUE_WIDGETS[
            checkedRange(
                lineCount,
                1,
                4,
                "named NPC lineCount"
            )-1
        ];
    }

    static int twoOptionRoot(){
        return TWO_OPTION_ROOT;
    }

    static int twoOptionWidget(int optionIndex){
        if(optionIndex==1)
            return TWO_OPTION_FIRST_WIDGET;
        if(optionIndex==2)
            return TWO_OPTION_SECOND_WIDGET;
        throw new IllegalArgumentException(
            "optionIndex="+optionIndex
        );
    }

    static int statementRoot(int lineCount){
        return STATEMENT_ROOTS[
            checkedRange(
                lineCount,
                1,
                5,
                "statement lineCount"
            )-1
        ];
    }

    static int statementContinueWidget(
        int lineCount
    ){
        return STATEMENT_CONTINUE_WIDGETS[
            checkedRange(
                lineCount,
                1,
                5,
                "statement lineCount"
            )-1
        ];
    }

    private static int checkedCount(
        List<String> values,
        int minimum,
        int maximum,
        String field
    ){
        if(values==null)
            throw new NullPointerException(field);

        return checkedRange(
            values.size(),
            minimum,
            maximum,
            field
        );
    }

    private static int checkedRange(
        int value,
        int minimum,
        int maximum,
        String field
    ){
        if(value<minimum||value>maximum)
            throw new IllegalArgumentException(
                field+"="+value+
                " expected="+minimum+".."+maximum
            );
        return value;
    }

    private static String wireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);
        if(value.isEmpty())
            throw new IllegalArgumentException(
                field+" empty"
            );

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

    private StandardDialoguePresentationAdapter(){}
}
