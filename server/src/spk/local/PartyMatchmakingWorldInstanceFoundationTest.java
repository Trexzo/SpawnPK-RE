package spk.local;

import java.lang.reflect.Field;
import java.util.*;

/** Deterministic regressions for Issue #158 domain separation. */
public final class PartyMatchmakingWorldInstanceFoundationTest {
    public static void main(String[] args){
        partyLifecycle();
        matchmakingAndInstanceLifecycle();
        protocolBoundaryGuard();

        System.out.println(
            "ISSUE158_PARTY_MATCHMAKING_INSTANCE_PASS "+
            "party=true "+
            "matchmaking=true "+
            "worldInstance=true "+
            "externalSelector=true "+
            "separateOwnership=true "+
            "immutable=true "+
            "invalidTransitionsFailClosed=true "+
            "mapAllocation=false "+
            "protocolIndependent=true "+
            "authorityPreserved=true"
        );
    }

    private static void partyLifecycle(){
        PartyService service=new PartyService();
        PartyId id=PartyId.of("custom:raid-party");

        PartyService.Snapshot created=
            service.create(
                id,
                "player:leader",
                PartyService.Visibility.PUBLIC,
                "CUSTOM_LOCALLAB"
            );

        eq(
            "player:leader",
            created.leaderRef,
            "party leader"
        );
        check(
            created.member("player:leader"),
            "leader is member"
        );

        expect(
            UnsupportedOperationException.class,
            ()->created.members.clear(),
            "party members immutable"
        );

        service.invite(id,"player:bob");

        expect(
            IllegalStateException.class,
            ()->service.invite(id,"player:bob"),
            "duplicate invite"
        );

        service.cancelInvite(id,"player:bob");

        expect(
            IllegalStateException.class,
            ()->service.cancelInvite(
                id,
                "player:bob"
            ),
            "cancel missing invite"
        );

        service.invite(id,"player:bob");
        PartyService.Snapshot joined=
            service.acceptInvite(
                id,
                "player:bob"
            );

        check(
            joined.member("player:bob"),
            "invited member joined"
        );
        check(
            !joined.invited("player:bob"),
            "invite consumed"
        );

        service.leave(id,"player:bob");

        expect(
            IllegalStateException.class,
            ()->service.leave(
                id,
                "player:bob"
            ),
            "duplicate leave"
        );

        service.invite(id,"player:carol");
        service.acceptInvite(id,"player:carol");
        service.kick(id,"player:carol");

        expect(
            IllegalStateException.class,
            ()->service.kick(
                id,
                "player:leader"
            ),
            "leader kick fails closed"
        );

        expect(
            IllegalStateException.class,
            ()->service.leave(
                id,
                "player:leader"
            ),
            "leader departure policy not invented"
        );

        PartyService.Snapshot privateParty=
            service.setVisibility(
                id,
                PartyService.Visibility.PRIVATE
            );

        eq(
            PartyService.Visibility.PRIVATE,
            privateParty.visibility,
            "party visibility"
        );
    }

