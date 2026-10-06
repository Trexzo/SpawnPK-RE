package spk.local;

import java.util.*;

public final class G2ShopSellbackInventoryServiceTest {
    private static final AtomicTransactionService.SourceAuthority AUTHORITY=
        AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB;

    private static final int COINS=995;
    private static final int ROCKTAIL=15272;

    public static void main(String[] args){
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,"opensrc");

        AtomicTransactionService transactions=new AtomicTransactionService();
        ShopService shops=new ShopService(transactions);
        ShopService.ShopId shopId=
            ShopService.ShopId.of("shop:g2-sellback");

        shops.register(
            new ShopService.ShopDefinition(
                shopId,
                "G2 Sellback",
                "shop-owner:g2-sellback",
                Collections.singletonList(
                    ShopService.Offer.finite(
                        "item:"+ROCKTAIL,
                        "item:"+COINS,
                        10L,
                        1L
                    )
                ),
                AUTHORITY
            )
        );

        ShopSellbackService sellbacks=new ShopSellbackService(shops);
        sellbacks.register(
            new ShopSellbackService.BuybackDefinition(
                shopId,
                "item:"+ROCKTAIL,
                "item:"+COINS,
                4L,
                AUTHORITY
            )
        );

        G2ShopSellbackInventoryService service=
            new G2ShopSellbackInventoryService(
                world,
                player,
                sellbacks
            );

