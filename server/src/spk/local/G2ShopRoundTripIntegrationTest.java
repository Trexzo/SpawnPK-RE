package spk.local;

import java.util.*;

public final class G2ShopRoundTripIntegrationTest {
    private static final AtomicTransactionService.SourceAuthority AUTHORITY=
        AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB;
    private static final int COINS=995;
    private static final int ROCKTAIL=15272;

    public static void main(String[] args){
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        world.registerPlayer(player,"opensrc");

        AtomicTransactionService transactions=new AtomicTransactionService();
        ShopService shops=new ShopService(transactions);
        ShopService.ShopId shopId=ShopService.ShopId.of("shop:g2-round-trip");

        shops.register(new ShopService.ShopDefinition(
            shopId,
            "G2 Round Trip",
            "shop-owner:g2-round-trip",
            Collections.singletonList(
                ShopService.Offer.finite(
                    "item:"+ROCKTAIL,
                    "item:"+COINS,
                    10L,
                    5L
                )
            ),
            AUTHORITY
        ));

        ShopSellbackService sellbacks=new ShopSellbackService(shops);
        sellbacks.register(new ShopSellbackService.BuybackDefinition(
            shopId,
            "item:"+ROCKTAIL,
            "item:"+COINS,
            4L,
            AUTHORITY
        ));

        G2ShopPurchaseService purchase=
            new G2ShopPurchaseService(world,player,shops);
        G2ShopSellbackInventoryService sellback=
            new G2ShopSellbackInventoryService(world,player,sellbacks);

        try{
            installCoins(player,100);

            G2ShopPurchaseService.Result firstBuy=
                purchase.purchase(shopId,"item:"+ROCKTAIL,2L);

            require(
                firstBuy.purchased() &&
                player.bank().inventoryCount(COINS)==80 &&
                player.bank().inventoryCount(ROCKTAIL)==2 &&
                finiteStock(shops,shopId)==3L,
                "first buy postimage mismatch "+firstBuy
            );

            G2ShopSellbackInventoryService.Result sold=
                sellback.sell(shopId,"item:"+ROCKTAIL,1L);

            require(
                sold.sold() &&
                player.bank().inventoryCount(COINS)==84 &&
                player.bank().inventoryCount(ROCKTAIL)==1 &&
                finiteStock(shops,shopId)==4L,
                "sellback postimage mismatch "+sold
            );

            G2ShopPurchaseService.Result secondBuy=
                purchase.purchase(shopId,"item:"+ROCKTAIL,1L);

            require(
                secondBuy.purchased() &&
                player.bank().inventoryCount(COINS)==74 &&
                player.bank().inventoryCount(ROCKTAIL)==2 &&
                finiteStock(shops,shopId)==3L,
                "second buy postimage mismatch "+secondBuy
            );

            require(
                committed(transactions,firstBuy.transactionId) &&
                committed(transactions,sold.transactionId) &&
                committed(transactions,secondBuy.transactionId) &&
                !firstBuy.transactionId.equals(sold.transactionId) &&
                !firstBuy.transactionId.equals(secondBuy.transactionId) &&
                !sold.transactionId.equals(secondBuy.transactionId),
                "round-trip transactions were not exact independent commits"
            );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture("opensrc",player);
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(snapshot,restored);

            require(
                restored.bank().inventoryCount(COINS)==74 &&
                restored.bank().inventoryCount(ROCKTAIL)==2,
                "round-trip final postimage did not persist"
            );

            System.out.println(
                "G2_SHOP_ROUND_TRIP_PASS "+
                "buy=true "+
                "sellback=true "+
                "rebuy=true "+
                "inventoryExact=true "+
                "finiteStockExact=true "+
                "transactionsCommitted=true "+
                "snapshotRoundTrip=true "+
                "originalSpawnpkEconomyClaim=false "+
                "authority=CUSTOM_LOCALLAB"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player,player.generation());
            world.close();
        }
    }

    private static void installCoins(WorldPlayer player,int quantity){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);
        items[0]=COINS;
        quantities[0]=quantity;

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(items,quantities);
        }
    }

    private static long finiteStock(ShopService shops,ShopService.ShopId shopId){
        ShopService.OfferSnapshot offer=
            shops.getShop(shopId).offer("item:"+ROCKTAIL);

        require(
            offer!=null && offer.availableStock.isPresent(),
            "finite Shop offer missing"
        );
        return offer.availableStock.getAsLong();
    }

    private static boolean committed(
        AtomicTransactionService transactions,
        AtomicTransactionService.TransactionId id
    ){
        return id!=null &&
            transactions.snapshot(id).state==
                AtomicTransactionService.TransactionState.COMMITTED;
    }

    private static void require(boolean condition,String message){
        if(!condition)
            throw new AssertionError(message);
    }

    private G2ShopRoundTripIntegrationTest(){}
}
