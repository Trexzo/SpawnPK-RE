package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class DuelSessionServiceTest {
    public static void main(String[] args){
        lifecycleAndWinner();
        drawLifecycle();
        cancellationLifecycle();
        preflightGuards();
        protocolBoundary();

        System.out.println(
            "DUEL_SESSION_SERVICE_PASS "+
            "selfDuelRejected=true "+
            "participantExclusivity=true "+
            "challengedAcceptDeclineOnly=true "+
            "twoOnePlayerTeams=true "+
            "worldInstanceComposition=true "+
            "rulesAuthorityPolicy=true "+
            "participantScoreCallerResolved=true "+
            "teamScoreCallerResolved=true "+
            "forfeitNoAutoWinner=true "+
            "callerResolvedWinner=true "+
            "drawSupported=true "+
            "completionClosesInstance=true "+
            "cancellationClosesInstance=true "+
            "durableChildLease=true "+
            "directChildTerminalBlocked=true "+
            "directParticipantTransitionBlocked=true "+
            "ownedForfeitUnderLease=true "+
            "directScoreMutationBlocked=true "+
            "ownedScoreMutation=true "+
            "ownerTerminalReleasesChildLease=true "+
            "terminalFailureRetainsChildLease=true "+
            "participantIndexReleased=true "+
            "stakeMutation=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void lifecycleAndWinner(){
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();
        DuelSessionService service=
            new DuelSessionService(
                matches,
                instances
            );

        DuelSessionService.ChallengeId id=
            DuelSessionService.ChallengeId.of(
                "duel:1"
            );

        DuelSessionService.Snapshot proposed=
            service.propose(
                id,
                "player:a",
                "player:b",
                "standard",
                localRules(),
                "LOCAL_LAB_POLICY"
            );

        require(
            proposed.state==
                DuelSessionService.State.PROPOSED&&
            "player:a".equals(
                proposed.challengerRef
            )&&
            "player:b".equals(
                proposed.challengedRef
            )&&
            "standard".equals(
                proposed.duelTypeKey
            ),
            "Duel proposal"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.accept(
                id,
                "player:a"
            ),
            "challenger accepted own proposal"
        );

        require(
            service.accept(
                id,
                "player:b"
            ).state==
                DuelSessionService.State.ACCEPTED,
            "Duel accept"
        );

        MatchId matchId=
            MatchId.of(
                "match:duel:1"
            );
        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "instance:duel:1"
            );

        DuelSessionService.Snapshot active=
            service.startAccepted(
                id,
                matchId,
                instanceId
            );

        MatchSession match=
            matches.get(matchId);
        WorldInstanceService.Snapshot instance=
            instances.get(instanceId);

        require(
            active.active()&&
            match!=null&&
            match.state==
                MatchSession.State.ACTIVE&&
            match.teams.size()==2&&
            match.participants.size()==2&&
            match.team(
                active.challengerTeamId
            ).members.equals(
                Collections.singletonList(
                    "player:a"
                )
            )&&
            match.team(
                active.challengedTeamId
            ).members.equals(
                Collections.singletonList(
                    "player:b"
                )
            )&&
            instance!=null&&
            instance.lifecycle==
                WorldInstanceService.Lifecycle.ACTIVE&&
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
            "Duel active composition"
        );

        require(
            matches.compositionLeaseHeld(
                matchId
            )&&
            instances.compositionLeaseHeld(
                instanceId
            ),
            "Duel child lease missing"
        );

        expect(
            IllegalStateException.class,
            ()->matches.complete(
                matchId,
                new MatchSession.Result(
                    "external",
                    active.challengerTeamId,
                    "LOCAL_LAB_POLICY"
                )
            ),
            "external Duel match completion"
        );
        expect(
            IllegalStateException.class,
            ()->instances.detach(
                instanceId,
                "player:a"
            ),
            "external Duel instance detach"
        );
        expect(
            IllegalStateException.class,
            ()->matches.forfeit(
                matchId,
                "player:b"
            ),
            "external Duel participant forfeit"
        );
        expect(
            IllegalStateException.class,
            ()->matches.disconnect(
                matchId,
                "player:a"
            ),
            "external Duel participant disconnect"
        );

        require(
            matches.get(
                matchId
            ).participant(
                "player:a"
            ).status==
                MatchSession.ParticipantStatus.PRESENT&&
            matches.get(
                matchId
            ).participant(
                "player:b"
            ).status==
                MatchSession.ParticipantStatus.PRESENT,
            "Duel participant changed outside owner"
        );

        expect(
            IllegalStateException.class,
            ()->matches.adjustParticipantScore(
                matchId,
                "player:a",
                "external_hits",
                1L
            ),
            "external Duel participant score"
        );
        expect(
            IllegalStateException.class,
            ()->matches.adjustTeamScore(
                matchId,
                active.challengedTeamId,
                "external_rounds",
                1L
            ),
            "external Duel team score"
        );

        service.adjustParticipantScore(
            id,
            "player:a",
            "hits",
            2L
        );
        service.adjustTeamScore(
            id,
            "player:b",
            "rounds",
            1L
        );

        MatchSession scored=
            matches.get(matchId);

        require(
            Long.valueOf(2L).equals(
                scored.participant(
                    "player:a"
                ).scores.get(
                    "hits"
                )
            )&&
            Long.valueOf(1L).equals(
                scored.team(
                    active.challengedTeamId
                ).scores.get(
                    "rounds"
                )
            ),
            "Duel caller-resolved scoring"
        );

        service.forfeit(
            id,
            "player:b"
        );

        MatchSession forfeited=
            matches.get(matchId);

        require(
            forfeited.state==
                MatchSession.State.ACTIVE&&
            forfeited.participant(
                "player:b"
            ).status==
                MatchSession
                    .ParticipantStatus
                    .FORFEITED&&
            forfeited.result==null,
            "Duel forfeit auto-resolved winner"
        );

        DuelSessionService.Snapshot completed=
            service.complete(
                id,
                "player:a",
                "caller_resolved",
                "LOCAL_LAB_POLICY"
            );

        MatchSession terminal=
            matches.get(matchId);
        WorldInstanceService.Snapshot closed=
            instances.get(instanceId);

        require(
            completed.state==
                DuelSessionService.State.COMPLETED&&
            completed.terminal()&&
            terminal.state==
                MatchSession.State.COMPLETED&&
            terminal.result!=null&&
            terminal.result.winnerTeamId
                .equals(
                    active.challengerTeamId
                )&&
            closed.lifecycle==
                WorldInstanceService.Lifecycle.CLOSED&&
            closed.participants.isEmpty()&&
            !matches.compositionLeaseHeld(
                matchId
            )&&
            !instances.compositionLeaseHeld(
                instanceId
            )&&
            service.openFor(
                "player:a"
            )==null&&
            service.openFor(
                "player:b"
            )==null,
            "Duel completion"
        );

        expect(
            IllegalStateException.class,
            ()->service.adjustTeamScore(
                id,
                "player:a",
                "rounds",
                1L
            ),
            "terminal Duel score mutation"
        );
    }

    private static void drawLifecycle(){
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();
        DuelSessionService service=
            new DuelSessionService(
                matches,
                instances
            );

        DuelSessionService.ChallengeId id=
            DuelSessionService.ChallengeId.of(
                "duel:draw"
            );

        service.propose(
            id,
            "player:c",
            "player:d",
            "standard",
            localRules(),
            "LOCAL_LAB_POLICY"
        );
        service.accept(
            id,
            "player:d"
        );

        DuelSessionService.Snapshot active=
            service.startAccepted(
                id,
                MatchId.of(
                    "match:duel:draw"
                ),
                WorldInstanceId.of(
                    "instance:duel:draw"
                )
            );

        service.complete(
            id,
            null,
            "draw",
            "LOCAL_LAB_POLICY"
        );

        MatchSession match=
            matches.get(
                active.matchId
            );

        require(
            match.result!=null&&
            !match.result.hasWinner()&&
            "draw".equals(
                match.result.outcomeKey
            ),
            "Duel draw"
        );
    }

    private static void cancellationLifecycle(){
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();
        DuelSessionService service=
            new DuelSessionService(
                matches,
                instances
            );

        DuelSessionService.ChallengeId declinedId=
            DuelSessionService.ChallengeId.of(
                "duel:declined"
            );

        service.propose(
            declinedId,
            "player:e",
            "player:f",
            "whip",
            localRules(),
            "LOCAL_LAB_POLICY"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.decline(
                declinedId,
                "player:e"
            ),
            "challenger declined as challenged"
        );

        require(
            service.decline(
                declinedId,
                "player:f"
            ).state==
                DuelSessionService.State.DECLINED&&
            service.openFor(
                "player:e"
            )==null,
            "Duel decline"
        );

        DuelSessionService.ChallengeId openCancel=
            DuelSessionService.ChallengeId.of(
                "duel:cancel-open"
            );

        service.propose(
            openCancel,
            "player:e",
            "player:f",
            "standard",
            localRules(),
            "LOCAL_LAB_POLICY"
        );

        require(
            service.cancelOpen(
                openCancel,
                "player:e"
            ).state==
                DuelSessionService.State.CANCELLED,
            "open Duel cancel"
        );

        DuelSessionService.ChallengeId activeCancel=
            DuelSessionService.ChallengeId.of(
                "duel:cancel-active"
            );

        service.propose(
            activeCancel,
            "player:e",
            "player:f",
            "standard",
            localRules(),
            "LOCAL_LAB_POLICY"
        );
        service.accept(
            activeCancel,
            "player:f"
        );

        DuelSessionService.Snapshot active=
            service.startAccepted(
                activeCancel,
                MatchId.of(
                    "match:duel:cancel"
                ),
                WorldInstanceId.of(
                    "instance:duel:cancel"
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->service.cancelActive(
                activeCancel,
                "   "
            ),
            "invalid active Duel cancel reason"
        );

        require(
            service.get(
                activeCancel
            ).state==
                DuelSessionService.State.ACTIVE&&
            matches.compositionLeaseHeld(
                active.matchId
            )&&
            instances.compositionLeaseHeld(
                active.instanceId
            )&&
            service.openFor(
                "player:e"
            )!=null&&
            service.openFor(
                "player:f"
            )!=null,
            "failed Duel terminalization dropped child ownership"
        );

        DuelSessionService.Snapshot cancelled=
            service.cancelActive(
                activeCancel,
                "caller_cancelled"
            );

        require(
            cancelled.state==
                DuelSessionService.State.CANCELLED&&
            matches.get(
                active.matchId
            ).state==
                MatchSession.State.CANCELLED&&
            instances.get(
                active.instanceId
            ).lifecycle==
                WorldInstanceService.Lifecycle.CLOSED&&
            !matches.compositionLeaseHeld(
                active.matchId
            )&&
            !instances.compositionLeaseHeld(
                active.instanceId
            )&&
            service.openFor(
                "player:e"
            )==null&&
            service.openFor(
                "player:f"
            )==null,
            "active Duel cancellation"
        );
    }

    private static void preflightGuards(){
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();
        DuelSessionService service=
            new DuelSessionService(
                matches,
                instances
            );

        expect(
            IllegalArgumentException.class,
            ()->service.propose(
                DuelSessionService.ChallengeId.of(
                    "duel:self"
                ),
                "player:self",
                "player:self",
                "standard",
                localRules(),
                "LOCAL_LAB_POLICY"
            ),
            "self Duel"
        );

        DuelSessionService.ChallengeId first=
            DuelSessionService.ChallengeId.of(
                "duel:exclusive"
            );

        service.propose(
            first,
            "player:x",
            "player:y",
            "standard",
            localRules(),
            "LOCAL_LAB_POLICY"
        );

        expect(
            IllegalStateException.class,
            ()->service.propose(
                DuelSessionService.ChallengeId.of(
                    "duel:exclusive-2"
                ),
                "player:y",
                "player:z",
                "standard",
                localRules(),
                "LOCAL_LAB_POLICY"
            ),
            "participant Duel exclusivity"
        );

        service.cancelOpen(
            first,
            "player:x"
        );

        MatchRules ffa=
            new MatchRules(
                MatchRules.TeamMode.FREE_FOR_ALL,
                MatchRules.SpellPolicy.UNRESTRICTED,
                MatchRules.PrayerPolicy.UNRESTRICTED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.WinConditionKind.CALLER_RESOLVED,
                MatchRules.NO_SCORE_TARGET,
                "duel",
                "LOCAL_LAB_POLICY"
            );

        DuelSessionService.ChallengeId invalidRules=
            DuelSessionService.ChallengeId.of(
                "duel:ffa"
            );

        service.propose(
            invalidRules,
            "player:x",
            "player:y",
            "standard",
            ffa,
            "LOCAL_LAB_POLICY"
        );
        service.accept(
            invalidRules,
            "player:y"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.startAccepted(
                invalidRules,
                MatchId.of(
                    "match:duel:ffa"
                ),
                WorldInstanceId.of(
                    "instance:duel:ffa"
                )
            ),
            "non-TEAMS Duel rules"
        );

        require(
            matches.size()==0&&
            instances.size()==0,
            "invalid Duel rules mutated reusable services"
        );

        service.cancelOpen(
            invalidRules,
            "player:x"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.propose(
                DuelSessionService.ChallengeId.of(
                    "duel:authority"
                ),
                "player:x",
                "player:y",
                "standard",
                localRules(),
                "EXACT_CURRENT_CLIENT"
            ),
            "Duel gameplay authority mismatch"
        );

        require(
            matches.size()==0&&
            instances.size()==0,
            "authority mismatch mutated reusable services"
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
            "duel",
            "LOCAL_LAB_POLICY"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                DuelSessionService.class,
                DuelSessionService.Snapshot.class
        }){
            for(Field field:type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("interface")||
                   name.contains("stake")||
                   name.contains("reward"))
                    throw new AssertionError(
                        "protocol/economy state leaked into Duel "+
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
            throw new AssertionError(
                label
            );
    }

    private DuelSessionServiceTest(){}
}
