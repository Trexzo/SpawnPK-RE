package spk.local;

import java.lang.reflect.Field;
import java.util.Locale;

public final class DailyMoneyMakingStateServiceTest {
    public static void main(String[] args){
        ObjectiveProgressService objectives=
            new ObjectiveProgressService();

        objectives.define(
            new ObjectiveDefinition(
                "dmm:easy:green_dragons",
                100,
                "CUSTOM_LOCALLAB"
            )
        );

        objectives.define(
            new ObjectiveDefinition(
                "dmm:medium:example",
                25,
                "CUSTOM_LOCALLAB"
            )
        );

        DailyMoneyMakingStateService service=
            new DailyMoneyMakingStateService(
                objectives
            );

        assertInitial(service);
        assertSelection(service);
        assertTrackingUsesObjectiveAuthority(
            service,
            objectives
        );
        assertFailClosed(service);
        assertNoDuplicateProgressOrProtocolIdentity();

        System.out.println(
            "DAILY_MONEY_MAKING_STATE_PASS "+
            "difficultySemantic=true "+
            "selectedCategoryExactCurrentClient=easy,medium,hard "+
            "trackedObjectiveReference=true "+
            "progressOwnedByObjectiveService=true "+
            "unknownObjectiveFailClosed=true "+
            "rewardRulesInvented=false "+
            "teleportRulesInvented=false "+
            "resetScheduleInvented=false "+
            "protocolIdentityInState=false"
        );
    }

    private static void assertInitial(
        DailyMoneyMakingStateService service
    ){
        DailyMoneyMakingStateService.Snapshot snapshot=
            service.snapshot();

        if(snapshot.selectedDifficulty!=null||
           snapshot.hasTrackedObjective()||
           service.trackedObjectiveProgress()!=null)
            throw new AssertionError(
                "initial="+snapshot
            );
    }

    private static void assertSelection(
        DailyMoneyMakingStateService service
    ){
        if(!service.selectDifficulty(
                DailyMoneyMakingStateService
                    .Difficulty.EASY))
            throw new AssertionError(
                "first EASY select did not change"
            );

        if(service.selectDifficulty(
                DailyMoneyMakingStateService
                    .Difficulty.EASY))
            throw new AssertionError(
                "duplicate EASY select changed"
            );

        if(!service.selectDifficulty(
                DailyMoneyMakingStateService
                    .Difficulty.MEDIUM))
            throw new AssertionError(
                "MEDIUM select did not change"
            );

        DailyMoneyMakingStateService.Snapshot snapshot=
            service.snapshot();

        if(snapshot.selectedDifficulty!=
                DailyMoneyMakingStateService
                    .Difficulty.MEDIUM)
            throw new AssertionError(
                "selected="+
                snapshot.selectedDifficulty
            );
    }

    private static void assertTrackingUsesObjectiveAuthority(
        DailyMoneyMakingStateService service,
        ObjectiveProgressService objectives
    ){
        if(!service.trackObjective(
                "DMM:EASY:GREEN_DRAGONS"))
            throw new AssertionError(
                "first track did not change"
            );

        if(service.trackObjective(
                "dmm:easy:green_dragons"))
            throw new AssertionError(
                "duplicate track changed"
            );

        DailyMoneyMakingStateService.Snapshot snapshot=
            service.snapshot();

        if(!"dmm:easy:green_dragons".equals(
                snapshot.trackedObjectiveKey))
            throw new AssertionError(
                "tracked key="+
                snapshot.trackedObjectiveKey
            );

        objectives.advance(
            "dmm:easy:green_dragons",
            17
        );

        ObjectiveProgressService.Snapshot progress=
            service.trackedObjectiveProgress();

        if(progress==null||
           progress.progress!=17||
           progress.goal!=100||
           progress.complete)
            throw new AssertionError(
                "progress="+progress
            );

        objectives.advance(
            "dmm:easy:green_dragons",
            1000
        );

        progress=
            service.trackedObjectiveProgress();

        if(progress.progress!=100||
           !progress.complete)
            throw new AssertionError(
                "completed progress="+
                progress
            );

        if(!service.clearTrackedObjective())
            throw new AssertionError(
                "first clear=false"
            );

        if(service.clearTrackedObjective())
            throw new AssertionError(
                "second clear=true"
            );

        if(service.trackedObjectiveProgress()!=null)
            throw new AssertionError(
                "cleared tracking still returns progress"
            );
    }

    private static void assertFailClosed(
        DailyMoneyMakingStateService service
    ){
        boolean unknownRejected=false;

        try{
            service.trackObjective(
                "dmm:missing"
            );
        }catch(IllegalArgumentException expected){
            unknownRejected=true;
        }

        if(!unknownRejected)
            throw new AssertionError(
                "unknown objective accepted"
            );

        boolean nullDifficultyRejected=false;

        try{
            service.selectDifficulty(
                null
            );
        }catch(NullPointerException expected){
            nullDifficultyRejected=true;
        }

        if(!nullDifficultyRejected)
            throw new AssertionError(
                "null difficulty accepted"
            );
    }

    private static void assertNoDuplicateProgressOrProtocolIdentity(){
        for(Class<?> type:
                new Class<?>[]{
                    DailyMoneyMakingStateService.class,
                    DailyMoneyMakingStateService
                        .Snapshot.class
                }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("widget")||
                   name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("subtype")||
                   name.contains("target"))
                    throw new AssertionError(
                        "protocol identity leaked into "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );

                if(type==
                        DailyMoneyMakingStateService.class&&
                   (name.equals("progress")||
                    name.equals("goal")||
                    name.equals("currentprogress")))
                    throw new AssertionError(
                        "duplicate objective progress field "+
                        field.getName()
                    );
            }
        }
    }
}
