package spk.local;

public final class G92TournamentMatchStartIntegrationTest {
    private static final String FIRST="g92-first";
    private static final String SECOND="g92-second";

    public static void main(String[] args)throws Exception{
        boolean registrationStartsScheduled=false;
        boolean explicitActivation=false;
        boolean callerPairing=false;
        boolean distinctEntrants=false;
        boolean matchActive=false;
        boolean instanceActive=false;
        boolean entrantsInMatch=false;
        boolean duplicateInMatchRejected=false;
        boolean oneMatchOneInstance=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer first=
            new WorldPlayer();
        WorldPlayer second=
            new WorldPlayer();

        world.registerPlayer(
            first,
            FIRST
        );
        world.registerPlayer(
            second,
            SECOND
        );

        try{
            LocalLabTournamentRuntime runtime=
                world.localTournament();

            LocalLabTournamentRuntime.RegistrationResult
                firstRegistration=
                    runtime.register(
                        FIRST
                    );
            LocalLabTournamentRuntime.RegistrationResult
                secondRegistration=
                    runtime.register(
                        SECOND
                    );

            registrationStartsScheduled=
                firstRegistration.created&&
                secondRegistration.created&&
                runtime.snapshot().eventLifecycle==
                    GlobalEventService.Lifecycle.SCHEDULED&&
                runtime.matches().size()==0&&
                runtime.instances().size()==0;

            require(
                registrationStartsScheduled,
                "registrations did not remain SCHEDULED before explicit activation"
            );

            try{
                runtime.activateAndStartMatch(
                    FIRST,
                    FIRST,
                    world.clock().tick()
                );
            }catch(IllegalArgumentException expected){
                distinctEntrants=true;
            }

            require(
                distinctEntrants&&
                runtime.snapshot().eventLifecycle==
                    GlobalEventService.Lifecycle.SCHEDULED&&
                runtime.matches().size()==0&&
                runtime.instances().size()==0,
                "same-participant rejection mutated Tournament state"
            );

            LocalLabTournamentRuntime.MatchStartResult
                started=
                    runtime.activateAndStartMatch(
                        FIRST,
                        SECOND,
                        world.clock().tick()
                    );

            TournamentService.Snapshot snapshot=
                started.snapshot;
            TournamentService.MatchSnapshot match=
                snapshot.match(
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

            explicitActivation=
                snapshot.eventLifecycle==
                    GlobalEventService.Lifecycle.ACTIVE&&
                runtime.events()
                    .get(
                        LocalLabTournamentRuntime.EVENT_ID
                    ).lifecycle==
                    GlobalEventService.Lifecycle.ACTIVE;

            callerPairing=
                match!=null&&
                FIRST.equals(
                    match.firstParticipant
                )&&
                SECOND.equals(
                    match.secondParticipant
                );

            matchActive=
                match!=null&&
                match.state==
                    TournamentService
                        .TournamentMatchState.ACTIVE&&
                session!=null&&
                session.state==
                    MatchSession.State.ACTIVE&&
                started.instanceId.equals(
                    session.instanceId
                )&&
                session.participant(FIRST)!=null&&
                session.participant(SECOND)!=null;

            instanceActive=
                instance!=null&&
                instance.lifecycle==
                    WorldInstanceService.Lifecycle.ACTIVE&&
                instance.participant(FIRST)&&
                instance.participant(SECOND);

            TournamentService.EntrantSnapshot firstEntrant=
                snapshot.entrant(FIRST);
            TournamentService.EntrantSnapshot secondEntrant=
                snapshot.entrant(SECOND);

            entrantsInMatch=
                firstEntrant!=null&&
                secondEntrant!=null&&
                firstEntrant.state==
                    TournamentService.EntrantState.IN_MATCH&&
                secondEntrant.state==
                    TournamentService.EntrantState.IN_MATCH&&
                started.matchId.equals(
                    firstEntrant.activeMatchId
                )&&
                started.matchId.equals(
                    secondEntrant.activeMatchId
                );

            oneMatchOneInstance=
                snapshot.matches.size()==1&&
                runtime.matches().size()==1&&
                runtime.instances().size()==1;

            require(
                explicitActivation&&
                callerPairing&&
                matchActive&&
                instanceActive&&
                entrantsInMatch&&
                oneMatchOneInstance,
                "explicit caller-paired Tournament match composition failed"
            );

            try{
                runtime.activateAndStartMatch(
                    FIRST,
                    SECOND,
                    world.clock().tick()
                );
            }catch(IllegalStateException expected){
                duplicateInMatchRejected=true;
            }

            duplicateInMatchRejected=
                duplicateInMatchRejected&&
                runtime.snapshot().matches.size()==1&&
                runtime.matches().size()==1&&
                runtime.instances().size()==1;

            require(
                duplicateInMatchRejected,
                "IN_MATCH entrant created duplicate Tournament child state"
            );

            System.out.println(
                "G92_TOURNAMENT_MATCH_START_PASS"+
                " registrationStartsScheduled="+
                    registrationStartsScheduled+
                " explicitActivation="+
                    explicitActivation+
                " callerPairing="+callerPairing+
                " distinctEntrants="+distinctEntrants+
                " matchActive="+matchActive+
                " instanceActive="+instanceActive+
                " entrantsInMatch="+entrantsInMatch+
                " duplicateInMatchRejected="+
                    duplicateInMatchRejected+
                " oneMatchOneInstance="+
                    oneMatchOneInstance+
                " autoPairClaim=false"+
                " arenaClaim=false"+
                " winnerClaim=false"+
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

    private G92TournamentMatchStartIntegrationTest(){}
}
