package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class BloodSlayerPresentationTest {
    public static void main(String[] args){
        exactInputRouting();
        exactPresentationConstants();
        authorityBoundary();

        System.out.println(
            "BLOOD_SLAYER_PRESENTATION_PASS "+
            "root54100=true "+
            "modeWidgets=4 "+
            "getTask54113=true "+
            "c2s185=true "+
            "pointChannels=true "+
            "taskCatalogOwned=false "+
            "assignmentPolicyOwned=false "+
            "pointFormulaOwned=false "+
            "rewardPolicyOwned=false"
        );
    }

    private static void exactInputRouting(){
        requireMode(
            54109,
            BloodSlayerModeService
                .Mode
                .MONSTER_HUNTER_PVM
        );
        requireMode(
            54110,
            BloodSlayerModeService
                .Mode
                .BOSS_HUNTER_PVM
        );
        requireMode(
            54111,
            BloodSlayerModeService
                .Mode
                .BOUNTY_HUNTER_PK
        );
        requireMode(
            54112,
            BloodSlayerModeService
                .Mode
                .SLAUGHTER_PK
        );

        BloodSlayerPresentation.Input get=
            BloodSlayerPresentation.resolveWidget(
                54113
            );

        require(
            get!=null&&
            get.kind==
                BloodSlayerPresentation
                    .InputKind
                    .REQUEST_TASK&&
            get.mode==null,
            "Get Task input"
        );

        require(
            BloodSlayerPresentation.resolveWidget(
                54108
            )==null&&
            BloodSlayerPresentation.resolveWidget(
                54114
            )==null,
            "decorative/hover widgets not actions"
        );

        expect(
            IllegalArgumentException.class,
            ()->BloodSlayerPresentation
                .resolveWidget(-1),
            "negative widget"
        );
        expect(
            IllegalArgumentException.class,
            ()->BloodSlayerPresentation
                .resolveWidget(65536),
            "widget above unsigned short"
        );
    }

    private static void exactPresentationConstants(){
        require(
            BloodSlayerPresentation.ROOT==54100,
            "root"
        );
        require(
            BloodSlayerPresentation
                .BLOOD_SLAYER_POINTS_WIDGET==
                54118&&
            BloodSlayerPresentation
                .SLAYER_POINTS_WIDGET==
                54119,
            "point text targets"
        );
        require(
            BloodSlayerPresentation
                .WIDGET_ACTION_OPCODE==185,
            "widget action opcode"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                BloodSlayerPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                BloodSlayerPresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0xd3&&
            (root[1]&255)==0x54,
            "root 54100 S2C97 body"
        );

        require(
            BloodSlayerModeService
                .Mode
                .values()
                .length==4,
            "semantic mode count parity"
        );
    }

    private static void requireMode(
        int widget,
        BloodSlayerModeService.Mode mode
    ){
        BloodSlayerPresentation.Input input=
            BloodSlayerPresentation.resolveWidget(
                widget
            );

        require(
            input!=null&&
            input.kind==
                BloodSlayerPresentation
                    .InputKind
                    .SELECT_MODE&&
            input.mode==mode,
            "mode widget "+
            widget+
            " -> "+
            mode
        );
    }

    private static void authorityBoundary(){
        for(Field field:
                BloodSlayerPresentation.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("reward")||
               name.contains("taskcatalog")||
               name.contains("assignment")||
               name.contains("pointformula")||
               name.contains("skipcost")||
               name.contains("duration")||
               name.contains("targetpool"))
                throw new AssertionError(
                    "unowned Blood Slayer policy field "+
                    field.getName()
                );
        }

        for(Method method:
                BloodSlayerPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("allocatetask")||
               name.contains("calculatepoints")||
               name.contains("grantreward")||
               name.contains("selecttarget"))
                throw new AssertionError(
                    "unowned Blood Slayer policy method "+
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
                label+
                " wrong failure "+
                failure,
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

    private BloodSlayerPresentationTest(){}
}
