package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class BloodPoolStoreServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args){
        AtomicTransactionService transactions=
            new AtomicTransactionService();

        ShopService shops=
            new ShopService(
                transactions
            );

        ShopService.ShopId bloodPool=
            ShopService.ShopId.of(
                "shop:blood-pool"
            );

        shops.register(
            new ShopService.ShopDefinition(
                bloodPool,
                "Blood Pool",
                "shop-owner:blood-pool",
                Arrays.asList(
                    ShopService.Offer.finite(
                        "item:blood:a",
                        "currency:blood",
                        7L,
                        5L
                    ),
                    ShopService.Offer.unlimited(
                        "item:blood:b",
                        "currency:blood",
                        11L
                    ),
                    ShopService.Offer.finite(
                        "item:blood:c",
                        "currency:blood",
                        13L,
                        2L
                    )
                ),
                POLICY
            )
        );

        BloodPoolStoreService service=
            new BloodPoolStoreService(
                shops,
                bloodPool,
                POLICY
            );

        BloodPoolStoreService.Snapshot initial=
            service.replaceSlots(
                Arrays.asList(
                    new BloodPoolStoreService
                        .SlotSpec(
                            "slot:a",
                            "item:blood:a"
                        ),
                    new BloodPoolStoreService
                        .SlotSpec(
                            "slot:b",
                            "item:blood:b"
                        )
                )
            );

        require(
            initial.slots.size()==2&&
            "slot:a".equals(
                initial.slots.get(0)
                    .slotKey
            )&&
            "slot:b".equals(
                initial.slots.get(1)
                    .slotKey
            )&&
            initial.slot(
                "slot:a"
            ).priceEach==7L&&
            initial.slot(
                "slot:a"
            ).availableStock
                .getAsLong()==5L&&
            !initial.slot(
                "slot:b"
            ).availableStock
                .isPresent()&&
            BloodPoolStoreService
                .PRESENTATION_AUTHORITY
                .equals(
                    initial
                        .presentationAuthority
                ),
            "Blood Pool initial ordered projection"
        );

        failureAtomicReplacement(
            service
        );

        BloodPoolStoreService.Snapshot reset=
            service.replaceSlots(
                Collections.emptyList()
            );

        require(
            reset.slots.isEmpty()&&
            service.slotCount()==0,
            "Blood Pool reset/clear"
        );

        service.replaceSlots(
            Arrays.asList(
                new BloodPoolStoreService
                    .SlotSpec(
                        "slot:a",
                        "item:blood:a"
                    ),
                new BloodPoolStoreService
                    .SlotSpec(
                        "slot:c",
                        "item:blood:c"
                    )
            )
        );

        ShopService.PurchaseSnapshot purchase=
            service.requestPurchase(
                " Player:Alice ",
                "slot:a",
                3L
            );

        require(
            "player:alice".equals(
                purchase.buyerRef
            )&&
            purchase.shopId.equals(
                bloodPool
            )&&
            service.snapshot()
                .slot(
                    "slot:a"
                )
                .availableStock
                .getAsLong()==2L,
            "Blood Pool purchase/live stock projection"
        );

        // Rebuild presentation while purchase is still reserved.
        service.replaceSlots(
            Collections.singletonList(
                new BloodPoolStoreService
                    .SlotSpec(
                        "slot:c",
                        "item:blood:c"
                    )
            )
        );

        require(
            service.getPurchase(
                purchase.purchaseId
            ).itemRef.equals(
                "item:blood:a"
            )&&
            service.snapshot()
                .slot(
                    "slot:a"
                )==null,
            "Blood Pool in-flight purchase stability"
        );

        service.cancelPurchase(
            purchase.purchaseId
        );

        service.replaceSlots(
            Arrays.asList(
                new BloodPoolStoreService
                    .SlotSpec(
                        "slot:a",
                        "item:blood:a"
                    ),
                new BloodPoolStoreService
                    .SlotSpec(
                        "slot:c",
                        "item:blood:c"
                    )
            )
        );

        require(
            service.snapshot()
                .slot(
                    "slot:a"
                )
                .availableStock
                .getAsLong()==5L,
            "Blood Pool cancellation restored live stock"
        );

        ShopService.PurchaseSnapshot settle=
            service.requestPurchase(
                "player:alice",
                "slot:a",
                2L
            );

        AtomicTransactionService.TransactionId tx=
            transactions.create(
                "player:alice",
                "blood-pool:purchase",
                POLICY
            );

        transactions.reserve(
            tx,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:blood",
                    14L,
                    "player:alice",
                    POLICY
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:blood:a",
                    2L,
                    "shop-owner:blood-pool",
                    POLICY
                )
            )
        );

        transactions.commit(tx);

        ShopService.PurchaseSnapshot settled=
            service.confirmSettlement(
                settle.purchaseId,
                tx
            );

        require(
            settled.state==
                ShopService
                    .PurchaseState.SETTLED&&
            service.snapshot()
                .slot(
                    "slot:a"
                )
                .availableStock
                .getAsLong()==3L,
            "Blood Pool settlement delegation"
        );

        authorityFence(
            shops,
            bloodPool
        );
        foreignPurchaseFence(
            shops,
            service
        );
        immutableSnapshots(service);
        protocolBoundary();

        System.out.println(
            "BLOOD_POOL_STORE_SERVICE_PASS "+
            "shopServiceReuse=true "+
            "authorityFence=true "+
            "replacementAtomic=true "+
            "emptyReset=true "+
            "orderedRebuild=true "+
            "liveStockProjection=true "+
            "normalizedBuyer=true "+
            "purchaseDelegation=true "+
            "finiteReservationVisible=true "+
            "cancelRestoresStock=true "+
            "settlementDelegation=true "+
            "inflightPurchaseStable=true "+
            "sellBackOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void failureAtomicReplacement(
        BloodPoolStoreService service
    ){
        BloodPoolStoreService.Snapshot before=
            service.snapshot();

        expect(
            IllegalArgumentException.class,
            ()->service.replaceSlots(
                Arrays.asList(
                    new BloodPoolStoreService
                        .SlotSpec(
                            "slot:dup",
                            "item:blood:a"
                        ),
                    new BloodPoolStoreService
                        .SlotSpec(
                            "SLOT:DUP",
                            "item:blood:b"
                        )
                )
            ),
            "duplicate Blood Pool slotKey"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceSlots(
                Arrays.asList(
                    new BloodPoolStoreService
                        .SlotSpec(
                            "slot:x",
                            "item:blood:a"
                        ),
                    new BloodPoolStoreService
                        .SlotSpec(
                            "slot:y",
                            "ITEM:BLOOD:A"
                        )
                )
            ),
            "duplicate Blood Pool itemRef"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceSlots(
                Collections.singletonList(
                    new BloodPoolStoreService
                        .SlotSpec(
                            "slot:missing",
                            "item:not-offered"
                        )
                )
            ),
            "Blood Pool missing Shop offer"
        );

        BloodPoolStoreService.Snapshot after=
            service.snapshot();

        require(
            after.slots.size()==
                before.slots.size()&&
            "slot:a".equals(
                after.slots.get(0)
                    .slotKey
            )&&
            "slot:b".equals(
                after.slots.get(1)
                    .slotKey
            ),
            "failed Blood Pool replacement mutated live slots"
        );
    }

    private static void authorityFence(
        ShopService shops,
        ShopService.ShopId shopId
    ){
        expect(
            IllegalArgumentException.class,
            ()->new BloodPoolStoreService(
                shops,
                shopId,
                AtomicTransactionService
                    .SourceAuthority
                    .INFERENCE
            ),
            "Blood Pool authority mismatch"
        );
    }

    private static void foreignPurchaseFence(
        ShopService shops,
        BloodPoolStoreService service
    ){
        ShopService.ShopId otherId=
            ShopService.ShopId.of(
                "shop:other"
            );

        shops.register(
            new ShopService.ShopDefinition(
                otherId,
                "Other",
                "shop-owner:other",
                Collections.singletonList(
                    ShopService.Offer.unlimited(
                        "item:other",
                        "currency:blood",
                        1L
                    )
                ),
                POLICY
            )
        );

        ShopService.PurchaseSnapshot foreign=
            shops.requestPurchase(
                otherId,
                "player:x",
                "item:other",
                1L
            );

        expect(
            IllegalArgumentException.class,
            ()->service.cancelPurchase(
                foreign.purchaseId
            ),
            "foreign Shop purchase accepted by Blood Pool"
        );

        shops.cancelPurchase(
            foreign.purchaseId
        );
    }

    private static void immutableSnapshots(
        BloodPoolStoreService service
    ){
        boolean immutable=false;

        try{
            service.snapshot()
                .slots.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Blood Pool slot projection mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                BloodPoolStoreService.class,
                BloodPoolStoreService.SlotSpec.class,
                BloodPoolStoreService.SlotSnapshot.class,
                BloodPoolStoreService.Snapshot.class
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
                   name.contains("target37")||
                   name.contains("clientid")||
                   name.contains("sellback"))
                    throw new AssertionError(
                        "protocol/client/sellback identity leaked into Blood Pool Store "+
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

    private BloodPoolStoreServiceTest(){}
}
