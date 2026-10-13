package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Session-local LocalLab adapter for exact Daily Money Making controls.
 *
 * G18.2 owns difficulty selection. G18.4 binds the exact Track widget to
 * one explicitly CUSTOM_LOCALLAB World-owned EASY PvM objective. Teleport and
 * unconfigured categories remain fail-closed; no production policy is inferred.
 */
final class LocalDailyMoneyMakingUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G182_DAILY_MONEY_MAKING_DIFFICULTY";

    static final class Result {
        final DailyMoneyMakingPresentation.InputKind input;
        final String status;
        final boolean stateChanged;
        final DailyMoneyMakingStateService.Snapshot snapshot;

        Result(
            DailyMoneyMakingPresentation.InputKind input,
            String status,
            boolean stateChanged,
            DailyMoneyMakingStateService.Snapshot snapshot
        ){
            this.input=
                Objects.requireNonNull(
                    input,
                    "input"
                );
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.stateChanged=stateChanged;
            this.snapshot=
                Objects.requireNonNull(
                    snapshot,
                    "snapshot"
                );
        }

        boolean succeeded(){
            return "DIFFICULTY_SELECTED".equals(status)||
                "TRACKING_UPDATED".equals(status);
        }
    }

    private final DailyMoneyMakingStateService state=
        new DailyMoneyMakingStateService(
            new ObjectiveProgressService()
        );

    private final World world;
    private final WorldPlayer owner;
    private final long ownerGeneration;
    private final String playerName;

    /** Historical G18.2 fixture constructor: no World activity authority. */
    LocalDailyMoneyMakingUiHandler(){
        world=null;
        owner=null;
        ownerGeneration=-1L;
        playerName=null;
    }

    /** Installed only after LocalSession registers its exact login player. */
    LocalDailyMoneyMakingUiHandler(
        World world,
        WorldPlayer owner,
        long generation,
        String playerName
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.owner=Objects.requireNonNull(owner,"owner");
        if(generation<0L)
            throw new IllegalArgumentException("generation="+generation);
        this.ownerGeneration=generation;
        this.playerName=Objects.requireNonNull(playerName,"playerName");
    }

    Result handle(
        DailyMoneyMakingPresentation.Input input,
        ServerPacketWriter packets
    )throws IOException{
        DailyMoneyMakingPresentation.Input checked=
            Objects.requireNonNull(
                input,
                "input"
            );

        switch(checked.kind){
            case SELECT_EASY:
                return select(
                    DailyMoneyMakingStateService
                        .Difficulty.EASY,
                    checked,
                    packets
                );
            case SELECT_MEDIUM:
                return select(
                    DailyMoneyMakingStateService
                        .Difficulty.MEDIUM,
                    checked,
                    packets
                );
            case SELECT_HARD:
                return select(
                    DailyMoneyMakingStateService
                        .Difficulty.HARD,
                    checked,
                    packets
                );
            case TRACK:
                return track(checked,packets);
            case TELEPORT:
                return new Result(
                    checked.kind,
                    "NO_TELEPORT_AUTHORITY",
                    false,
                    state.snapshot()
                );
            default:
                throw new IllegalStateException(
                    "Unhandled Daily Money Making input "+
                    checked.kind
                );
        }
    }

    private Result track(
        DailyMoneyMakingPresentation.Input input,
        ServerPacketWriter packets
    )throws IOException{
        DailyMoneyMakingStateService.Snapshot selection=state.snapshot();
        // Keep the G18.2 unbound fixture fail-closed.
        if(world==null)
            return new Result(input.kind,"NO_ACTIVITY_AUTHORITY",false,selection);

        if(selection.selectedDifficulty==null)
            return new Result(input.kind,"NO_SELECTED_DIFFICULTY",false,selection);

        if(selection.selectedDifficulty!=DailyMoneyMakingStateService.Difficulty.EASY)
            return new Result(input.kind,"NO_CONFIGURED_ACTIVITY",false,selection);

        // A recycled username must never let an old session activate another
        // player's World objective. No wire or domain mutation on failure.
        if(!world.players().owns(owner,ownerGeneration)||
           world.players().byName(playerName)!=owner)
            return new Result(input.kind,"NO_CURRENT_PLAYER",false,selection);

        LocalLabDailyMoneyMakingRuntime.ActivationResult activation=
            world.localLabDailyMoneyMaking().activateEasy(playerName);
        LocalLabDailyMoneyMakingRuntime.Snapshot runtime=activation.snapshot;
        if(!LocalLabDailyMoneyMakingRuntime.OBJECTIVE_KEY.equals(
                runtime.state.trackedObjectiveKey))
            throw new IllegalStateException("Daily Money Making objective drift");

        ApplicationControl126Service.dailyMoneyMakingTrackingText(
            Objects.requireNonNull(packets,"packets"),
            "LocalLab Easy PvM: "+runtime.objective.progress+"/"+
                LocalLabDailyMoneyMakingRuntime.GOAL
        );
        return new Result(input.kind,"TRACKING_UPDATED",activation.activatedNow,
            runtime.state);
    }

    DailyMoneyMakingStateService.Snapshot snapshot(){
        return state.snapshot();
    }

    private Result select(
        DailyMoneyMakingStateService.Difficulty difficulty,
        DailyMoneyMakingPresentation.Input input,
        ServerPacketWriter packets
    )throws IOException{
        boolean changed=
            state.selectDifficulty(
                difficulty
            );

        ApplicationControl126Service
            .dailyMoneyMakingDifficulty(
                Objects.requireNonNull(
                    packets,
                    "packets"
                ),
                difficulty
            );

        return new Result(
            input.kind,
            "DIFFICULTY_SELECTED",
            changed,
            state.snapshot()
        );
    }
}
