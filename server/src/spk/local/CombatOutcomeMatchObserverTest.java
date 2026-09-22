package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class CombatOutcomeMatchObserverTest {
    public static void main(String[] args){
        MatchSessionService matches=
            new MatchSessionService();

        MatchId activeId=
            MatchId.of(
                "match:combat-observer"
            );

        createActiveMatch(
            matches,
            activeId,
            "player:a",
            "player:b"
        );

        CombatOutcomeMatchObserver observer=
            new CombatOutcomeMatchObserver(
                matches,
                "player:a",
                Arrays.asList(
                    new CombatOutcomeMatchObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeMatchObserver
                            .SubjectRole.ATTACKER,
                        activeId,
                        CombatOutcomeMatchObserver
                            .ScoreScope.PARTICIPANT,
                        "kills",
                        2L,
                        "CUSTOM_LOCALLAB"
                    ),
                    new CombatOutcomeMatchObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeMatchObserver
                            .SubjectRole.ATTACKER,
                        activeId,
                        CombatOutcomeMatchObserver
                            .ScoreScope.SUBJECT_TEAM,
                        "team_kills",
                        3L,
                        "CUSTOM_LOCALLAB"
                    ),
                    new CombatOutcomeMatchObserver.Binding(
                        CombatOutcomeType.PLAYER_DEATH,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeMatchObserver
                            .SubjectRole.VICTIM,
                        activeId,
                        CombatOutcomeMatchObserver
                            .ScoreScope.PARTICIPANT,
                        "deaths",
                        1L,
                        "CUSTOM_LOCALLAB"
                    )
                )
            );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:x",
                "player:b",
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                1L,
                "CUSTOM_LOCALLAB"
            )
        );

        assertScores(
            matches.get(activeId),
            0L,
            0L,
            0L,
            "unrelated subject"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "npc:7",
                CombatOutcomeType.NPC_KILL,
                CombatOutcomeContext.NPC_PVM,
                2L,
                "CUSTOM_LOCALLAB"
            )
        );

        assertScores(
            matches.get(activeId),
            0L,
            0L,
            0L,
            "type/context mismatch"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "player:b",
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                3L,
                "CUSTOM_LOCALLAB"
            )
        );

        assertScores(
            matches.get(activeId),
            2L,
            3L,
            0L,
            "matching attacker outcome"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:b",
                "player:a",
                CombatOutcomeType.PLAYER_DEATH,
                CombatOutcomeContext.PLAYER_PVP,
                4L,
                "CUSTOM_LOCALLAB"
            )
        );

        assertScores(
            matches.get(activeId),
            2L,
            3L,
            1L,
            "matching victim outcome"
        );

        MatchSession beforeTerminal=
            matches.get(activeId);

        require(
            beforeTerminal.state==
                MatchSession.State.ACTIVE&&
            beforeTerminal.result==null,
            "observer completed match implicitly"
        );

        matches.complete(
            activeId,
            new MatchSession.Result(
                "caller_resolved",
                MatchTeamId.of(
                    "team:a"
                ),
                "CUSTOM_LOCALLAB"
            )
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "player:b",
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                5L,
                "CUSTOM_LOCALLAB"
            )
        );

        MatchSession terminal=
            matches.get(activeId);

        assertScores(
            terminal,
            2L,
            3L,
            1L,
            "terminal match should ignore outcome"
        );

        assertNonPresentIgnored();
        assertConstructionGuards();
        assertImmutableBindings(observer);
        assertDomainBoundary();

        require(
            observer.bindings().size()==3&&
            "player:a".equals(
                observer.subjectRef()
            )&&
            "CUSTOM_LOCALLAB".equals(
                observer.bindings()
                    .get(0)
                    .sourceAuthority
            ),
            "observer metadata"
        );

        System.out.println(
            "COMBAT_OUTCOME_MATCH_OBSERVER_PASS "+
            "attackerBinding=true "+
            "victimBinding=true "+
            "typeContextFiltered=true "+
            "participantScoring=true "+
            "subjectTeamScoring=true "+
            "inactiveIgnored=true "+
            "nonPresentIgnored=true "+
            "automaticWinner=false "+
            "automaticCompletion=false "+
            "duplicateBindingRejected=true "+
            "unknownMatchRejected=true "+
            "subjectMembershipValidated=true "+
            "rewardMutation=false "+
            "authorityInspectable=true "+
            "protocolIndependent=true"
        );
    }

    private static void assertNonPresentIgnored(){
        MatchSessionService matches=
            new MatchSessionService();

        MatchId id=
            MatchId.of(
                "match:left-subject"
            );

        createActiveMatch(
            matches,
            id,
            "player:a",
            "player:b"
        );

        CombatOutcomeMatchObserver observer=
            new CombatOutcomeMatchObserver(
                matches,
                "player:a",
                Collections.singletonList(
                    new CombatOutcomeMatchObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeMatchObserver
                            .SubjectRole.ATTACKER,
                        id,
                        CombatOutcomeMatchObserver
                            .ScoreScope.SUBJECT_TEAM,
                        "kills",
                        1L,
                        "CUSTOM_LOCALLAB"
                    )
                )
            );

        matches.leave(
            id,
            "player:a"
        );

        observer.onCombatOutcome(
            new CombatOutcome(
                "player:a",
                "player:b",
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                10L,
                "CUSTOM_LOCALLAB"
            )
        );

        MatchSession snapshot=
            matches.get(id);

        long score=
            snapshot.team(
                MatchTeamId.of(
                    "team:a"
                )
            ).scores.getOrDefault(
                "kills",
                0L
            );

        require(
            score==0L,
            "non-present subject scored team"
        );
    }

    private static void assertConstructionGuards(){
        MatchSessionService matches=
            new MatchSessionService();

        MatchId id=
            MatchId.of(
                "match:guard"
            );

        createActiveMatch(
            matches,
            id,
            "player:a",
            "player:b"
        );

        CombatOutcomeMatchObserver.Binding binding=
            new CombatOutcomeMatchObserver.Binding(
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                CombatOutcomeMatchObserver
                    .SubjectRole.ATTACKER,
                id,
                CombatOutcomeMatchObserver
                    .ScoreScope.PARTICIPANT,
                "kills",
                1L,
                "CUSTOM_LOCALLAB"
            );

        boolean duplicateRejected=false;

        try{
            new CombatOutcomeMatchObserver(
                matches,
                "player:a",
                Arrays.asList(
                    binding,
                    new CombatOutcomeMatchObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeMatchObserver
                            .SubjectRole.ATTACKER,
                        id,
                        CombatOutcomeMatchObserver
                            .ScoreScope.PARTICIPANT,
                        "kills",
                        2L,
                        "CUSTOM_LOCALLAB"
                    )
                )
            );
        }catch(
            IllegalArgumentException expected
        ){
            duplicateRejected=true;
        }

        require(
            duplicateRejected,
            "duplicate binding accepted"
        );

        boolean unknownMatchRejected=false;

        try{
            new CombatOutcomeMatchObserver(
                matches,
                "player:a",
                Collections.singletonList(
                    new CombatOutcomeMatchObserver.Binding(
                        CombatOutcomeType.PLAYER_KILL,
                        CombatOutcomeContext.PLAYER_PVP,
                        CombatOutcomeMatchObserver
                            .SubjectRole.ATTACKER,
                        MatchId.of(
                            "match:missing"
                        ),
                        CombatOutcomeMatchObserver
                            .ScoreScope.PARTICIPANT,
                        "kills",
                        1L,
                        "CUSTOM_LOCALLAB"
                    )
                )
            );
        }catch(
            IllegalArgumentException expected
        ){
            unknownMatchRejected=true;
        }

        require(
            unknownMatchRejected,
            "unknown match binding accepted"
        );

        boolean membershipRejected=false;

        try{
            new CombatOutcomeMatchObserver(
                matches,
                "player:outsider",
                Collections.singletonList(
                    binding
                )
            );
        }catch(
            IllegalArgumentException expected
        ){
            membershipRejected=true;
        }

        require(
            membershipRejected,
            "subject outside match accepted"
        );
    }

    private static void assertImmutableBindings(
        CombatOutcomeMatchObserver observer
    ){
        boolean immutable=false;

        try{
            observer.bindings().clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "match observer bindings mutable"
        );
    }

    private static void createActiveMatch(
        MatchSessionService matches,
        MatchId id,
        String a,
        String b
    ){
        matches.create(
            id,
            new MatchRules(
                MatchRules.TeamMode.TEAMS,
                MatchRules.SpellPolicy.UNRESTRICTED,
                MatchRules.PrayerPolicy.UNRESTRICTED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.WinConditionKind.CALLER_RESOLVED,
                MatchRules.NO_SCORE_TARGET,
                "combat_observer_test",
                "CUSTOM_LOCALLAB"
            )
        );

        MatchTeamId teamA=
            MatchTeamId.of(
                "team:a"
            );
        MatchTeamId teamB=
            MatchTeamId.of(
                "team:b"
            );

        matches.addTeam(id,teamA);
        matches.addTeam(id,teamB);
        matches.join(id,teamA,a);
        matches.join(id,teamB,b);
        matches.attachInstance(
            id,
            WorldInstanceId.of(
                "instance:"+
                id.toString()
            )
        );
        matches.markReady(id);
        matches.activate(id);
    }

    private static void assertScores(
        MatchSession match,
        long participantKills,
        long teamKills,
        long participantDeaths,
        String label
    ){
        MatchSession.Participant participant=
            match.participant(
                "player:a"
            );

        MatchSession.Team team=
            match.team(
                MatchTeamId.of(
                    "team:a"
                )
            );

        require(
            participant!=null&&
            team!=null,
            label+" missing participant/team"
        );

        require(
            participant.scores.getOrDefault(
                "kills",
                0L
            )==participantKills&&
            participant.scores.getOrDefault(
                "deaths",
                0L
            )==participantDeaths&&
            team.scores.getOrDefault(
                "team_kills",
                0L
            )==teamKills,
            label+
            " participant="+
            participant.scores+
            " team="+team.scores
        );
    }

    private static void assertDomainBoundary(){
        Class<?>[] types={
            CombatOutcomeMatchObserver.class,
            CombatOutcomeMatchObserver.Binding.class
        };

        for(Class<?> type:types){
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
                   name.contains("sceneindex")||
                   name.contains("reward"))
                    throw new AssertionError(
                        "protocol/reward identity leaked "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
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

    private CombatOutcomeMatchObserverTest(){}
}
