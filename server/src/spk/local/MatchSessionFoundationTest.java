package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** Deterministic regressions for Issue #173 semantic match-session foundation. */
public final class MatchSessionFoundationTest {
    public static void main(String[] args){
        lifecycleAndScoring();
        cancellationLifecycle();
        protocolBoundaryGuard();

        System.out.println(
            "ISSUE173_MATCH_SESSION_FOUNDATION_PASS "+
            "rulesImmutable=true "+
            "lifecycleDeterministic=true "+
            "teamsSemantic=true "+
            "scoringActiveOnly=true "+
            "callerResolvedWinner=true "+
            "participantHooks=true "+
            "snapshotsImmutable=true "+
            "worldInstanceReferenceOnly=true "+
            "protocolIndependent=true "+
            "productionRulesInvented=false"
        );
    }

    private static void lifecycleAndScoring(){
        MatchSessionService service=new MatchSessionService();
        MatchId id=MatchId.of("custom:match-1");
        MatchTeamId red=MatchTeamId.of("custom:red");
        MatchTeamId blue=MatchTeamId.of("custom:blue");

        MatchRules rules=new MatchRules(
            MatchRules.TeamMode.TEAMS,
            MatchRules.SpellPolicy.STANDARD_ONLY,
            MatchRules.PrayerPolicy.STANDARD_ONLY,
            MatchRules.RestrictionPolicy.DISABLED,
            MatchRules.RestrictionPolicy.DISABLED,
            MatchRules.WinConditionKind.SCORE_TARGET,
            5L,
            "custom:clan-wars",
            "CUSTOM_LOCALLAB"
        );

        MatchSession created=service.create(id,rules);

        eq(MatchSession.State.CREATED,created.state,"created state");
        eq("CUSTOM_LOCALLAB",created.rules.sourceAuthority,"authority");
        check(created.rules.hasScoreTarget(),"score target present");
        eq(5L,created.rules.scoreTarget,"score target");

        expect(
            IllegalStateException.class,
            ()->service.create(id,rules),
            "duplicate match id"
        );

        expect(
            IllegalStateException.class,
            ()->service.markReady(id),
            "ready without teams"
        );

        service.addTeam(id,red);
        service.addTeam(id,blue);

        expect(
            IllegalStateException.class,
            ()->service.addTeam(id,red),
            "duplicate team"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.join(
                id,
                MatchTeamId.of("custom:ghost"),
                "player:ghost"
            ),
            "unknown team join"
        );

        service.join(id,red,"player:alice");
        service.join(id,blue,"player:bob");
        service.join(id,blue,"player:carol");

        expect(
            IllegalStateException.class,
            ()->service.join(id,blue,"player:alice"),
            "duplicate participant"
        );

        MatchSession assembled=service.get(id);
        expect(
            UnsupportedOperationException.class,
            ()->assembled.teams.clear(),
            "teams immutable"
        );
        expect(
            UnsupportedOperationException.class,
            ()->assembled.participants.clear(),
            "participants immutable"
        );
        expect(
            UnsupportedOperationException.class,
            ()->assembled.team(red).members.add("player:illegal"),
            "team members immutable"
        );
        expect(
            UnsupportedOperationException.class,
            ()->assembled.team(red).scores.put("kills",1L),
            "team scores immutable"
        );

        WorldInstanceService instances=new WorldInstanceService();
        WorldInstanceId instanceId=
            WorldInstanceId.of("custom:match-instance-1");

        service.attachInstance(id,instanceId);

        check(
            instances.get(instanceId)==null,
            "match attachment allocated WorldInstance"
        );

        instances.create(
            instanceId,
            id.toString(),
            "CUSTOM_LOCALLAB"
        );
        instances.attach(instanceId,"player:alice");
        instances.attach(instanceId,"player:bob");
        instances.attach(instanceId,"player:carol");
        instances.activate(instanceId);

        MatchSession ready=service.markReady(id);
        eq(MatchSession.State.READY,ready.state,"ready state");
        eq(instanceId,ready.instanceId,"instance reference");

        expect(
            IllegalStateException.class,
            ()->service.adjustTeamScore(id,red,"kills",1L),
            "score before active"
        );

        MatchSession active=service.activate(id);
        eq(MatchSession.State.ACTIVE,active.state,"active state");

        MatchSession targetReached=
            service.adjustTeamScore(id,red,"kills",5L);

        eq(
            Long.valueOf(5L),
            targetReached.team(red).scores.get("kills"),
            "team score"
        );
        eq(
            MatchSession.State.ACTIVE,
            targetReached.state,
            "score target does not auto-complete"
        );

        MatchSession playerScored=
            service.adjustParticipantScore(
                id,
                "player:alice",
                "kills",
                2L
            );

        eq(
            Long.valueOf(2L),
            playerScored.participant("player:alice").scores.get("kills"),
            "participant score"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.adjustTeamScore(id,red,"kills",0L),
            "zero score delta"
        );
        expect(
            IllegalArgumentException.class,
            ()->service.adjustTeamScore(id,red,"kills",-6L),
            "negative score floor"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.complete(
                id,
                new MatchSession.Result(
                    "caller:invalid-winner",
                    MatchTeamId.of("custom:not-in-match"),
                    "CUSTOM_LOCALLAB"
                )
            ),
            "winner must belong to match"
        );

        eq(
            MatchSession.State.ACTIVE,
            service.get(id).state,
            "invalid result leaves active state"
        );

        service.leave(id,"player:alice");
        service.forfeit(id,"player:bob");
        service.disconnect(id,"player:carol");

        MatchSession departed=service.get(id);
        eq(
            MatchSession.ParticipantStatus.LEFT,
            departed.participant("player:alice").status,
            "leave hook"
        );
        eq(
            MatchSession.ParticipantStatus.FORFEITED,
            departed.participant("player:bob").status,
            "forfeit hook"
        );
        eq(
            MatchSession.ParticipantStatus.DISCONNECTED,
            departed.participant("player:carol").status,
            "disconnect hook"
        );

        expect(
            IllegalStateException.class,
            ()->service.adjustParticipantScore(
                id,
                "player:alice",
                "kills",
                1L
            ),
            "departed participant cannot score"
        );

        MatchSession completed=
            service.complete(
                id,
                new MatchSession.Result(
                    "caller:red-wins",
                    red,
                    "CUSTOM_LOCALLAB"
                )
            );

        eq(
            MatchSession.State.COMPLETED,
            completed.state,
            "completed state"
        );
        check(completed.terminal(),"completed terminal");
        check(completed.result.hasWinner(),"winner present");
        eq(red,completed.result.winnerTeamId,"winner team");

        expect(
            IllegalStateException.class,
            ()->service.adjustTeamScore(id,red,"kills",1L),
            "score after completion"
        );
        expect(
            IllegalStateException.class,
            ()->service.leave(id,"player:alice"),
            "participant hook after completion"
        );
        expect(
            IllegalStateException.class,
            ()->service.cancel(id,"caller:late-cancel"),
            "cancel after completion"
        );

        eq(
            WorldInstanceService.Lifecycle.ACTIVE,
            instances.get(instanceId).lifecycle,
            "match lifecycle does not own instance lifecycle"
        );

        List<MatchSession> all=service.snapshot();
        expect(
            UnsupportedOperationException.class,
            all::clear,
            "service snapshot immutable"
        );
    }

