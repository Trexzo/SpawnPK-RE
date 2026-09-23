package spk.local;

import java.lang.reflect.Method;
import java.util.Locale;

public final class TaskScrollPresentationTest {
    public static void main(String[] args){
        exactProjectionConstants();
        exactControlFamilies();
        exactRewardShape();
        inputDispatchStillUnowned();

        System.out.println(
            "TASK_SCROLL_PRESENTATION_PASS "+
            "root18559=true "+
            "infoRows20=true "+
            "rewardSlots100=true "+
            "collectFamily55764_55766=true "+
            "trackFamily55768_55770=true "+
            "rawWidgetDispatchOwned=false "+
            "taskCatalogOwned=false "+
            "rewardPolicyOwned=false"
        );
    }

    private static void exactProjectionConstants(){
        require(
            TaskScrollPresentation.ROOT==18559,
            "root"
        );
        require(
            TaskScrollPresentation
                .TITLE_WIDGET==55733&&
            TaskScrollPresentation
                .INFO_FIRST_WIDGET==55738&&
            TaskScrollPresentation
                .INFO_LAST_WIDGET==55757&&
            TaskScrollPresentation
                .PROGRESS_TEXT_WIDGET==55763,
            "text widgets"
        );
        require(
            TaskScrollPresentation.INFO_ROWS==
                TaskScrollService.MAX_INFO_LINES,
            "info capacity parity"
        );
        require(
            TaskScrollPresentation
                .REWARD_SLOTS==100&&
            TaskScrollPresentation
                .REWARD_GRID_WIDGET==55759,
            "reward grid"
        );
        require(
            TaskScrollPresentation
                .informationWidget(0)==55738&&
            TaskScrollPresentation
                .informationWidget(19)==55757,
            "information endpoints"
        );

        byte[] root=
            BootstrapPackets.interface97(
                TaskScrollPresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0x48&&
            (root[1]&255)==0x7f,
            "root S2C97 body"
        );
    }

    private static void exactControlFamilies(){
        TaskScrollPresentation.ControlFamily collect=
            TaskScrollPresentation.COLLECT_CONTROL;
        TaskScrollPresentation.ControlFamily track=
            TaskScrollPresentation.TRACK_CONTROL;

        require(
            collect.intent==
                TaskScrollPresentation.Intent
                    .COLLECT_REWARD&&
            collect.buttonWidgetA==55764&&
            collect.buttonWidgetB==55765&&
            collect.hoverWidget==55766,
            "Collect control family"
        );

        require(
            track.intent==
                TaskScrollPresentation.Intent
                    .TRACK_PROGRESS&&
            track.buttonWidgetA==55768&&
            track.buttonWidgetB==55769&&
            track.hoverWidget==55770,
            "Track control family"
        );
    }

    private static void exactRewardShape(){
        int[] ids=new int[100];
        int[] qty=new int[100];

        for(int i=0;i<ids.length;i++)
            ids[i]=-1;

        try{
            byte[] body=
                BootstrapPackets.itemContainer53(
                    TaskScrollPresentation
                        .REWARD_GRID_WIDGET,
                    ids,
                    qty
                );

            require(
                body.length>=4&&
                (body[0]&255)==0xd9&&
                (body[1]&255)==0xcf&&
                (body[2]&255)==0x00&&
                (body[3]&255)==0x64,
                "reward grid packet53 header"
            );
        }catch(Exception failure){
            throw new AssertionError(
                "reward grid body",
                failure
            );
        }

        expect(
            IllegalArgumentException.class,
            ()->{
                try{
                    TaskScrollPresentation
                        .publishRewards(
                            null,
                            new int[99],
                            new int[99]
                        );
                }catch(java.io.IOException impossible){
                    throw new AssertionError(
                        impossible
                    );
                }
            },
            "exact reward capacity"
        );
    }

    private static void inputDispatchStillUnowned(){
        for(Method method:
                TaskScrollPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("resolvewidget")||
               name.contains("handleclick")||
               name.contains("dispatchinput"))
                throw new AssertionError(
                    "unproven Task Scroll input dispatch "+
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

    private TaskScrollPresentationTest(){}
}
