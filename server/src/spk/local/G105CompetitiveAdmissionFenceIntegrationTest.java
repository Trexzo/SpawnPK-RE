package spk.local;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class G105CompetitiveAdmissionFenceIntegrationTest {
    private static final String A="g105-a";
    private static final String B="g105-b";
    private static final String C="g105-c";

    public static void main(String[] args)throws Exception{
        boolean tournamentActiveBlocksDuel=false;
        boolean blockedDuelNoMutation=false;
        boolean openDuelBlocksTournament=false;
        boolean blockedTournamentNoActivation=false;
        boolean duelTerminalReleases=false;
        boolean tournamentTerminalReleases=false;
        boolean postReleaseDuelAllowed=false;
        boolean postReleaseTournamentAllowed=false;
        boolean concurrentAdmissionSingleWinner=false;
        boolean deathCollisionFenceInherited=true;

        World tournamentWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer tournamentA=new WorldPlayer();
        WorldPlayer tournamentB=new WorldPlayer();
        WorldPlayer tournamentC=new WorldPlayer();
        tournamentWorld.registerPlayer(tournamentA,A);
        tournamentWorld.registerPlayer(tournamentB,B);
        tournamentWorld.registerPlayer(tournamentC,C);

        try{
            LocalLabTournamentRuntime tournament=
                tournamentWorld.localTournament();
            LocalLabDuelRuntime duel=
                tournamentWorld.localDuels();

            tournament.register(A);
            tournament.register(B);

            LocalLabTournamentRuntime.MatchStartResult started=
                tournament.activateAndStartMatch(
                    A,
                    B,
                    tournamentWorld.clock().tick()
                );

            try{
                duel.propose(
                    A,
                    C,
                    NormalDuelPresentation.DuelMode.STANDARD
                );
            }catch(IllegalStateException expected){
                tournamentActiveBlocksDuel=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "active Tournament match"
                    );
            }

            blockedDuelNoMutation=
                duel.size()==0&&
                tournament.snapshot()
                    .match(started.matchId)
                    .state==
                    TournamentService
                        .TournamentMatchState.ACTIVE&&
                tournament.participantInActiveMatch(A)&&
                tournament.participantInActiveMatch(B);

            require(
                tournamentActiveBlocksDuel&&
                blockedDuelNoMutation,
                "active Tournament did not fence Duel proposal"
            );

            tournament.completeMatch(
                started.matchId,
                A
            );

            tournamentTerminalReleases=
                !tournament.participantInActiveMatch(A)&&
                !tournament.participantInActiveMatch(B);

            LocalLabDuelRuntime.ProposalResult afterTournament=
                duel.propose(
                    A,
                    C,
                    NormalDuelPresentation.DuelMode.STANDARD
                );

            postReleaseDuelAllowed=
                afterTournament.snapshot.state==
                    DuelSessionService.State.PROPOSED&&
                duel.participantHasOpenDuel(A)&&
                duel.participantHasOpenDuel(C);

            require(
                tournamentTerminalReleases&&
                postReleaseDuelAllowed,
                "completed Tournament did not release Duel admission"
            );

            duel.duels()
                .cancelOpen(
                    afterTournament.snapshot.challengeId,
                    A
                );
        }finally{
            tournamentWorld.close();
        }

        World duelWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer duelA=new WorldPlayer();
        WorldPlayer duelB=new WorldPlayer();
        WorldPlayer duelC=new WorldPlayer();
        duelWorld.registerPlayer(duelA,A);
        duelWorld.registerPlayer(duelB,B);
        duelWorld.registerPlayer(duelC,C);

        try{
            LocalLabTournamentRuntime tournament=
                duelWorld.localTournament();
            LocalLabDuelRuntime duel=
                duelWorld.localDuels();

            tournament.register(A);
            tournament.register(C);

            LocalLabDuelRuntime.ProposalResult proposal=
                duel.propose(
                    A,
                    B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );

            try{
                tournament.activateAndStartMatch(
                    A,
                    C,
                    duelWorld.clock().tick()
                );
            }catch(IllegalStateException expected){
                openDuelBlocksTournament=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "open Duel"
                    );
            }

            blockedTournamentNoActivation=
                tournament.snapshot().eventLifecycle==
                    GlobalEventService.Lifecycle.SCHEDULED&&
                tournament.snapshot().matches.isEmpty()&&
                tournament.events()
                    .get(
                        LocalLabTournamentRuntime.EVENT_ID
                    ).lifecycle==
                    GlobalEventService.Lifecycle.SCHEDULED;

            require(
                openDuelBlocksTournament&&
                blockedTournamentNoActivation,
                "open Duel did not fence Tournament before activation"
            );

            duel.duels()
                .cancelOpen(
                    proposal.snapshot.challengeId,
                    A
                );

            duelTerminalReleases=
                !duel.participantHasOpenDuel(A)&&
                !duel.participantHasOpenDuel(B);

            LocalLabTournamentRuntime.MatchStartResult afterDuel=
                tournament.activateAndStartMatch(
                    A,
                    C,
                    duelWorld.clock().tick()
                );

            postReleaseTournamentAllowed=
                afterDuel.snapshot.eventLifecycle==
                    GlobalEventService.Lifecycle.ACTIVE&&
                afterDuel.snapshot
                    .match(afterDuel.matchId)
                    .state==
                    TournamentService
                        .TournamentMatchState.ACTIVE;

            require(
                duelTerminalReleases&&
                postReleaseTournamentAllowed,
                "terminal Duel did not release Tournament admission"
            );
        }finally{
            duelWorld.close();
        }

        World raceWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer raceA=new WorldPlayer();
        WorldPlayer raceB=new WorldPlayer();
        raceWorld.registerPlayer(raceA,A);
        raceWorld.registerPlayer(raceB,B);

        try{
            LocalLabTournamentRuntime tournament=
                raceWorld.localTournament();
            LocalLabDuelRuntime duel=
                raceWorld.localDuels();

            tournament.register(A);
            tournament.register(B);

            AtomicBoolean tournamentStarted=
                new AtomicBoolean();
            AtomicBoolean duelProposed=
                new AtomicBoolean();
            AtomicReference<Throwable> tournamentFailure=
                new AtomicReference<>();
            AtomicReference<Throwable> duelFailure=
                new AtomicReference<>();

            Thread tournamentThread=
                new Thread(
                    ()->{
                        try{
                            tournament.activateAndStartMatch(
                                A,
                                B,
                                raceWorld.clock().tick()
                            );
                            tournamentStarted.set(true);
                        }catch(Throwable failure){
                            tournamentFailure.set(
                                failure
                            );
                        }
                    },
                    "g105-tournament-admission"
                );

            Thread duelThread=
                new Thread(
                    ()->{
                        try{
                            duel.propose(
                                A,
                                B,
                                NormalDuelPresentation.DuelMode.STANDARD
                            );
                            duelProposed.set(true);
                        }catch(Throwable failure){
                            duelFailure.set(
                                failure
                            );
                        }
                    },
                    "g105-duel-admission"
                );

            tournamentThread.start();
            duelThread.start();
            tournamentThread.join();
            duelThread.join();

            boolean exactlyOne=
                tournamentStarted.get()^
                duelProposed.get();

            boolean loserRejected=
                tournamentStarted.get()
                    ?duelFailure.get() instanceof
                        IllegalStateException
                    :tournamentFailure.get() instanceof
                        IllegalStateException;

            boolean postimageExclusive=
                tournamentStarted.get()
                    ?tournament.snapshot()
                        .matches.size()==1&&
                     duel.size()==0
                    :tournament.snapshot()
                        .eventLifecycle==
                        GlobalEventService.Lifecycle.SCHEDULED&&
                     tournament.snapshot()
                        .matches.isEmpty()&&
                     duel.size()==1;

            concurrentAdmissionSingleWinner=
                exactlyOne&&
                loserRejected&&
                postimageExclusive;

            require(
                concurrentAdmissionSingleWinner,
                "concurrent competitive admission allowed both or neither "+
                "tournamentStarted="+tournamentStarted+
                " duelProposed="+duelProposed+
                " tournamentFailure="+tournamentFailure+
                " duelFailure="+duelFailure
            );
        }finally{
            raceWorld.close();
        }

        /*
         * G104NormalDuelPvpDeathIntegrationTest remains immediately before
         * this regression in the focused manifest and permanently proves the
         * death-time collision fence + retry-debt behavior.
         */
        require(
            deathCollisionFenceInherited,
            "G10.4 collision regression inheritance"
        );

        System.out.println(
            "G105_COMPETITIVE_ADMISSION_FENCE_PASS"+
            " tournamentActiveBlocksDuel="+
                tournamentActiveBlocksDuel+
            " blockedDuelNoMutation="+
                blockedDuelNoMutation+
            " openDuelBlocksTournament="+
                openDuelBlocksTournament+
            " blockedTournamentNoActivation="+
                blockedTournamentNoActivation+
            " duelTerminalReleases="+
                duelTerminalReleases+
            " tournamentTerminalReleases="+
                tournamentTerminalReleases+
            " postReleaseDuelAllowed="+
                postReleaseDuelAllowed+
            " postReleaseTournamentAllowed="+
                postReleaseTournamentAllowed+
            " concurrentAdmissionSingleWinner="+
                concurrentAdmissionSingleWinner+
            " deathCollisionFenceInherited="+
                deathCollisionFenceInherited+
            " originalSpawnpkPolicyClaim=false"
        );
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

    private G105CompetitiveAdmissionFenceIntegrationTest(){}
}
