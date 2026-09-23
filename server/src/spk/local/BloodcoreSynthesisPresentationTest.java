package spk.local;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public final class BloodcoreSynthesisPresentationTest {
    public static void main(String[] args){
        exactControlFamilies();
        exactRemoveModes();
        exactRoot();
        unresolvedDispatchAndSecondSurfaceStayUnowned();

        System.out.println(
            "BLOODCORE_SYNTHESIS_PRESENTATION_PASS "+
            "root61078=true "+
            "controlFamilies4=true "+
            "removeModes5=true "+
            "status61096_61097=true "+
            "rawWidgetDispatchOwned=false "+
            "secondSurfaceRoleOwned=false "+
            "recipePolicyOwned=false"
        );
    }

    private static void exactControlFamilies(){
        List<BloodcoreSynthesisPresentation.ControlFamily>
            controls=
                BloodcoreSynthesisPresentation
                    .controls();

        require(
            controls.size()==4,
            "control count"
        );

        requireControl(
            BloodcoreSynthesisService.Control.START,
            61081,61082,61083
        );
        requireControl(
            BloodcoreSynthesisService.Control.SHOP,
            61086,61087,61088
        );
        requireControl(
            BloodcoreSynthesisService.Control.GUIDE,
            61091,61092,61093
        );
        requireControl(
            BloodcoreSynthesisService.Control.LOTTO,
            61101,61102,61103
        );
    }

    private static void requireControl(
        BloodcoreSynthesisService.Control control,
        int a,
        int b,
        int hover
    ){
        BloodcoreSynthesisPresentation.ControlFamily family=
            BloodcoreSynthesisPresentation.control(control);

        require(
            family.control==control&&
            family.buttonWidgetA==a&&
            family.buttonWidgetB==b&&
            family.hoverWidget==hover,
            "control "+control
        );
    }

    private static void exactRemoveModes(){
        int[] opcodes={145,117,43,129,135};
        BloodcoreSynthesisPresentation.RemoveMode[]
            modes={
                BloodcoreSynthesisPresentation.RemoveMode.ONE,
                BloodcoreSynthesisPresentation.RemoveMode.FIVE,
                BloodcoreSynthesisPresentation.RemoveMode.TEN,
                BloodcoreSynthesisPresentation.RemoveMode.ALL,
                BloodcoreSynthesisPresentation.RemoveMode.X
            };

        for(int i=0;i<opcodes.length;i++){
            BloodcoreSynthesisPresentation.RemoveIntent intent=
                BloodcoreSynthesisPresentation
                    .resolveRemoveAction(
                        new ItemContainerAction(
                            opcodes[i],
                            61099,
                            4,
                            22844,
                            0,
                            "option"
                        )
                    );

            require(
                intent!=null&&
                intent.slot==4&&
                intent.itemId==22844&&
                intent.mode==modes[i],
                "remove opcode "+opcodes[i]
            );
        }

        require(
            BloodcoreSynthesisPresentation
                .resolveRemoveAction(
                    new ItemContainerAction(
                        145,
                        61100,
                        0,
                        22844,
                        0,
                        "option"
                    )
                )==null,
            "second surface not input"
        );
    }

    private static void exactRoot(){
        require(
            BloodcoreSynthesisPresentation.ROOT==
                61078,
            "root"
        );
        require(
            BloodcoreSynthesisPresentation
                .INPUT_ITEM_WIDGET==61099&&
            BloodcoreSynthesisPresentation
                .SECOND_ITEM_WIDGET==61100&&
            BloodcoreSynthesisPresentation
                .STATUS_WIDGET_A==61096&&
            BloodcoreSynthesisPresentation
                .STATUS_WIDGET_B==61097,
            "exact widgets"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                BloodcoreSynthesisPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                BloodcoreSynthesisPresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0xee&&
            (root[1]&255)==0x96,
            "root S2C97 body"
        );
    }

    private static void unresolvedDispatchAndSecondSurfaceStayUnowned(){
        for(Method method:
                BloodcoreSynthesisPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("resolvewidget")||
               name.contains("handleclick")||
               name.contains("output")||
               name.contains("resultgrid")||
               name.contains("yield"))
                throw new AssertionError(
                    "unproven Synthesis behavior exposed "+
                    method.getName()
                );
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private BloodcoreSynthesisPresentationTest(){}
}
