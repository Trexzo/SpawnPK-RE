package spk.local;

import java.util.Objects;

/**
 * Player-facing access to the explicit LocalLab G14.1 Daily PvM challenge.
 *
 * The command may assign/read status only. Reward claim/reset behavior is not
 * exposed because that policy is not owned by G14.1.
 */
final class LocalDailyChallengeCommandHandler {
    static final String AUTHORITY=
        LocalLabDailyChallengeRuntime.AUTHORITY;

    static final class Result {
        final String logText;
        final String clientMessage;

        Result(
            String logText,
            String clientMessage
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
        }
    }

    private final WorldPlayer player;
    private final LocalLabDailyChallengeRuntime runtime;

    LocalDailyChallengeCommandHandler(
        WorldPlayer player,
        LocalLabDailyChallengeRuntime runtime
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
        String[] tokens
    ){
        if(tokens==null||
           tokens.length==0||
           !"daily".equalsIgnoreCase(
                tokens[0]
           ))
            return null;

        if(tokens.length==1||
           (tokens.length==2&&
            "status".equalsIgnoreCase(
                tokens[1]
            )))
            return status(
                runtime.status(
                    requirePlayerRef()
                )
            );

        return new Result(
            "G141_DAILY_COMMAND result=REJECTED_SYNTAX"+
                " stateMutation=false"+
                " rewardClaimExposed=false"+
                " authority="+
                AUTHORITY,
            "Usage: ::daily or ::daily status. Reward claiming is not configured."
        );
    }

    Result handle(
        DailyChallengeClientRequest request
    ){
        DailyChallengeClientRequest checked=
            Objects.requireNonNull(
                request,
                "request"
            );

        if(!LocalLabDailyChallengeRuntime
                .CHALLENGE_KEY
                .equals(
                    checked.challengeKey()
                ))
            return null;

        if(checked.action()==
                DailyChallengeClientRequest.Action.CLAIM)
            return new Result(
                "G143_DAILY_TYPED_REQUEST action=CLAIM"+
                    " challenge="+
                    checked.challengeKey()+
                    " result=DISABLED_NO_REWARD_AUTHORITY"+
                    " exactTyped=true"+
                    " stateMutation=false"+
                    " rewardClaimExposed=false"+
                    " authority="+
                    AUTHORITY,
                "Daily reward claiming is not configured."
            );

        if(checked.action()!=
                DailyChallengeClientRequest.Action.INFO)
            throw new IllegalStateException(
                "Unsupported Daily Challenge action "+
                checked.action()
            );

        Result status=
            status(
                runtime.status(
                    requirePlayerRef()
                )
            );

        return new Result(
            "G143_DAILY_TYPED_REQUEST action=INFO"+
                " challenge="+
                checked.challengeKey()+
                " exactTyped=true "+
                status.logText,
            status.clientMessage
        );
    }

    private Result status(
        LocalLabDailyChallengeRuntime.StatusResult status
    ){
        DailyChallengeApplicationService.ChallengeSnapshot
            challenge=status.challenge;

        String state=
            challenge.complete
                ?"COMPLETE"
                :"ACTIVE";

        String message=
            challenge.complete
                ?"Daily PvM complete: "+
                    challenge.current+
                    "/"+
                    challenge.target+
                    ". Reward claiming is not configured."
                :"Daily PvM: "+
                    challenge.current+
                    "/"+
                    challenge.target+
                    " - Kill certified Monster Spawner targets.";

        return new Result(
            "G141_DAILY_COMMAND action=STATUS"+
                " assignedNow="+
                status.assignedNow+
                " challenge="+
                challenge.challengeKey+
                " state="+state+
                " progress="+
                challenge.current+
                "/"+
                challenge.target+
                " claimed="+
                challenge.claimed+
                " rewardClaimExposed=false"+
                " resetPolicyClaim=false"+
                " persistenceAuthority=CUSTOM_LOCALLAB"+
                " originalPersistenceClaim=false"+
                " authority="+
                AUTHORITY,
            message
        );
    }

    private String requirePlayerRef(){
        String value=player.username();

        if(value==null||
           value.trim().isEmpty())
            throw new IllegalStateException(
                "Daily command requires registered WorldPlayer"
            );

        return value;
    }
}
