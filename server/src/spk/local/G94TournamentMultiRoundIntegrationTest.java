package spk.local;

public final class G94TournamentMultiRoundIntegrationTest {
    private static final String ALPHA="g94-alpha";
    private static final String BRAVO="g94-bravo";
    private static final String CHARLIE="g94-charlie";

    public static void main(String[] args)throws Exception{
        boolean firstRoundComplete=false;
        boolean winnerReenters=false;
        boolean eliminatedRejected=false;
        boolean secondRoundStarted=false;
        boolean secondRoundComplete=false;
        boolean explicitTournamentComplete=false;
        boolean terminalRegistrationRejected=false;
        boolean terminalMatchRejected=false;
        boolean twoMatchesTwoInstances=false;
        boolean allChildrenClosed=false;

        World world=
            World.isolatedForTest(
                60_000L
            );

        world.registerPlayer(
            new WorldPlayer(),
            ALPHA
        );
        world.registerPlayer(
            new WorldPlayer(),
            BRAVO
        );
        world.registerPlayer(
            new WorldPlayer(),
            CHARLIE
        );

        try{
            LocalLabTournamentRuntime runtime=
                world.localTournament();

            runtime.register(ALPHA);
            runtime.register(BRAVO);
            runtime.register(CHARLIE);

            LocalLabTournamentRuntime.MatchStartResult first=
                runtime.activateAndStartMatch(
                    ALPHA,
                    BRAVO,
                    world.clock().tick()
                );

            TournamentService.Snapshot afterFirst=
                runtime.completeMatch(
                    first.matchId,
                    ALPHA
                );

            firstRoundComplete=
                afterFirst.match(first.matchId).state==
                    TournamentService
                        .TournamentMatchState.COMPLETED;

            winnerReenters=
                afterFirst.entrant(ALPHA).state==
                    TournamentService
                        .EntrantState.REGISTERED&&
                afterFirst.entrant(ALPHA)
                    .activeMatchId==null;

            require(
                firstRoundComplete&&
                winnerReenters&&
                afterFirst.entrant(BRAVO).state==
                    TournamentService
                        .EntrantState.ELIMINATED,
                "first Tournament round postimage failed"
            );

            try{
                runtime.activateAndStartMatch(
                    BRAVO,
                    CHARLIE,
                    world.clock().tick()
                );
            }catch(IllegalStateException expected){
                eliminatedRejected=true;
            }

            eliminatedRejected=
                eliminatedRejected&&
                runtime.snapshot().matches.size()==1&&
                runtime.matches().size()==1&&
                runtime.instances().size()==1;

            require(
                eliminatedRejected,
                "ELIMINATED entrant was reused"
            );

            LocalLabTournamentRuntime.MatchStartResult second=
                runtime.activateAndStartMatch(
                    ALPHA,
                    CHARLIE,
                    world.clock().tick()
                );

            secondRoundStarted=
                second.snapshot.matches.size()==2&&
                second.snapshot.match(second.matchId).state==
                    TournamentService
                        .TournamentMatchState.ACTIVE&&
                second.snapshot.entrant(ALPHA).state==
                    TournamentService
                        .EntrantState.IN_MATCH&&
                second.snapshot.entrant(CHARLIE).state==
                    TournamentService
                        .EntrantState.IN_MATCH;

            require(
                secondRoundStarted,
                "second explicit Tournament round did not start"
            );

            TournamentService.Snapshot afterSecond=
                runtime.completeMatch(
                    second.matchId,
                    ALPHA
                );

            secondRoundComplete=
                afterSecond.match(second.matchId).state==
                    TournamentService
                        .TournamentMatchState.COMPLETED&&
                afterSecond.entrant(ALPHA).state==
                    TournamentService
                        .EntrantState.REGISTERED&&
                afterSecond.entrant(CHARLIE).state==
                    TournamentService
                        .EntrantState.ELIMINATED;

            require(
                secondRoundComplete,
                "second Tournament round did not complete"
            );

            TournamentService.Snapshot terminal=
                runtime.completeTournament(
                    world.clock().tick()
                );

            explicitTournamentComplete=
                terminal.eventLifecycle==
                    GlobalEventService
                        .Lifecycle.COMPLETED&&
                runtime.events()
                    .get(
                        LocalLabTournamentRuntime.EVENT_ID
                    ).lifecycle==
                    GlobalEventService
                        .Lifecycle.COMPLETED;

            twoMatchesTwoInstances=
                terminal.matches.size()==2&&
                runtime.matches().size()==2&&
                runtime.instances().size()==2;

            allChildrenClosed=
                runtime.instances()
                    .get(first.instanceId)
                    .lifecycle==
                    WorldInstanceService
                        .Lifecycle.CLOSED&&
                runtime.instances()
                    .get(second.instanceId)
                    .lifecycle==
                    WorldInstanceService
                        .Lifecycle.CLOSED&&
                runtime.matches()
                    .get(first.matchId)
                    .state==
                    MatchSession.State.COMPLETED&&
                runtime.matches()
                    .get(second.matchId)
                    .state==
                    MatchSession.State.COMPLETED&&
                runtime.events()
                    .terminalHoldCount(
                        LocalLabTournamentRuntime.EVENT_ID
                    )==0;

            require(
                explicitTournamentComplete&&
                twoMatchesTwoInstances&&
                allChildrenClosed,
                "explicit Tournament terminalization failed"
            );

            try{
                runtime.register(
                    "g94-late"
                );
            }catch(IllegalStateException expected){
                terminalRegistrationRejected=true;
            }

            try{
                runtime.activateAndStartMatch(
                    ALPHA,
                    CHARLIE,
                    world.clock().tick()
                );
            }catch(IllegalStateException expected){
                terminalMatchRejected=true;
            }

            require(
                terminalRegistrationRejected&&
                terminalMatchRejected&&
                runtime.snapshot().eventLifecycle==
                    GlobalEventService
                        .Lifecycle.COMPLETED&&
                runtime.snapshot().matches.size()==2,
                "terminal Tournament accepted new mutation"
            );

            System.out.println(
                "G94_TOURNAMENT_MULTI_ROUND_PASS"+
                " firstRoundComplete="+firstRoundComplete+
                " winnerReenters="+winnerReenters+
                " eliminatedRejected="+eliminatedRejected+
                " secondRoundStarted="+secondRoundStarted+
                " secondRoundComplete="+secondRoundComplete+
                " explicitTournamentComplete="+
                    explicitTournamentComplete+
                " terminalRegistrationRejected="+
                    terminalRegistrationRejected+
                " terminalMatchRejected="+
                    terminalMatchRejected+
                " twoMatchesTwoInstances="+
                    twoMatchesTwoInstances+
                " allChildrenClosed="+allChildrenClosed+
                " autoBracketClaim=false"+
                " championClaim=false"+
                " pvpWinnerClaim=false"+
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

    private G94TournamentMultiRoundIntegrationTest(){}
}
