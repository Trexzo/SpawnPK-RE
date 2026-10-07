package spk.local;

public final class G104NormalDuelPvpDeathIntegrationTest {
    private static final String DIRECT_A="g104-direct-a";
    private static final String DIRECT_B="g104-direct-b";
    private static final String LIVE_A="g104-live-a";
    private static final String LIVE_B="g104-live-b";
    private static final String STALE_A="g104-stale-a";
    private static final String STALE_B="g104-stale-b";
    private static final String OUT_A="g104-out-a";
    private static final String OUT_B="g104-out-b";
    private static final String COLLIDE_A="g104-collide-a";
    private static final String COLLIDE_B="g104-collide-b";

    public static void main(String[] args)throws Exception{
        boolean activeDuel=false;
        boolean canonicalDeathCompletes=false;
        boolean attackerWins=false;
        boolean sessionCompleted=false;
        boolean instanceClosed=false;
        boolean participantsReleased=false;
        boolean precommitReplayIdempotent=false;
        boolean terminalDedupeRetired=false;
        boolean nonDuelIsolation=false;
        boolean staleAttackerCapturedIdentity=false;
        boolean competitiveCollisionFailClosed=false;
        boolean tournamentUnaffectedOnCollision=false;
        boolean duelUnaffectedOnCollision=false;
        boolean collisionDebtRetried=false;

        World directWorld=World.isolatedForTest(60_000L);
        WorldPlayer directA=new WorldPlayer();
        WorldPlayer directB=new WorldPlayer();
        directWorld.registerPlayer(directA,DIRECT_A);
        directWorld.registerPlayer(directB,DIRECT_B);

        try{
            LocalLabDuelRuntime runtime=directWorld.localDuels();
            LocalLabDuelRuntime.ProposalResult proposed=
                runtime.propose(
                    DIRECT_A,
                    DIRECT_B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );
            LocalLabDuelRuntime.StartResult started=
                runtime.acceptAndStart(DIRECT_B);

            activeDuel=
                started.snapshot.state==
                    DuelSessionService.State.ACTIVE&&
                runtime.claimsCanonicalPvpDeath(
                    DIRECT_A,
                    DIRECT_B,
                    1L
                );

            require(activeDuel,"direct Duel fixture not active");

            LocalLabDuelRuntime.PvpDeathResult first=
                runtime.recordCanonicalPvpDeath(
                    DIRECT_A,
                    DIRECT_B,
                    1L
                );
            LocalLabDuelRuntime.PvpDeathResult replay=
                runtime.recordCanonicalPvpDeath(
                    DIRECT_A,
                    DIRECT_B,
                    1L
                );

            precommitReplayIdempotent=
                first.duelMatch&&
                first.completedNow&&
                replay.duelMatch&&
                replay.challengeId.equals(
                    proposed.snapshot.challengeId
                )&&
                runtime.pvpDeathDedupeCount()==1&&
                runtime.duels()
                    .get(proposed.snapshot.challengeId)
                    .state==
                    DuelSessionService.State.COMPLETED;

            require(
                precommitReplayIdempotent,
                "pre-commit Duel death replay was not idempotent"
            );

            runtime.retireCanonicalPvpDeath(
                DIRECT_A,
                DIRECT_B,
                1L
            );

            require(
                runtime.pvpDeathDedupeCount()==0,
                "direct Duel death memo did not retire"
            );
        }finally{
            directWorld.close();
        }

        World liveWorld=World.isolatedForTest(60_000L);
        WorldPlayer liveAttacker=new WorldPlayer();
        WorldPlayer liveVictim=new WorldPlayer();
        long liveAttackerGeneration=
            liveWorld.registerPlayer(
                liveAttacker,
                LIVE_A
            );
        long liveVictimGeneration=
            liveWorld.registerPlayer(
                liveVictim,
                LIVE_B
            );

        try{
            LocalLabDuelRuntime runtime=
                liveWorld.localDuels();

            LocalLabDuelRuntime.ProposalResult proposal=
                runtime.propose(
                    LIVE_A,
                    LIVE_B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );
            LocalLabDuelRuntime.StartResult started=
                runtime.acceptAndStart(
                    LIVE_B
                );

            G95TournamentPvpDeathIntegrationTest.lethal(
                liveWorld,
                liveAttacker,
                liveAttackerGeneration,
                liveVictim,
                liveVictimGeneration,
                20L
            );

            G95TournamentPvpDeathIntegrationTest.SettlementHarness settlement=
                new G95TournamentPvpDeathIntegrationTest.SettlementHarness(
                    liveWorld,
                    liveVictim
                );

            settlement.coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g104-live] "
                );

            DuelSessionService.Snapshot completed=
                runtime.duels()
                    .get(
                        proposal.snapshot.challengeId
                    );
            MatchSession match=
                runtime.matches()
                    .get(
                        started.snapshot.matchId
                    );
            WorldInstanceService.Snapshot instance=
                runtime.instances()
                    .get(
                        started.snapshot.instanceId
                    );

            canonicalDeathCompletes=
                completed!=null&&
                completed.state==
                    DuelSessionService.State.COMPLETED;

