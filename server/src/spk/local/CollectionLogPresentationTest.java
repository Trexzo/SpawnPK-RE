package spk.local;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class CollectionLogPresentationTest {
    public static void main(String[] args){
        exactConstants();
        exactContainerShape();
        selectionTransportStillUnowned();

        System.out.println(
            "COLLECTION_LOG_PRESENTATION_PASS "+
            "name54414=true "+
            "obtained54415=true "+
            "kill54416=true "+
            "items54418=true "+
            "reward54419_54420=true "+
            "slots120=true "+
            "outerRootOwned=false "+
            "selectionTransportOwned=false "+
            "semanticEntryIdsReinterpretedAsItemIds=false "+
            "rewardEconomicsOwned=false"
        );
    }

    private static void exactConstants(){
        require(
            CollectionLogPresentation
                .COLLECTION_NAME_WIDGET==54414&&
            CollectionLogPresentation
                .OBTAINED_COUNT_WIDGET==54415&&
            CollectionLogPresentation
                .KILL_COUNT_WIDGET==54416&&
            CollectionLogPresentation
                .RESULT_ITEMS_WIDGET==54418&&
            CollectionLogPresentation
                .REWARD_HEADING_WIDGET==54419&&
            CollectionLogPresentation
                .REWARD_DESCRIPTION_WIDGET==54420,
            "exact projection widgets"
        );

        require(
            CollectionLogPresentation
                .COLLECTION_SELECTION_CONTROL_WIDGET==
                54421&&
            CollectionLogPresentation
                .CATEGORY_SELECTION_CONTROL_WIDGET==
                54422,
            "exact hidden selection controls"
        );

        require(
            CollectionLogPresentation
                .RESULT_SLOTS==
                CollectionLogApplicationService
                    .MAX_RESULT_SLOTS,
            "capacity parity"
        );

        require(
            "EXACT_CURRENT_CLIENT".equals(
                CollectionLogPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );
    }

    private static void exactContainerShape(){
        int[] ids=new int[120];
        int[] qty=new int[120];
        Arrays.fill(ids,-1);

        try{
            byte[] body=
                BootstrapPackets.itemContainer53(
                    CollectionLogPresentation
                        .RESULT_ITEMS_WIDGET,
                    ids,
                    qty
                );

            require(
                body.length>=4&&
                (body[0]&255)==0xd4&&
                (body[1]&255)==0x92&&
                (body[2]&255)==0x00&&
                (body[3]&255)==0x78,
                "result grid packet53 header"
            );
        }catch(Exception failure){
            throw new AssertionError(
                "result grid body",
                failure
            );
        }

        expect(
            IllegalArgumentException.class,
            ()->{
                try{
                    CollectionLogPresentation
                        .publishItems(
                            null,
                            new int[119],
                            new int[119]
                        );
                }catch(java.io.IOException impossible){
                    throw new AssertionError(
                        impossible
                    );
                }
            },
            "exact 120-slot requirement"
        );
    }

    private static void selectionTransportStillUnowned(){
        for(Method method:
                CollectionLogPresentation.class
                    .getDeclaredMethods()){
            String name=method.getName()
                .toLowerCase();

            if(name.contains("selectcategory")||
               name.contains("selectcollection")||
               name.contains("selectioncontrol")||
               name.contains("openroot")||
               name.equals("open"))
                throw new AssertionError(
                    "unproven Collection Log transport exposed "+
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

    private CollectionLogPresentationTest(){}
}
