package spk.local;

import java.lang.reflect.Method;
import java.util.Locale;

public final class BloodShardSalvagePresentationTest {
    public static void main(String[] args){
        exactWidgetRouting();
        exactRemoveModes();
        exactRoot();
        unknownSecondSurfaceStillUnowned();

        System.out.println(
            "BLOOD_SHARD_SALVAGE_PRESENTATION_PASS "+
            "root18546=true "+
            "input60012=true "+
            "salvage60014=true "+
            "guide60019=true "+
            "removeModes5=true "+
            "secondSurfaceRoleOwned=false "+
            "recipePolicyOwned=false "+
            "yieldPolicyOwned=false"
        );
    }

    private static void exactWidgetRouting(){
        require(
            BloodShardSalvagePresentation
                .resolveWidget(60014)==
                BloodShardSalvagePresentation
                    .Intent
                    .SALVAGE,
            "Salvage action"
        );

        require(
            BloodShardSalvagePresentation
                .resolveWidget(60019)==
                BloodShardSalvagePresentation
                    .Intent
                    .READ_GUIDE,
            "Guide action"
        );

        require(
            BloodShardSalvagePresentation
                .resolveWidget(60015)==null&&
            BloodShardSalvagePresentation
                .resolveWidget(60020)==null,
            "paired hover widgets"
        );
    }

    private static void exactRemoveModes(){
        int[] opcodes={145,117,43,129,135};
        BloodShardSalvagePresentation.RemoveMode[]
            modes={
                BloodShardSalvagePresentation.RemoveMode.ONE,
                BloodShardSalvagePresentation.RemoveMode.FIVE,
                BloodShardSalvagePresentation.RemoveMode.TEN,
                BloodShardSalvagePresentation.RemoveMode.ALL,
                BloodShardSalvagePresentation.RemoveMode.X
            };

        for(int i=0;i<opcodes.length;i++){
            ItemContainerAction action=
                new ItemContainerAction(
                    opcodes[i],
                    60012,
                    3,
                    1234,
                    0,
                    "option"
                );

            BloodShardSalvagePresentation.RemoveIntent
                intent=
                    BloodShardSalvagePresentation
                        .resolveRemoveAction(action);

            require(
                intent!=null&&
                intent.slot==3&&
                intent.itemId==1234&&
                intent.mode==modes[i],
                "remove mode opcode="+
                opcodes[i]
            );
        }

        require(
            BloodShardSalvagePresentation
                .resolveRemoveAction(
                    new ItemContainerAction(
                        145,
                        60013,
                        0,
                        1234,
                        0,
                        "option"
                    )
                )==null,
            "second surface not input"
        );

        require(
            BloodShardSalvagePresentation
                .resolveRemoveAction(
                    new ItemContainerAction(
                        16,
                        60012,
                        0,
                        1234,
                        0,
                        "option"
                    )
                )==null,
            "unrelated opcode"
        );
    }

    private static void exactRoot(){
        require(
            BloodShardSalvagePresentation.ROOT==
                18546,
            "root"
        );
        require(
            BloodShardSalvagePresentation
                .INPUT_ITEM_WIDGET==60012&&
            BloodShardSalvagePresentation
                .SECOND_ITEM_WIDGET==60013&&
            BloodShardSalvagePresentation
                .STATUS_WIDGET==60018,
            "exact widgets"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                BloodShardSalvagePresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                BloodShardSalvagePresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0x48&&
            (root[1]&255)==0x72,
            "root S2C97 body"
        );
    }

    private static void unknownSecondSurfaceStillUnowned(){
        for(Method method:
                BloodShardSalvagePresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("output")||
               name.contains("resultgrid")||
               name.contains("preview")||
               name.contains("yield"))
                throw new AssertionError(
                    "unproven second-surface role exposed "+
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

    private BloodShardSalvagePresentationTest(){}
}
