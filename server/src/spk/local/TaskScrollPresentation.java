package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 Task Scroll presentation adapter.
 *
 * Current evidence closes root/text/reward-grid projection and the two control
 * families, but does not uniquely identify which member of each multi-widget
 * button family is the server-bound C2S185 source. This class therefore does
 * not expose a raw-widget input resolver.
 */
final class TaskScrollPresentation {
    static final int ROOT=18559;
    static final int TITLE_WIDGET=55733;
    static final int INFO_FIRST_WIDGET=55738;
    static final int INFO_LAST_WIDGET=55757;
    static final int REWARD_GRID_WIDGET=55759;
    static final int PROGRESS_TEXT_WIDGET=55763;
    static final int INFO_ROWS=20;
    static final int REWARD_SLOTS=100;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    enum Intent {
        COLLECT_REWARD,
        TRACK_PROGRESS
    }

    static final class ControlFamily {
        final Intent intent;
        final int buttonWidgetA;
        final int buttonWidgetB;
        final int hoverWidget;

        ControlFamily(
            Intent intent,
            int buttonWidgetA,
            int buttonWidgetB,
            int hoverWidget
        ){
            this.intent=Objects.requireNonNull(intent,"intent");
            this.buttonWidgetA=buttonWidgetA;
            this.buttonWidgetB=buttonWidgetB;
            this.hoverWidget=hoverWidget;
        }
    }

    static final ControlFamily COLLECT_CONTROL=
        new ControlFamily(
            Intent.COLLECT_REWARD,
            55764,
            55765,
            55766
        );

    static final ControlFamily TRACK_CONTROL=
        new ControlFamily(
            Intent.TRACK_PROGRESS,
            55768,
            55769,
            55770
        );

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(ROOT)
            );
    }

    static void openEmpty(
        ServerPacketWriter packets
    )throws IOException{
        ServerPacketWriter checked=
            Objects.requireNonNull(
                packets,
                "packets"
            );

        open(checked);

        ApplicationBus126Publisher.send(
            checked,
            TITLE_WIDGET,
            "No Task Scroll assigned"
        );

        publishInformation(
            checked,
            Collections.emptyList()
        );

        ApplicationBus126Publisher.send(
            checked,
            PROGRESS_TEXT_WIDGET,
            ""
        );

        int[] itemIds=
            new int[REWARD_SLOTS];
        int[] quantities=
            new int[REWARD_SLOTS];

        Arrays.fill(
            itemIds,
            -1
        );

        publishRewards(
            checked,
            itemIds,
            quantities
        );
    }

    static void publishTask(
        ServerPacketWriter packets,
        TaskScrollService.Snapshot task,
        String titleText,
        String progressText,
        int[] rewardItemIds,
        int[] rewardQuantities
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        Objects.requireNonNull(task,"task");

        ApplicationBus126Publisher.send(
            packets,
            TITLE_WIDGET,
            wireText(
                titleText,
                "titleText"
            )
        );

        publishInformation(
            packets,
            task.definition.informationLines
        );

        ApplicationBus126Publisher.send(
            packets,
            PROGRESS_TEXT_WIDGET,
            wireText(
                progressText,
                "progressText"
            )
        );

        publishRewards(
            packets,
            rewardItemIds,
            rewardQuantities
        );
    }

    static void publishInformation(
        ServerPacketWriter packets,
        List<String> informationLines
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        Objects.requireNonNull(
            informationLines,
            "informationLines"
        );

        if(informationLines.size()>INFO_ROWS)
            throw new IllegalArgumentException(
                "Task Scroll information rows="+
                informationLines.size()+
                " max="+INFO_ROWS
            );

        for(int i=0;i<INFO_ROWS;i++){
            String text=
                i<informationLines.size()
                    ?wireText(
                        informationLines.get(i),
                        "informationLines["+i+"]"
                    )
                    :"";

            ApplicationBus126Publisher.send(
                packets,
                INFO_FIRST_WIDGET+i,
                text
            );
        }
    }

    static void publishRewards(
        ServerPacketWriter packets,
        int[] itemIds,
        int[] quantities
    )throws IOException{
        if(itemIds==null||
           quantities==null||
           itemIds.length!=REWARD_SLOTS||
           quantities.length!=REWARD_SLOTS)
            throw new IllegalArgumentException(
                "Task Scroll reward grid requires "+
                REWARD_SLOTS+
                " slots"
            );

        Objects.requireNonNull(packets,"packets")
            .varShort(
                53,
                BootstrapPackets.itemContainer53(
                    REWARD_GRID_WIDGET,
                    itemIds,
                    quantities
                )
            );
    }

    static int informationWidget(int index){
        if(index<0||index>=INFO_ROWS)
            throw new IllegalArgumentException(
                "information index="+index
            );

        return INFO_FIRST_WIDGET+index;
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

    private TaskScrollPresentation(){}
}
