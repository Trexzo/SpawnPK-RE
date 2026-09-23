package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class ShopSellbackServiceTest {
    private static final AtomicTransactionService.SourceAuthority AUTH=
        AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB;

    public static void main(String[] args){
        AtomicTransactionService transactions=
            new AtomicTransactionService();

        ShopService shops=
            new ShopService(transactions);

        ShopService.ShopId shopId=
            ShopService.ShopId.of("shop:general");

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
                    ),
                    ShopService.Offer.unlimited(
                        "item:overflow",
                        "currency:coins",
                        1L
                    )
                ),
                AUTH
            )
        );

        ShopSellbackService sellbacks=
            new ShopSellbackService(shops);

        sellbacks.register(
            new ShopSellbackService.BuybackDefinition(
                shopId,
                "item:sword",
                "currency:coins",
                4L,
                AUTH
            )
        );
        sellbacks.register(
            new ShopSellbackService.BuybackDefinition(
                shopId,
                "item:potion",
                "currency:coins",
                1L,
                AUTH
            )
        );
        sellbacks.register(
            new ShopSellbackService.BuybackDefinition(
                shopId,
                "item:overflow",
                "currency:coins",
                Long.MAX_VALUE,
                AUTH
            )
        );

        finiteCancelDoesNotChangeVisibleStock(
            sellbacks,
            shops,
            shopId
        );

        ShopSellbackService.SaleSnapshot settled=
            finiteSettlementCreditsStock(
                sellbacks,
                shops,
                transactions,
                shopId
            );

        purchaseCancelAfterSellbackRestoresRaisedStock(
            shops,
            shopId
        );

        unlimitedSettlementLeavesUnlimited(
            sellbacks,
            shops,
            transactions,
            shopId
        );

        settlementProofGuards(
            sellbacks,
            transactions,
            shopId
        );

        crossFlowReplayGuards(
            sellbacks,
            shops,
            transactions,
            shopId
        );

        payoutOverflowFailsBeforeSale(
            sellbacks,
            shopId
        );

        immutableSnapshots(sellbacks);
        protocolBoundary();
        assertExternalCompositionNotMethodSynchronized();

        require(
            settled.state==
                ShopSellbackService.SaleState.SETTLED&&
            settled.terminal()&&
            settled.sourceAuthority==AUTH,
            "settled sale metadata"
        );

        System.out.println(
            "SHOP_SELLBACK_SERVICE_PASS "+
            "callerDefinedBuyback=true "+
            "finiteIncomingReservation=true "+
            "cancelReleasesIncoming=true "+
            "settledFiniteStockCredit=true "+
            "unlimitedStockStable=true "+
            "sellerItemCoverage=true "+
            "shopCurrencyCoverage=true "+
            "wrongSellerRejected=true "+
            "authorityMismatchRejected=true "+
            "crossPurchaseSaleReplayRejected=true "+
            "crossSalePurchaseReplayRejected=true "+
            "sameSaleIdempotent=true "+
            "raisedStockPurchaseCancelSafe=true "+
            "payoutOverflowFailClosed=true "+
            "inventoryMutation=false "+
            "currencyMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void finiteCancelDoesNotChangeVisibleStock(
        ShopSellbackService sellbacks,
        ShopService shops,
        ShopService.ShopId shopId
    ){
        long before=finiteStock(
            shops,
            shopId,
            "item:sword"
        );

        ShopSellbackService.SaleSnapshot sale=
            sellbacks.requestSale(
                shopId,
                "player:a",
                "item:sword",
                2L
            );

        require(
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==before,
            "incoming reservation changed visible stock"
        );

        sellbacks.cancelSale(sale.saleId);
        sellbacks.cancelSale(sale.saleId);

        require(
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==before,
            "cancelled sellback changed visible stock"
        );
    }

    private static ShopSellbackService.SaleSnapshot
        finiteSettlementCreditsStock(
            ShopSellbackService sellbacks,
            ShopService shops,
            AtomicTransactionService transactions,
            ShopService.ShopId shopId
        ){
        ShopSellbackService.SaleSnapshot sale=
            sellbacks.requestSale(
                shopId,
                "player:a",
                "item:sword",
                2L
            );

        AtomicTransactionService.TransactionId id=
            committedSaleTransaction(
                transactions,
                "player:a",
                "item:sword",
                2L,
                8L,
                AUTH,
                "sale:finite"
            );

        ShopSellbackService.SaleSnapshot settled=
            sellbacks.confirmSettlement(
                sale.saleId,
                id
            );

        ShopSellbackService.SaleSnapshot again=
            sellbacks.confirmSettlement(
                sale.saleId,
                id
            );

        require(
            settled.settlementTransactionId
                .equals(id)&&
            again.state==
                ShopSellbackService.SaleState.SETTLED&&
            finiteStock(
                shops,
                shopId,
                "item:sword"
            )==7L,
            "finite sellback settlement"
        );

        boolean cancelRejected=false;

        try{
            sellbacks.cancelSale(sale.saleId);
        }catch(IllegalStateException expected){
            cancelRejected=true;
        }

        require(
            cancelRejected,
            "settled sale cancelled"
        );

        return settled;
    }

    private static void purchaseCancelAfterSellbackRestoresRaisedStock(
        ShopService shops,
        ShopService.ShopId shopId
    ){
        ShopService.PurchaseSnapshot purchase=
            shops.requestPurchase(
                shopId,
                "player:p",
                "item:sword",
                3L
            );

        require(
            finiteStock(shops,shopId,"item:sword")==4L,
            "raised finite stock purchase reserve"
        );

        shops.cancelPurchase(
            purchase.purchaseId
        );

        require(
            finiteStock(shops,shopId,"item:sword")==7L,
            "purchase cancellation failed above initial stock"
        );
    }

    private static void unlimitedSettlementLeavesUnlimited(
        ShopSellbackService sellbacks,
        ShopService shops,
        AtomicTransactionService transactions,
        ShopService.ShopId shopId
    ){
        ShopSellbackService.SaleSnapshot sale=
            sellbacks.requestSale(
                shopId,
                "player:u",
                "item:potion",
                5L
            );

        AtomicTransactionService.TransactionId id=
            committedSaleTransaction(
                transactions,
                "player:u",
                "item:potion",
                5L,
                5L,
                AUTH,
                "sale:unlimited"
            );

        sellbacks.confirmSettlement(
            sale.saleId,
            id
        );

        require(
            !shops.getShop(shopId)
                .offer("item:potion")
                .availableStock
                .isPresent(),
            "unlimited Shop stock became finite"
        );
    }

    private static void settlementProofGuards(
        ShopSellbackService sellbacks,
        AtomicTransactionService transactions,
        ShopService.ShopId shopId
    ){
        ShopSellbackService.SaleSnapshot wrongSeller=
            sellbacks.requestSale(
                shopId,
                "player:seller",
                "item:potion",
                1L
            );

        AtomicTransactionService.TransactionId wrongSellerId=
            committedSaleTransaction(
                transactions,
                "player:other",
                "item:potion",
                1L,
                1L,
                AUTH,
                "sale:wrong-seller"
            );

        expect(
            SecurityException.class,
            ()->sellbacks.confirmSettlement(
                wrongSeller.saleId,
                wrongSellerId
            ),
            "wrong seller"
        );
        sellbacks.cancelSale(wrongSeller.saleId);

        ShopSellbackService.SaleSnapshot missingItem=
            sellbacks.requestSale(
                shopId,
                "player:m",
                "item:potion",
                1L
            );

        AtomicTransactionService.TransactionId missingItemId=
            transactions.create(
                "player:m",
                "sale:missing-item",
                AUTH
            );
        transactions.reserve(
            missingItemId,
            Collections.singletonList(
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    1L,
                    "shop-owner:general",
                    AUTH
                )
            )
        );
        transactions.commit(missingItemId);

        expect(
            IllegalArgumentException.class,
            ()->sellbacks.confirmSettlement(
                missingItem.saleId,
                missingItemId
            ),
            "missing seller item"
        );
        sellbacks.cancelSale(missingItem.saleId);

        ShopSellbackService.SaleSnapshot missingPayout=
            sellbacks.requestSale(
                shopId,
                "player:c",
                "item:potion",
                1L
            );

        AtomicTransactionService.TransactionId missingPayoutId=
            transactions.create(
                "player:c",
                "sale:missing-payout",
                AUTH
            );
        transactions.reserve(
            missingPayoutId,
            Collections.singletonList(
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    "item:potion",
                    1L,
                    "player:c",
                    AUTH
                )
            )
        );
        transactions.commit(missingPayoutId);

        expect(
            IllegalArgumentException.class,
            ()->sellbacks.confirmSettlement(
                missingPayout.saleId,
                missingPayoutId
            ),
            "missing Shop currency payout"
        );
        sellbacks.cancelSale(missingPayout.saleId);

        ShopSellbackService.SaleSnapshot wrongAuthority=
            sellbacks.requestSale(
                shopId,
                "player:d",
                "item:potion",
                1L
            );

        AtomicTransactionService.SourceAuthority other=
            AtomicTransactionService.SourceAuthority.INFERENCE;

        AtomicTransactionService.TransactionId wrongAuthorityId=
            committedSaleTransaction(
                transactions,
                "player:d",
                "item:potion",
                1L,
                1L,
                other,
                "sale:wrong-authority"
            );

        expect(
            IllegalArgumentException.class,
            ()->sellbacks.confirmSettlement(
                wrongAuthority.saleId,
                wrongAuthorityId
            ),
            "wrong authority"
        );
        sellbacks.cancelSale(wrongAuthority.saleId);
    }

    private static void crossFlowReplayGuards(
        ShopSellbackService sellbacks,
        ShopService shops,
        AtomicTransactionService transactions,
        ShopService.ShopId shopId
    ){
        // Purchase first, then attempt to reuse the same committed transaction
        // for a sellback. It deliberately covers both semantic exchanges.
        ShopService.PurchaseSnapshot purchase=
            shops.requestPurchase(
                shopId,
                "player:x",
                "item:sword",
                1L
            );
        ShopSellbackService.SaleSnapshot sale=
            sellbacks.requestSale(
                shopId,
                "player:x",
                "item:sword",
                1L
            );

        AtomicTransactionService.TransactionId first=
            dualCoverageTransaction(
                transactions,
                "player:x",
                "item:sword",
                10L,
                4L,
                "cross:purchase-first"
            );

        shops.confirmSettlement(
            purchase.purchaseId,
            first
        );

        expect(
            IllegalStateException.class,
            ()->sellbacks.confirmSettlement(
                sale.saleId,
                first
            ),
            "purchase transaction reused for sale"
        );
        sellbacks.cancelSale(sale.saleId);

        // Sale first, then attempt to reuse the same transaction for purchase.
        ShopSellbackService.SaleSnapshot saleFirst=
            sellbacks.requestSale(
                shopId,
                "player:y",
                "item:sword",
                1L
            );
        ShopService.PurchaseSnapshot purchaseSecond=
            shops.requestPurchase(
                shopId,
                "player:y",
                "item:sword",
                1L
            );

        AtomicTransactionService.TransactionId second=
            dualCoverageTransaction(
                transactions,
                "player:y",
                "item:sword",
                10L,
                4L,
                "cross:sale-first"
            );

        sellbacks.confirmSettlement(
            saleFirst.saleId,
            second
        );

        expect(
            IllegalStateException.class,
            ()->shops.confirmSettlement(
                purchaseSecond.purchaseId,
                second
            ),
            "sale transaction reused for purchase"
        );
        shops.cancelPurchase(
            purchaseSecond.purchaseId
        );

        ShopSellbackService.SaleSnapshot duplicate=
            sellbacks.requestSale(
                shopId,
                "player:y",
                "item:sword",
                1L
            );

        expect(
            IllegalStateException.class,
            ()->sellbacks.confirmSettlement(
                duplicate.saleId,
                second
            ),
            "sale transaction reused for another sale"
        );
        sellbacks.cancelSale(duplicate.saleId);
    }

    private static void payoutOverflowFailsBeforeSale(
        ShopSellbackService sellbacks,
        ShopService.ShopId shopId
    ){
        int before=sellbacks.size();

        expect(
            IllegalArgumentException.class,
            ()->sellbacks.requestSale(
                shopId,
                "player:o",
                "item:overflow",
                2L
            ),
            "sellback payout overflow"
        );

        require(
            sellbacks.size()==before,
            "overflow created Sale state"
        );
    }

    private static AtomicTransactionService.TransactionId
        committedSaleTransaction(
            AtomicTransactionService transactions,
            String seller,
            String item,
            long quantity,
            long payout,
            AtomicTransactionService.SourceAuthority authority,
            String reference
        ){
        AtomicTransactionService.TransactionId id=
            transactions.create(
                seller,
                reference,
                authority
            );

        transactions.reserve(
            id,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    item,
                    quantity,
                    seller,
                    authority
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    payout,
                    "shop-owner:general",
                    authority
                )
            )
        );

        transactions.commit(id);
        return id;
    }

    private static AtomicTransactionService.TransactionId
        dualCoverageTransaction(
            AtomicTransactionService transactions,
            String actor,
            String item,
            long purchasePrice,
            long salePayout,
            String reference
        ){
        AtomicTransactionService.TransactionId id=
            transactions.create(
                actor,
                reference,
                AUTH
            );

        transactions.reserve(
            id,
            Arrays.asList(
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    purchasePrice,
                    actor,
                    AUTH
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    item,
                    1L,
                    "shop-owner:general",
                    AUTH
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.ITEM,
                    item,
                    1L,
                    actor,
                    AUTH
                ),
                new EscrowAsset(
                    EscrowAsset.Kind.CURRENCY,
                    "currency:coins",
                    salePayout,
                    "shop-owner:general",
                    AUTH
                )
            )
        );

        transactions.commit(id);
        return id;
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
            offer.availableStock.isPresent(),
            "finite offer missing"
        );

        return offer.availableStock.getAsLong();
    }

    private static void immutableSnapshots(
        ShopSellbackService sellbacks
    ){
        boolean immutable=false;

        try{
            sellbacks.snapshot().clear();
        }catch(UnsupportedOperationException expected){
            immutable=true;
        }

        require(
            immutable,
            "sellback snapshot mutable"
        );
    }

    private static void assertExternalCompositionNotMethodSynchronized(){
        for(String name:new String[]{
                "register",
                "requestSale",
                "confirmSettlement",
                "cancelSale"
        }){
            boolean found=false;

            for(java.lang.reflect.Method method:
                    ShopSellbackService.class
                        .getDeclaredMethods()){
                if(!method.getName().equals(name))
                    continue;

                found=true;

                require(
                    !java.lang.reflect.Modifier
                        .isSynchronized(
                            method.getModifiers()
                    ),
                    "Shop sell-back external method still synchronized "+
                    name
                );
            }

            require(
                found,
                "Shop sell-back method missing "+
                name
            );
        }
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                ShopSellbackService.class,
                ShopSellbackService.BuybackDefinition.class,
                ShopSellbackService.SaleSnapshot.class
        }){
            for(Field field:type.getDeclaredFields()){
                String name=field.getName()
                    .toLowerCase(Locale.ROOT);

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("sceneindex")||
                   name.contains("client"))
                    throw new AssertionError(
                        "client/protocol identity leaked "+
                        type.getSimpleName()+
                        "."+field.getName()
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
                label+" wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private ShopSellbackServiceTest(){}
}