        try{
            require(
                ItemCatalog.isStackable(COINS),
                "coin payout fixture must be stackable"
            );

            installInventory(player,5,3);

            G2ShopSellbackInventoryService.Result sold=
                service.sell(
                    shopId,
                    "item:"+ROCKTAIL,
                    2L
                );

            require(
                sold.sold()&&
                sold.sale!=null&&
                sold.sale.state==
                    ShopSellbackService.SaleState.SETTLED&&
                sold.transactionId!=null&&
                sold.soldItemId==ROCKTAIL&&
                sold.payoutItemId==COINS&&
                sold.quantity==2L&&
                sold.payout==8L&&
                G2ShopSellbackInventoryService.SAVE_REASON.equals(
                    sold.saveReason
                ),
                "successful sellback receipt "+sold
            );

            require(
                player.bank().inventoryCount(ROCKTAIL)==1&&
                player.bank().inventoryCount(COINS)==13,
                "successful sellback did not debit/pay canonical inventory"
            );

            require(
                finiteStock(shops,shopId)==3L,
                "successful sellback did not credit finite Shop stock"
            );

            AtomicTransactionService.Snapshot proof=
                transactions.snapshot(sold.transactionId);

            require(
                proof.state==
                    AtomicTransactionService.TransactionState.COMMITTED&&
                proof.ownerRef.equals("opensrc")&&
                hasAsset(
                    proof,
                    EscrowAsset.Kind.ITEM,
                    "item:"+ROCKTAIL,
                    2L,
                    "opensrc"
                )&&
                hasAsset(
                    proof,
                    EscrowAsset.Kind.CURRENCY,
                    "item:"+COINS,
                    8L,
                    "shop-owner:g2-sellback"
                ),
                "sellback transaction proof incomplete"
            );

            PlayerSnapshot persisted=
                PlayerSnapshotCodec.capture("opensrc",player);
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(persisted,restored);

            require(
                restored.bank().inventoryCount(ROCKTAIL)==1&&
                restored.bank().inventoryCount(COINS)==13,
                "sellback postimage did not round-trip snapshot"
            );

            installInventory(player,0,1);
            InventoryImage beforeInsufficient=capture(player);
            long stockBeforeInsufficient=finiteStock(shops,shopId);

            G2ShopSellbackInventoryService.Result insufficient=
                service.sell(
                    shopId,
                    "item:"+ROCKTAIL,
                    2L
                );

            require(
                insufficient.status==
                    G2ShopSellbackInventoryService.Status.INSUFFICIENT_ITEM&&
                sameInventory(beforeInsufficient,capture(player))&&
                finiteStock(shops,shopId)==stockBeforeInsufficient,
                "insufficient-item rejection was not atomic "+insufficient
            );

            installOverflowPayoutFixture(player);
            InventoryImage beforeOverflow=capture(player);
            long stockBeforeOverflow=finiteStock(shops,shopId);

            G2ShopSellbackInventoryService.Result overflow=
                service.sell(
                    shopId,
                    "item:"+ROCKTAIL,
                    1L
                );

            require(
                overflow.status==
                    G2ShopSellbackInventoryService.Status.QUANTITY_OVERFLOW&&
                sameInventory(beforeOverflow,capture(player))&&
                finiteStock(shops,shopId)==stockBeforeOverflow,
                "payout-overflow rejection was not atomic "+overflow
            );

            installInventory(player,0,1);
            int hp=player.playerState().currentLevel(PlayerState.HITPOINTS);
            new PlayerLifecycleService(player).applyDamage(
                hp,
                55L,
                "G2_SELLBACK_DEAD_GUARD"
            );

            long stockBeforeDead=finiteStock(shops,shopId);
            G2ShopSellbackInventoryService.Result dead=
                service.sell(
                    shopId,
                    "item:"+ROCKTAIL,
                    1L
                );

            require(
                dead.status==
                    G2ShopSellbackInventoryService.Status.DEAD&&
                player.bank().inventoryCount(ROCKTAIL)==1&&
                finiteStock(shops,shopId)==stockBeforeDead,
                "dead-player sellback mutated state "+dead
            );

            player.lifecycle().markRespawned();
            world.unregisterPlayer(player,generation);

            G2ShopSellbackInventoryService.Result stale=
                service.sell(
                    shopId,
                    "item:"+ROCKTAIL,
                    1L
                );

            require(
                stale.status==
                    G2ShopSellbackInventoryService.Status.STALE_PLAYER&&
                finiteStock(shops,shopId)==stockBeforeDead,
                "stale-player sellback mutated Shop state "+stale
            );

            /*
             * Invalid RESERVED proof must fail before transaction commit and
             * before incoming finite stock is materialized.
             */
            long guardedStockBefore=finiteStock(shops,shopId);
            ShopSellbackService.SaleSnapshot guardedSale=
                sellbacks.requestSale(
                    shopId,
                    "guarded-seller",
                    "item:"+ROCKTAIL,
                    1L
                );

            AtomicTransactionService.TransactionId guardedTransaction=
                transactions.create(
                    "wrong-seller",
                    "g2-sellback-guarded-invalid",
                    AUTHORITY
                );

            transactions.reserve(
                guardedTransaction,
                Arrays.asList(
                    new EscrowAsset(
                        EscrowAsset.Kind.ITEM,
                        "item:"+ROCKTAIL,
                        guardedSale.quantity,
                        "wrong-seller",
                        AUTHORITY
                    ),
                    new EscrowAsset(
                        EscrowAsset.Kind.CURRENCY,
                        "item:"+COINS,
                        guardedSale.totalPayout,
                        guardedSale.shopStockOwnerRef,
                        AUTHORITY
                    )
                )
            );

            boolean guardedRejected=false;

            try{
                sellbacks.commitReservedSettlement(
                    guardedSale.saleId,
                    guardedTransaction
                );
            }catch(SecurityException expected){
                guardedRejected=true;
            }

            require(
                guardedRejected&&
                transactions.snapshot(guardedTransaction).state==
                    AtomicTransactionService.TransactionState.RESERVED&&
                sellbacks.get(guardedSale.saleId).state==
                    ShopSellbackService.SaleState.RESERVED&&
                finiteStock(shops,shopId)==guardedStockBefore,
                "invalid RESERVED sellback proof crossed commit fence"
            );

            transactions.cancel(guardedTransaction);
            sellbacks.cancelSale(guardedSale.saleId);

            require(
                finiteStock(shops,shopId)==guardedStockBefore,
                "guarded sellback cancellation changed finite stock"
            );

            System.out.println(
                "G2_SHOP_SELLBACK_INVENTORY_PASS "+
                "successfulSale=true "+
                "itemDebited=true "+
                "payoutDelivered=true "+
                "finiteStockCredited=true "+
                "transactionProof=true "+
                "reservedSettlementFence=true "+
                "noGhostCommittedTransaction=true "+
                "insufficientItemsAtomic=true "+
                "payoutOverflowAtomic=true "+
                "deadRejected=true "+
                "staleRejected=true "+
                "snapshotRoundTrip=true "+
                "originalSpawnpkEconomyClaim=false "+
                "authority="+
                G2ShopSellbackInventoryService.AUTHORITY
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player,player.generation());
            world.close();
        }
    }

    private static void installInventory(
        WorldPlayer player,
        int coins,
        int rocktails
    ){
        int[] items=emptyItems();
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];

        int slot=0;
        if(coins>0){
            items[slot]=COINS;
            quantities[slot]=coins;
            slot++;
        }

        for(int n=0;n<rocktails;n++){
            items[slot]=ROCKTAIL;
            quantities[slot]=1;
            slot++;
        }

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(items,quantities);
        }
    }

    private static void installOverflowPayoutFixture(
        WorldPlayer player
    ){
        int[] items=emptyItems();
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];

        items[0]=COINS;
        quantities[0]=Integer.MAX_VALUE;
        items[1]=ROCKTAIL;
        quantities[1]=1;

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(items,quantities);
        }
    }

    private static InventoryImage capture(WorldPlayer player){
        int[] items=emptyItems();
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];

        synchronized(player.mutationLock()){
            for(int slot=0;slot<items.length;slot++){
                BankState.InventorySlotSnapshot snapshot=
                    player.bank().inventorySlotSnapshot(slot);

                if(!snapshot.occupied)
                    continue;

                items[slot]=snapshot.itemId;
                quantities[slot]=snapshot.quantity;
            }
        }

        return new InventoryImage(items,quantities);
    }

    private static long finiteStock(
        ShopService shops,
        ShopService.ShopId shopId
    ){
        ShopService.OfferSnapshot offer=
            shops.getShop(shopId).offer("item:"+ROCKTAIL);

        require(
            offer!=null&&offer.availableStock.isPresent(),
            "finite sellback offer missing"
        );

        return offer.availableStock.getAsLong();
    }

    private static boolean hasAsset(
        AtomicTransactionService.Snapshot snapshot,
        EscrowAsset.Kind kind,
        String semanticKey,
        long quantity,
        String ownerRef
    ){
        for(AtomicTransactionService.Reservation reservation:
                snapshot.reservations){
            EscrowAsset asset=reservation.asset;

            if(asset.kind==kind&&
               asset.semanticKey.equals(semanticKey)&&
               asset.quantity==quantity&&
               asset.ownerRef.equals(ownerRef)&&
               asset.sourceAuthority==AUTHORITY)
                return true;
        }

        return false;
    }

    private static int[] emptyItems(){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);
        return items;
    }

    private static boolean sameInventory(
        InventoryImage left,
        InventoryImage right
    ){
        return Arrays.equals(left.items,right.items)&&
            Arrays.equals(left.quantities,right.quantities);
    }

    private static final class InventoryImage {
        final int[] items;
        final int[] quantities;

        InventoryImage(int[] items,int[] quantities){
            this.items=items;
            this.quantities=quantities;
        }
    }

    private static void require(boolean condition,String message){
        if(!condition)
            throw new AssertionError(message);
    }

    private G2ShopSellbackInventoryServiceTest(){}
}