    private static void matchmakingAndInstanceLifecycle(){
        MatchmakingService matchmaking=
            new MatchmakingService();

        QueueId queueId=
            QueueId.of("custom:lobby");

        matchmaking.defineQueue(
            new MatchmakingService.QueueDefinition(
                queueId,
                2,
                "CUSTOM_LOCALLAB"
            )
        );

        matchmaking.enqueue(
            queueId,
            "player:alice"
        );
        matchmaking.enqueue(
            queueId,
            "player:bob"
        );
        matchmaking.enqueue(
            queueId,
            "player:carol"
        );

        expect(
            IllegalStateException.class,
            ()->matchmaking.enqueue(
                queueId,
                "player:alice"
            ),
            "duplicate queue member"
        );

        expect(
            IllegalStateException.class,
            ()->matchmaking.beginAssignment(
                queueId,
                (definition,waiting)->
                    Arrays.asList(
                        "player:alice",
                        "player:ghost"
                    )
            ),
            "selector cannot invent participant"
        );

        eq(
            3,
            matchmaking.getQueue(
                queueId
            ).waitingCount(),
            "failed selector leaves queue unchanged"
        );

        MatchmakingService.AssignmentSnapshot batch=
            matchmaking.beginAssignment(
                queueId,
                (definition,waiting)->
                    Arrays.asList(
                        waiting.get(0),
                        waiting.get(1)
                    )
            );

        eq(
            MatchmakingService.MemberState.STARTING,
            batch.state,
            "starting state"
        );
        eq(
            2,
            batch.participants.size(),
            "selected count"
        );
        eq(
            1,
            matchmaking.getQueue(
                queueId
            ).waitingCount(),
            "unselected participant remains waiting"
        );

        expect(
            IllegalStateException.class,
            ()->matchmaking.dequeue(
                queueId,
                "player:alice"
            ),
            "starting participant cannot dequeue"
        );

        matchmaking.dequeue(
            queueId,
            "player:carol"
        );

        WorldInstanceId instanceId=
            WorldInstanceId.of(
                "custom:match-instance-1"
            );

        check(
            matchmaking.assign(
                batch.assignmentId,
                instanceId
            ),
            "first assignment"
        );

        check(
            !matchmaking.assign(
                batch.assignmentId,
                instanceId
            ),
            "same assignment idempotent"
        );

        expect(
            IllegalStateException.class,
            ()->matchmaking.assign(
                batch.assignmentId,
                WorldInstanceId.of(
                    "custom:other-instance"
                )
            ),
            "assignment cannot rebind instance"
        );

        MatchmakingService.AssignmentSnapshot assigned=
            matchmaking.getAssignment(
                batch.assignmentId
            );

        eq(
            MatchmakingService.MemberState.ASSIGNED,
            assigned.state,
            "assigned state"
        );
        eq(
            instanceId,
            assigned.instanceId,
            "semantic instance reference"
        );

        expect(
            UnsupportedOperationException.class,
            ()->assigned.participants.clear(),
            "assignment participants immutable"
        );

        MatchmakingService belowMinimum=
            new MatchmakingService();

        QueueId small=
            QueueId.of("custom:small");

        belowMinimum.defineQueue(
            new MatchmakingService.QueueDefinition(
                small,
                2,
                "CUSTOM_LOCALLAB"
            )
        );
        belowMinimum.enqueue(
            small,
            "player:only"
        );

        expect(
            IllegalStateException.class,
            ()->belowMinimum.beginAssignment(
                small,
                (definition,waiting)->
                    waiting
            ),
            "minimum capacity metadata enforced"
        );

        WorldInstanceService instances=
            new WorldInstanceService();

        check(
            instances.get(instanceId)==null,
            "matchmaking did not create instance"
        );

        WorldInstanceService.Snapshot created=
            instances.create(
                instanceId,
                batch.assignmentId.toString(),
                "CUSTOM_LOCALLAB"
            );

        eq(
            WorldInstanceService.Lifecycle.CREATED,
            created.lifecycle,
            "instance created"
        );

        instances.attach(
            instanceId,
            "player:alice"
        );
        instances.attach(
            instanceId,
            "player:bob"
        );

        expect(
            IllegalStateException.class,
            ()->instances.attach(
                instanceId,
                "player:alice"
            ),
            "duplicate attach"
        );

        WorldInstanceService.Snapshot active=
            instances.activate(instanceId);

        eq(
            WorldInstanceService.Lifecycle.ACTIVE,
            active.lifecycle,
            "instance active"
        );

        WorldInstanceService.Snapshot closing=
            instances.beginClosing(instanceId);

        eq(
            WorldInstanceService.Lifecycle.CLOSING,
            closing.lifecycle,
            "instance closing"
        );

        expect(
            IllegalStateException.class,
            ()->instances.attach(
                instanceId,
                "player:late"
            ),
            "no attach while closing"
        );

        expect(
            IllegalStateException.class,
            ()->instances.close(instanceId),
            "attached participants block close"
        );

        instances.detach(
            instanceId,
            "player:alice"
        );
        instances.detach(
            instanceId,
            "player:bob"
        );

        WorldInstanceService.Snapshot closed=
            instances.close(instanceId);

        eq(
            WorldInstanceService.Lifecycle.CLOSED,
            closed.lifecycle,
            "instance closed"
        );
        check(
            instances.close(
                instanceId
            ).terminal(),
            "repeat close idempotent"
        );

        expect(
            IllegalStateException.class,
            ()->instances.detach(
                instanceId,
                "player:alice"
            ),
            "closed instance immutable"
        );

        expect(
            UnsupportedOperationException.class,
            ()->closed.participants.add(
                "player:illegal"
            ),
            "instance snapshot immutable"
        );
    }

    private static void protocolBoundaryGuard(){
        Class<?>[] classes={
            PartyId.class,
            PartyService.class,
            PartyService.Snapshot.class,
            QueueId.class,
            MatchmakingService.class,
            MatchmakingService.QueueDefinition.class,
            MatchmakingService.MemberSnapshot.class,
            MatchmakingService.AssignmentSnapshot.class,
            WorldInstanceId.class,
            WorldInstanceService.class,
            WorldInstanceService.Snapshot.class
        };

        String[] banned={
            "widget",
            "opcode",
            "subtype",
            "packet",
            "sprite",
            "clientclass",
            "sceneindex",
            "regionid",
            "mapid"
        };

        for(Class<?> type:classes){
            for(Field field:type.getDeclaredFields()){
                String name=
                    field.getName().toLowerCase(
                        Locale.ROOT
                    );

                for(String token:banned){
                    if(name.contains(token))
                        fail(
                            "protocol/map identity leaked "+
                            type.getName()+"."+
                            field.getName()
                        );
                }

                String fieldType=
                    field.getType().getName();

                if(fieldType.contains(
                        "ServerPacket")||
                   fieldType.contains(
                        "ClientPacket")||
                   fieldType.equals(
                        "spk.local.World")||
                   fieldType.contains(
                        "WorldRegion"))
                    fail(
                        "runtime/presentation dependency "+
                        type.getName()+"."+
                        field.getName()
                    );
            }
        }
    }

    private static void check(
        boolean condition,
        String label
    ){
        if(!condition)
            fail(label);
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
        int expected,
        int actual,
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
                fail(
                    label+
                    " threw "+
                    error
                );
        }
    }

    private static void fail(String message){
        throw new AssertionError(message);
    }

    private interface Throwing {
        void run() throws Exception;
    }
}