            attackerWins=
                canonicalDeathCompletes&&
                match!=null&&
                match.result!=null&&
                match.result.hasWinner()&&
                completed.challengerTeamId.equals(
                    match.result.winnerTeamId
                )&&
                "canonical-pvp-death".equals(
                    match.result.outcomeKey
                )&&
                LocalLabDuelRuntime.PVP_DEATH_AUTHORITY
                    .equals(
                        match.result.decisionAuthority
                    );

            sessionCompleted=
                match!=null&&
                match.state==
                    MatchSession.State.COMPLETED;

            instanceClosed=
                instance!=null&&
                instance.lifecycle==
                    WorldInstanceService.Lifecycle.CLOSED;

            participantsReleased=
                runtime.openFor(LIVE_A)==null&&
                runtime.openFor(LIVE_B)==null;

            terminalDedupeRetired=
                runtime.pvpDeathDedupeCount()==0;

            require(
                canonicalDeathCompletes&&
                attackerWins&&
                sessionCompleted&&
                instanceClosed&&
                participantsReleased&&
                terminalDedupeRetired,
                "canonical Duel PvP settlement postimage failed"
            );

            settlement.coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g104-live-replay] "
                );

            terminalDedupeRetired=
                terminalDedupeRetired&&
                runtime.pvpDeathDedupeCount()==0&&
                runtime.duels()
                    .get(proposal.snapshot.challengeId)
                    .state==
                    DuelSessionService.State.COMPLETED;

            require(
                terminalDedupeRetired,
                "terminal Duel death replay recreated memo"
            );
        }finally{
            liveWorld.close();
        }

        World staleWorld=World.isolatedForTest(60_000L);
        WorldPlayer staleAttacker=new WorldPlayer();
        WorldPlayer staleVictim=new WorldPlayer();
        long staleAttackerGeneration=
            staleWorld.registerPlayer(
                staleAttacker,
                STALE_A
            );
        long staleVictimGeneration=
            staleWorld.registerPlayer(
                staleVictim,
                STALE_B
            );

        try{
            LocalLabDuelRuntime runtime=
                staleWorld.localDuels();

            LocalLabDuelRuntime.ProposalResult proposal=
                runtime.propose(
                    STALE_A,
                    STALE_B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );
            runtime.acceptAndStart(STALE_B);

            G95TournamentPvpDeathIntegrationTest.lethal(
                staleWorld,
                staleAttacker,
                staleAttackerGeneration,
                staleVictim,
                staleVictimGeneration,
                30L
            );

            require(
                staleWorld.unregisterPlayer(
                    staleAttacker,
                    staleAttackerGeneration
                ),
                "stale Duel attacker unregister fixture"
            );

            new G95TournamentPvpDeathIntegrationTest.SettlementHarness(
                staleWorld,
                staleVictim
            ).coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g104-stale] "
                );

            DuelSessionService.Snapshot completed=
                runtime.duels()
                    .get(
                        proposal.snapshot.challengeId
                    );
            MatchSession match=
                runtime.matches()
                    .get(
                        completed.matchId
                    );

            staleAttackerCapturedIdentity=
                completed.state==
                    DuelSessionService.State.COMPLETED&&
                match!=null&&
                match.result!=null&&
                completed.challengerTeamId.equals(
                    match.result.winnerTeamId
                )&&
                runtime.pvpDeathDedupeCount()==0;

            require(
                staleAttackerCapturedIdentity,
                "captured stale attacker identity did not settle Duel"
            );
        }finally{
            staleWorld.close();
        }

        World outsideWorld=World.isolatedForTest(60_000L);
        WorldPlayer outsideAttacker=new WorldPlayer();
        WorldPlayer outsideVictim=new WorldPlayer();
        long outsideAttackerGeneration=
            outsideWorld.registerPlayer(
                outsideAttacker,
                OUT_A
            );
        long outsideVictimGeneration=
            outsideWorld.registerPlayer(
                outsideVictim,
                OUT_B
            );

        try{
            G95TournamentPvpDeathIntegrationTest.lethal(
                outsideWorld,
                outsideAttacker,
                outsideAttackerGeneration,
                outsideVictim,
                outsideVictimGeneration,
                40L
            );

            new G95TournamentPvpDeathIntegrationTest.SettlementHarness(
                outsideWorld,
                outsideVictim
            ).coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g104-outside] "
                );

            LocalLabDuelRuntime runtime=
                outsideWorld.localDuels();

            nonDuelIsolation=
                runtime.size()==0&&
                runtime.pvpDeathDedupeCount()==0;

            require(
                nonDuelIsolation,
                "non-Duel canonical death mutated Duel runtime"
            );
        }finally{
            outsideWorld.close();
        }

        World collisionWorld=World.isolatedForTest(60_000L);
        WorldPlayer collisionAttacker=new WorldPlayer();
        WorldPlayer collisionVictim=new WorldPlayer();
        long collisionAttackerGeneration=
            collisionWorld.registerPlayer(
                collisionAttacker,
                COLLIDE_A
            );
        long collisionVictimGeneration=
            collisionWorld.registerPlayer(
                collisionVictim,
                COLLIDE_B
            );

        try{
            LocalLabTournamentRuntime tournament=
                collisionWorld.localTournament();
            tournament.register(COLLIDE_A);
            tournament.register(COLLIDE_B);
            LocalLabTournamentRuntime.MatchStartResult tournamentMatch=
                tournament.activateAndStartMatch(
                    COLLIDE_A,
                    COLLIDE_B,
                    collisionWorld.clock().tick()
                );

            LocalLabDuelRuntime duel=
                collisionWorld.localDuels();
            LocalLabDuelRuntime.ProposalResult duelProposal=
                duel.propose(
                    COLLIDE_A,
                    COLLIDE_B,
                    NormalDuelPresentation.DuelMode.STANDARD
                );
            LocalLabDuelRuntime.StartResult duelMatch=
                duel.acceptAndStart(
                    COLLIDE_B
                );

            G95TournamentPvpDeathIntegrationTest.lethal(
                collisionWorld,
                collisionAttacker,
                collisionAttackerGeneration,
                collisionVictim,
                collisionVictimGeneration,
                50L
            );

            G95TournamentPvpDeathIntegrationTest.SettlementHarness collision=
                new G95TournamentPvpDeathIntegrationTest.SettlementHarness(
                    collisionWorld,
                    collisionVictim
                );

            try{
                collision.coordinator
                    .settleCurrentDeathForSessionTeardown(
                        "[g104-collision] "
                    );
            }catch(IllegalStateException expected){
                competitiveCollisionFailClosed=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "multiple competitive owners"
                    );
            }

            TournamentService.MatchSnapshot tournamentAfterCollision=
                tournament.snapshot()
                    .match(
                        tournamentMatch.matchId
                    );
            DuelSessionService.Snapshot duelAfterCollision=
                duel.duels()
                    .get(
                        duelProposal.snapshot.challengeId
                    );

            tournamentUnaffectedOnCollision=
                tournamentAfterCollision!=null&&
                tournamentAfterCollision.state==
                    TournamentService
                        .TournamentMatchState.ACTIVE&&
                tournament.pvpDeathDedupeCount()==0;

            duelUnaffectedOnCollision=
                duelAfterCollision!=null&&
                duelAfterCollision.state==
                    DuelSessionService.State.ACTIVE&&
                duel.pvpDeathDedupeCount()==0&&
                duel.matches()
                    .get(duelMatch.snapshot.matchId)
                    .state==
                    MatchSession.State.ACTIVE&&
                duel.instances()
                    .get(duelMatch.snapshot.instanceId)
                    .lifecycle==
                    WorldInstanceService.Lifecycle.ACTIVE;

            require(
                competitiveCollisionFailClosed&&
                tournamentUnaffectedOnCollision&&
                duelUnaffectedOnCollision,
                "competitive collision mutated a domain before failing"
            );

            /*
             * Resolve the competing Duel explicitly, then retry the exact same
             * deferred death. The restored settlement debt must now allow the
             * remaining Tournament owner to settle canonically.
             */
            duel.duels()
                .cancelActive(
                    duelProposal.snapshot.challengeId,
                    "g104-collision-resolution"
                );

            collision.coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g104-collision-retry] "
                );

            collisionDebtRetried=
                tournament.snapshot()
                    .match(tournamentMatch.matchId)
                    .state==
                    TournamentService
                        .TournamentMatchState.COMPLETED&&
                COLLIDE_A.equals(
                    tournament.snapshot()
                        .match(tournamentMatch.matchId)
                        .winnerRef
                )&&
                tournament.pvpDeathDedupeCount()==0&&
                duel.pvpDeathDedupeCount()==0;

            require(
                collisionDebtRetried,
                "competitive collision lost deferred death settlement debt"
            );
        }finally{
            collisionWorld.close();
        }

        System.out.println(
            "G104_NORMAL_DUEL_PVP_DEATH_PASS"+
            " activeDuel="+activeDuel+
            " canonicalDeathCompletes="+canonicalDeathCompletes+
            " attackerWins="+attackerWins+
            " sessionCompleted="+sessionCompleted+
            " instanceClosed="+instanceClosed+
            " participantsReleased="+participantsReleased+
            " precommitReplayIdempotent="+precommitReplayIdempotent+
            " terminalDedupeRetired="+terminalDedupeRetired+
            " nonDuelIsolation="+nonDuelIsolation+
            " staleAttackerCapturedIdentity="+
                staleAttackerCapturedIdentity+
            " competitiveCollisionFailClosed="+
                competitiveCollisionFailClosed+
            " tournamentUnaffectedOnCollision="+
                tournamentUnaffectedOnCollision+
            " duelUnaffectedOnCollision="+
                duelUnaffectedOnCollision+
            " collisionDebtRetried="+collisionDebtRetried+
            " stakeClaim=false"+
            " restrictionClaim=false"+
            " arenaClaim=false"+
            " rewardClaim=false"+
            " persistenceClaim=false"+
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

    private G104NormalDuelPvpDeathIntegrationTest(){}
}
