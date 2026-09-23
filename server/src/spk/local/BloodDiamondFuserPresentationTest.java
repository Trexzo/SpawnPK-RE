package spk.local;

import java.lang.reflect.Method;
import java.util.Locale;

public final class BloodDiamondFuserPresentationTest {
    public static void main(String[] args){
        exactInputRouting();
        exactConstants();
        recipePolicyStillUnowned();

        System.out.println(
            "BLOOD_DIAMOND_FUSER_PRESENTATION_PASS "+
            "root318=true "+
            "fuseRows3=true "+
            "cycle65752=true "+
            "recipeBindingSemantic=true "+
            "recipeVisualsOwned=false "+
            "economicsOwned=false "+
            "outcomePolicyOwned=false"
        );
    }

    private static void exactInputRouting(){
        int[] widgets={65406,65410,65414};

        for(int i=0;i<widgets.length;i++){
            BloodDiamondFuserPresentation.Input input=
                BloodDiamondFuserPresentation
                    .resolveWidget(widgets[i]);

            require(
                input!=null&&
                input.kind==
                    BloodDiamondFuserPresentation
                        .InputKind
                        .FUSE_ROW&&
                input.rowIndex==i,
                "Fuse row "+i
            );
        }

        BloodDiamondFuserPresentation.Input cycle=
            BloodDiamondFuserPresentation
                .resolveWidget(
                    BloodDiamondFuserPresentation
                        .CYCLE_WIRE_WIDGET
                );

        require(
            cycle!=null&&
            cycle.kind==
                BloodDiamondFuserPresentation
                    .InputKind
                    .CYCLE_ITEMS&&
            cycle.rowIndex==-1,
            "Cycle items"
        );

        require(
            BloodDiamondFuserPresentation
                .resolveWidget(65407)==null&&
            BloodDiamondFuserPresentation
                .resolveWidget(65411)==null&&
            BloodDiamondFuserPresentation
                .resolveWidget(65415)==null&&
            BloodDiamondFuserPresentation
                .resolveWidget(217)==null,
            "hover/paired wire ids"
        );

        expect(
            IllegalArgumentException.class,
            ()->BloodDiamondFuserPresentation
                .resolveWidget(
                    BloodDiamondFuserPresentation
                        .CYCLE_WIDGET
                ),
            "client-local cycle id is not raw C2S185"
        );
    }

    private static void exactConstants(){
        require(
            BloodDiamondFuserPresentation.ROOT==318,
            "root"
        );
        require(
            BloodDiamondFuserPresentation.ROWS==
                BloodDiamondFuserService.ROW_COUNT,
            "row parity"
        );
        require(
            BloodDiamondFuserPresentation
                .fuseWidget(0)==65406&&
            BloodDiamondFuserPresentation
                .fuseWidget(2)==65414,
            "fuse widget endpoints"
        );
        require(
            BloodDiamondFuserPresentation
                .CYCLE_WIDGET==65752&&
            BloodDiamondFuserPresentation
                .CYCLE_WIRE_WIDGET==216,
            "cycle client/wire widget ids"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                BloodDiamondFuserPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                BloodDiamondFuserPresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0x01&&
            (root[1]&255)==0x3e,
            "root S2C97 body"
        );

        expect(
            IllegalArgumentException.class,
            ()->BloodDiamondFuserPresentation
                .fuseWidget(3),
            "row overflow"
        );
    }

    private static void recipePolicyStillUnowned(){
        for(Method method:
                BloodDiamondFuserPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("cost")||
               name.contains("yield")||
               name.contains("chance")||
               name.contains("grant")||
               name.contains("price")||
               name.contains("model")||
               name.contains("visual"))
                throw new AssertionError(
                    "unowned Fuser policy/presentation method "+
                    method.getName()
                );
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private BloodDiamondFuserPresentationTest(){}
}
