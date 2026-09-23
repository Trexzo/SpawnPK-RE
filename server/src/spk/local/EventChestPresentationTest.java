package spk.local;

import java.util.Arrays;

public final class EventChestPresentationTest {
    public static void main(String[] args){
        exactInputRouting();
        exactContainerShapes();
        exactConstants();

        System.out.println(
            "EVENT_CHEST_PRESENTATION_PASS "+
            "root60600=true "+
            "exchange60604=true "+
            "nextTier60626=true "+
            "reset60631=true "+
            "mainGrid175=true "+
            "smallGrids3x4=true "+
            "semanticKeysReinterpretedAsItemIds=false "+
            "rollPolicyOwned=false "+
            "rewardPolicyOwned=false"
        );
    }

    private static void exactInputRouting(){
        require(
            EventChestPresentation
                .resolveAction(60604)==
                EventChestService.Action.EXCHANGE,
            "Exchange action"
        );
        require(
            EventChestPresentation
                .resolveAction(60626)==
                EventChestService.Action.ENTER_NEXT_TIER,
            "Enter next tier action"
        );
        require(
            EventChestPresentation
                .resolveAction(60631)==
                EventChestService.Action.RESET_EVENT_ITEMS,
            "Reset event items action"
        );

        require(
            EventChestPresentation
                .resolveAction(60605)==null&&
            EventChestPresentation
                .resolveAction(60628)==null&&
            EventChestPresentation
                .resolveAction(60632)==null,
            "hover roots are not actions"
        );
    }

    private static void exactContainerShapes(){
        int[] mainIds=new int[175];
        int[] mainQty=new int[175];
        Arrays.fill(mainIds,-1);

        try{
            byte[] main=
                BootstrapPackets.itemContainer53(
                    EventChestPresentation
                        .MAIN_GRID_WIDGET,
                    mainIds,
                    mainQty
                );

            require(
                main.length>=4&&
                (main[0]&255)==0xec&&
                (main[1]&255)==0xba&&
                (main[2]&255)==0x00&&
                (main[3]&255)==0xaf,
                "main grid packet53 header"
            );
        }catch(Exception failure){
            throw new AssertionError(
                "main grid body",
                failure
            );
        }

        for(int widget:new int[]{
                60611,
                60612,
                60613
        }){
            try{
                byte[] body=
                    BootstrapPackets.itemContainer53(
                        widget,
                        new int[]{-1,-1,-1,-1},
                        new int[]{0,0,0,0}
                    );

                require(
                    body.length>=4&&
                    (body[2]&255)==0&&
                    (body[3]&255)==4,
                    "small grid slot count "+
                    widget
                );
            }catch(Exception failure){
                throw new AssertionError(
                    "small grid body "+
                    widget,
                    failure
                );
            }
        }

        expect(
            IllegalArgumentException.class,
            ()->{
                try{
                    EventChestPresentation
                        .publishMainGrid(
                            null,
                            new int[174],
                            new int[174]
                        );
                }catch(java.io.IOException impossible){
                    throw new AssertionError(
                        impossible
                    );
                }
            },
            "main grid exact capacity"
        );

        expect(
            IllegalArgumentException.class,
            ()->{
                try{
                    EventChestPresentation
                        .publishSmallGrid(
                            null,
                            3,
                            new int[4],
                            new int[4]
                        );
                }catch(java.io.IOException impossible){
                    throw new AssertionError(
                        impossible
                    );
                }
            },
            "small grid index"
        );
    }

    private static void exactConstants(){
        require(
            EventChestPresentation.ROOT==60600,
            "root"
        );
        require(
            EventChestPresentation
                .MAIN_GRID_CAPACITY==
                EventChestService
                    .MAIN_GRID_CAPACITY&&
            EventChestPresentation
                .SMALL_GRID_CAPACITY==
                EventChestService
                    .SMALL_GRID_CAPACITY,
            "service/presentation capacity parity"
        );
        require(
            EventChestService
                .SMALL_GRID_COUNT==3,
            "small grid count"
        );
        require(
            EventChestPresentation
                .WIDGET_ACTION_OPCODE==185,
            "widget opcode"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                EventChestPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                EventChestPresentation.ROOT
            );
        require(
            root.length==2&&
            (root[0]&255)==0xec&&
            (root[1]&255)==0xb8,
            "root S2C97 body"
        );
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

    private EventChestPresentationTest(){}
}
