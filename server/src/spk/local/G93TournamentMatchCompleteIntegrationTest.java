package spk.local;

public final class G93TournamentMatchCompleteIntegrationTest {
    private static final String WINNER="g93-winner";
    private static final String LOSER="g93-loser";

    public static void main(String[] args)throws Exception{
        boolean matchStarted=false;
        boolean callerWinner=false;
        boolean matchCompleted=false;
        boolean sessionCompleted=false;
        boolean instanceClosed=false;
        boolean winnerRegistered=false;
        boolean loserEliminated=false;
        boolean holdsReleased=false;
        boolean replayRejected=false;
        boolean eventRemainsActive=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer winner=
            new WorldPlayer();
        WorldPlayer loser=
            new WorldPlayer();

        world.registerPlayer(
            winner,
            WINNER
        );
        world.registerPlayer(
            loser,
            LOSER
        );

        try{
            LocalLabTournamentRuntime runtime=
                world.localTournament();

            runtime.register(WINNER);
            runtime.register(LOSER);

            LocalLabTournamentRuntime.MatchStartResult
                started=
                    runtime.activateAndStartMatch(
                        WINNER,
                        LOSER,
                        world.clock().tick()
                    );

            matchStarted=
                started.snapshot.eventLifecycle==
                    GlobalEventService.Lifecycle.ACTIVE&&
                started.snapshot.match(
                    started.matchId
                )!=null&&
                runtime.matches().size()==1&&
                runtime.instances().size()==1;

            require(
                matchStarted,
                "Tournament match fixture did not start"
            );

            callerWinner=
                WINNER.equals(
                    started.snapshot.match(
                        started.matchId
                    ).firstParticipant
                );

            require(
                callerWinner,
                "winner fixture not in started match"
            );

            require(
                runtime.events()
                    .terminalHoldCount(
                        LocalLabTournamentRuntime.EVENT_ID
                    )==1&&
                runtime.matches()
                    .compositionLeaseHeld(
                        started.matchId
                    )&&
                runtime.instances()
                    .compositionLeaseHeld(
                        started.instanceId
                    ),
                "Tournament child holds missing before completion"
            );

            TournamentService.Snapshot completed=
                runtime.completeMatch(
                    started.matchId,
                    WINNER
                );

            TournamentService.MatchSnapshot match=
                completed.match(
                    started.matchId
                );
            MatchSession session=
                runtime.matches().get(
                    started.matchId
                );
            WorldInstanceService.Snapshot instance=
                runtime.instances().get(
                    started.instanceId
                );
            TournamentService.EntrantSnapshot winnerState=
                completed.entrant(
                    WINNER
                );
            TournamentService.EntrantSnapshot loserState=
                completed.entrant(
                    LOSER
                );

            matchCompleted=
                match!=null&&
                match.state==
                    TournamentService
                        .TournamentMatchState.COMPLETED&&
                WINNER.equals(
                    match.winnerRef
                );

            sessionCompleted=
                session!=null&&
                session.state==
                    MatchSession.State.COMPLETED&&
                session.result!=null&&
                session.result.hasWinner()&&
                "caller-resolved-win".equals(
                    session.result.outcomeKey
                )&&
                LocalLabTournamentRuntime.AUTHORITY
                    .equals(
                        session.result
                            .decisionAuthority
                    );

            instanceClosed=
                instance!=null&&
                instance.lifecycle==
                    WorldInstanceService.Lifecycle.CLOSED;

            winnerRegistered=
                winnerState!=null&&
                winnerState.state==
                    TournamentService
                        .EntrantState.REGISTERED&&
                winnerState.activeMatchId==null;

            loserEliminated=
                loserState!=null&&
                loserState.state==
                    TournamentService
                        .EntrantState.ELIMINATED&&
                loserState.activeMatchId==null;

            holdsReleased=
                runtime.events()
                    .terminalHoldCount(
                        LocalLabTournamentRuntime.EVENT_ID
                    )==0&&
                !runtime.matches()
                    .compositionLeaseHeld(
                        started.matchId
                    )&&
                !runtime.instances()
                    .compositionLeaseHeld(
                        started.instanceId
                    );

            eventRemainsActive=
                completed.eventLifecycle==
                    GlobalEventService.Lifecycle.ACTIVE&&
                runtime.events()
                    .get(
                        LocalLabTournamentRuntime.EVENT_ID
                    ).lifecycle==
                    GlobalEventService.Lifecycle.ACTIVE;

            require(
                matchCompleted&&
                sessionCompleted&&
                instanceClosed&&
                winnerRegistered&&
                loserEliminated&&
                holdsReleased&&
                eventRemainsActive,
                "explicit Tournament match completion postimage failed"
            );

            try{
                runtime.completeMatch(
                    started.matchId,
                    WINNER
                );
            }catch(IllegalStateException expected){
                replayRejected=true;
            }

            replayRejected=
                replayRejected&&
                runtime.matches().size()==1&&
                runtime.instances().size()==1&&
                runtime.snapshot().matches.size()==1&&
                runtime.snapshot()
                    .match(started.matchId)
                    .state==
                    TournamentService
                        .TournamentMatchState.COMPLETED;

            require(
                replayRejected,
                "completed Tournament match replay mutated state"
            );

            System.out.println(
                "G93_TOURNAMENT_MATCH_COMPLETE_PASS"+
                " matchStarted="+matchStarted+
                " callerWinner="+callerWinner+
                " matchCompleted="+matchCompleted+
                " sessionCompleted="+sessionCompleted+
                " instanceClosed="+instanceClosed+
                " winnerRegistered="+winnerRegistered+
                " loserEliminated="+loserEliminated+
                " holdsReleased="+holdsReleased+
                " replayRejected="+replayRejected+
                " eventRemainsActive="+eventRemainsActive+
                " nextPairClaim=false"+
                " pvpWinnerClaim=false"+
                " championClaim=false"+
                " rewardClaim=false"+
                " persistenceClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G93TournamentMatchCompleteIntegrationTest(){}
}
