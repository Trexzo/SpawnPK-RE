package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Blood Diamond Fuser input/presentation adapter.
 *
 * The client proves three independent Fuse positions plus one Cycle control.
 * Recipe bindings, recipe visuals, economics and outcomes remain semantic/server
 * authority.
 */
final class BloodDiamondFuserPresentation {
    static final int ROOT=318;
    static final int CYCLE_WIDGET=65752;
    static final int ROWS=3;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    private static final int[] FUSE_WIDGETS={
        65406,
        65410,
        65414
    };

    enum InputKind {
        FUSE_ROW,
        CYCLE_ITEMS
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

        static Input fuseRow(int rowIndex){
            return new Input(
                InputKind.FUSE_ROW,
                checkedRow(rowIndex)
            );
        }

        static Input cycle(){
            return new Input(
                InputKind.CYCLE_ITEMS,
                -1
            );
        }
    }

    static Input resolveWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        for(int i=0;i<FUSE_WIDGETS.length;i++)
            if(widgetId==FUSE_WIDGETS[i])
                return Input.fuseRow(i);

        if(widgetId==CYCLE_WIDGET)
            return Input.cycle();

        return null;
    }

    static RecipeId selectedRecipeForRow(
        BloodDiamondFuserService.Snapshot snapshot,
        int rowIndex
    ){
        Objects.requireNonNull(snapshot,"snapshot");

        BloodDiamondFuserService.CycleSnapshot cycle=
            snapshot.selectedCycle();

        if(cycle==null)
            throw new IllegalStateException(
                "Blood Diamond Fuser has no selected cycle"
            );

        return cycle.recipeForRow(
            checkedRow(rowIndex)
        );
    }

    static int fuseWidget(int rowIndex){
        return FUSE_WIDGETS[
            checkedRow(rowIndex)
        ];
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

    private static int checkedRow(int rowIndex){
        if(rowIndex<0||rowIndex>=ROWS)
            throw new IllegalArgumentException(
                "rowIndex="+rowIndex
            );
        return rowIndex;
    }

    private BloodDiamondFuserPresentation(){}
}
