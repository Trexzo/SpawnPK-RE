package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class ClanWarSessionServiceTest {
    public static void main(String[] args){
        assertAcceptedCompositionAndCompletion();
        assertCancellation();
        assertFailClosedPreflight();
        assertDomainBoundary();

        System.out.println(
            "CLAN_WAR_SESSION_SERVICE_PASS "+
            "acceptedChallengeOnly=true "+
            "clanSnapshotValidated=true "+
            "participantOverlapRejected=true "+
            "twoTeamComposition=true "+
            "worldInstanceComposition=true "+
            "callerResolvedScoring=true "+
            "completionClosesInstance=true "+
            "cancellationClosesInstance=true "+
            "durableChildLease=true "+
            "directChildTerminalBlocked=true "+
            "directParticipantTransitionBlocked=true "+
            "directScoreMutationBlocked=true "+
            "ownedScoreMutation=true "+
            "ownerTerminalReleasesChildLease=true "+
            "terminalFailureRetainsChildLease=true "+
            "definitionPreserved=true "+
            "ruleResolverExternal=true "+
            "ruleAuthorityPolicy=true "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void assertAcceptedCompositionAndCompletion(){
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();

        ClanWarSessionService service=
            new ClanWarSessionService(
                matches,
                instances,
                ClanWarSessionServiceTest
                    ::localRules,
                "LOCAL_LAB_POLICY"
            );

        ClanAggregate challenger=
            clan(
                "clan:a",
                "A",
                "player:a1",
                "player:a2"
            );
        ClanAggregate challenged=
            clan(
                "clan:b",
                "B",
                "player:b1",
                "player:b2"
            );

        ClanWarDefinition definition=
            definition();

        ClanWarChallenge challenge=
            new ClanWarChallenge(
                new ClanWarChallenge.ChallengeId(
                    "cw:1"
                ),
                challenger.id(),
                challenged.id(),
                definition
            );

        challenge.accept();

        MatchId matchId=
            MatchId.of(
                "match:cw1"
            );
        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "instance:cw1"
            );

        ClanWarSessionService.Snapshot started=
            service.startAccepted(
                challenge,
                challenger.snapshot(),
                challenged.snapshot(),
                matchId,
                instanceId
            );

        MatchSession match=
            matches.get(
                matchId
            );
        WorldInstanceService.Snapshot instance=
            instances.get(
                instanceId
            );

        require(
            started.lifecycle==
                ClanWarSessionService
                    .Lifecycle.ACTIVE&&
            match!=null&&
            match.state==
                MatchSession.State.ACTIVE&&
            match.teams.size()==2&&
            match.participants.size()==4&&
            instance!=null&&
            instance.lifecycle==
                WorldInstanceService
                    .Lifecycle.ACTIVE&&
            instance.participants.size()==4,
            "accepted Clan War not composed"
        );

        require(
            matches.compositionLeaseHeld(
                matchId
            )&&
            instances.compositionLeaseHeld(
                instanceId
            ),
            "Clan War child lease missing"
        );

        expect(
            IllegalStateException.class,
            ()->matches.cancel(
                matchId,
                "external_cancel"
            ),
            "external Clan War match cancel"
        );
        expect(
            IllegalStateException.class,
            ()->instances.beginClosing(
                instanceId
            ),
            "external Clan War instance close"
        );
        expect(
            IllegalStateException.class,
            ()->matches.leave(
                matchId,
                "player:a1"
            ),
            "external Clan War participant leave"
        );

        require(
            matches.get(
                matchId
            ).participant(
                "player:a1"
            ).status==
                MatchSession.ParticipantStatus.PRESENT,
            "Clan War participant changed outside owner"
        );

        require(
            match.team(
                started.challengerTeamId
            ).members.containsAll(
                Arrays.asList(
                    "player:a1",
                    "player:a2"
                )
            )&&
            match.team(
                started.challengedTeamId
            ).members.containsAll(
                Arrays.asList(
                    "player:b1",
                    "player:b2"
                )
            ),
            "Clan War team membership"
        );

        expect(
            IllegalStateException.class,
            ()->matches.adjustTeamScore(
                matchId,
                started.challengerTeamId,
                "external_score",
                1L
            ),
            "external Clan War score mutation"
        );

        service.adjustClanScore(
            started.challengeId,
            challenger.id(),
            "kills",
            3L
        );

        MatchSession scored=
            matches.get(
                matchId
            );

        require(
            Long.valueOf(3L).equals(
                scored.team(
                    started.challengerTeamId
                ).scores.get(
                    "kills"
                )
            )&&
            scored.team(
                started.challengedTeamId
            ).scores.get(
                "kills"
            )==null,
            "Clan War score not team scoped"
        );

        ClanWarSessionService.Snapshot completed=
            service.complete(
                started.challengeId,
                challenger.id(),
                "caller_resolved",
                "LOCAL_LAB_POLICY"
            );

        MatchSession terminal=
            matches.get(
                matchId
            );
        WorldInstanceService.Snapshot
            closed=
                instances.get(
                    instanceId
                );

        require(
            completed.lifecycle==
                ClanWarSessionService
                    .Lifecycle.COMPLETED&&
            completed.terminal()&&
            completed.definition==
                definition&&
            terminal.state==
                MatchSession.State.COMPLETED&&
            terminal.result!=null&&
            terminal.result.winnerTeamId
                .equals(
                    started.challengerTeamId
                )&&
            closed.lifecycle==
                WorldInstanceService
                    .Lifecycle.CLOSED&&
            closed.participants.isEmpty()&&
            !matches.compositionLeaseHeld(
                matchId
            )&&
            !instances.compositionLeaseHeld(
                instanceId
            ),
            "Clan War completion"
        );

        boolean terminalMutationRejected=false;

        try{
            service.adjustClanScore(
                started.challengeId,
                challenger.id(),
                "kills",
                1L
            );
        }catch(
            IllegalStateException expected
        ){
            terminalMutationRejected=true;
        }

        require(
            terminalMutationRejected,
            "terminal Clan War accepted score mutation"
        );
    }

    private static void assertCancellation(){
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();

        ClanWarSessionService service=
            new ClanWarSessionService(
                matches,
                instances,
                ClanWarSessionServiceTest
                    ::localRules,
                "LOCAL_LAB_POLICY"
            );

        ClanAggregate a=
            clan(
                "clan:cancel-a",
                "A",
                "player:ca",
                null
            );
        ClanAggregate b=
            clan(
                "clan:cancel-b",
                "B",
                "player:cb",
                null
            );

        ClanWarChallenge challenge=
            new ClanWarChallenge(
                new ClanWarChallenge.ChallengeId(
                    "cw:cancel"
                ),
                a.id(),
                b.id(),
                definition()
            );

        challenge.accept();

        ClanWarSessionService.Snapshot started=
            service.startAccepted(
                challenge,
                a.snapshot(),
                b.snapshot(),
                MatchId.of(
                    "match:cw-cancel"
                ),
                WorldInstanceId.of(
                    "instance:cw-cancel"
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->service.cancel(
                started.challengeId,
                "   "
            ),
            "invalid Clan War cancel reason"
        );

        require(
            service.get(
                started.challengeId
            ).lifecycle==
                ClanWarSessionService.Lifecycle.ACTIVE&&
            matches.compositionLeaseHeld(
                started.matchId
            )&&
            instances.compositionLeaseHeld(
                started.instanceId
            ),
            "failed Clan War terminalization dropped child lease"
        );

        ClanWarSessionService.Snapshot cancelled=
            service.cancel(
                started.challengeId,
                "caller_cancelled"
            );

        require(
            cancelled.lifecycle==
                ClanWarSessionService
                    .Lifecycle.CANCELLED&&
            matches.get(
                started.matchId
            ).state==
                MatchSession.State.CANCELLED&&
            instances.get(
                started.instanceId
            ).lifecycle==
                WorldInstanceService
                    .Lifecycle.CLOSED&&
            !matches.compositionLeaseHeld(
                started.matchId
            )&&
            !instances.compositionLeaseHeld(
                started.instanceId
            ),
            "Clan War cancellation"
        );
    }

    private static void assertFailClosedPreflight(){
        MatchSessionService matches=
            new MatchSessionService();
        WorldInstanceService instances=
            new WorldInstanceService();

        ClanWarSessionService service=
            new ClanWarSessionService(
                matches,
                instances,
                ClanWarSessionServiceTest
                    ::localRules,
                "LOCAL_LAB_POLICY"
            );

        ClanAggregate a=
            clan(
                "clan:pre-a",
                "A",
                "player:pa",
                null
            );
        ClanAggregate b=
            clan(
                "clan:pre-b",
                "B",
                "player:pb",
                null
            );

        ClanWarChallenge proposed=
            new ClanWarChallenge(
                new ClanWarChallenge.ChallengeId(
                    "cw:proposed"
                ),
                a.id(),
                b.id(),
                definition()
            );

        boolean proposedRejected=false;

        try{
            service.startAccepted(
                proposed,
                a.snapshot(),
                b.snapshot(),
                MatchId.of(
                    "match:proposed"
                ),
                WorldInstanceId.of(
                    "instance:proposed"
                )
            );
        }catch(
            IllegalStateException expected
        ){
            proposedRejected=true;
        }

        require(
            proposedRejected&&
            matches.size()==0&&
            instances.size()==0&&
            service.size()==0,
            "proposed challenge mutated runtime"
        );

        ClanWarChallenge mismatch=
            new ClanWarChallenge(
                new ClanWarChallenge.ChallengeId(
                    "cw:mismatch"
                ),
                a.id(),
                b.id(),
                definition()
            );

        mismatch.accept();

        boolean mismatchRejected=false;

        try{
            service.startAccepted(
                mismatch,
                b.snapshot(),
                a.snapshot(),
                MatchId.of(
                    "match:mismatch"
                ),
                WorldInstanceId.of(
                    "instance:mismatch"
                )
            );
        }catch(
            IllegalArgumentException expected
        ){
            mismatchRejected=true;
        }

        require(
            mismatchRejected&&
            matches.size()==0&&
            instances.size()==0,
            "mismatched clan snapshots mutated runtime"
        );

        ClanAggregate overlapA=
            clan(
                "clan:overlap-a",
                "A",
                "player:shared",
                null
            );
        ClanAggregate overlapB=
            clan(
                "clan:overlap-b",
                "B",
                "player:shared",
                null
            );

        ClanWarChallenge overlap=
            new ClanWarChallenge(
                new ClanWarChallenge.ChallengeId(
                    "cw:overlap"
                ),
                overlapA.id(),
                overlapB.id(),
                definition()
            );

        overlap.accept();

        boolean overlapRejected=false;

        try{
            service.startAccepted(
                overlap,
                overlapA.snapshot(),
                overlapB.snapshot(),
                MatchId.of(
                    "match:overlap"
                ),
                WorldInstanceId.of(
                    "instance:overlap"
                )
            );
        }catch(
            IllegalStateException expected
        ){
            overlapRejected=true;
        }

        require(
            overlapRejected&&
            matches.size()==0&&
            instances.size()==0,
            "overlap preflight mutated runtime"
        );
    }

    private static MatchRules localRules(
        ClanWarDefinition definition
    ){
        MatchRules.SpellPolicy spell;

        switch(definition.spellRule()){
            case ALL_SPELLBOOKS:
                spell=
                    MatchRules.SpellPolicy
                        .UNRESTRICTED;
                break;
            case STANDARD_SPELLS:
                spell=
                    MatchRules.SpellPolicy
                        .STANDARD_ONLY;
                break;
            case BINDING_ONLY:
                spell=
                    MatchRules.SpellPolicy
                        .BINDING_ONLY;
                break;
            case DISABLED:
                spell=
                    MatchRules.SpellPolicy
                        .DISABLED;
                break;
            default:
                throw new AssertionError(
                    definition.spellRule()
                );
        }

        MatchRules.PrayerPolicy prayer;

        switch(definition.prayerRule()){
            case ALL_ALLOWED:
                prayer=
                    MatchRules.PrayerPolicy
                        .UNRESTRICTED;
                break;
            case STANDARD_PRAYERS:
                prayer=
                    MatchRules.PrayerPolicy
                        .STANDARD_ONLY;
                break;
            case DISABLED:
                prayer=
                    MatchRules.PrayerPolicy
                        .DISABLED;
                break;
            default:
                throw new AssertionError(
                    definition.prayerRule()
                );
        }

        MatchRules.WinConditionKind win;
        long target=
            MatchRules.NO_SCORE_TARGET;

        switch(definition.victoryMode()){
            case KILL_TARGET:
                win=
                    MatchRules.WinConditionKind
                        .SCORE_TARGET;
                target=
                    definition.killTarget();
                break;
            case LAST_TEAM_STANDING:
                win=
                    MatchRules.WinConditionKind
                        .LAST_TEAM_STANDING;
                break;
            case KILL_EM_ALL:
                win=
                    MatchRules.WinConditionKind
                        .CALLER_RESOLVED;
                break;
            default:
                throw new AssertionError(
                    definition.victoryMode()
                );
        }

        return new MatchRules(
            MatchRules.TeamMode.TEAMS,
            spell,
            prayer,
            MatchRules.RestrictionPolicy.ALLOWED,
            MatchRules.RestrictionPolicy.ALLOWED,
            win,
            target,
            "clan_wars",
            "LOCAL_LAB_POLICY"
        );
    }

    private static ClanWarDefinition definition(){
        return new ClanWarDefinition(
            ClanWarDefinition
                .SpellRule
                .ALL_SPELLBOOKS,
            ClanWarDefinition
                .PrayerRule
                .ALL_ALLOWED,
            ClanWarDefinition
                .WeaponRule
                .NO_STAFF_OF_THE_DEAD,
            ClanWarDefinition
                .VictoryMode
                .KILL_TARGET,
            25,
            ClanWarDefinition
                .Arena
                .WASTELAND,
            EnumSet.of(
                ClanWarDefinition
                    .AdvancedRule
                    .PJ_TIMER
            ),
            ClanEvidenceAuthority
                .EXACT_CURRENT_CLIENT
        );
    }

    private static ClanAggregate clan(
        String id,
        String name,
        String owner,
        String member
    ){
        EnumMap<
            ClanAggregate.Permission,
            ClanAggregate.PermissionThreshold
        > permissions=
            new EnumMap<>(
                ClanAggregate.Permission.class
            );

        for(ClanAggregate.Permission permission:
                ClanAggregate.Permission.values())
            permissions.put(
                permission,
                ClanAggregate
                    .PermissionThreshold
                    .ANYONE
            );

        ClanAggregate clan=
            new ClanAggregate(
                new ClanAggregate.ClanId(
                    id
                ),
                name,
                new ClanAggregate.MemberId(
                    owner
                ),
                permissions,
                false,
                ClanEvidenceAuthority
                    .CUSTOM_LOCALLAB
            );

        if(member!=null)
            clan.putMember(
                new ClanAggregate.MemberId(
                    member
                ),
                ClanAggregate.Rank.RECRUIT
            );

        return clan;
    }

    private static void assertDomainBoundary(){
        Class<?>[] types={
            ClanWarSessionService.class,
            ClanWarSessionService.Snapshot.class
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
                   name.contains("interface")||
                   name.contains("clientindex"))
                    throw new AssertionError(
                        "protocol identity leaked into Clan War runtime "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );

                if(name.contains("reward"))
                    throw new AssertionError(
                        "reward state leaked into Clan War runtime "+
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

    private ClanWarSessionServiceTest(){}
}
