package spk.local;

import java.util.*;

/**
 * Clean-R25 buy-side Shop composition.
 *
 * ShopService already owns stock reservation and committed semantic settlement
 * proof. This layer owns only one inventory-backed LocalLab purchase: remove
 * the buyer's semantic currency item and add the purchased semantic item as one
 * complete canonical inventory postimage.
 */
final class G2ShopPurchaseService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2_SHOP_PURCHASE_V1";
    static final String SAVE_REASON=
        "G2_SHOP_PURCHASE";

    enum Status {
        PURCHASED,
        STALE_PLAYER,
        DEAD,
        UNSUPPORTED_SEMANTIC_KEY,
        UNKNOWN_ITEM,
        INSUFFICIENT_CURRENCY,
        INVENTORY_FULL,
        QUANTITY_OVERFLOW
    }

    static final class Result {
        final Status status;
        final ShopService.PurchaseSnapshot purchase;
        final AtomicTransactionService.TransactionId transactionId;
        final int currencyItemId;
        final int purchasedItemId;
        final long currencySpent;
        final long quantity;
        final String saveReason;
        final String detail;
        final String authority;

        private Result(
            Status status,
            ShopService.PurchaseSnapshot purchase,
            AtomicTransactionService.TransactionId transactionId,
            int currencyItemId,
            int purchasedItemId,
            long currencySpent,
            long quantity,
            String saveReason,
            String detail
        ){
            this.status=status;
            this.purchase=purchase;
            this.transactionId=transactionId;
            this.currencyItemId=currencyItemId;
            this.purchasedItemId=purchasedItemId;
            this.currencySpent=currencySpent;
            this.quantity=quantity;
            this.saveReason=saveReason;
            this.detail=detail;
            this.authority=AUTHORITY;
        }

        boolean purchased(){
            return status==Status.PURCHASED;
        }

        @Override public String toString(){
            return "Result{status="+status+
                ",purchase="+
                (purchase==null
                    ?"null"
                    :purchase.purchaseId)+
                ",transaction="+transactionId+
                ",currencyItem="+currencyItemId+
                ",purchasedItem="+purchasedItemId+
                ",spent="+currencySpent+
                ",quantity="+quantity+
                ",detail="+detail+
                "}";
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

    private static final class Plan {
        final Status rejection;
        final String detail;
        final int[] items;
        final int[] quantities;

        private Plan(
            Status rejection,
            String detail,
            int[] items,
            int[] quantities
        ){
            this.rejection=rejection;
            this.detail=detail;
            this.items=items;
            this.quantities=quantities;
        }

        static Plan accepted(
            int[] items,
            int[] quantities
        ){
            return new Plan(
                null,
                null,
                items,
                quantities
            );
        }

        static Plan rejected(
            Status rejection,
            String detail
        ){
            return new Plan(
                Objects.requireNonNull(
                    rejection,
                    "rejection"
                ),
                detail,
                null,
                null
            );
        }

        boolean accepted(){
            return rejection==null;
        }
    }

    private final World world;
    private final WorldPlayer player;
    private final ShopService shops;
    private final AtomicTransactionService transactions;

    G2ShopPurchaseService(
        World world,
        WorldPlayer player,
        ShopService shops
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
        this.shops=
            Objects.requireNonNull(
                shops,
                "shops"
            );
        this.transactions=
            Objects.requireNonNull(
                shops.transactions(),
                "transactions"
            );
    }

    Result purchase(
        ShopService.ShopId shopId,
        String itemRef,
        long quantity
    ){
        final long generation=
            player.generation();

        final Result[] result=
            new Result[1];

        final boolean current;

        try{
            current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    player,
                    generation,
                    ()->result[0]=
                        purchaseOwned(
                            shopId,
                            itemRef,
                            quantity
                        )
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Shop purchase ownership execution failed",
                failure
            );
        }

        if(!current)
            return rejected(
                Status.STALE_PLAYER,
                null,
                -1,
                -1,
                0L,
                quantity,
                "player ownership is not current"
            );

        if(result[0]==null)
            throw new IllegalStateException(
                "Shop purchase ownership action produced no result"
            );

        return result[0];
    }

    /**
     * Runs only under World lifecycle + exact player mutation ownership.
     * Registration/generation therefore cannot change during reservation,
     * carried-state commit and Shop settlement.
     */
    private Result purchaseOwned(
        ShopService.ShopId shopId,
        String itemRef,
        long quantity
    ){
        if(player.lifecycle().dead())
            return rejected(
                Status.DEAD,
                null,
                -1,
                -1,
                0L,
                quantity,
                "dead players cannot buy"
            );

        String buyerRef=
            PartyService.requireRef(
                player.username()
            );

        ShopService.PurchaseSnapshot reservation=
            shops.requestPurchase(
                Objects.requireNonNull(
                    shopId,
                    "shopId"
                ),
                buyerRef,
                itemRef,
                quantity
            );

        final int currencyItemId;
        final int purchasedItemId;

        try{
            currencyItemId=
                parseItemRef(
                    reservation.currencyRef
                );
            purchasedItemId=
                parseItemRef(
                    reservation.itemRef
                );
        }catch(IllegalArgumentException unsupported){
            cancelReserved(
                reservation.purchaseId
            );
            return rejected(
                Status.UNSUPPORTED_SEMANTIC_KEY,
                reservation,
                -1,
                -1,
                reservation.totalPrice,
                reservation.quantity,
                unsupported.getMessage()
            );
        }

        if(!ItemCatalog.exists(
                currencyItemId)||
           !ItemCatalog.exists(
                purchasedItemId)){
            cancelReserved(
                reservation.purchaseId
            );
            return rejected(
                Status.UNKNOWN_ITEM,
                reservation,
                currencyItemId,
                purchasedItemId,
                reservation.totalPrice,
                reservation.quantity,
                "Shop semantic item missing from current ItemCatalog"
            );
        }

        InventoryImage before=
            captureInventory(
                player.bank()
            );

        Plan plan=
            planExchange(
                before,
                currencyItemId,
                reservation.totalPrice,
                purchasedItemId,
                reservation.quantity
            );

        if(!plan.accepted()){
            cancelReserved(
                reservation.purchaseId
            );
            return rejected(
                plan.rejection,
                reservation,
                currencyItemId,
                purchasedItemId,
                reservation.totalPrice,
                reservation.quantity,
                plan.detail
            );
        }

        AtomicTransactionService.TransactionId
            transactionId=null;

        try{
            transactionId=
                transactions.create(
                    buyerRef,
                    "shop-purchase:"+
                        reservation.purchaseId,
                    reservation.sourceAuthority
                );

            transactions.reserve(
                transactionId,
                Arrays.asList(
                    new EscrowAsset(
                        EscrowAsset.Kind.CURRENCY,
                        reservation.currencyRef,
                        reservation.totalPrice,
                        buyerRef,
                        reservation.sourceAuthority
                    ),
                    new EscrowAsset(
                        EscrowAsset.Kind.ITEM,
                        reservation.itemRef,
                        reservation.quantity,
                        reservation.stockOwnerRef,
                        reservation.sourceAuthority
                    )
                )
            );

            /*
             * Canonical carried state changes before final settlement proof,
             * but both remain inside exact World/player ownership.  If the
             * Shop finalizer rejects while the transaction is still open,
             * inventory, transaction and finite stock are all rolled back.
             */
            player.bank()
                .replaceInventorySemantic(
                    plan.items,
                    plan.quantities
                );

            ShopService.PurchaseSnapshot settled=
                shops.commitReservedSettlement(
                    reservation.purchaseId,
                    transactionId
                );

            if(!settled.settled()||
               !transactionId.equals(
                    settled.settlementTransactionId))
                throw new IllegalStateException(
                    "Shop reserved settlement did not commit exact transaction "+
                    reservation.purchaseId
                );

            return new Result(
                Status.PURCHASED,
                settled,
                transactionId,
                currencyItemId,
                purchasedItemId,
                reservation.totalPrice,
                reservation.quantity,
                SAVE_REASON,
                "PURCHASED"
            );
        }catch(RuntimeException failure){
            player.bank()
                .replaceInventorySemantic(
                    before.items,
                    before.quantities
                );

            if(transactionId!=null)
                cancelTransactionIfOpen(
                    transactionId
                );

            ShopService.PurchaseSnapshot current=
                shops.getPurchase(
                    reservation.purchaseId
                );

            if(current!=null&&
               current.state==
                    ShopService.PurchaseState.RESERVED)
                shops.cancelPurchase(
                    reservation.purchaseId
                );

            throw failure;
        }
    }

    private void cancelTransactionIfOpen(
        AtomicTransactionService.TransactionId
            transactionId
    ){
        AtomicTransactionService.Snapshot snapshot=
            transactions.snapshot(
                transactionId
            );

        if(snapshot.state==
                AtomicTransactionService.TransactionState.CREATED||
           snapshot.state==
                AtomicTransactionService.TransactionState.RESERVED)
            transactions.cancel(
                transactionId
            );
    }

    private void cancelReserved(
        ShopService.PurchaseId purchaseId
    ){
        ShopService.PurchaseSnapshot current=
            shops.getPurchase(
                purchaseId
            );

        if(current!=null&&
           current.state==
               ShopService.PurchaseState.RESERVED)
            shops.cancelPurchase(
                purchaseId
            );
    }

    private static InventoryImage captureInventory(
        BankState bank
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

        for(int slot=0;
            slot<BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.Stack stack=
                bank.inventoryAt(slot);

            if(stack==null)
                continue;

            items[slot]=stack.itemId;
            quantities[slot]=stack.qty;
        }

        return new InventoryImage(
            items,
            quantities
        );
    }

    private static Plan planExchange(
        InventoryImage before,
        int currencyItemId,
        long currencyRequired,
        int purchasedItemId,
        long purchasedQuantity
    ){
        if(currencyRequired<=0L||
           purchasedQuantity<=0L)
            return Plan.rejected(
                Status.QUANTITY_OVERFLOW,
                "non-positive purchase amount"
            );

        int[] items=
            before.items.clone();
        int[] quantities=
            before.quantities.clone();

        long remainingCurrency=
            currencyRequired;

        for(int slot=0;
            slot<items.length&&
            remainingCurrency>0L;
            slot++){
            if(items[slot]!=
                    currencyItemId)
                continue;

            int available=
                quantities[slot];

            if(available<=0)
                return Plan.rejected(
                    Status.QUANTITY_OVERFLOW,
                    "invalid canonical currency quantity slot="+
                    slot+
                    " qty="+available
                );

            long consumed=
                Math.min(
                    remainingCurrency,
                    (long)available
                );

            quantities[slot]-=
                (int)consumed;
            remainingCurrency-=
                consumed;

            if(quantities[slot]==0)
                items[slot]=-1;
        }

        if(remainingCurrency>0L)
            return Plan.rejected(
                Status.INSUFFICIENT_CURRENCY,
                "missing currency item="+
                    currencyItemId+
                    " required="+currencyRequired+
                    " missing="+remainingCurrency
            );

        if(ItemCatalog.isStackable(
                purchasedItemId)){
            if(purchasedQuantity>
                    Integer.MAX_VALUE)
                return Plan.rejected(
                    Status.QUANTITY_OVERFLOW,
                    "stackable purchase exceeds int quantity item="+
                    purchasedItemId
                );

            int destination=
                findItem(
                    items,
                    purchasedItemId
                );

            if(destination<0)
                destination=
                    firstEmpty(items);

            if(destination<0)
                return Plan.rejected(
                    Status.INVENTORY_FULL,
                    "no inventory slot for stackable item="+
                    purchasedItemId
                );

            long next=
                (items[destination]<0
                    ?0L
                    :(long)quantities[destination])+
                purchasedQuantity;

            if(next>Integer.MAX_VALUE)
                return Plan.rejected(
                    Status.QUANTITY_OVERFLOW,
                    "stackable inventory quantity overflow item="+
                    purchasedItemId
                );

            items[destination]=
                purchasedItemId;
            quantities[destination]=
                (int)next;

            return Plan.accepted(
                items,
                quantities
            );
        }

        if(purchasedQuantity>
                BankState.INVENTORY_CAPACITY)
            return Plan.rejected(
                Status.INVENTORY_FULL,
                "non-stackable purchase exceeds inventory capacity item="+
                purchasedItemId+
                " qty="+purchasedQuantity
            );

        int free=0;

        for(int item:items)
            if(item<0)
                free++;

        if((long)free<
                purchasedQuantity)
            return Plan.rejected(
                Status.INVENTORY_FULL,
                "inventory free slots="+free+
                " required="+purchasedQuantity
            );

        for(long n=0L;
            n<purchasedQuantity;
            n++){
            int destination=
                firstEmpty(items);

            if(destination<0)
                throw new IllegalStateException(
                    "inventory free-slot planning drift"
                );

            items[destination]=
                purchasedItemId;
            quantities[destination]=1;
        }

        return Plan.accepted(
            items,
            quantities
        );
    }

    private static int parseItemRef(
        String semanticRef
    ){
        String normalized=
            MatchRules.normalizeKey(
                semanticRef,
                "semanticRef"
            );

        if(!normalized.startsWith(
                "item:"))
            throw new IllegalArgumentException(
                "inventory-backed Shop semantic ref must be item:<id> actual="+
                normalized
            );

        String idText=
            normalized.substring(
                "item:".length()
            );

        try{
            int itemId=
                Integer.parseInt(
                    idText
                );

            if(itemId<0)
                throw new NumberFormatException(
                    "negative"
                );

            return itemId;
        }catch(NumberFormatException bad){
            throw new IllegalArgumentException(
                "invalid Shop item semantic ref="+
                normalized,
                bad
            );
        }
    }

    private static int findItem(
        int[] items,
        int itemId
    ){
        for(int slot=0;
            slot<items.length;
            slot++)
            if(items[slot]==itemId)
                return slot;

        return -1;
    }

    private static int firstEmpty(
        int[] items
    ){
        for(int slot=0;
            slot<items.length;
            slot++)
            if(items[slot]<0)
                return slot;

        return -1;
    }

    private static Result rejected(
        Status status,
        ShopService.PurchaseSnapshot purchase,
        int currencyItemId,
        int purchasedItemId,
        long currencySpent,
        long quantity,
        String detail
    ){
        return new Result(
            status,
            purchase,
            null,
            currencyItemId,
            purchasedItemId,
            currencySpent,
            quantity,
            null,
            detail
        );
    }
}
