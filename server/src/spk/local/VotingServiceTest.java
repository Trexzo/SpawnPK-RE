package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class VotingServiceTest {
    private static final String VERIFIER=
        "EXTERNAL_VOTE_VERIFIER_TEST";

    public static void main(String[] args){
        require(
            VotingService.Provider
                .values().length==3,
            "Voting exact provider count"
        );

        Set<String> providerNames=
            new HashSet<>();

        for(VotingService.Provider provider:
                VotingService.Provider
                    .values())
            providerNames.add(
                provider.name()
            );

        require(
            providerNames.contains(
                "TOPG"
            )&&
            providerNames.contains(
                "RUNELOCUS"
            )&&
            providerNames.contains(
                "RSPS_LIST"
            )&&
            !providerNames.contains(
                "MOPARSCAPE"
            ),
            "Voting live provider set"
        );

        VotingService service=
            new VotingService();

        VotingService.CreditResult first=
            service.confirmVerifiedVote(
                " Player:Alice ",
                VotingService.Provider.TOPG,
                "receipt:001",
                3L,
                VERIFIER
            );

        require(
            first.changed&&
            "player:alice".equals(
                first.player.playerRef
            )&&
            first.player.balance==3L&&
            first.player.creditedBy(
                VotingService.Provider.TOPG
            )==3L&&
            first.player.lifetimeCredited==3L&&
            VotingService
                .PRESENTATION_AUTHORITY
                .equals(
                    first.player
                        .presentationAuthority
                ),
            "Voting first verified credit"
        );

        VotingService.CreditResult replay=
            service.confirmVerifiedVote(
                "PLAYER:ALICE",
                VotingService.Provider.TOPG,
                "RECEIPT:001",
                3L,
                VERIFIER
            );

        require(
            !replay.changed&&
            replay.player.balance==3L,
            "Voting verified receipt idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service.confirmVerifiedVote(
                "player:bob",
                VotingService.Provider.TOPG,
                "receipt:001",
                3L,
                VERIFIER
            ),
            "Voting receipt reused by another player"
        );

        expect(
            IllegalStateException.class,
            ()->service.confirmVerifiedVote(
                "player:alice",
                VotingService.Provider.TOPG,
                "receipt:001",
                4L,
                VERIFIER
            ),
            "Voting receipt amount mismatch"
        );

        // Provider-scoped receipt namespace: same external receipt text from a
        // different live provider remains a distinct verification identity.
        VotingService.CreditResult bob=
            service.confirmVerifiedVote(
                "player:bob",
                VotingService.Provider.RUNELOCUS,
                "receipt:001",
                2L,
                VERIFIER
            );

        require(
            bob.player.balance==2L&&
            service.get(
                "player:alice"
            ).balance==3L,
            "Voting player/provider isolation"
        );

        service.confirmVerifiedVote(
            "player:alice",
            VotingService.Provider.RSPS_LIST,
            "receipt:002",
            7L,
            VERIFIER
        );

        VotingService.PlayerSnapshot alice=
            service.get(
                "player:alice"
            );

        require(
            alice.balance==10L&&
            alice.lifetimeCredited==10L&&
            alice.creditedBy(
                VotingService.Provider.TOPG
            )==3L&&
            alice.creditedBy(
                VotingService.Provider.RSPS_LIST
            )==7L,
            "Voting caller-defined point amounts"
        );

        reservationLifecycle(service);
        immutableSnapshots(service);
        protocolBoundary();

        System.out.println(
            "VOTING_SERVICE_PASS "+
            "exactProviders3=true "+
            "moparscapeLive=false "+
            "normalizedPlayerIdentity=true "+
            "providerReceiptIdempotency=true "+
            "receiptMismatchProtected=true "+
            "playerProviderIsolation=true "+
            "callerDefinedPoints=true "+
            "balanceAccounting=true "+
            "spendReservation=true "+
            "overspendRejected=true "+
            "cancelRestoresAvailable=true "+
            "externalSettlementBeforeDebit=true "+
            "terminalSpendIdempotent=true "+
            "providerUrlOwned=false "+
            "rewardRateOwned=false "+
            "cooldownOwned=false "+
            "ticketConversionOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void reservationLifecycle(
        VotingService service
    ){
        VotingService.SpendResult reserved=
            service.reserveExternalSpend(
                "player:alice",
                "spend:one",
                5L
            );

        require(
            reserved.changed&&
            reserved.player.balance==10L&&
            reserved.player.reserved==5L&&
            reserved.player.available==5L&&
            reserved.spend.state==
                VotingService
                    .SpendState.RESERVED,
            "Voting point reservation"
        );

        VotingService.SpendResult replay=
            service.reserveExternalSpend(
                "PLAYER:ALICE",
                "SPEND:ONE",
                5L
            );

        require(
            !replay.changed&&
            replay.player.reserved==5L,
            "Voting spend reservation idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service.reserveExternalSpend(
                "player:alice",
                "spend:one",
                4L
            ),
            "Voting spend replay mismatch"
        );

        expect(
            IllegalStateException.class,
            ()->service.reserveExternalSpend(
                "player:alice",
                "spend:too-much",
                6L
            ),
            "Voting overspend"
        );

        VotingService.SpendResult cancelled=
            service.cancelExternalSpend(
                "player:alice",
                "spend:one"
            );

        require(
            cancelled.changed&&
            cancelled.player.balance==10L&&
            cancelled.player.reserved==0L&&
            cancelled.player.available==10L&&
            cancelled.spend.state==
                VotingService
                    .SpendState.CANCELLED,
            "Voting spend cancel"
        );

        require(
            !service.cancelExternalSpend(
                "player:alice",
                "spend:one"
            ).changed,
            "Voting spend cancel idempotency"
        );

        VotingService.SpendResult second=
            service.reserveExternalSpend(
                "player:alice",
                "spend:two",
                4L
            );

        require(
            second.player.balance==10L&&
            second.player.reserved==4L&&
            second.player.available==6L,
            "Voting second reservation"
        );

        VotingService.SpendResult settled=
            service.confirmExternalSpendSettled(
                "player:alice",
                "spend:two"
            );

        require(
            settled.changed&&
            settled.player.balance==6L&&
            settled.player.reserved==0L&&
            settled.player.available==6L&&
            settled.player.lifetimeSpent==4L&&
            settled.spend.state==
                VotingService
                    .SpendState.SETTLED,
            "Voting post-external-settlement debit"
        );

        require(
            !service
                .confirmExternalSpendSettled(
                    "player:alice",
                    "spend:two"
                ).changed,
            "Voting spend settlement idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service.cancelExternalSpend(
                "player:alice",
                "spend:two"
            ),
            "Voting settled spend cancel"
        );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmExternalSpendSettled(
                    "player:alice",
                    "spend:one"
                ),
            "Voting cancelled spend settle"
        );
    }

    private static void immutableSnapshots(
        VotingService service
    ){
        VotingService.PlayerSnapshot snapshot=
            service.get(
                "player:alice"
            );

        boolean providersImmutable=false;
        boolean spendsImmutable=false;

        try{
            snapshot.creditedByProvider
                .clear();
        }catch(
            UnsupportedOperationException expected
        ){
            providersImmutable=true;
        }

        try{
            snapshot.spends.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            spendsImmutable=true;
        }

        require(
            providersImmutable&&
            spendsImmutable,
            "Voting snapshots mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                VotingService.class,
                VotingService.PlayerSnapshot.class,
                VotingService.SpendSnapshot.class
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
                   name.contains("url")||
                   name.contains("browser")||
                   name.contains("cooldown")||
                   name.contains("daily")||
                   name.contains("ticket")||
                   name.contains("rate"))
                    throw new AssertionError(
                        "protocol/provider-policy identity leaked into Voting "+
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

    private VotingServiceTest(){}
}
