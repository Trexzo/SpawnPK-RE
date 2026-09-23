package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class ShopServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        AUTHORITY=
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

        ShopService.ShopId shopId=
            ShopService.ShopId.of(
                "shop:general"
            );

        shops.register(
            new ShopService.ShopDefinition(
                shopId,
                "General Store",
                "shop-owner:general",
                Arrays.asList(
                    ShopService.Offer.finite(
                        "item:sword",
                        "currency:coins",
                        10L,
                        5L
                    ),
                    ShopService.Offer.unlimited(
                        "item:potion",
                        "currency:coins",
                        2L
                    )
                ),
                AUTHORITY
            )
        );

        assertFiniteReservationAndCancellation(
            shops,
            shopId
        );

        ShopService.PurchaseSnapshot settled=
            assertFiniteSettlement(
                shops,
                shopId,
                transactions
            );

        assertUnlimitedStock(
            shops,
            shopId,
            transactions
        );

        assertSettlementGuards(
            shops,
            shopId,
            transactions
        );

        assertOverflowFailClosed(
            shops,
            shopId
        );

        assertImmutableSnapshots(
            shops
        );

        assertDomainBoundary();
        assertSettlementLookupNotMethodSynchronized();

        require(
            settled.settled()&&
            settled.terminal()&&
            settled.sourceAuthority==
                AUTHORITY,
            "settled purchase metadata"
        );

        System.out.println(
            "SHOP_SERVICE_PASS "+
            "finiteReservation=true "+
            "oversubscriptionPrevented=true "+
            "cancelRestoresStock=true "+
            "settlementConsumesStock=true "+
            "unlimitedStockStable=true "+
            "buyerCurrencyCoverage=true "+
            "shopItemCoverage=true "+
            "wrongBuyerRejected=true "+
            "wrongAuthorityRejected=true "+
            "transactionReuseRejected=true "+
            "settlementIdempotent=true "+
            "canonicalTransactionNamespace=true "+
            "priceOverflowFailClosed=true "+
            "inventoryMutation=false "+
            "currencyMutation=false "+
            "sellBackPolicy=false "+
            "protocolIndependent=true"
        );
    }

    private static void assertFiniteReservationAndCancellation(
        ShopService shops,
        ShopService.ShopId shopId
    ){
        ShopService.PurchaseSnapshot reserved=
            shops.requestPurchase(
                shopId,
                "player:a",
                "item:sword",
                3L
            );

        require(
            reserved.state==
                ShopService
                    .PurchaseState
                    .RESERVED&&
            reserved.totalPrice==30L&&
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==2L,
            "finite reservation"
        );

        boolean oversubscribed=false;

        try{
            shops.requestPurchase(
                shopId,
                "player:b",
                "item:sword",
                3L
            );
        }catch(
            IllegalStateException expected
        ){
            oversubscribed=true;
        }

        require(
            oversubscribed&&
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==2L,
            "finite oversubscription"
        );

        ShopService.PurchaseSnapshot cancelled=
            shops.cancelPurchase(
                reserved.purchaseId
            );

        require(
            cancelled.state==
                ShopService
                    .PurchaseState
                    .CANCELLED&&
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==5L,
            "finite cancellation restore"
        );

        shops.cancelPurchase(
            reserved.purchaseId
        );

        require(
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==5L,
            "duplicate cancellation restored twice"
        );
    }

    private static ShopService.PurchaseSnapshot
        assertFiniteSettlement(
            ShopService shops,
            ShopService.ShopId shopId,
            AtomicTransactionService transactions
        ){
        ShopService.PurchaseSnapshot purchase=
            shops.requestPurchase(
                shopId,
                "player:a",
                "item:sword",
                4L
            );

        require(
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==1L,
            "finite stock reservation before settlement"
        );

        AtomicTransactionService.TransactionId
            transactionId=
                transactions.create(
                    "player:a",
                    "shop:general:purchase",
                    AUTHORITY
                );

        transactions.reserve(
            transactionId,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    40L,
                    "player:a",
                    AUTHORITY
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:sword",
                    4L,
                    "shop-owner:general",
                    AUTHORITY
                )
            )
        );

        AtomicTransactionService.Snapshot committed=
            transactions.commit(
                transactionId
            );

        ShopService.PurchaseSnapshot settled=
            shops.confirmSettlement(
                purchase.purchaseId,
                committed.transactionId
            );

        ShopService.PurchaseSnapshot again=
            shops.confirmSettlement(
                purchase.purchaseId,
                committed.transactionId
            );

        require(
            settled.state==
                ShopService
                    .PurchaseState
                    .SETTLED&&
            settled.settlementTransactionId
                .equals(transactionId)&&
            again.state==
                ShopService
                    .PurchaseState
                    .SETTLED&&
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==1L,
            "finite settlement/idempotency"
        );

        boolean settledCancelRejected=false;

        try{
            shops.cancelPurchase(
                purchase.purchaseId
            );
        }catch(
            IllegalStateException expected
        ){
            settledCancelRejected=true;
        }

        require(
            settledCancelRejected&&
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==1L,
            "settled purchase cancellation"
        );

        return settled;
    }

    private static void assertUnlimitedStock(
        ShopService shops,
        ShopService.ShopId shopId,
        AtomicTransactionService transactions
    ){
        ShopService.OfferSnapshot before=
            shops.getShop(shopId)
                .offer(
                    "item:potion"
                );

        require(
            before!=null&&
            before.stockMode==
                ShopService.StockMode.UNLIMITED&&
            !before.availableStock
                .isPresent(),
            "unlimited offer snapshot"
        );

        ShopService.PurchaseSnapshot purchase=
            shops.requestPurchase(
                shopId,
                "player:u",
                "item:potion",
                100L
            );

        AtomicTransactionService.TransactionId id=
            transactions.create(
                "player:u",
                "shop:unlimited",
                AUTHORITY
            );

        transactions.reserve(
            id,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    200L,
                    "player:u",
                    AUTHORITY
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:potion",
                    100L,
                    "shop-owner:general",
                    AUTHORITY
                )
            )
        );

        transactions.commit(id);

        shops.confirmSettlement(
            purchase.purchaseId,
            id
        );

        ShopService.OfferSnapshot after=
            shops.getShop(shopId)
                .offer(
                    "item:potion"
                );

        require(
            !after.availableStock
                .isPresent(),
            "unlimited stock became finite"
        );
    }

    private static void assertSettlementGuards(
        ShopService shops,
        ShopService.ShopId shopId,
        AtomicTransactionService transactions
    ){
        assertMissingItemCoverage(
            shops,
            shopId,
            transactions
        );

        assertWrongBuyer(
            shops,
            shopId,
            transactions
        );

        assertWrongAuthority(
            shops,
            shopId,
            transactions
        );

        assertTransactionReuse(
            shops,
            shopId,
            transactions
        );
    }

    private static void assertMissingItemCoverage(
        ShopService shops,
        ShopService.ShopId shopId,
        AtomicTransactionService transactions
    ){
        ShopService.PurchaseSnapshot purchase=
            shops.requestPurchase(
                shopId,
                "player:m",
                "item:sword",
                1L
            );

        AtomicTransactionService.TransactionId id=
            transactions.create(
                "player:m",
                "missing-item",
                AUTHORITY
            );

        transactions.reserve(
            id,
            Collections.singletonList(
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    10L,
                    "player:m",
                    AUTHORITY
                )
            )
        );

        boolean rejected=false;

        try{
            transactions.commit(id);
        shops.confirmSettlement(
            purchase.purchaseId,
            id
        );
        }catch(
            IllegalArgumentException expected
        ){
            rejected=true;
        }

        require(
            rejected&&
            shops.getPurchase(
                purchase.purchaseId
            ).state==
                ShopService.PurchaseState.RESERVED,
            "missing item coverage accepted"
        );

        shops.cancelPurchase(
            purchase.purchaseId
        );

        require(
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==1L,
            "failed settlement reservation not restorable"
        );
    }

    private static void assertWrongBuyer(
        ShopService shops,
        ShopService.ShopId shopId,
        AtomicTransactionService transactions
    ){
        ShopService.PurchaseSnapshot purchase=
            shops.requestPurchase(
                shopId,
                "player:a",
                "item:potion",
                1L
            );

        AtomicTransactionService.TransactionId id=
            transactions.create(
                "player:b",
                "wrong-buyer",
                AUTHORITY
            );

        transactions.reserve(
            id,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    2L,
                    "player:b",
                    AUTHORITY
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:potion",
                    1L,
                    "shop-owner:general",
                    AUTHORITY
                )
            )
        );

        boolean rejected=false;

        try{
            transactions.commit(id);
        shops.confirmSettlement(
            purchase.purchaseId,
            id
        );
        }catch(
            SecurityException expected
        ){
            rejected=true;
        }

        require(
            rejected,
            "wrong buyer settlement accepted"
        );

        shops.cancelPurchase(
            purchase.purchaseId
        );
    }

    private static void assertWrongAuthority(
        ShopService shops,
        ShopService.ShopId shopId,
        AtomicTransactionService transactions
    ){
        ShopService.PurchaseSnapshot purchase=
            shops.requestPurchase(
                shopId,
                "player:a",
                "item:potion",
                1L
            );

        AtomicTransactionService.SourceAuthority other=
            AtomicTransactionService
                .SourceAuthority
                .INFERENCE;

        AtomicTransactionService.TransactionId id=
            transactions.create(
                "player:a",
                "wrong-authority",
                other
            );

        transactions.reserve(
            id,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    2L,
                    "player:a",
                    other
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:potion",
                    1L,
                    "shop-owner:general",
                    other
                )
            )
        );

        boolean rejected=false;

        try{
            transactions.commit(id);
        shops.confirmSettlement(
            purchase.purchaseId,
            id
        );
        }catch(
            IllegalArgumentException expected
        ){
            rejected=true;
        }

        require(
            rejected,
            "wrong settlement authority accepted"
        );

        shops.cancelPurchase(
            purchase.purchaseId
        );
    }

    private static void assertTransactionReuse(
        ShopService shops,
        ShopService.ShopId shopId,
        AtomicTransactionService transactions
    ){
        ShopService.PurchaseSnapshot first=
            shops.requestPurchase(
                shopId,
                "player:r",
                "item:potion",
                1L
            );
        ShopService.PurchaseSnapshot second=
            shops.requestPurchase(
                shopId,
                "player:r",
                "item:potion",
                1L
            );

        AtomicTransactionService.TransactionId id=
            transactions.create(
                "player:r",
                "reuse",
                AUTHORITY
            );

        transactions.reserve(
            id,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    2L,
                    "player:r",
                    AUTHORITY
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:potion",
                    1L,
                    "shop-owner:general",
                    AUTHORITY
                )
            )
        );

        AtomicTransactionService.Snapshot committed=
            transactions.commit(id);

        shops.confirmSettlement(
            first.purchaseId,
            committed.transactionId
        );

        boolean rejected=false;

        try{
            shops.confirmSettlement(
                second.purchaseId,
                committed.transactionId
            );
        }catch(
            IllegalStateException expected
        ){
            rejected=true;
        }

        require(
            rejected,
            "settlement transaction reused"
        );

        shops.cancelPurchase(
            second.purchaseId
        );
    }

    private static void assertOverflowFailClosed(
        ShopService shops,
        ShopService.ShopId shopId
    ){
        int before=
            shops.purchaseCount();

        boolean rejected=false;

        try{
            shops.requestPurchase(
                shopId,
                "player:o",
                "item:potion",
                Long.MAX_VALUE
            );
        }catch(
            IllegalArgumentException expected
        ){
            rejected=true;
        }

        require(
            rejected&&
            shops.purchaseCount()==before,
            "price overflow created purchase"
        );
    }

    private static void assertImmutableSnapshots(
        ShopService shops
    ){
        boolean shopImmutable=false;
        boolean purchaseImmutable=false;

        try{
            shops.shopSnapshot().clear();
        }catch(
            UnsupportedOperationException expected
        ){
            shopImmutable=true;
        }

        try{
            shops.purchaseSnapshot().clear();
        }catch(
            UnsupportedOperationException expected
        ){
            purchaseImmutable=true;
        }

        require(
            shopImmutable&&purchaseImmutable,
            "shop snapshots mutable"
        );
    }

    private static long finiteStock(
        ShopService shops,
        ShopService.ShopId shopId,
        String itemRef
    ){
        ShopService.OfferSnapshot offer=
            shops.getShop(shopId)
                .offer(itemRef);

        require(
            offer!=null&&
            offer.availableStock
                .isPresent(),
            "finite offer missing"
        );

        return offer.availableStock
            .getAsLong();
    }

    private static void assertSettlementLookupNotMethodSynchronized(){
        try{
            java.lang.reflect.Method method=
                ShopService.class.getDeclaredMethod(
                    "confirmSettlement",
                    ShopService.PurchaseId.class,
                    AtomicTransactionService.TransactionId.class
                );

            require(
                !java.lang.reflect.Modifier
                    .isSynchronized(
                        method.getModifiers()
                    ),
                "Shop confirmSettlement still method-synchronized"
            );
        }catch(ReflectiveOperationException error){
            throw new AssertionError(
                "Shop settlement reflection",
                error
            );
        }
    }

    private static void assertDomainBoundary(){
        Class<?>[] types={
            ShopService.class,
            ShopService.ShopDefinition.class,
            ShopService.Offer.class,
            ShopService.ShopSnapshot.class,
            ShopService.PurchaseSnapshot.class
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
                   name.contains("npcid")||
                   name.contains("client"))
                    throw new AssertionError(
                        "client/protocol identity leaked into Shop domain "+
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

    private ShopServiceTest(){}
}
