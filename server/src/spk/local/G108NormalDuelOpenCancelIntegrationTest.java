package spk.local;

public final class G108NormalDuelOpenCancelIntegrationTest {
    private static final String A="g108-a";
    private static final String B="g108-b";

    public static void main(String[] args)throws Exception{
        boolean proposalOpen=false;
        boolean challengerCancel=false;
        boolean challengedCancel=false;
        boolean cancelled=false;
        boolean participantsReleased=false;
        boolean reproposalAllowed=false;
        boolean tournamentAdmissionRestored=false;
        boolean replayRejected=false;

        World world=
            World.isolatedForTest(
                60_000L
            );

        world.registerPlayer(
            new WorldPlayer(),
            A
        );
        world.registerPlayer(
            new WorldPlayer(),
            B
        );

        try{
            LocalLabDuelRuntime duels=
                world.localDuels();

            LocalLabDuelRuntime.ProposalResult first=
                duels.propose(
                    A,
                    B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );

            proposalOpen=
                first.snapshot.state==
                    DuelSessionService.State.PROPOSED&&
                duels.openFor(A)!=null&&
                duels.openFor(B)!=null;

            require(
                proposalOpen,
                "first Duel proposal did not open"
            );

            DuelSessionService.Snapshot firstCancelled=
                duels.cancelOpen(
                    A
                );

            challengerCancel=
                firstCancelled.state==
                    DuelSessionService.State.CANCELLED;

            participantsReleased=
                duels.openFor(A)==null&&
                duels.openFor(B)==null;

            require(
                challengerCancel&&
                participantsReleased,
                "challenger cancellation did not release participants"
            );

            LocalLabDuelRuntime.ProposalResult second=
                duels.propose(
                    A,
                    B,
                    NormalDuelPresentation.DuelMode.WHIP_ONLY
                );

            reproposalAllowed=
                second.snapshot.state==
                    DuelSessionService.State.PROPOSED&&
                !second.snapshot.challengeId.equals(
                    first.snapshot.challengeId
                );

            require(
                reproposalAllowed,
                "fresh proposal blocked after challenger cancellation"
            );

            DuelSessionService.Snapshot secondCancelled=
                duels.cancelOpen(
                    B
                );

            challengedCancel=
                secondCancelled.state==
                    DuelSessionService.State.CANCELLED;

            cancelled=
                challengerCancel&&
                challengedCancel;

            participantsReleased=
                participantsReleased&&
                duels.openFor(A)==null&&
                duels.openFor(B)==null;

            require(
                cancelled&&
                participantsReleased,
                "challenged cancellation did not release participants"
            );

            try{
                duels.cancelOpen(
                    B
                );
            }catch(IllegalStateException expected){
                replayRejected=true;
            }

            replayRejected=
                replayRejected&&
                duels.openFor(A)==null&&
                duels.openFor(B)==null;

            require(
                replayRejected,
                "terminal Duel cancel replay mutated open state"
            );

            LocalLabTournamentRuntime tournament=
                world.localTournament();

            tournament.register(A);
            tournament.register(B);

            LocalLabTournamentRuntime.MatchStartResult match=
                tournament.activateAndStartMatch(
                    A,
                    B,
                    world.clock().tick()
                );

            tournamentAdmissionRestored=
                match.snapshot.match(
                    match.matchId
                )!=null&&
                match.snapshot.match(
                    match.matchId
                ).state==
                    TournamentService
                        .TournamentMatchState.ACTIVE;

            require(
                tournamentAdmissionRestored,
                "Tournament admission not restored after Duel cancellation"
            );

            System.out.println(
                "G108_NORMAL_DUEL_OPEN_CANCEL_PASS"+
                " proposalOpen="+proposalOpen+
                " challengerCancel="+challengerCancel+
                " challengedCancel="+challengedCancel+
                " cancelled="+cancelled+
                " participantsReleased="+participantsReleased+
                " reproposalAllowed="+reproposalAllowed+
                " tournamentAdmissionRestored="+tournamentAdmissionRestored+
                " replayRejected="+replayRejected+
                " cancelWidgetClaim=false"+
                " forfeitWinnerClaim=false"+
                " arenaClaim=false"+
                " stakeClaim=false"+
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

    private G108NormalDuelOpenCancelIntegrationTest(){}
}
