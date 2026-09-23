package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class DonorPanelPresentationTest {
    public static void main(String[] args){
        exactIntentRouting();
        exactPresentationConstants();
        authorityBoundary();

        System.out.println(
            "DONOR_PANEL_PRESENTATION_PASS "+
            "root60062=true "+
            "intents=7 "+
            "progress60091=true "+
            "nextPromo60100=true "+
            "paymentProcessingOwned=false "+
            "thresholdPolicyOwned=false "+
            "rewardPolicyOwned=false "+
            "zoneCoordinatesOwned=false"
        );
    }

    private static void exactIntentRouting(){
        requireIntent(
            60073,
            DonorPanelPresentation.Intent
                .DONATE_FOR_REWARDS
        );
        requireIntent(
            60074,
            DonorPanelPresentation.Intent
                .VIEW_DONATOR_PERKS
        );
        requireIntent(
            60075,
            DonorPanelPresentation.Intent
                .OPEN_DONATOR_SHOP
        );
        requireIntent(
            60076,
            DonorPanelPresentation.Intent
                .TELEPORT_DONATOR_ZONE
        );
        requireIntent(
            60077,
            DonorPanelPresentation.Intent
                .TELEPORT_ELITE_DONATOR_ZONE
        );
        requireIntent(
            60078,
            DonorPanelPresentation.Intent
                .TELEPORT_VIP_DONATOR_ZONE
        );
        requireIntent(
            60079,
            DonorPanelPresentation.Intent
                .TELEPORT_SPONSOR_DONATOR_ZONE
        );

        require(
            DonorPanelPresentation.resolveWidget(
                60080
            )==null,
            "unrelated widget"
        );

        require(
            DonorPanelPresentation
                .Intent
                .values()
                .length==7,
            "exact intent count"
        );
    }

    private static void exactPresentationConstants(){
        require(
            DonorPanelPresentation.ROOT==60062,
            "root"
        );
        require(
            DonorPanelPresentation
                .PROMOTION_PROGRESS_WIDGET==
                60091&&
            DonorPanelPresentation
                .NEXT_PROMOTION_WIDGET==
                60100,
            "promotion text widgets"
        );
        require(
            DonorPanelPresentation
                .WIDGET_ACTION_OPCODE==185,
            "widget action opcode"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                DonorPanelPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                DonorPanelPresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0xea&&
            (root[1]&255)==0x9e,
            "root S2C97 body"
        );
    }

    private static void requireIntent(
        int widget,
        DonorPanelPresentation.Intent expected
    ){
        require(
            DonorPanelPresentation
                .resolveWidget(widget)==
                expected,
            "widget "+
            widget+
            " -> "+
            expected
        );
    }

    private static void authorityBoundary(){
        for(Field field:
                DonorPanelPresentation.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("paymentprovider")||
               name.contains("threshold150")||
               name.contains("threshold300")||
               name.contains("threshold500")||
               name.contains("rewarditem")||
               name.contains("discountpercent")||
               name.contains("coordinate")||
               name.contains("rankrequirement"))
                throw new AssertionError(
                    "unowned donor policy field "+
                    field.getName()
                );
        }

        for(Method method:
                DonorPanelPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("checkout")||
               name.contains("charge")||
               name.contains("grantreward")||
               name.contains("applydiscount")||
               name.contains("teleportto"))
                throw new AssertionError(
                    "unowned donor policy method "+
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

    private DonorPanelPresentationTest(){}
}
