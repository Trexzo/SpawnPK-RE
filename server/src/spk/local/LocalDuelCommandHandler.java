package spk.local;

import java.util.Objects;

/**
 * Explicit LocalLab command access to the certified normal-Duel lifecycle.
 *
 * These commands are convenience transport only. They do not claim recovered
 * SpawnPK accept/decline/cancel widgets or original server command behavior.
 */
final class LocalDuelCommandHandler {
    static final String AUTHORITY=
        LocalLabDuelRuntime.AUTHORITY;
    static final String ROUTE_AUTHORITY=
        "CUSTOM_LOCALLAB";

    static final class Result {
        final String logText;
        final String clientMessage;
        final boolean mutated;

        Result(
            String logText,
            String clientMessage,
            boolean mutated
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
            this.mutated=mutated;
        }
    }

    private final WorldPlayer player;
    private final LocalLabDuelRuntime runtime;

    LocalDuelCommandHandler(
        WorldPlayer player,
        LocalLabDuelRuntime runtime
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

    static boolean isRoute(
        String[] tokens
    ){
        if(tokens==null||tokens.length==0)
            return false;

        String command=tokens[0];

        return "duelaccept".equalsIgnoreCase(command)||
            "dueldecline".equalsIgnoreCase(command)||
            "duelcancel".equalsIgnoreCase(command);
    }

    Result handle(
        String[] tokens
    ){
        if(!isRoute(tokens))
            return null;

        String command=
            tokens[0].toLowerCase(
                java.util.Locale.ROOT
            );

        if(tokens.length!=1)
            return rejected(
                command,
                "SYNTAX",
                "Usage: ::duelaccept, ::dueldecline, or ::duelcancel"
            );

        String playerRef=requirePlayerRef();

        DuelSessionService.Snapshot open=
            runtime.openFor(
                playerRef
            );

        if("duelaccept".equals(command)){
            if(open==null||
               open.state!=
                    DuelSessionService.State.PROPOSED)
                return rejected(
                    "ACCEPT",
                    "NO_PROPOSED_DUEL",
                    "No proposed Duel is waiting for acceptance."
                );

            if(!playerRef.equals(
                    open.challengedRef))
                return rejected(
                    "ACCEPT",
                    "NOT_CHALLENGED_PLAYER",
                    "Only the challenged player can accept this Duel."
                );

            LocalLabDuelRuntime.StartResult started=
                runtime.acceptAndStart(
                    playerRef
                );

            return new Result(
                "G109_NORMAL_DUEL_COMMAND action=ACCEPT"+
                " result=ACTIVE"+
                " challenge="+
                started.snapshot.challengeId+
                " match="+
                started.snapshot.matchId+
                " routeAuthority="+
                ROUTE_AUTHORITY+
                " gameplayAuthority="+
                AUTHORITY+
                " nativeAcceptWidgetClaim=false"+
                " originalCommandClaim=false",
                "Duel accepted and started.",
                true
            );
        }

        if("dueldecline".equals(command)){
            if(open==null||
               open.state!=
                    DuelSessionService.State.PROPOSED)
                return rejected(
                    "DECLINE",
                    "NO_PROPOSED_DUEL",
                    "No proposed Duel is waiting to be declined."
                );

            if(!playerRef.equals(
                    open.challengedRef))
                return rejected(
                    "DECLINE",
                    "NOT_CHALLENGED_PLAYER",
                    "Only the challenged player can decline this Duel."
                );

            DuelSessionService.Snapshot declined=
                runtime.decline(
                    playerRef
                );

            return new Result(
                "G109_NORMAL_DUEL_COMMAND action=DECLINE"+
                " result="+declined.state+
                " challenge="+
                declined.challengeId+
                " routeAuthority="+
                ROUTE_AUTHORITY+
                " gameplayAuthority="+
                AUTHORITY+
                " nativeDeclineWidgetClaim=false"+
                " originalCommandClaim=false",
                "Duel declined.",
                true
            );
        }

        if(open==null||
           (open.state!=
                DuelSessionService.State.PROPOSED&&
            open.state!=
                DuelSessionService.State.ACCEPTED))
            return rejected(
                "CANCEL",
                "NO_OPEN_DUEL",
                "No open Duel can be cancelled."
            );

        DuelSessionService.Snapshot cancelled=
            runtime.cancelOpen(
                playerRef
            );

        return new Result(
            "G109_NORMAL_DUEL_COMMAND action=CANCEL"+
            " result="+cancelled.state+
            " challenge="+
            cancelled.challengeId+
            " routeAuthority="+
            ROUTE_AUTHORITY+
            " gameplayAuthority="+
            AUTHORITY+
            " nativeCancelWidgetClaim=false"+
            " originalCommandClaim=false",
            "Duel cancelled.",
            true
        );
    }

    private Result rejected(
        String action,
        String reason,
        String clientMessage
    ){
        return new Result(
            "G109_NORMAL_DUEL_COMMAND action="+
            action+
            " result=REJECTED"+
            " reason="+reason+
            " stateMutation=false"+
            " routeAuthority="+
            ROUTE_AUTHORITY+
            " gameplayAuthority="+
            AUTHORITY+
            " originalCommandClaim=false",
            clientMessage,
            false
        );
    }

    private String requirePlayerRef(){
        String value=player.username();

        if(value==null||
           value.trim().isEmpty())
            throw new IllegalStateException(
                "Duel command requires registered WorldPlayer"
            );

        return PartyService.requireRef(
            value
        );
    }
}
