package spk.local;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

public final class TournamentServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_SINGLE_ELIMINATION";

    public static void main(String[] args)throws Exception{
        tournamentLifecycle();
        globalEventOwnershipLinearized();
        activeChildTerminalHold();
        cancellationLifecycle();
        authorityGuards();
        protocolBoundary();

        System.out.println(
            "TOURNAMENT_SERVICE_PASS "+
            "globalEventComposition=true "+
            "scheduledRegistrationOnly=true "+
            "callerPairing=true "+
            "twoOnePlayerTeams=true "+
            "worldInstanceComposition=true "+
            "winnerReeligible=true "+
            "loserEliminated=true "+
            "cancelledMatchRestoresEntrants=true "+
            "eliminatedReentryRejected=true "+
            "activeMatchBlocksTerminalEvent=true "+
            "globalEventOwnershipLinearized=true "+
            "activeChildTerminalHold=true "+
            "deadlineTerminalHold=true "+
            "childCompletionReleasesHold=true "+
            "childCancellationReleasesHold=true "+
            "failedStartupNoHold=true "+
            "failedChildTerminalRetainsHold=true "+
            "lockOrderTournamentEventMatchInstance=true "+
            "explicitTournamentCompletion=true "+
            "singleEliminationPolicyExplicit=true "+
            "prizeMutation=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void tournamentLifecycle(){
        GlobalEventService events=
            new GlobalEventService();
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();

        TournamentService service=
            new TournamentService(
                events,
                matches,
                instances
            );

        WorldEventId eventId=
            WorldEventId.of(
                "tournament:world:1"
            );

        TournamentService.Snapshot created=
            service.registerTournament(
                eventDefinition(
                    eventId,
                    100L,
                    200L
                ),
                localRules(),
                POLICY
            );

        require(
            created.eventLifecycle==
                GlobalEventService
                    .Lifecycle.SCHEDULED&&
            POLICY.equals(
                created.policyAuthority
            ),
            "Tournament registration"
        );

        service.registerEntrant(
            eventId,
            "player:a"
        );
        service.registerEntrant(
            eventId,
            "player:b"
        );
        service.registerEntrant(
            eventId,
            "player:c"
        );

        expect(
            IllegalStateException.class,
            ()->service.registerEntrant(
                eventId,
                "player:a"
            ),
            "duplicate tournament entrant"
        );

        expect(
            IllegalStateException.class,
            ()->service.startMatch(
                eventId,
                "player:a",
                "player:b",
                MatchId.of(
                    "match:tournament:early"
                ),
                WorldInstanceId.of(
                    "instance:tournament:early"
                )
            ),
            "scheduled tournament match"
        );

        require(
            matches.size()==0&&
            instances.size()==0,
            "scheduled match attempt mutated runtime"
        );

        events.tick(100L);

        expect(
            IllegalStateException.class,
            ()->service.registerEntrant(
                eventId,
                "player:d"
            ),
            "active tournament registration"
        );

        MatchId firstMatchId=
            MatchId.of(
                "match:tournament:1"
            );
        WorldInstanceId firstInstanceId=
            WorldInstanceId.of(
                "instance:tournament:1"
            );

        TournamentService.Snapshot active=
            service.startMatch(
                eventId,
                "player:a",
                "player:b",
                firstMatchId,
                firstInstanceId
            );

        TournamentService.MatchSnapshot
            activeMatch=
                active.match(
                    firstMatchId
                );

        MatchSession match=
            matches.get(
                firstMatchId
            );
        WorldInstanceService.Snapshot instance=
            instances.get(
                firstInstanceId
            );

        require(
            activeMatch!=null&&
            activeMatch.state==
                TournamentService
                    .TournamentMatchState
                    .ACTIVE&&
            match.state==
                MatchSession.State.ACTIVE&&
            match.teams.size()==2&&
            match.team(
                activeMatch.firstTeamId
            ).members.equals(
                Collections.singletonList(
                    "player:a"
                )
            )&&
            match.team(
                activeMatch.secondTeamId
            ).members.equals(
                Collections.singletonList(
                    "player:b"
                )
            )&&
            instance.lifecycle==
                WorldInstanceService
                    .Lifecycle.ACTIVE&&
            new LinkedHashSet<>(
                instance.participants
            ).equals(
                new LinkedHashSet<>(
                    Arrays.asList(
                        "player:a",
                        "player:b"
                    )
                )
            ),
            "Tournament active match composition"
        );

        expect(
            IllegalStateException.class,
            ()->service.completeTournament(
                eventId,
                110L
            ),
            "active match allowed tournament completion"
        );

        require(
            events.get(
                eventId
            ).lifecycle==
                GlobalEventService
                    .Lifecycle.ACTIVE,
            "blocked completion mutated GlobalEvent"
        );

        TournamentService.Snapshot resolved=
            service.completeMatch(
                eventId,
                firstMatchId,
                "player:a",
                "caller_resolved",
                POLICY
            );

        require(
            resolved.entrant(
                "player:a"
            ).state==
                TournamentService
                    .EntrantState.REGISTERED&&
            resolved.entrant(
                "player:b"
            ).state==
                TournamentService
                    .EntrantState.ELIMINATED&&
            resolved.match(
                firstMatchId
            ).winnerRef.equals(
                "player:a"
            )&&
            instances.get(
                firstInstanceId
            ).lifecycle==
                WorldInstanceService
                    .Lifecycle.CLOSED,
            "Tournament match resolution"
        );

        expect(
            IllegalStateException.class,
            ()->service.startMatch(
                eventId,
                "player:b",
                "player:c",
                MatchId.of(
                    "match:tournament:eliminated"
                ),
                WorldInstanceId.of(
                    "instance:tournament:eliminated"
                )
            ),
            "eliminated entrant re-entered"
        );

        MatchId cancelledMatchId=
            MatchId.of(
                "match:tournament:cancelled"
            );

        service.startMatch(
            eventId,
            "player:a",
            "player:c",
            cancelledMatchId,
            WorldInstanceId.of(
                "instance:tournament:cancelled"
            )
        );

        TournamentService.Snapshot
            cancelledMatch=
                service.cancelMatch(
                    eventId,
                    cancelledMatchId,
                    "caller_cancelled"
                );

        require(
            cancelledMatch.entrant(
                "player:a"
            ).state==
                TournamentService
                    .EntrantState.REGISTERED&&
            cancelledMatch.entrant(
                "player:c"
            ).state==
                TournamentService
                    .EntrantState.REGISTERED,
            "cancelled tournament match entrant restore"
        );

        MatchId finalMatchId=
            MatchId.of(
                "match:tournament:final"
            );

        service.startMatch(
            eventId,
            "player:a",
            "player:c",
            finalMatchId,
            WorldInstanceId.of(
                "instance:tournament:final"
            )
        );

        service.completeMatch(
            eventId,
            finalMatchId,
            "player:a",
            "caller_resolved",
            POLICY
        );

        TournamentService.Snapshot completed=
            service.completeTournament(
                eventId,
                150L
            );

        require(
            completed.eventLifecycle==
                GlobalEventService
                    .Lifecycle.COMPLETED&&
            completed.entrant(
                "player:a"
            ).state==
                TournamentService
                    .EntrantState.REGISTERED&&
            completed.entrant(
                "player:c"
            ).state==
                TournamentService
                    .EntrantState.ELIMINATED,
            "Tournament explicit completion"
        );

        expect(
            IllegalStateException.class,
            ()->service.registerEntrant(
                eventId,
                "player:z"
            ),
            "terminal tournament registration"
        );
    }

    private static void globalEventOwnershipLinearized()
        throws Exception{
        registrationCannotCrossLifecycleTransition();
        matchStartupCannotCrossTerminalTransition();
    }

    private static void registrationCannotCrossLifecycleTransition()
        throws Exception{
        GlobalEventService events=
            new GlobalEventService();
        TournamentService service=
            new TournamentService(
                events,
                new MatchSessionService(),
                new WorldInstanceService()
            );

        WorldEventId eventId=
            WorldEventId.of(
                "tournament:owned-registration"
            );

        service.registerTournament(
            eventDefinition(
                eventId,
                10L,
                100L
            ),
            localRules(),
            POLICY
        );

        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch eventOwned=
            new CountDownLatch(1);
        CountDownLatch allowTransition=
            new CountDownLatch(1);

        try{
            Future<?> transition=
                workers.submit(
                    ()->{
                        events.withEventCompositionOwnership(
                            eventId,
                            ()->{
                                eventOwned.countDown();

                                if(!allowTransition.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "registration transition release timeout"
                                    );

                                events.tick(10L);
                            }
                        );

                        return null;
                    }
                );

            require(
                eventOwned.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "registration GlobalEvent owner did not enter"
            );

            Future<TournamentService.Snapshot> registration=
                workers.submit(
                    ()->service.registerEntrant(
                        eventId,
                        "player:blocked"
                    )
                );

            try{
                registration.get(
                    200L,
                    TimeUnit.MILLISECONDS
                );
                throw new AssertionError(
                    "tournament registration crossed GlobalEvent ownership"
                );
            }catch(TimeoutException expected){
                // Expected: TournamentService monitor is held while waiting
                // for the backing GlobalEvent ownership boundary.
            }

            allowTransition.countDown();

            transition.get(
                5L,
                TimeUnit.SECONDS
            );

            try{
                registration.get(
                    5L,
                    TimeUnit.SECONDS
                );
                throw new AssertionError(
                    "ACTIVE transition allowed stale registration"
                );
            }catch(ExecutionException failure){
                require(
                    failure.getCause() instanceof
                        IllegalStateException,
                    "blocked registration wrong failure "+
                    failure.getCause()
                );
            }

            TournamentService.Snapshot after=
                service.get(eventId);

            require(
                after.eventLifecycle==
                    GlobalEventService
                        .Lifecycle.ACTIVE&&
                after.entrant(
                    "player:blocked"
                )==null,
                "lifecycle transition crossed tournament registration"
            );
        }finally{
            allowTransition.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
        }
    }

    private static void matchStartupCannotCrossTerminalTransition()
        throws Exception{
        GlobalEventService events=
            new GlobalEventService();
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();
        TournamentService service=
            new TournamentService(
                events,
                matches,
                instances
            );

        WorldEventId eventId=
            WorldEventId.of(
                "tournament:owned-start"
            );

        service.registerTournament(
            eventDefinition(
                eventId,
                10L,
                100L
            ),
            localRules(),
            POLICY
        );
        service.registerEntrant(
            eventId,
            "player:a"
        );
        service.registerEntrant(
            eventId,
            "player:b"
        );
        events.tick(10L);

        MatchId matchId=
            MatchId.of(
                "match:tournament:owned"
            );
        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "instance:tournament:owned"
            );

        ExecutorService workers=
            Executors.newFixedThreadPool(2);
        CountDownLatch eventOwned=
            new CountDownLatch(1);
        CountDownLatch allowTerminal=
            new CountDownLatch(1);

        try{
            Future<?> terminal=
                workers.submit(
                    ()->{
                        events.withEventCompositionOwnership(
                            eventId,
                            ()->{
                                eventOwned.countDown();

                                if(!allowTerminal.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "match terminal release timeout"
                                    );

                                events.complete(
                                    eventId,
                                    20L
                                );
                            }
                        );

                        return null;
                    }
                );

            require(
                eventOwned.await(
                    5L,
                    TimeUnit.SECONDS
                ),
                "match GlobalEvent owner did not enter"
            );

            Future<TournamentService.Snapshot> startup=
                workers.submit(
                    ()->service.startMatch(
                        eventId,
                        "player:a",
                        "player:b",
                        matchId,
                        instanceId
                    )
                );

            try{
                startup.get(
                    200L,
                    TimeUnit.MILLISECONDS
                );
                throw new AssertionError(
                    "Tournament startup crossed GlobalEvent ownership"
                );
            }catch(TimeoutException expected){
                // Expected. GlobalEvent ownership precedes reusable match /
                // instance composition.
            }

            require(
                matches.size()==0&&
                instances.size()==0,
                "match/instance mutated before GlobalEvent ownership"
            );

            allowTerminal.countDown();

            terminal.get(
                5L,
                TimeUnit.SECONDS
            );

            try{
                startup.get(
                    5L,
                    TimeUnit.SECONDS
                );
                throw new AssertionError(
                    "terminal event allowed stale Tournament startup"
                );
            }catch(ExecutionException failure){
                require(
                    failure.getCause() instanceof
                        IllegalStateException,
                    "blocked tournament startup wrong failure "+
                    failure.getCause()
                );
            }

            TournamentService.Snapshot after=
                service.get(eventId);

            require(
                after.eventLifecycle==
                    GlobalEventService
                        .Lifecycle.COMPLETED&&
                after.entrant(
                    "player:a"
                ).state==
                    TournamentService
                        .EntrantState.REGISTERED&&
                after.entrant(
                    "player:b"
                ).state==
                    TournamentService
                        .EntrantState.REGISTERED&&
                after.match(matchId)==null&&
                matches.size()==0&&
                instances.size()==0,
                "terminal transition crossed Tournament startup composition"
            );
        }finally{
            allowTerminal.countDown();
            workers.shutdownNow();
            workers.awaitTermination(
                5L,
                TimeUnit.SECONDS
            );
        }
    }


    private static void activeChildTerminalHold(){
        completionTerminalHoldLifecycle();
        cancellationTerminalHoldLifecycle();
        failedStartupCreatesNoHold();
        failedChildTerminalizationRetainsHold();
    }

    private static void completionTerminalHoldLifecycle(){
        GlobalEventService events=
            new GlobalEventService();
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();
        TournamentService service=
            new TournamentService(
                events,
                matches,
                instances
            );

        WorldEventId eventId=
            WorldEventId.of(
                "tournament:terminal-hold-complete"
            );
        MatchId matchId=
            MatchId.of(
                "match:tournament:terminal-hold-complete"
            );
        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "instance:tournament:terminal-hold-complete"
            );

        service.registerTournament(
            eventDefinition(
                eventId,
                10L,
                30L
            ),
            localRules(),
            POLICY
        );
        service.registerEntrant(
            eventId,
            "player:a"
        );
        service.registerEntrant(
            eventId,
            "player:b"
        );
        events.tick(10L);

        service.startMatch(
            eventId,
            "player:a",
            "player:b",
            matchId,
            instanceId
        );

        expect(
            IllegalStateException.class,
            ()->events.complete(
                eventId,
                20L
            ),
            "active child allowed direct event completion"
        );
        expect(
            IllegalStateException.class,
            ()->events.cancel(
                eventId,
                20L
            ),
            "active child allowed direct event cancellation"
        );

        events.tick(30L);

        TournamentService.Snapshot blocked=
            service.get(eventId);

        require(
            blocked.eventLifecycle==
                GlobalEventService
                    .Lifecycle.ACTIVE&&
            blocked.match(matchId).state==
                TournamentService
                    .TournamentMatchState.ACTIVE&&
            matches.get(matchId).state==
                MatchSession.State.ACTIVE&&
            instances.get(instanceId).lifecycle==
                WorldInstanceService.Lifecycle.ACTIVE,
            "deadline crossed active Tournament child hold"
        );

        service.completeMatch(
            eventId,
            matchId,
            "player:a",
            "caller_resolved",
            POLICY
        );

        events.tick(30L);

        TournamentService.Snapshot completed=
            service.get(eventId);

        require(
            completed.eventLifecycle==
                GlobalEventService
                    .Lifecycle.COMPLETED&&
            completed.match(matchId).state==
                TournamentService
                    .TournamentMatchState.COMPLETED&&
            matches.get(matchId).state==
                MatchSession.State.COMPLETED&&
            instances.get(instanceId).lifecycle==
                WorldInstanceService.Lifecycle.CLOSED,
            "child completion did not release terminal hold cleanly"
        );
    }

    private static void cancellationTerminalHoldLifecycle(){
        GlobalEventService events=
            new GlobalEventService();
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();
        TournamentService service=
            new TournamentService(
                events,
                matches,
                instances
            );

        WorldEventId eventId=
            WorldEventId.of(
                "tournament:terminal-hold-cancel"
            );
        MatchId matchId=
            MatchId.of(
                "match:tournament:terminal-hold-cancel"
            );
        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "instance:tournament:terminal-hold-cancel"
            );

        service.registerTournament(
            eventDefinition(
                eventId,
                10L,
                30L
            ),
            localRules(),
            POLICY
        );
        service.registerEntrant(
            eventId,
            "player:c"
        );
        service.registerEntrant(
            eventId,
            "player:d"
        );
        events.tick(10L);

        service.startMatch(
            eventId,
            "player:c",
            "player:d",
            matchId,
            instanceId
        );

        events.tick(30L);

        require(
            events.get(eventId).lifecycle==
                GlobalEventService
                    .Lifecycle.ACTIVE,
            "cancellation fixture deadline crossed child hold"
        );

        service.cancelMatch(
            eventId,
            matchId,
            "caller_cancelled"
        );

        events.cancel(
            eventId,
            30L
        );

        TournamentService.Snapshot cancelled=
            service.get(eventId);

        require(
            cancelled.eventLifecycle==
                GlobalEventService
                    .Lifecycle.CANCELLED&&
            cancelled.match(matchId).state==
                TournamentService
                    .TournamentMatchState.CANCELLED&&
            instances.get(instanceId).lifecycle==
                WorldInstanceService.Lifecycle.CLOSED,
            "child cancellation did not release terminal hold"
        );
    }

    private static void failedStartupCreatesNoHold(){
        GlobalEventService events=
            new GlobalEventService();
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();
        TournamentService service=
            new TournamentService(
                events,
                matches,
                instances
            );

        WorldEventId eventId=
            WorldEventId.of(
                "tournament:terminal-hold-start-failure"
            );
        MatchId duplicateMatchId=
            MatchId.of(
                "match:tournament:terminal-hold-duplicate"
            );

        service.registerTournament(
            eventDefinition(
                eventId,
                10L,
                40L
            ),
            localRules(),
            POLICY
        );
        service.registerEntrant(
            eventId,
            "player:e"
        );
        service.registerEntrant(
            eventId,
            "player:f"
        );
        events.tick(10L);

        matches.create(
            duplicateMatchId,
            localRules()
        );

        expect(
            IllegalStateException.class,
            ()->service.startMatch(
                eventId,
                "player:e",
                "player:f",
                duplicateMatchId,
                WorldInstanceId.of(
                    "instance:tournament:terminal-hold-start-failure"
                )
            ),
            "failed startup fixture"
        );

        events.complete(
            eventId,
            20L
        );

        require(
            events.get(eventId).lifecycle==
                GlobalEventService
                    .Lifecycle.COMPLETED,
            "failed Tournament startup leaked terminal hold"
        );
    }

    private static void failedChildTerminalizationRetainsHold(){
        GlobalEventService events=
            new GlobalEventService();
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();
        TournamentService service=
            new TournamentService(
                events,
                matches,
                instances
            );

        WorldEventId eventId=
            WorldEventId.of(
                "tournament:terminal-hold-terminal-failure"
            );
        MatchId matchId=
            MatchId.of(
                "match:tournament:terminal-hold-terminal-failure"
            );
        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "instance:tournament:terminal-hold-terminal-failure"
            );

        service.registerTournament(
            eventDefinition(
                eventId,
                10L,
                40L
            ),
            localRules(),
            POLICY
        );
        service.registerEntrant(
            eventId,
            "player:g"
        );
        service.registerEntrant(
            eventId,
            "player:h"
        );
        events.tick(10L);

        service.startMatch(
            eventId,
            "player:g",
            "player:h",
            matchId,
            instanceId
        );

        instances.beginClosing(
            instanceId
        );
        instances.detach(
            instanceId,
            "player:g"
        );
        instances.detach(
            instanceId,
            "player:h"
        );
        instances.close(
            instanceId
        );

        expect(
            IllegalStateException.class,
            ()->service.completeMatch(
                eventId,
                matchId,
                "player:g",
                "caller_resolved",
                POLICY
            ),
            "failed child terminalization fixture"
        );

        expect(
            IllegalStateException.class,
            ()->events.complete(
                eventId,
                20L
            ),
            "failed child terminalization prematurely released hold"
        );

        TournamentService.Snapshot after=
            service.get(eventId);

        require(
            after.eventLifecycle==
                GlobalEventService
                    .Lifecycle.ACTIVE&&
            after.match(matchId).state==
                TournamentService
                    .TournamentMatchState.ACTIVE&&
            matches.get(matchId).state==
                MatchSession.State.ACTIVE,
            "failed child terminalization mutated Tournament child state"
        );
    }


    private static void cancellationLifecycle(){
        GlobalEventService events=
            new GlobalEventService();
        TournamentService service=
            new TournamentService(
                events,
                new MatchSessionService(),
                new WorldInstanceService()
            );

        WorldEventId eventId=
            WorldEventId.of(
                "tournament:cancel"
            );

        service.registerTournament(
            eventDefinition(
                eventId,
                10L,
                50L
            ),
            localRules(),
            POLICY
        );

        service.registerEntrant(
            eventId,
            "player:x"
        );
        service.registerEntrant(
            eventId,
            "player:y"
        );

        TournamentService.Snapshot cancelled=
            service.cancelTournament(
                eventId,
                5L
            );

        require(
            cancelled.eventLifecycle==
                GlobalEventService
                    .Lifecycle.CANCELLED&&
            cancelled.entrant(
                "player:x"
            ).state==
                TournamentService
                    .EntrantState.WITHDRAWN&&
            cancelled.entrant(
                "player:y"
            ).state==
                TournamentService
                    .EntrantState.WITHDRAWN,
            "Tournament cancellation"
        );
    }

    private static void authorityGuards(){
        GlobalEventService events=
            new GlobalEventService();
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();

        TournamentService service=
            new TournamentService(
                events,
                matches,
                instances
            );

        WorldEventId wrongRulesId=
            WorldEventId.of(
                "tournament:wrong-rules"
            );

        MatchRules wrongRules=
            new MatchRules(
                MatchRules.TeamMode.FREE_FOR_ALL,
                MatchRules.SpellPolicy.UNRESTRICTED,
                MatchRules.PrayerPolicy.UNRESTRICTED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.WinConditionKind.CALLER_RESOLVED,
                MatchRules.NO_SCORE_TARGET,
                "tournament",
                POLICY
            );

        expect(
            IllegalArgumentException.class,
            ()->service.registerTournament(
                eventDefinition(
                    wrongRulesId,
                    10L,
                    20L
                ),
                wrongRules,
                POLICY
            ),
            "non-TEAMS tournament rules"
        );

        require(
            events.size()==0&&
            service.size()==0,
            "bad tournament rules mutated state"
        );

        WorldEventId wrongAuthorityId=
            WorldEventId.of(
                "tournament:wrong-authority"
            );

        expect(
            IllegalArgumentException.class,
            ()->service.registerTournament(
                new WorldEventDefinition(
                    wrongAuthorityId,
                    10L,
                    20L,
                    Collections.emptyList(),
                    "EXACT_CURRENT_CLIENT"
                ),
                localRules(),
                POLICY
            ),
            "tournament event authority mismatch"
        );

        require(
            events.size()==0&&
            service.size()==0,
            "authority mismatch mutated state"
        );
    }

    private static WorldEventDefinition
        eventDefinition(
            WorldEventId id,
            long startTick,
            long endTick
        ){
        return new WorldEventDefinition(
            id,
            startTick,
            endTick,
            Collections.singletonList(
                new WorldEventDefinition
                    .PhaseDefinition(
                        "active",
                        startTick
                    )
            ),
            POLICY
        );
    }

    private static MatchRules localRules(){
        return new MatchRules(
            MatchRules.TeamMode.TEAMS,
            MatchRules.SpellPolicy.UNRESTRICTED,
            MatchRules.PrayerPolicy.UNRESTRICTED,
            MatchRules.RestrictionPolicy.ALLOWED,
            MatchRules.RestrictionPolicy.ALLOWED,
            MatchRules.WinConditionKind.CALLER_RESOLVED,
            MatchRules.NO_SCORE_TARGET,
            "world_tournament",
            POLICY
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                TournamentService.class,
                TournamentService.Snapshot.class,
                TournamentService.EntrantSnapshot.class,
                TournamentService.MatchSnapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("interface")||
                   name.contains("reward")||
                   name.contains("prize")||
                   name.contains("shop"))
                    throw new AssertionError(
                        "protocol/economy state leaked into Tournament "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private TournamentServiceTest(){}
}
