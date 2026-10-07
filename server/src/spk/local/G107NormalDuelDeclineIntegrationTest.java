package spk.local;

public final class G107NormalDuelDeclineIntegrationTest {
    private static final String A="g107-a";
    private static final String B="g107-b";

    public static void main(String[] args)throws Exception{
        boolean proposalOpen=false;
        boolean challengedOnlyDecline=false;
        boolean challengerCannotDecline=false;
        boolean declined=false;
        boolean participantsReleased=false;
        boolean reproposalAllowed=false;
        boolean tournamentAdmissionRestored=false;
        boolean replayRejected=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        world.registerPlayer(new WorldPlayer(),A);
        world.registerPlayer(new WorldPlayer(),B);

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
                "proposal did not open"
            );

            try{
                duels.decline(A);
            }catch(IllegalArgumentException expected){
                challengerCannotDecline=true;
            }

            challengedOnlyDecline=
                challengerCannotDecline&&
                duels.openFor(A)!=null&&
                duels.openFor(B)!=null;

            require(
                challengedOnlyDecline,
                "challenger decline did not fail closed"
            );

            DuelSessionService.Snapshot declinedSnapshot=
                duels.decline(B);

            declined=
                declinedSnapshot.state==
                    DuelSessionService.State.DECLINED;

            participantsReleased=
                duels.openFor(A)==null&&
                duels.openFor(B)==null;

            require(
                declined&&participantsReleased,
                "decline did not release participants"
            );

            LocalLabDuelRuntime.ProposalResult second=
                duels.propose(
                    A,
                    B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );

            reproposalAllowed=
                second.snapshot.state==
                    DuelSessionService.State.PROPOSED&&
                !second.snapshot.challengeId.equals(
                    first.snapshot.challengeId
                );

            require(
                reproposalAllowed,
                "fresh proposal blocked after decline"
            );

            duels.decline(B);

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
                "Tournament admission not restored after Duel decline"
            );

            try{
                duels.decline(B);
            }catch(IllegalStateException expected){
                replayRejected=true;
            }

            replayRejected=
                replayRejected&&
                duels.openFor(A)==null&&
                duels.openFor(B)==null;

            require(
                replayRejected,
                "decline replay mutated state"
            );

            System.out.println(
                "G107_NORMAL_DUEL_DECLINE_PASS"+
                " proposalOpen="+proposalOpen+
                " challengedOnlyDecline="+challengedOnlyDecline+
                " challengerCannotDecline="+challengerCannotDecline+
                " declined="+declined+
                " participantsReleased="+participantsReleased+
                " reproposalAllowed="+reproposalAllowed+
                " tournamentAdmissionRestored="+tournamentAdmissionRestored+
                " replayRejected="+replayRejected+
                " declineWidgetClaim=false"+
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
            throw new AssertionError(message);
    }

    private G107NormalDuelDeclineIntegrationTest(){}
}
