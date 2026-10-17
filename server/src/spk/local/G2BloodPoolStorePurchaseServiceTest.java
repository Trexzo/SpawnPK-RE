package spk.local;

import java.util.*;

public final class G2BloodPoolStorePurchaseServiceTest {
    private static final AtomicTransactionService.SourceAuthority AUTHORITY=
        AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB;
    private static final int COINS=995;
    private static final int ROCKTAIL=15272;

    public static void main(String[] args){
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        world.registerPlayer(player,"opensrc");

        AtomicTransactionService transactions=
            new AtomicTransactionService();
        ShopService shops=new ShopService(transactions);
        ShopService.ShopId shopId=
            ShopService.ShopId.of("shop:g2-blood-pool");

        shops.register(
            new ShopService.ShopDefinition(
                shopId,
                "G2 Blood Pool",
                "shop-owner:g2-blood-pool",
                Collections.singletonList(
                    ShopService.Offer.finite(
                        "item:"+ROCKTAIL,
                        "item:"+COINS,
                        10L,
                        5L
                    )
                ),
                AUTHORITY
            )
        );

        BloodPoolStoreService bloodPool=
            new BloodPoolStoreService(
                shops,
                shopId,
                AUTHORITY
            );

        bloodPool.replaceSlots(
            Collections.singletonList(
                new BloodPoolStoreService.SlotSpec(
                    "slot:rocktail",
                    "item:"+ROCKTAIL
                )
            )
        );

        G2ShopPurchaseService g2=
            new G2ShopPurchaseService(
                world,
                player,
                shops
            );

        G2BloodPoolStorePurchaseService service=
            new G2BloodPoolStorePurchaseService(
                bloodPool,
                g2
            );

        try{
            require(
                bloodPool.shops()==shops&&
                g2.shops()==shops,
                "composition did not retain exact shared ShopService"
            );

            installCoins(player,100);

            G2BloodPoolStorePurchaseService.Result bought=
                service.purchase(
                    "SLOT:ROCKTAIL",
                    2L
                );

            require(
                bought.purchased()&&
                bought.status==
                    G2BloodPoolStorePurchaseService.Status.PURCHASED&&
                "slot:rocktail".equals(bought.slotKey)&&
                ("item:"+ROCKTAIL).equals(bought.itemRef)&&
                bought.purchase!=null&&
                bought.purchase.purchased()&&
                player.bank().inventoryCount(COINS)==80&&
                player.bank().inventoryCount(ROCKTAIL)==2&&
                finiteStock(shops,shopId)==3L,
                "canonical Blood Pool purchase did not settle exact state"
            );

            require(
                transactions.snapshot(
                    bought.purchase.transactionId
                ).state==
                    AtomicTransactionService.TransactionState.COMMITTED,
                "Blood Pool delegated G2 transaction not committed"
            );

            PlayerSnapshot persisted=
                PlayerSnapshotCodec.capture(
                    "opensrc",
                    player
                );
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                persisted,
                restored
            );

            require(
                restored.bank().inventoryCount(COINS)==80&&
                restored.bank().inventoryCount(ROCKTAIL)==2,
                "Blood Pool purchase postimage did not survive snapshot"
            );

            installCoins(player,5);
            InventoryImage beforeFailure=
                captureInventory(player);
            long stockBeforeFailure=
                finiteStock(shops,shopId);

            G2BloodPoolStorePurchaseService.Result rejected=
                service.purchase(
                    "slot:rocktail",
                    1L
                );

            require(
                rejected.status==
                    G2BloodPoolStorePurchaseService.Status.PURCHASE_REJECTED&&
                rejected.purchase!=null&&
                rejected.purchase.status==
                    G2ShopPurchaseService.Status.INSUFFICIENT_CURRENCY&&
                sameInventory(
                    beforeFailure,
                    captureInventory(player)
                )&&
                finiteStock(shops,shopId)==stockBeforeFailure,
                "failed Blood Pool purchase did not restore G2 reservation atomically"
            );

            InventoryImage beforeMissing=
                captureInventory(player);
            long stockBeforeMissing=
                finiteStock(shops,shopId);

            G2BloodPoolStorePurchaseService.Result missing=
                service.purchase(
                    "slot:missing",
                    1L
                );

            require(
                missing.status==
                    G2BloodPoolStorePurchaseService.Status.MISSING_SLOT&&
                missing.purchase==null&&
                sameInventory(
                    beforeMissing,
                    captureInventory(player)
                )&&
                finiteStock(shops,shopId)==stockBeforeMissing,
                "missing Blood Pool slot reached canonical Shop mutation"
            );

            AtomicTransactionService otherTransactions=
                new AtomicTransactionService();
            ShopService otherShops=
                new ShopService(otherTransactions);

            otherShops.register(
                new ShopService.ShopDefinition(
                    shopId,
                    "Other Blood Pool",
                    "shop-owner:other-blood-pool",
                    Collections.singletonList(
                        ShopService.Offer.finite(
                            "item:"+ROCKTAIL,
                            "item:"+COINS,
                            10L,
                            5L
                        )
                    ),
                    AUTHORITY
                )
            );

            boolean sharedAuthorityRejected=false;
            try{
                new G2BloodPoolStorePurchaseService(
                    bloodPool,
                    new G2ShopPurchaseService(
                        world,
                        player,
                        otherShops
                    )
                );
            }catch(IllegalArgumentException expected){
                sharedAuthorityRejected=true;
            }

            require(
                sharedAuthorityRejected,
                "different ShopService instances composed as one Blood Pool authority"
            );

            localLabAuthorityFence(
                world,
                player,
                shopId
            );
            protocolBoundary();

            System.out.println(
                "G2_BLOOD_POOL_STORE_PURCHASE_PASS "+
                "sharedShopAuthority=true "+
                "localLabAuthorityFence=true "+
                "slotResolution=true "+
                "canonicalPurchase=true "+
                "currencyDebited=true "+
                "itemDelivered=true "+
                "finiteStockCommitted=true "+
                "failureAtomic=true "+
                "missingSlotRejected=true "+
                "snapshotRoundTrip=true "+
                "sellbackOwned=false "+
                "rawWidgetOwned=false "+
                "originalSpawnpkEconomyClaim=false "+
                "authority="+G2BloodPoolStorePurchaseService.AUTHORITY
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );
            world.close();
        }
    }

    private static long finiteStock(
        ShopService shops,
        ShopService.ShopId shopId
    ){
        ShopService.OfferSnapshot offer=
            shops.getShop(shopId)
                .offer("item:"+ROCKTAIL);

        require(
            offer!=null&&
            offer.availableStock.isPresent(),
            "finite Blood Pool Shop offer missing"
        );

        return offer.availableStock.getAsLong();
    }

    private static void installCoins(
        WorldPlayer player,
        int quantity
    ){
        int[] items=
            new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=
            new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);
        items[0]=COINS;
        quantities[0]=quantity;

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(
                items,
                quantities
            );
        }
    }

    private static InventoryImage captureInventory(
        WorldPlayer player
    ){
        int[] items=
            new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=
            new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);

        synchronized(player.mutationLock()){
            for(int slot=0;
                slot<BankState.INVENTORY_CAPACITY;
                slot++){
                BankState.InventorySlotSnapshot snapshot=
                    player.bank()
                        .inventorySlotSnapshot(slot);

                if(!snapshot.occupied)
                    continue;

                items[slot]=snapshot.itemId;
                quantities[slot]=snapshot.quantity;
            }
        }

        return new InventoryImage(
            items,
            quantities
        );
    }

    private static boolean sameInventory(
        InventoryImage left,
        InventoryImage right
    ){
        return Arrays.equals(
                left.items,
                right.items
            )&&
            Arrays.equals(
                left.quantities,
                right.quantities
            );
    }

    private static void localLabAuthorityFence(
        World world,
        WorldPlayer player,
        ShopService.ShopId shopId
    ){
        AtomicTransactionService inferredTransactions=
            new AtomicTransactionService();
        ShopService inferredShops=
            new ShopService(
                inferredTransactions
            );

        inferredShops.register(
            new ShopService.ShopDefinition(
                shopId,
                "Inference Blood Pool",
                "shop-owner:inference-blood-pool",
                Collections.singletonList(
                    ShopService.Offer.finite(
                        "item:"+ROCKTAIL,
                        "item:"+COINS,
                        10L,
                        5L
                    )
                ),
                AtomicTransactionService
                    .SourceAuthority
                    .INFERENCE
            )
        );

        BloodPoolStoreService inferredBloodPool=
            new BloodPoolStoreService(
                inferredShops,
                shopId,
                AtomicTransactionService
                    .SourceAuthority
                    .INFERENCE
            );

        boolean rejected=false;

        try{
            new G2BloodPoolStorePurchaseService(
                inferredBloodPool,
                new G2ShopPurchaseService(
                    world,
                    player,
                    inferredShops
                )
            );
        }catch(IllegalArgumentException expected){
            rejected=true;
        }

        require(
            rejected,
            "non-LocalLab Blood Pool authority composed into G2 LocalLab purchase"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                G2BloodPoolStorePurchaseService.class,
                G2BloodPoolStorePurchaseService.Result.class
        }){
            for(java.lang.reflect.Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            java.util.Locale.ROOT
                        );

                if(name.contains("widget")||
                   name.contains("opcode")||
                   name.contains("sellback"))
                    throw new AssertionError(
                        "raw widget/opcode/sellback authority leaked into G2 Blood Pool purchase "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static final class InventoryImage {
        final int[] items;
        final int[] quantities;

        InventoryImage(
            int[] items,
            int[] quantities
        ){
            this.items=items;
            this.quantities=quantities;
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G2BloodPoolStorePurchaseServiceTest(){}
}
