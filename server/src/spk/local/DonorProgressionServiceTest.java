package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class DonorProgressionServiceTest {
    private static final String VERIFIER=
        "EXTERNAL_DONOR_VERIFIER_TEST";

    public static void main(String[] args){
        require(
            DonorProgressionService
                .CreditSource
                .values().length==2,
            "exact donor promotion source count"
        );

        DonorProgressionService service=
            new DonorProgressionService(
                Arrays.asList(
                    new DonorProgressionService
                        .TierDefinition(
                            "tier:bronze",
                            11L
                        ),
                    new DonorProgressionService
                        .TierDefinition(
                            "tier:silver",
                            29L
                        ),
                    new DonorProgressionService
                        .TierDefinition(
                            "tier:gold",
                            47L
                        ),
                    new DonorProgressionService
                        .TierDefinition(
                            "tier:platinum",
                            83L
                        )
                )
            );

        // Synthetic thresholds intentionally prove the client static
        // 150/300/500 presentation is not hardcoded as server policy.
        for(DonorProgressionService.TierDefinition tier:
                service.tiers())
            require(
                tier.requiredPromotionUnits!=150L&&
                tier.requiredPromotionUnits!=300L&&
                tier.requiredPromotionUnits!=500L,
                "client donor threshold promoted into server policy"
            );

        DonorProgressionService.CreditResult first=
            service.confirmPromotionCredit(
                " Player:Alice ",
                DonorProgressionService
                    .CreditSource
                    .VERIFIED_PAYMENT,
                "receipt:payment:1",
                7L,
                VERIFIER
            );

        require(
            first.changed&&
            !first.tierChanged&&
            "player:alice".equals(
                first.player.playerRef
            )&&
            first.player
                .lifetimePromotionUnits==7L&&
            !first.player.promoted()&&
            "tier:bronze".equals(
                first.player.nextTierKey
            )&&
            first.player
                .unitsUntilNextTier
                .longValue()==4L&&
            first.player.creditedBy(
                DonorProgressionService
                    .CreditSource
                    .VERIFIED_PAYMENT
            )==7L&&
            DonorProgressionService
                .PRESENTATION_AUTHORITY
                .equals(
                    first.player
                        .presentationAuthority
                ),
            "donor first verified credit"
        );

        DonorProgressionService.CreditResult replay=
            service.confirmPromotionCredit(
                "PLAYER:ALICE",
                DonorProgressionService
                    .CreditSource
                    .VERIFIED_PAYMENT,
                "RECEIPT:PAYMENT:1",
                7L,
                VERIFIER
            );

        require(
            !replay.changed&&
            replay.player
                .lifetimePromotionUnits==7L,
            "donor receipt idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service.confirmPromotionCredit(
                "player:bob",
                DonorProgressionService
                    .CreditSource
                    .VERIFIED_PAYMENT,
                "receipt:payment:1",
                7L,
                VERIFIER
            ),
            "donor payment receipt reused by another player"
        );

        DonorProgressionService.CreditResult bond=
            service.confirmPromotionCredit(
                "player:alice",
                DonorProgressionService
                    .CreditSource
                    .BOND_OPENED,
                "receipt:payment:1",
                5L,
                VERIFIER
            );

        require(
            bond.changed&&
            bond.tierChanged&&
            bond.previousTierKey==null&&
            "tier:bronze".equals(
                bond.player.currentTierKey
            )&&
            bond.player
                .lifetimePromotionUnits==12L&&
            bond.player.creditedBy(
                DonorProgressionService
                    .CreditSource
                    .VERIFIED_PAYMENT
            )==7L&&
            bond.player.creditedBy(
                DonorProgressionService
                    .CreditSource
                    .BOND_OPENED
            )==5L,
            "donor payment/bond source separation"
        );

        DonorProgressionService.CreditResult silver=
            service.confirmPromotionCredit(
                "player:alice",
                DonorProgressionService
                    .CreditSource
                    .VERIFIED_PAYMENT,
                "receipt:payment:2",
                17L,
                VERIFIER
            );

        require(
            silver.tierChanged&&
            "tier:bronze".equals(
                silver.previousTierKey
            )&&
            "tier:silver".equals(
                silver.player.currentTierKey
            )&&
            silver.player
                .lifetimePromotionUnits==29L&&
            "tier:gold".equals(
                silver.player.nextTierKey
            )&&
            silver.player
                .unitsUntilNextTier
                .longValue()==18L,
            "donor silver tier transition"
        );

        DonorProgressionService.CreditResult max=
            service.confirmPromotionCredit(
                "player:alice",
                DonorProgressionService
                    .CreditSource
                    .BOND_OPENED,
                "receipt:bond:2",
                100L,
                VERIFIER
            );

        require(
            max.tierChanged&&
            "tier:platinum".equals(
                max.player.currentTierKey
            )&&
            max.player.maxTier()&&
            max.player.nextTierKey==null&&
            max.player.nextTierThreshold==null&&
            max.player.unitsUntilNextTier==null,
            "donor max tier projection"
        );

        playerIsolation(service);
        tierGuards();
        immutableSnapshots(service);
        protocolBoundary();

        System.out.println(
            "DONOR_PROGRESSION_SERVICE_PASS "+
            "exactCreditSources2=true "+
            "callerTierThresholds=true "+
            "clientThresholdsHardcoded=false "+
            "strictTierOrdering=true "+
            "normalizedPlayerIdentity=true "+
            "sourceReceiptIdempotency=true "+
            "receiptMismatchProtected=true "+
            "paymentBondSeparation=true "+
            "tierTransitions=true "+
            "noTierBelowFirst=true "+
            "nextTierRemaining=true "+
            "maxTierProjection=true "+
            "currencyOwned=false "+
            "paymentProcessingOwned=false "+
            "bondItemOwned=false "+
            "rewardPerkOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void playerIsolation(
        DonorProgressionService service
    ){
        service.confirmPromotionCredit(
            "player:bob",
            DonorProgressionService
                .CreditSource
                .VERIFIED_PAYMENT,
            "receipt:bob:1",
            3L,
            VERIFIER
        );

        require(
            service.get(
                "player:bob"
            ).lifetimePromotionUnits==3L&&
            service.get(
                "player:alice"
            ).lifetimePromotionUnits==129L,
            "donor player isolation"
        );
    }

    private static void tierGuards(){
        expect(
            IllegalArgumentException.class,
            ()->new DonorProgressionService(
                Arrays.asList(
                    new DonorProgressionService
                        .TierDefinition(
                            "tier:a",
                            10L
                        ),
                    new DonorProgressionService
                        .TierDefinition(
                            "tier:b",
                            10L
                        )
                )
            ),
            "donor equal thresholds"
        );

        expect(
            IllegalArgumentException.class,
            ()->new DonorProgressionService(
                Arrays.asList(
                    new DonorProgressionService
                        .TierDefinition(
                            "tier:a",
                            20L
                        ),
                    new DonorProgressionService
                        .TierDefinition(
                            "tier:b",
                            10L
                        )
                )
            ),
            "donor descending thresholds"
        );

        expect(
            IllegalArgumentException.class,
            ()->new DonorProgressionService(
                Arrays.asList(
                    new DonorProgressionService
                        .TierDefinition(
                            "tier:a",
                            10L
                        ),
                    new DonorProgressionService
                        .TierDefinition(
                            "TIER:A",
                            20L
                        )
                )
            ),
            "donor duplicate tier key"
        );
    }

    private static void immutableSnapshots(
        DonorProgressionService service
    ){
        boolean tiersImmutable=false;
        boolean sourcesImmutable=false;

        try{
            service.tiers().clear();
        }catch(
            UnsupportedOperationException expected
        ){
            tiersImmutable=true;
        }

        try{
            service.get(
                "player:alice"
            ).creditedBySource.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            sourcesImmutable=true;
        }

        require(
            tiersImmutable&&
            sourcesImmutable,
            "donor snapshots mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                DonorProgressionService.class,
                DonorProgressionService.TierDefinition.class,
                DonorProgressionService.PlayerSnapshot.class
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
                   name.contains("dollar")||
                   name.contains("paypal")||
                   name.contains("osrsgp")||
                   name.contains("itemid")||
                   name.contains("reward")||
                   name.contains("perk"))
                    throw new AssertionError(
                        "protocol/payment/reward identity leaked into Donor Progression "+
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

    private DonorProgressionServiceTest(){}
}
