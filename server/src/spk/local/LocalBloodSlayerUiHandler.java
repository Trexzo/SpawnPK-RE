package spk.local;

import java.util.Objects;

/**
 * Live LocalLab semantic adapter for the exact-v308 Blood Slayer input
 * contract. Transport/persistence ordering remains at the session boundary.
 */
final class LocalBloodSlayerUiHandler {
    static final String AUTHORITY=
        LocalLabSlayerRuntime.AUTHORITY;
    static final String SAVE_TASK=
        "G4_BLOOD_SLAYER_START";

    enum Status {
        MODE_SELECTED,
        TASK_ASSIGNED,
        TASK_EXISTING,
        NO_SUPPORTED_MODE_SELECTED,
        UNSUPPORTED_MODE,
        PERSISTENCE_INVALID
    }

    static final class Result {
        final Status status;
        final BloodSlayerModeService.Mode mode;
        final LocalLabSlayerRuntime.StatusSnapshot taskStatus;
        final String clientMessage;
        final String saveReason;

        Result(
            Status status,
            BloodSlayerModeService.Mode mode,
            LocalLabSlayerRuntime.StatusSnapshot taskStatus,
            String clientMessage,
            String saveReason
        ){
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
            this.mode=mode;
            this.taskStatus=taskStatus;
            this.clientMessage=Objects.requireNonNull(
                clientMessage,
                "clientMessage"
            );
            this.saveReason=saveReason;
        }
    }

    private final WorldPlayer player;
    private final LocalLabSlayerRuntime runtime;

    LocalBloodSlayerUiHandler(
        WorldPlayer player,
        LocalLabSlayerRuntime runtime
    ){
        this.player=Objects.requireNonNull(
            player,
            "player"
        );
        this.runtime=Objects.requireNonNull(
            runtime,
            "runtime"
        );
    }

    Result handle(
        BloodSlayerPresentation.Input input,
        long worldTick
    ){
        BloodSlayerPresentation.Input checked=
            Objects.requireNonNull(
                input,
                "input"
            );

        String playerRef=requirePlayerRef();

        LocalLabSlayerRuntime.StatusSnapshot before=
            runtime.status(
                playerRef
            );

        if(!before.persistenceValid())
            return new Result(
                Status.PERSISTENCE_INVALID,
                checked.mode,
                before,
                "Blood Slayer state is invalid; task mutations are disabled.",
                null
            );

        if(checked.kind==
                BloodSlayerPresentation
                    .InputKind.SELECT_MODE){
            if(checked.mode!=
                    BloodSlayerModeService.Mode
                        .MONSTER_HUNTER_PVM)
                return new Result(
                    Status.UNSUPPORTED_MODE,
                    checked.mode,
                    before,
                    "This LocalLab slice currently supports Blood Slayer Monster Hunter only.",
                    null
                );

            LocalLabSlayerRuntime.StatusSnapshot selected=
                runtime.selectMonsterHunterMode(
                    playerRef
                );

            if(!selected.persistenceValid())
                return new Result(
                    Status.PERSISTENCE_INVALID,
                    checked.mode,
                    selected,
                    "Blood Slayer state is invalid; task mutations are disabled.",
                    null
                );

            return new Result(
                Status.MODE_SELECTED,
                checked.mode,
                selected,
                "Blood Slayer mode selected: Monster Hunter.",
                null
            );
        }

        if(checked.kind==
                BloodSlayerPresentation
                    .InputKind.REQUEST_TASK){
            if(before.selectedMode!=
                    BloodSlayerModeService.Mode
                        .MONSTER_HUNTER_PVM)
                return new Result(
                    Status.NO_SUPPORTED_MODE_SELECTED,
                    before.selectedMode,
                    before,
                    "Select Monster Hunter before requesting this LocalLab Blood Slayer task.",
                    null
                );

            LocalLabSlayerRuntime.StartResult started=
                runtime.startMonsterHunter(
                    playerRef,
                    worldTick
                );

            if(!started.status.persistenceValid())
                return new Result(
                    Status.PERSISTENCE_INVALID,
                    before.selectedMode,
                    started.status,
                    "Blood Slayer state is invalid; task mutations are disabled.",
                    null
                );

            ObjectiveProgressService.Snapshot objective=
                started.status.task.objective;

            return new Result(
                started.created
                    ?Status.TASK_ASSIGNED
                    :Status.TASK_EXISTING,
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM,
                started.status,
                "Blood Slayer Monster Hunter: "+
                    objective.progress+
                    "/"+
                    objective.goal+
                    " ("+
                    started.status.task.state+
                    "), completions="+
                    started.status.completions+
                    ".",
                started.created
                    ?SAVE_TASK
                    :null
            );
        }

        throw new IllegalStateException(
            "Unhandled Blood Slayer input kind "+
            checked.kind
        );
    }

    private String requirePlayerRef(){
        String value=player.username();

        if(value==null||
           value.trim().isEmpty())
            throw new IllegalStateException(
                "Blood Slayer UI requires registered WorldPlayer"
            );

        return value;
    }
}
