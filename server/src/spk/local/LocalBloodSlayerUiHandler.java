package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Live LocalLab adapter for the exact-v308 Blood Slayer input contract.
 *
 * Exact widgets select/request only. G4.3 intentionally supports only the
 * explicit G4.1 Monster Hunter policy; other recovered modes fail closed.
 */
final class LocalBloodSlayerUiHandler {
    static final String AUTHORITY=LocalLabSlayerRuntime.AUTHORITY;

    enum Status {
        MODE_SELECTED,
        TASK_ASSIGNED,
        TASK_EXISTING,
        NO_SUPPORTED_MODE_SELECTED,
        UNSUPPORTED_MODE
    }

    static final class Result {
        final Status status;
        final BloodSlayerModeService.Mode mode;
        final LocalLabSlayerRuntime.StatusSnapshot taskStatus;
        final String clientMessage;

        Result(
            Status status,
            BloodSlayerModeService.Mode mode,
            LocalLabSlayerRuntime.StatusSnapshot taskStatus,
            String clientMessage
        ){
            this.status=Objects.requireNonNull(status,"status");
            this.mode=mode;
            this.taskStatus=taskStatus;
            this.clientMessage=Objects.requireNonNull(clientMessage,"clientMessage");
        }
    }

    private final WorldPlayer player;
    private final LocalLabSlayerRuntime runtime;

    LocalBloodSlayerUiHandler(
        WorldPlayer player,
        LocalLabSlayerRuntime runtime
    ){
        this.player=Objects.requireNonNull(player,"player");
        this.runtime=Objects.requireNonNull(runtime,"runtime");
    }

    Result handle(
        BloodSlayerPresentation.Input input,
        long worldTick,
        ServerPacketWriter packets
    )throws IOException{
        BloodSlayerPresentation.Input checked=
            Objects.requireNonNull(input,"input");
        Objects.requireNonNull(packets,"packets");

        String playerRef=requirePlayerRef();

        if(checked.kind==BloodSlayerPresentation.InputKind.SELECT_MODE){
            if(checked.mode!=BloodSlayerModeService.Mode.MONSTER_HUNTER_PVM)
                return publish(
                    packets,
                    new Result(
                        Status.UNSUPPORTED_MODE,
                        checked.mode,
                        runtime.status(playerRef),
                        "This LocalLab slice currently supports Blood Slayer Monster Hunter only."
                    )
                );

            runtime.bloodSlayer().selectMode(playerRef,checked.mode);

            return publish(
                packets,
                new Result(
                    Status.MODE_SELECTED,
                    checked.mode,
                    runtime.status(playerRef),
                    "Blood Slayer mode selected: Monster Hunter."
                )
            );
        }

        if(checked.kind==BloodSlayerPresentation.InputKind.REQUEST_TASK){
            LocalLabSlayerRuntime.StatusSnapshot before=
                runtime.status(playerRef);

            if(before.selectedMode!=BloodSlayerModeService.Mode.MONSTER_HUNTER_PVM)
                return publish(
                    packets,
                    new Result(
                        Status.NO_SUPPORTED_MODE_SELECTED,
                        before.selectedMode,
                        before,
                        "Select Monster Hunter before requesting this LocalLab Blood Slayer task."
                    )
                );

            LocalLabSlayerRuntime.StartResult started=
                runtime.startMonsterHunter(playerRef,worldTick);
            ObjectiveProgressService.Snapshot objective=
                started.status.task.objective;

            return publish(
                packets,
                new Result(
                    started.created?Status.TASK_ASSIGNED:Status.TASK_EXISTING,
                    BloodSlayerModeService.Mode.MONSTER_HUNTER_PVM,
                    started.status,
                    "Blood Slayer Monster Hunter: "+
                        objective.progress+"/"+objective.goal+
                        " ("+started.status.task.state+")."
                )
            );
        }

        throw new IllegalStateException(
            "Unhandled Blood Slayer input kind "+checked.kind
        );
    }

    private Result publish(
        ServerPacketWriter packets,
        Result result
    )throws IOException{
        new SocialChatPresentationPublisher(packets)
            .serverMessage(result.clientMessage);
        return result;
    }

    private String requirePlayerRef(){
        String value=player.username();
        if(value==null||value.trim().isEmpty())
            throw new IllegalStateException(
                "Blood Slayer UI requires registered WorldPlayer"
            );
        return value;
    }
}
