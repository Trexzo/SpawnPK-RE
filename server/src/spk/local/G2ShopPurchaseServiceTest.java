package spk.local;

import java.util.*;

public final class G2ShopPurchaseServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        AUTHORITY=
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

        long generation=
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
                "shop:g2-supplies"
            );

        shops.register(
            new ShopService.ShopDefinition(
                shopId,
                "G2 Supplies",
                "shop-owner:g2-supplies",
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

        G2ShopPurchaseService service=
            new G2ShopPurchaseService(
                world,
                player,
                shops
            );

        try{
            installCoins(
                player,
                100
            );

            G2ShopPurchaseService.Result bought=
                service.purchase(
                    shopId,
                    "item:"+ROCKTAIL,
                    2L
                );

            require(
                bought.purchased()&&
                bought.purchase!=null&&
                bought.purchase.settled()&&
                bought.transactionId!=null&&
                bought.currencyItemId==COINS&&
                bought.purchasedItemId==ROCKTAIL&&
                bought.currencySpent==20L&&
                bought.quantity==2L&&
                G2ShopPurchaseService.SAVE_REASON.equals(
                    bought.saveReason
                ),
                "successful Shop purchase receipt "+
                bought
            );

            require(
                player.bank().inventoryCount(
                    COINS
                )==80&&
                player.bank().inventoryCount(
                    ROCKTAIL
                )==2,
                "successful purchase did not debit/deliver canonical inventory"
            );

            require(
                finiteStock(
                    shops,
                    shopId
                )==3L,
                "successful purchase did not commit finite stock"
            );

            AtomicTransactionService.Snapshot
                settlement=
                    transactions.snapshot(
                        bought.transactionId
                    );

            require(
                settlement.state==
                    AtomicTransactionService
                        .TransactionState
                        .COMMITTED&&
                settlement.ownerRef.equals(
                    "opensrc"
                )&&
                hasAsset(
                    settlement,
                    EscrowAsset.Kind.CURRENCY,
                    "item:"+COINS,
                    20L,
                    "opensrc"
                )&&
                hasAsset(
                    settlement,
                    EscrowAsset.Kind.ITEM,
                    "item:"+ROCKTAIL,
                    2L,
                    "shop-owner:g2-supplies"
                ),
                "canonical Shop transaction proof incomplete"
            );

            PlayerSnapshot persisted=
                PlayerSnapshotCodec.capture(
                    "opensrc",
                    player
                );
            WorldPlayer restored=
                new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                persisted,
                restored
            );

            require(
                restored.bank().inventoryCount(
                    COINS
                )==80&&
                restored.bank().inventoryCount(
                    ROCKTAIL
                )==2,
                "successful Shop postimage did not round-trip through snapshot"
            );

            installCoins(
                player,
                5
            );

            InventoryImage beforeInsufficient=
                capture(
                    player
                );
            long stockBeforeInsufficient=
                finiteStock(
                    shops,
                    shopId
                );

            G2ShopPurchaseService.Result
                insufficient=
                    service.purchase(
                        shopId,
                        "item:"+ROCKTAIL,
                        1L
                    );

            require(
                insufficient.status==
                    G2ShopPurchaseService
                        .Status
                        .INSUFFICIENT_CURRENCY&&
                sameInventory(
                    beforeInsufficient,
                    capture(player)
                )&&
                finiteStock(
                    shops,
                    shopId
                )==
                    stockBeforeInsufficient,
                "insufficient-currency rejection was not atomic "+
                insufficient
            );

            installFullInventory(
                player
            );

            InventoryImage beforeFull=
                capture(
                    player
                );
            long stockBeforeFull=
                finiteStock(
                    shops,
                    shopId
                );

            G2ShopPurchaseService.Result full=
                service.purchase(
                    shopId,
                    "item:"+ROCKTAIL,
                    1L
                );

            require(
                full.status==
                    G2ShopPurchaseService
                        .Status
                        .INVENTORY_FULL&&
                sameInventory(
                    beforeFull,
                    capture(player)
                )&&
                finiteStock(
                    shops,
                    shopId
                )==
                    stockBeforeFull,
                "inventory-full rejection was not atomic "+
                full
            );

            installCoins(
                player,
                100
            );

            int hp=
                player.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    );

            new PlayerLifecycleService(
                player
            ).applyDamage(
                hp,
                44L,
                "G2_SHOP_DEAD_GUARD"
            );

            long stockBeforeDead=
                finiteStock(
                    shops,
                    shopId
                );

            G2ShopPurchaseService.Result dead=
                service.purchase(
                    shopId,
                    "item:"+ROCKTAIL,
                    1L
                );

            require(
                dead.status==
                    G2ShopPurchaseService
                        .Status
                        .DEAD&&
                player.bank().inventoryCount(
                    COINS
                )==100&&
                player.bank().inventoryCount(
                    ROCKTAIL
                )==0&&
                finiteStock(
                    shops,
                    shopId
                )==
                    stockBeforeDead,
                "dead-player Shop request mutated state "+
                dead
            );

            player.lifecycle()
                .markRespawned();

            world.unregisterPlayer(
                player,
                generation
            );

            G2ShopPurchaseService.Result stale=
                service.purchase(
                    shopId,
                    "item:"+ROCKTAIL,
                    1L
                );

            require(
                stale.status==
                    G2ShopPurchaseService
                        .Status
                        .STALE_PLAYER&&
                finiteStock(
                    shops,
                    shopId
                )==
                    stockBeforeDead,
                "stale player Shop request mutated stock "+
                stale
            );

            /*
             * The Shop finalizer must reject an invalid RESERVED proof before
             * it becomes COMMITTED, so callers can still cancel both sides.
             */
            ShopService.PurchaseSnapshot guardedPurchase=
                shops.requestPurchase(
                    shopId,
                    "guarded-buyer",
                    "item:"+ROCKTAIL,
                    1L
                );

            AtomicTransactionService.TransactionId
                guardedTransaction=
                    transactions.create(
                        "wrong-buyer",
                        "g2-guarded-invalid",
                        AUTHORITY
                    );

            transactions.reserve(
                guardedTransaction,
                Arrays.asList(
                    new EscrowAsset(
                        EscrowAsset.Kind.CURRENCY,
                        "item:"+COINS,
                        guardedPurchase.totalPrice,
                        "wrong-buyer",
                        AUTHORITY
                    ),
                    new EscrowAsset(
                        EscrowAsset.Kind.ITEM,
                        "item:"+ROCKTAIL,
                        guardedPurchase.quantity,
                        guardedPurchase.stockOwnerRef,
                        AUTHORITY
                    )
                )
            );

            boolean guardedRejected=false;

            try{
                shops.commitReservedSettlement(
                    guardedPurchase.purchaseId,
                    guardedTransaction
                );
            }catch(SecurityException expected){
                guardedRejected=true;
            }

            require(
                guardedRejected&&
                transactions.snapshot(
                    guardedTransaction
                ).state==
                    AtomicTransactionService
                        .TransactionState
                        .RESERVED&&
                shops.getPurchase(
                    guardedPurchase.purchaseId
                ).state==
                    ShopService.PurchaseState.RESERVED,
                "invalid reserved Shop proof crossed commit fence"
            );

            transactions.cancel(
                guardedTransaction
            );
            shops.cancelPurchase(
                guardedPurchase.purchaseId
            );

            System.out.println(
                "G2_SHOP_PURCHASE_PASS "+
                "successfulPurchase=true "+
                "currencyDebited=true "+
                "itemDelivered=true "+
                "finiteStockCommitted=true "+
                "transactionProof=true "+
                "reservedSettlementFence=true "+
                "noGhostCommittedTransaction=true "+
                "insufficientCurrencyAtomic=true "+
                "inventoryFullAtomic=true "+
                "deadRejected=true "+
                "staleRejected=true "+
                "snapshotRoundTrip=true "+
                "originalSpawnpkEconomyClaim=false "+
                "authority="+
                G2ShopPurchaseService.AUTHORITY
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
            emptyItems();
        int[] quantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

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

    private static void installFullInventory(
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

        items[0]=COINS;
        quantities[0]=100;

        for(int slot=1;
            slot<BankState.INVENTORY_CAPACITY;
            slot++){
            items[slot]=ROCKTAIL;
            quantities[slot]=1;
        }

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
            emptyItems();
        int[] quantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

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

    private static int[] emptyItems(){
        int[] items=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

        Arrays.fill(
            items,
            -1
        );

        return items;
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
            "finite Shop offer missing"
        );

        return offer.availableStock
            .getAsLong();
    }

    private static boolean hasAsset(
        AtomicTransactionService.Snapshot snapshot,
        EscrowAsset.Kind kind,
        String semanticKey,
        long quantity,
        String ownerRef
    ){
        for(AtomicTransactionService.Reservation
                reservation:
                snapshot.reservations){
            EscrowAsset asset=
                reservation.asset;

            if(asset.kind==kind&&
               asset.semanticKey.equals(
                    semanticKey
               )&&
               asset.quantity==quantity&&
               asset.ownerRef.equals(
                    ownerRef
               )&&
               asset.sourceAuthority==
                    AUTHORITY)
                return true;
        }

        return false;
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

    private G2ShopPurchaseServiceTest(){}
}
