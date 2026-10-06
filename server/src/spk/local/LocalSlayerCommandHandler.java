package spk.local;

import java.util.Objects;

/**
 * Player-facing command adapter for the explicit LocalLab G4.1 Blood Slayer
 * slice. Native Blood Slayer widgets are intentionally not claimed here.
 */
final class LocalSlayerCommandHandler {
    static final String AUTHORITY=
        LocalLabSlayerRuntime.AUTHORITY;

    static final class Result {
        final String logText;
        final String clientMessage;
        final boolean openRoot;

        Result(
            String logText,
            String clientMessage
        ){
            this(logText,clientMessage,false);
        }

        Result(
            String logText,
            String clientMessage,
            boolean openRoot
        ){
            this.logText=Objects.requireNonNull(logText,"logText");
            this.clientMessage=Objects.requireNonNull(clientMessage,"clientMessage");
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
                "STATUS"
            );

        if(tokens.length==2&&
           "open".equalsIgnoreCase(
                tokens[1]
           ))
            return new Result(
                "G4_BLOOD_SLAYER_COMMAND action=OPEN"+
                " stateMutation=false authority="+
                AUTHORITY,
                "Opening Blood Slayer.",
                true
            );

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
                    :"START_EXISTING"
            );
        }

        return new Result(
            "G4_BLOOD_SLAYER_COMMAND result=REJECTED_SYNTAX"+
            " stateMutation=false authority="+
            AUTHORITY,
            "Usage: ::slayer open, ::slayer start, or ::slayer status"
        );
    }

    private Result statusResult(
        LocalLabSlayerRuntime.StatusSnapshot status,
        String action
    ){
        if(status.task==null)
            return new Result(
                "G4_BLOOD_SLAYER_COMMAND action="+
                action+
                " task=NONE authority="+
                AUTHORITY,
                "Blood Slayer: no LocalLab task. Use ::slayer start."
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
                " authority="+
                AUTHORITY,
            "Blood Slayer Monster Hunter: "+
                objective.progress+
                "/"+
                objective.goal+
                " ("+
                state+
                ")."
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
