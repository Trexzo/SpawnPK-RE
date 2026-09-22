package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class DailyChallengeApplicationServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_DAILY_CHALLENGES";

    public static void main(String[] args){
        DailyChallengeApplicationService service=
            new DailyChallengeApplicationService();

        DailyChallengeApplicationService.PlayerSnapshot
            alice=
                service.replaceAll(
                    " Player:Alice ",
                    Arrays.asList(
                        spec(
                            10,
                            "daily:kills",
                            "Kill three validated targets",
                            "opaque-A:boss-family",
                            "opaque-B:display-mode",
                            "objective:daily:kills",
                            3L,
                            0L,
                            false,
                            POLICY
                        ),
                        spec(
                            11,
                            "daily:vote",
                            "Complete one validated vote",
                            "",
                            "opaque-B:vote",
                            "objective:daily:vote",
                            1L,
                            0L,
                            false,
                            POLICY
                        )
                    )
                );

        service.replaceAll(
            "player:bob",
            Collections.singletonList(
                spec(
                    10,
                    "daily:kills",
                    "Bob's independent challenge",
                    "bob-A",
                    "bob-B",
                    "objective:daily:kills",
                    3L,
                    0L,
                    false,
                    POLICY
                )
            )
        );

        require(
            "player:alice".equals(
                alice.playerRef
            )&&
            alice.challenges.size()==2&&
            "opaque-A:boss-family".equals(
                alice.challenge(
                    "DAILY:KILLS"
                ).metadataA
            )&&
            "opaque-B:display-mode".equals(
                alice.challenge(
                    "daily:kills"
                ).metadataB
            )&&
            DailyChallengeApplicationService
                .PRESENTATION_AUTHORITY
                .equals(
                    alice.challenge(
                        "daily:kills"
                    ).presentationAuthority
                ),
            "Daily Challenge exact application metadata"
        );

        DailyChallengeApplicationService.ProgressResult
            first=
                service.recordValidatedProgress(
                    "PLAYER:ALICE",
                    "daily:kills",
                    2L
                );

        require(
            !first.completedNow&&
            first.challenge.current==2L&&
            service.info(
                "player:bob",
                "daily:kills"
            ).current==0L,
            "Daily Challenge player-scoped progress"
        );

        DailyChallengeApplicationService.ProgressResult
            complete=
                service.recordValidatedProgress(
                    "player:alice",
                    "daily:kills",
                    99L
                );

        require(
            complete.completedNow&&
            complete.challenge.current==3L&&
            complete.challenge.target==3L&&
            complete.challenge.complete&&
            alice.completedUnclaimedCount()==0&&
            service.get(
                "player:alice"
            ).completedUnclaimedCount()==1,
            "Daily Challenge completion/goal clamp"
        );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmRewardSettledAndMarkClaimed(
                    "player:alice",
                    "daily:vote"
                ),
            "incomplete Daily Challenge claim"
        );

        DailyChallengeApplicationService.ClaimResult
            claimed=
                service
                    .confirmRewardSettledAndMarkClaimed(
                        "player:alice",
                        "daily:kills"
                    );

        require(
            claimed.changed&&
            claimed.challenge.claimed&&
            !service
                .confirmRewardSettledAndMarkClaimed(
                    "player:alice",
                    "daily:kills"
                ).changed,
            "Daily Challenge post-settlement claim"
        );

        failureAtomicReplacement(service);
        authorityFence();
        immutableSnapshot(service);
        protocolBoundary();

        System.out.println(
            "DAILY_CHALLENGE_APPLICATION_PASS "+
            "exactMaxRows10=true "+
            "playerIsolation=true "+
            "normalizedPlayerIdentity=true "+
            "opaqueMetadataPreserved=true "+
            "objectiveAuthorityFence=true "+
            "replacementAtomic=true "+
            "semanticKeyProgress=true "+
            "goalClampInherited=true "+
            "infoProjection=true "+
            "incompleteClaimRejected=true "+
            "externalRewardSettlementRequired=true "+
            "claimIdempotent=true "+
            "resetPolicyOwned=false "+
            "rewardPayloadAbsent=true "+
            "protocolIndependent=true"
        );
    }

    private static void failureAtomicReplacement(
        DailyChallengeApplicationService service
    ){
        DailyChallengeApplicationService.PlayerSnapshot
            before=
                service.get(
                    "player:alice"
                );

        ArrayList<
            DailyChallengeApplicationService
                .AssignmentSpec
        > tooMany=
            new ArrayList<>();

        for(int i=0;i<11;i++)
            tooMany.add(
                spec(
                    100+i,
                    "daily:many:"+i,
                    "Challenge "+i,
                    "A"+i,
                    "B"+i,
                    "objective:many:"+i,
                    1L,
                    0L,
                    false,
                    POLICY
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceAll(
                "player:alice",
                tooMany
            ),
            "Daily Challenge >10 rows"
        );

        expect(
            IllegalStateException.class,
            ()->service.replaceAll(
                "player:alice",
                Arrays.asList(
                    spec(
                        20,
                        "daily:duplicate-id-a",
                        "A",
                        "A",
                        "A",
                        "objective:duplicate-id-a",
                        1L,
                        0L,
                        false,
                        POLICY
                    ),
                    spec(
                        20,
                        "daily:duplicate-id-b",
                        "B",
                        "B",
                        "B",
                        "objective:duplicate-id-b",
                        1L,
                        0L,
                        false,
                        POLICY
                    )
                )
            ),
            "Daily Challenge duplicate client id"
        );

        DailyChallengeApplicationService.PlayerSnapshot
            after=
                service.get(
                    "player:alice"
                );

        require(
            after.challenges.size()==
                before.challenges.size()&&
            after.challenge(
                "daily:kills"
            )!=null&&
            after.challenge(
                "daily:kills"
            ).claimed,
            "failed Daily Challenge replace mutated live state"
        );
    }

    private static void authorityFence(){
        expect(
            IllegalArgumentException.class,
            ()->new DailyChallengeApplicationService
                .AssignmentSpec(
                    1,
                    "daily:authority",
                    "Authority mismatch",
                    "A",
                    "B",
                    new ObjectiveDefinition(
                        "objective:authority",
                        1L,
                        "EXACT_CURRENT_CLIENT"
                    ),
                    0L,
                    false,
                    POLICY
                ),
            "Daily Challenge objective authority mismatch"
        );
    }

    private static DailyChallengeApplicationService
        .AssignmentSpec spec(
            int clientChallengeId,
            String challengeKey,
            String description,
            String metadataA,
            String metadataB,
            String objectiveKey,
            long goal,
            long initialProgress,
            boolean claimed,
            String sourceAuthority
        ){
        return new DailyChallengeApplicationService
            .AssignmentSpec(
                clientChallengeId,
                challengeKey,
                description,
                metadataA,
                metadataB,
                new ObjectiveDefinition(
                    objectiveKey,
                    goal,
                    sourceAuthority
                ),
                initialProgress,
                claimed,
                sourceAuthority
            );
    }

    private static void immutableSnapshot(
        DailyChallengeApplicationService service
    ){
        boolean immutable=false;

        try{
            service.get(
                "player:alice"
            ).challenges.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Daily Challenge application snapshot mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                DailyChallengeApplicationService.class,
                DailyChallengeApplicationService.AssignmentSpec.class,
                DailyChallengeApplicationService.ChallengeSnapshot.class,
                DailyChallengeApplicationService.PlayerSnapshot.class
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
                   name.contains("subtype")||
                   name.contains("interface")||
                   name.contains("rewarditem")||
                   name.contains("rewardamount"))
                    throw new AssertionError(
                        "protocol/reward payload leaked into Daily Challenges "+
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

    private DailyChallengeApplicationServiceTest(){}
}
