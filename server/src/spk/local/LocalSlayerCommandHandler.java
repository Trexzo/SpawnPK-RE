package spk.local;

import java.util.Objects;

/**
 * Player-facing command adapter for the explicit LocalLab G4 Blood Slayer
 * slice. ::slayer open is only a LocalLab access path to exact-current UI.
 */
final class LocalSlayerCommandHandler {
    static final String AUTHORITY=
        LocalLabSlayerRuntime.AUTHORITY;

    static final class Result {
        final String logText;
        final String clientMessage;
        final String saveReason;
        final boolean openRoot;

        Result(
            String logText,
            String clientMessage,
            String saveReason,
            boolean openRoot
        ){
            this.logText=
                Objects.requireNonNull(
                    logText,
                    "logText"
                );
            this.clientMessage=
                Objects.requireNonNull(
                    clientMessage,
                    "clientMessage"
                );
            this.saveReason=saveReason;
            this.openRoot=openRoot;
        }
    }

    private final WorldPlayer player;
    private final LocalLabSlayerRuntime runtime;

    LocalSlayerCommandHandler(
        WorldPlayer player,
        LocalLabSlayerRuntime runtime
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.runtime=
            Objects.requireNonNull(
                runtime,
                "runtime"
            );
    }

    Result handle(
        String[] tokens,
        long worldTick
    ){
        if(tokens==null||
           tokens.length==0||
           !"slayer".equalsIgnoreCase(
                tokens[0]
           ))
            return null;

        String playerRef=requirePlayerRef();

        if(tokens.length==1||
           (tokens.length==2&&
            "status".equalsIgnoreCase(
                tokens[1]
            )))
            return statusResult(
                runtime.status(
                    playerRef
                ),
                "STATUS",
                null,
                false
            );

        if(tokens.length==2&&
           "open".equalsIgnoreCase(
                tokens[1]
           )){
            LocalLabSlayerRuntime.StatusSnapshot status=
                runtime.status(
                    playerRef
                );

            if(!status.persistenceValid())
                return statusResult(
                    status,
                    "OPEN_REJECTED_INVALID",
                    null,
                    false
                );

            return new Result(
                "G4_BLOOD_SLAYER_COMMAND action=OPEN"+
                " stateMutation=false authority="+
                AUTHORITY,
                "Opening Blood Slayer.",
                null,
                true
            );
        }

        if(tokens.length==2&&
           "start".equalsIgnoreCase(
                tokens[1]
           )){
            LocalLabSlayerRuntime.StartResult
                started=
                    runtime.startMonsterHunter(
                        playerRef,
                        worldTick
                    );

            return statusResult(
                started.status,
                started.created
                    ?"START_ASSIGNED"
                    :"START_EXISTING",
                started.created
                    ?"G4_BLOOD_SLAYER_START"
                    :null,
                false
            );
        }

        return new Result(
            "G4_BLOOD_SLAYER_COMMAND result=REJECTED_SYNTAX"+
            " stateMutation=false authority="+
            AUTHORITY,
            "Usage: ::slayer open, ::slayer start, or ::slayer status",
            null,
            false
        );
    }

    private Result statusResult(
        LocalLabSlayerRuntime.StatusSnapshot status,
        String action,
        String saveReason,
        boolean openRoot
    ){
        if(!status.persistenceValid())
            return new Result(
                "G4_BLOOD_SLAYER_COMMAND action="+
                action+
                " task=INVALID persistenceError="+
                status.persistenceError+
                " stateMutation=false authority="+
                AUTHORITY,
                "Blood Slayer state is invalid; task mutations are disabled.",
                null,
                false
            );

        if(status.task==null)
            return new Result(
                "G4_BLOOD_SLAYER_COMMAND action="+
                action+
                " task=NONE authority="+
                AUTHORITY,
                "Blood Slayer: no LocalLab task. Use ::slayer start.",
                saveReason,
                openRoot
            );

        ObjectiveProgressService.Snapshot objective=
            status.task.objective;

        String state=
            status.task.state.name();

        return new Result(
            "G4_BLOOD_SLAYER_COMMAND action="+
                action+
                " task="+
                status.task.definition.taskKey+
                " state="+
                state+
                " progress="+
                objective.progress+
                "/"+
                objective.goal+
                " completions="+
                status.completions+
                " authority="+
                AUTHORITY,
            "Blood Slayer Monster Hunter: "+
                objective.progress+
                "/"+
                objective.goal+
                " ("+
                state+
                "), completions="+
                status.completions+
                ".",
            saveReason,
            openRoot
        );
    }

    private String requirePlayerRef(){
        String value=player.username();

        if(value==null||
           value.trim().isEmpty())
            throw new IllegalStateException(
                "Slayer command requires registered WorldPlayer"
            );

        return value;
    }
}
