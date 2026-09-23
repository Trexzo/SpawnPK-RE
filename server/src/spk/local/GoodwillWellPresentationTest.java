package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class GoodwillWellPresentationTest {
    public static void main(String[] args){
        exactInputRouting();
        exactPresentationConstants();
        authorityBoundary();

        System.out.println(
            "GOODWILL_WELL_PRESENTATION_PASS "+
            "root51150=true "+
            "donate51163=true "+
            "progress51158=true "+
            "serverReward51162=true "+
            "individualReward51169=true "+
            "contributionItemPolicyOwned=false "+
            "goalUnitOwned=false "+
            "rewardFormulaOwned=false "+
            "durationOwned=false"
        );
    }

    private static void exactInputRouting(){
        GoodwillWellPresentation.Input donate=
            GoodwillWellPresentation.resolveWidget(
                51163
            );

        require(
            donate!=null&&
            donate.kind==
                GoodwillWellPresentation
                    .InputKind
                    .DONATE,
            "Donate action"
        );

        require(
            GoodwillWellPresentation
                .resolveWidget(51164)==null&&
            GoodwillWellPresentation
                .resolveWidget(51166)==null,
            "hover/label are not Donate actions"
        );

        expect(
            IllegalArgumentException.class,
            ()->GoodwillWellPresentation
                .resolveWidget(-1),
            "negative widget"
        );
        expect(
            IllegalArgumentException.class,
            ()->GoodwillWellPresentation
                .resolveWidget(65536),
            "widget above unsigned short"
        );
    }

    private static void exactPresentationConstants(){
        require(
            GoodwillWellPresentation.ROOT==51150,
            "root"
        );
        require(
            GoodwillWellPresentation
                .CONTRIBUTION_ITEM_TEXT_WIDGET==
                51154&&
            GoodwillWellPresentation
                .PROGRESS_TEXT_WIDGET==
                51158&&
            GoodwillWellPresentation
                .SERVER_REWARD_TEXT_WIDGET==
                51162&&
            GoodwillWellPresentation
                .INDIVIDUAL_REWARD_TEXT_WIDGET==
                51169,
            "text channels"
        );
        require(
            GoodwillWellPresentation
                .WIDGET_ACTION_OPCODE==185,
            "widget opcode"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                GoodwillWellPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                GoodwillWellPresentation.ROOT
            );
        require(
            root.length==2&&
            (root[0]&255)==0xc7&&
            (root[1]&255)==0xce,
            "root S2C97 body"
        );
    }

    private static void authorityBoundary(){
        for(Field field:
                GoodwillWellPresentation.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("item13741")||
               name.contains("goaldenominator")||
               name.contains("conversionratio")||
               name.contains("pointformula")||
               name.contains("durationhours")||
               name.contains("resettime")||
               name.contains("eligibility"))
                throw new AssertionError(
                    "unowned Goodwill policy field "+
                    field.getName()
                );
        }

        for(Method method:
                GoodwillWellPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("calculate")||
               name.contains("grantreward")||
               name.contains("acceptitem")||
               name.contains("activateeffect")||
               name.contains("resetcampaign"))
                throw new AssertionError(
                    "unowned Goodwill policy method "+
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

    private GoodwillWellPresentationTest(){}
}
