package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class DailyChallengeAssignmentServiceTest {
    public static void main(String[] args){
        assertAssignmentProgressAndClaim();
        assertAtomicReplacement();
        assertBoundary();

        System.out.println(
            "DAILY_CHALLENGE_ASSIGNMENT_SERVICE_PASS "+
            "objectiveFoundationReused=true "+
            "clientChallengeIdAdapter=true "+
            "progressDelegated=true "+
            "claimDelegated=true "+
            "atomicReplace=true "+
            "explicitResetOnly=true "+
            "rewardDispatch=false "+
            "packetIdentity=false"
        );
    }

    private static void assertAssignmentProgressAndClaim(){
        DailyChallengeAssignmentService service=
            new DailyChallengeAssignmentService();

        DailyChallengeAssignmentService.AssignmentSpec kills=
            spec(
                7,
                "daily:revenant_kills",
                "Kill revenants",
                "objective:revenant_kills",
                3,
                0,
                false
            );

        DailyChallengeAssignmentService.AssignmentSpec vote=
            spec(
                9,
                "daily:vote",
                "Vote once",
                "objective:vote",
                1,
                0,
                false
            );

        service.assign(kills);
        service.assign(vote);

        if(service.size()!=2)
            throw new AssertionError(
                "size="+service.size()
            );

        DailyChallengeAssignmentService.Snapshot byKey=
            service.getByChallengeKey(
                "DAILY:REVENANT_KILLS"
            );

        if(byKey==null||
           byKey.clientChallengeId!=7||
           byKey.goal!=3)
            throw new AssertionError(
                "byKey="+byKey
            );

        ObjectiveProgressService.ProgressResult first=
            service.advance(
                7,
                2
            );

        if(first.after.progress!=2||
           first.completedNow)
            throw new AssertionError(
                "first progress="+
                first.after
            );

        boolean earlyClaimRejected=false;

        try{
            service.markClaimed(7);
        }catch(IllegalStateException expected){
            earlyClaimRejected=true;
        }

        if(!earlyClaimRejected)
            throw new AssertionError(
                "incomplete challenge claimed"
            );

        ObjectiveProgressService.ProgressResult complete=
            service.advance(
                7,
                99
            );

        if(complete.after.progress!=3||
           !complete.after.complete||
           !complete.completedNow)
            throw new AssertionError(
                "complete="+
                complete.after
            );

        if(service.completedUnclaimedCount()!=1)
            throw new AssertionError(
                "completedUnclaimed="+
                service.completedUnclaimedCount()
            );

        if(!service.markClaimed(7))
            throw new AssertionError(
                "first claim=false"
            );

        if(service.markClaimed(7))
            throw new AssertionError(
                "second claim=true"
            );

        DailyChallengeAssignmentService.Snapshot claimed=
            service.get(7);

        if(!claimed.complete||
           !claimed.claimed||
           claimed.progress!=3||
           !"CUSTOM_LOCALLAB".equals(
               claimed.sourceAuthority))
            throw new AssertionError(
                "claimed="+claimed
            );

        if(service.completedUnclaimedCount()!=0)
            throw new AssertionError(
                "claimed challenge still counted"
            );

        boolean unknownRejected=false;

        try{
            service.advance(
                999,
                1
            );
        }catch(IllegalArgumentException expected){
            unknownRejected=true;
        }

        if(!unknownRejected)
            throw new AssertionError(
                "unknown client challenge id accepted"
            );
    }

    private static void assertAtomicReplacement(){
        DailyChallengeAssignmentService service=
            new DailyChallengeAssignmentService();

        service.assign(
            spec(
                1,
                "daily:one",
                "One",
                "objective:one",
                1,
                0,
                false
            )
        );

        List<DailyChallengeAssignmentService.AssignmentSpec>
            invalid=
                Arrays.asList(
                    spec(
                        2,
                        "daily:two",
                        "Two",
                        "objective:two",
                        2,
                        0,
                        false
                    ),
                    spec(
                        2,
                        "daily:three",
                        "Three",
                        "objective:three",
                        3,
                        0,
                        false
                    )
                );

        boolean duplicateRejected=false;

        try{
            service.replaceAll(invalid);
        }catch(IllegalStateException expected){
            duplicateRejected=true;
        }

        if(!duplicateRejected)
            throw new AssertionError(
                "duplicate replace accepted"
            );

        if(service.size()!=1||
           service.get(1)==null||
           service.get(2)!=null)
            throw new AssertionError(
                "failed replace mutated live state"
            );

        List<DailyChallengeAssignmentService.AssignmentSpec>
            next=
                Arrays.asList(
                    spec(
                        20,
                        "daily:next_a",
                        "Next A",
                        "objective:next_a",
                        4,
                        1,
                        false
                    ),
                    spec(
                        21,
                        "daily:next_b",
                        "Next B",
                        "objective:next_b",
                        2,
                        2,
                        true
                    )
                );

        List<DailyChallengeAssignmentService.Snapshot>
            replaced=
                service.replaceAll(next);

        if(replaced.size()!=2||
           service.get(1)!=null||
           service.get(20).progress!=1||
           !service.get(21).claimed)
            throw new AssertionError(
                "valid replace failed "+
                replaced
            );

        service.clearAssignments();

        if(service.size()!=0||
           !service.snapshot().isEmpty())
            throw new AssertionError(
                "clearAssignments failed"
            );
    }

    private static void assertBoundary(){
        DailyChallengeAssignmentService service=
            new DailyChallengeAssignmentService();

        boolean duplicateKeyRejected=false;

        try{
            service.replaceAll(
                Arrays.asList(
                    spec(
                        1,
                        "daily:same",
                        "A",
                        "objective:a",
                        1,
                        0,
                        false
                    ),
                    spec(
                        2,
                        "DAILY:SAME",
                        "B",
                        "objective:b",
                        1,
                        0,
                        false
                    )
                )
            );
        }catch(IllegalStateException expected){
            duplicateKeyRejected=true;
        }

        if(!duplicateKeyRejected)
            throw new AssertionError(
                "duplicate challenge key accepted"
            );

        boolean duplicateObjectiveRejected=false;

        try{
            service.replaceAll(
                Arrays.asList(
                    spec(
                        1,
                        "daily:a",
                        "A",
                        "objective:same",
                        1,
                        0,
                        false
                    ),
                    spec(
                        2,
                        "daily:b",
                        "B",
                        "OBJECTIVE:SAME",
                        1,
                        0,
                        false
                    )
                )
            );
        }catch(IllegalStateException expected){
            duplicateObjectiveRejected=true;
        }

        if(!duplicateObjectiveRejected)
            throw new AssertionError(
                "duplicate objective key accepted"
            );

        service.assign(
            spec(
                3,
                "daily:immutable",
                "Immutable",
                "objective:immutable",
                1,
                0,
                false
            )
        );

        List<DailyChallengeAssignmentService.Snapshot>
            snapshots=
                service.snapshot();

        boolean immutable=false;

        try{
            snapshots.clear();
        }catch(UnsupportedOperationException expected){
            immutable=true;
        }

        if(!immutable)
            throw new AssertionError(
                "snapshot list mutable"
            );

        for(Field field:
                DailyChallengeAssignmentService
                    .Snapshot.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("packet")||
               name.contains("opcode")||
               name.contains("widget")||
               name.contains("subtype")||
               name.contains("reward"))
                throw new AssertionError(
                    "forbidden state field "+
                    field.getName()
                );
        }
    }

    private static DailyChallengeAssignmentService.AssignmentSpec
        spec(
            int clientChallengeId,
            String challengeKey,
            String label,
            String objectiveKey,
            long goal,
            long initialProgress,
            boolean claimed
        ){
        return new DailyChallengeAssignmentService
            .AssignmentSpec(
                clientChallengeId,
                challengeKey,
                label,
                new ObjectiveDefinition(
                    objectiveKey,
                    goal,
                    "CUSTOM_LOCALLAB"
                ),
                initialProgress,
                claimed,
                "CUSTOM_LOCALLAB"
            );
    }
}
