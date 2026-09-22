package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class GoodwillWellServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_GOODWILL";

    public static void main(String[] args){
        GoodwillWellService service=
            new GoodwillWellService();

        GoodwillWellService.Snapshot opened=
            service.openCampaign(
                "goodwill:test",
                7L,
                POLICY
            );

        require(
            opened.goal==7L&&
            opened.goal!=100L&&
            opened.progress==0L&&
            opened.rawContributed==0L&&
            opened.state==
                GoodwillWellService.State.OPEN&&
            GoodwillWellService
                .PRESENTATION_AUTHORITY
                .equals(
                    opened
                        .presentationAuthority
                ),
            "Goodwill caller-defined goal"
        );

        expect(
            IllegalStateException.class,
            ()->service.openCampaign(
                "goodwill:second",
                5L,
                POLICY
            ),
            "second current Goodwill campaign"
        );

        GoodwillWellService.ContributionResult
            alice=
                service.confirmContributionSettled(
                    "goodwill:test",
                    " Player:Alice ",
                    "receipt:alice:1",
                    3L
                );

        require(
            alice.changed&&
            !alice.fundedNow&&
            "player:alice".equals(
                alice.participant.playerRef
            )&&
            alice.participant.contributed==3L&&
            alice.campaign.progress==3L,
            "Goodwill first contribution"
        );

        GoodwillWellService.ContributionResult
            replay=
                service.confirmContributionSettled(
                    "GOODWILL:TEST",
                    "PLAYER:ALICE",
                    "RECEIPT:ALICE:1",
                    3L
                );

        require(
            !replay.changed&&
            replay.participant
                .contributed==3L,
            "Goodwill receipt idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmContributionSettled(
                    "goodwill:test",
                    "player:bob",
                    "receipt:alice:1",
                    3L
                ),
            "Goodwill receipt replay mismatch"
        );

        service.confirmContributionSettled(
            "goodwill:test",
            "player:bob",
            "receipt:bob:1",
            2L
        );

        GoodwillWellService.ContributionResult
            funded=
                service.confirmContributionSettled(
                    "goodwill:test",
                    "player:alice",
                    "receipt:alice:2",
                    5L
                );

        require(
            funded.fundedNow&&
            funded.campaign.state==
                GoodwillWellService
                    .State.FUNDED&&
            funded.campaign.progress==7L&&
            funded.campaign.rawContributed==10L&&
            funded.participant
                .contributed==8L&&
            funded.campaign
                .participant(
                    "player:bob"
                ).contributed==2L,
            "Goodwill funded/clamped progress"
        );

        // Exact retry remains idempotent even though the receipt caused funding.
        require(
            !service
                .confirmContributionSettled(
                    "goodwill:test",
                    "player:alice",
                    "receipt:alice:2",
                    5L
                ).changed,
            "Goodwill funded receipt retry"
        );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmContributionSettled(
                    "goodwill:test",
                    "player:bob",
                    "receipt:bob:2",
                    1L
                ),
            "Goodwill contribution after funding"
        );

        GoodwillWellService.RewardConfirmationResult
            reward=
                service
                    .confirmServerRewardActivated(
                        "goodwill:test",
                        "activation:external:001"
                    );

        require(
            reward.changed&&
            reward.campaign.state==
                GoodwillWellService
                    .State.REWARD_CONFIRMED&&
            "activation:external:001".equals(
                reward.campaign
                    .rewardActivationReference
            )&&
            service.current()==null,
            "Goodwill external reward activation"
        );

        require(
            !service
                .confirmServerRewardActivated(
                    "goodwill:test",
                    "activation:external:001"
                ).changed,
            "Goodwill reward confirmation idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmServerRewardActivated(
                    "goodwill:test",
                    "activation:external:other"
                ),
            "Goodwill activation replay mismatch"
        );

        cancellationAndNextCampaign(
            service
        );
        immutableSnapshots(service);
        protocolBoundary();

        System.out.println(
            "GOODWILL_WELL_SERVICE_PASS "+
            "callerGoalNot100=true "+
            "normalizedPlayerIdentity=true "+
            "receiptIdempotency=true "+
            "receiptMismatchProtected=true "+
            "playerContributionIsolation=true "+
            "rawTotalPreserved=true "+
            "progressClamped=true "+
            "fundingTransition=true "+
            "postFundingContributionRejected=true "+
            "externalRewardActivationRequired=true "+
            "rewardConfirmationIdempotent=true "+
            "singleCurrentCampaign=true "+
            "cancellationTerminal=true "+
            "itemIdOwned=false "+
            "currencyOwned=false "+
            "pointsOwned=false "+
            "durationOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void cancellationAndNextCampaign(
        GoodwillWellService service
    ){
        GoodwillWellService.Snapshot second=
            service.openCampaign(
                "goodwill:cancel",
                4L,
                POLICY
            );

        require(
            service.current()
                .campaignKey.equals(
                    second.campaignKey
                ),
            "Goodwill next current campaign"
        );

        GoodwillWellService.Snapshot cancelled=
            service.cancelCampaign(
                "goodwill:cancel"
            );

        require(
            cancelled.state==
                GoodwillWellService
                    .State.CANCELLED&&
            service.current()==null,
            "Goodwill cancellation"
        );

        require(
            service.cancelCampaign(
                "goodwill:cancel"
            ).state==
                GoodwillWellService
                    .State.CANCELLED,
            "Goodwill cancellation idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmContributionSettled(
                    "goodwill:cancel",
                    "player:x",
                    "receipt:cancelled",
                    1L
                ),
            "Goodwill contribution to cancelled campaign"
        );
    }

    private static void immutableSnapshots(
        GoodwillWellService service
    ){
        boolean immutable=false;

        try{
            service.get(
                "goodwill:test"
            ).participants.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Goodwill participants mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                GoodwillWellService.class,
                GoodwillWellService.Snapshot.class,
                GoodwillWellService.ParticipantSnapshot.class
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
                   name.contains("itemid")||
                   name.contains("currency")||
                   name.contains("points")||
                   name.contains("duration")||
                   name.contains("hours")||
                   name.contains("multiplier"))
                    throw new AssertionError(
                        "protocol/economic/timing identity leaked into Goodwill Well "+
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

    private GoodwillWellServiceTest(){}
}