    private static void cancellationLifecycle(){
        MatchSessionService service=new MatchSessionService();
        MatchId id=MatchId.of("custom:cancelled-match");

        service.create(
            id,
            new MatchRules(
                MatchRules.TeamMode.FREE_FOR_ALL,
                MatchRules.SpellPolicy.UNRESTRICTED,
                MatchRules.PrayerPolicy.UNRESTRICTED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.RestrictionPolicy.ALLOWED,
                MatchRules.WinConditionKind.CALLER_RESOLVED,
                MatchRules.NO_SCORE_TARGET,
                "custom:ffa",
                "CUSTOM_LOCALLAB"
            )
        );

        MatchSession first=service.cancel(id,"caller:cancelled");
        MatchSession second=service.cancel(id,"caller:cancelled");

        eq(MatchSession.State.CANCELLED,first.state,"cancelled state");
        check(first.terminal(),"cancelled terminal");
        eq(
            first.cancellationReasonKey,
            second.cancellationReasonKey,
            "same cancellation idempotent"
        );

        expect(
            IllegalStateException.class,
            ()->service.cancel(id,"caller:different"),
            "different repeat cancellation"
        );
        expect(
            IllegalStateException.class,
            ()->service.addTeam(
                id,
                MatchTeamId.of("custom:late")
            ),
            "terminal addTeam"
        );
        expect(
            IllegalStateException.class,
            ()->service.activate(id),
            "terminal activate"
        );
    }

    private static void protocolBoundaryGuard(){
        Class<?>[] classes={
            MatchId.class,
            MatchTeamId.class,
            MatchRules.class,
            MatchSession.class,
            MatchSession.Team.class,
            MatchSession.Participant.class,
            MatchSession.Result.class,
            MatchSessionService.class
        };

        String[] bannedNames={
            "widget",
            "opcode",
            "subtype",
            "packet",
            "sprite",
            "clientclass",
            "sceneindex",
            "regionid",
            "mapid",
            "coordinate",
            "tile",
            "reward"
        };

        for(Field field:MatchRules.class.getDeclaredFields()){
            if(!Modifier.isStatic(field.getModifiers())&&
               !Modifier.isFinal(field.getModifiers()))
                fail("mutable MatchRules field "+field.getName());
        }

        for(Class<?> type:classes){
            for(Field field:type.getDeclaredFields()){
                String name=
                    field.getName().toLowerCase(Locale.ROOT);

                for(String token:bannedNames){
                    if(name.contains(token))
                        fail(
                            "presentation/map/reward identity leaked "+
                            type.getName()+"."+field.getName()
                        );
                }

                String fieldType=field.getType().getName();

                if(fieldType.contains("ServerPacket")||
                   fieldType.contains("ClientPacket")||
                   fieldType.contains("Isaac")||
                   fieldType.equals("java.net.Socket")||
                   fieldType.equals("spk.local.World")||
                   fieldType.contains("WorldRegion")||
                   fieldType.equals("spk.local.WorldInstanceService"))
                    fail(
                        "runtime/presentation dependency "+
                        type.getName()+"."+field.getName()
                    );
            }
        }
    }

    private static void check(boolean condition,String label){
        if(!condition) fail(label);
    }

    private static void eq(
        Object expected,
        Object actual,
        String label
    ){
        if(!Objects.equals(expected,actual))
            fail(
                label+
                " expected="+expected+
                " actual="+actual
            );
    }

    private static void eq(
        long expected,
        long actual,
        String label
    ){
        if(expected!=actual)
            fail(
                label+
                " expected="+expected+
                " actual="+actual
            );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Throwing action,
        String label
    ){
        try{
            action.run();
            fail(
                label+
                " did not throw "+
                type.getSimpleName()
            );
        }catch(Throwable error){
            if(!type.isInstance(error))
                fail(label+" threw "+error);
        }
    }

    private static void fail(String message){
        throw new AssertionError(message);
    }

    private interface Throwing {
        void run() throws Exception;
    }
}
