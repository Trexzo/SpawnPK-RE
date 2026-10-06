package spk.local;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public final class G2BloodPoolStorePurchaseServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    private static final int COINS=995;
    private static final int ROCKTAIL=15272;

    public static void main(String[] args){
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "opensrc"
        );

        AtomicTransactionService transactions=
            new AtomicTransactionService();
        ShopService shops=
            new ShopService(
                transactions
            );

        ShopService.ShopId shopId=
            ShopService.ShopId.of(
                "shop:g2-blood-pool"
            );

        shops.register(
            new ShopService.ShopDefinition(
                shopId,
                "G2 Blood Pool Fixture",
                "shop-owner:g2-blood-pool",
                Collections.singletonList(
                    ShopService.Offer.finite(
                        "item:"+ROCKTAIL,
                        "item:"+COINS,
                        10L,
                        5L
                    )
                ),
                POLICY
            )
        );

        BloodPoolStoreService bloodPool=
            new BloodPoolStoreService(
                shops,
                shopId,
                POLICY
            );

        bloodPool.replaceSlots(
            Collections.singletonList(
                new BloodPoolStoreService
                    .SlotSpec(
                        "slot:rocktail",
                        "item:"+ROCKTAIL
                    )
            )
        );

        G2BloodPoolStorePurchaseService service=
            new G2BloodPoolStorePurchaseService(
                world,
                player,
                bloodPool
            );

        try{
            require(
                service.sharedShopAuthority()&&
                bloodPool.shopAuthority()==shops,
                "Blood Pool canonical purchase did not reuse exact ShopService authority"
            );

            installCoins(
                player,
                100
            );

            G2BloodPoolStorePurchaseService.Result
                bought=
                    service.purchase(
                        "SLOT:ROCKTAIL",
                        2L
                    );

            require(
                bought.purchased()&&
                bought.status==
                    G2BloodPoolStorePurchaseService
                        .Status.PURCHASED&&
                "slot:rocktail".equals(
                    bought.slotKey
                )&&
                ("item:"+ROCKTAIL).equals(
                    bought.itemRef
                )&&
                bought.purchase!=null&&
                bought.purchase.purchase!=null&&
                bought.purchase.purchase.settled(),
                "Blood Pool slot did not settle through canonical G2 purchase"
            );

            require(
                player.bank().inventoryCount(
                    COINS
                )==80&&
                player.bank().inventoryCount(
                    ROCKTAIL
                )==2,
                "Blood Pool canonical purchase carried postimage wrong"
            );

            require(
                finiteStock(
                    shops,
                    shopId
                )==3L&&
                bloodPool.snapshot()
                    .slot(
                        "slot:rocktail"
                    )
                    .availableStock
                    .getAsLong()==3L,
                "Blood Pool live finite stock projection did not follow canonical purchase"
            );

            AtomicTransactionService.Snapshot
                settlement=
                    transactions.snapshot(
                        bought.purchase.transactionId
                    );

            require(
                settlement.state==
                    AtomicTransactionService
                        .TransactionState
                        .COMMITTED,
                "Blood Pool canonical purchase transaction not committed"
            );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    "opensrc",
                    player
                );
            WorldPlayer restored=
                new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                snapshot,
                restored
            );

            require(
                restored.bank().inventoryCount(
                    COINS
                )==80&&
                restored.bank().inventoryCount(
                    ROCKTAIL
                )==2,
                "Blood Pool purchase postimage did not round-trip"
            );

            InventoryImage beforeMissing=
                capture(
                    player
                );
            long stockBeforeMissing=
                finiteStock(
                    shops,
                    shopId
                );

            G2BloodPoolStorePurchaseService.Result
                missing=
                    service.purchase(
                        "slot:missing",
                        1L
                    );

            require(
                missing.status==
                    G2BloodPoolStorePurchaseService
                        .Status.UNKNOWN_SLOT&&
                missing.purchase==null&&
                sameInventory(
                    beforeMissing,
                    capture(player)
                )&&
                finiteStock(
                    shops,
                    shopId
                )==stockBeforeMissing,
                "missing Blood Pool slot mutated canonical state"
            );

            installCoins(
                player,
                5
            );

            InventoryImage beforeRejected=
                capture(
                    player
                );
            long stockBeforeRejected=
                finiteStock(
                    shops,
                    shopId
                );

            G2BloodPoolStorePurchaseService.Result
                rejected=
                    service.purchase(
                        "slot:rocktail",
                        1L
                    );

            require(
                rejected.status==
                    G2BloodPoolStorePurchaseService
                        .Status.SHOP_REJECTED&&
                rejected.purchase!=null&&
                rejected.purchase.status==
                    G2ShopPurchaseService
                        .Status
                        .INSUFFICIENT_CURRENCY&&
                sameInventory(
                    beforeRejected,
                    capture(player)
                )&&
                finiteStock(
                    shops,
                    shopId
                )==stockBeforeRejected&&
                bloodPool.snapshot()
                    .slot(
                        "slot:rocktail"
                    )
                    .availableStock
                    .getAsLong()==stockBeforeRejected,
                "rejected Blood Pool canonical purchase was not failure-atomic"
            );

            protocolBoundary();

            System.out.println(
                "G2_BLOOD_POOL_STORE_PURCHASE_PASS "+
                "sharedShopAuthority=true "+
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
                "authority="+
                G2BloodPoolStorePurchaseService.AUTHORITY
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

    private static void installCoins(
        WorldPlayer player,
        int quantity
    ){
        int[] items=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] quantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

        Arrays.fill(
            items,
            -1
        );

        items[0]=COINS;
        quantities[0]=quantity;

        synchronized(player.mutationLock()){
            player.bank()
                .replaceInventorySemantic(
                    items,
                    quantities
                );
        }
    }

    private static InventoryImage capture(
        WorldPlayer player
    ){
        int[] items=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] quantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

        Arrays.fill(
            items,
            -1
        );

        synchronized(player.mutationLock()){
            for(int slot=0;
                slot<BankState.INVENTORY_CAPACITY;
                slot++){
                BankState.InventorySlotSnapshot
                    snapshot=
                        player.bank()
                            .inventorySlotSnapshot(
                                slot
                            );

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

    private static long finiteStock(
        ShopService shops,
        ShopService.ShopId shopId
    ){
        ShopService.OfferSnapshot offer=
            shops.getShop(
                shopId
            ).offer(
                "item:"+ROCKTAIL
            );

        require(
            offer!=null&&
            offer.availableStock
                .isPresent(),
            "finite Blood Pool offer missing"
        );

        return offer.availableStock
            .getAsLong();
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

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                G2BloodPoolStorePurchaseService.class,
                G2BloodPoolStorePurchaseService.Result.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
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
            throw new AssertionError(
                message
            );
    }

    private G2BloodPoolStorePurchaseServiceTest(){}
}
