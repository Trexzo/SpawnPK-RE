package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Session-local LocalLab adapter for exact Daily Money Making controls.
 *
 * G18.2 owns only difficulty selection. Track and Teleport are recognized
 * exact-current intents but fail closed until concrete server authority exists.
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
            return "DIFFICULTY_SELECTED".equals(
                status
            );
        }
    }

    private final DailyMoneyMakingStateService state=
        new DailyMoneyMakingStateService(
            new ObjectiveProgressService()
        );

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
                return new Result(
                    checked.kind,
                    "NO_ACTIVITY_AUTHORITY",
                    false,
                    state.snapshot()
                );
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
