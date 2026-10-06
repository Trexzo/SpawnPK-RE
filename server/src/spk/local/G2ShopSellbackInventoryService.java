package spk.local;

import java.util.*;

/**
 * Clean-R25 sell-side Shop composition.
 *
 * ShopSellbackService owns buyback definitions, incoming Shop-stock reservation
 * and semantic settlement proof. This layer owns only the live player's
 * inventory-backed exchange: remove the sold semantic item and add the exact
 * payout semantic currency as one complete canonical inventory postimage.
 */
final class G2ShopSellbackInventoryService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2_SHOP_SELLBACK_V1";
    static final String SAVE_REASON=
        "G2_SHOP_SELLBACK";

    enum Status {
        SOLD,
        STALE_PLAYER,
        DEAD,
        UNSUPPORTED_SEMANTIC_KEY,
        UNKNOWN_ITEM,
        INSUFFICIENT_ITEM,
        INVENTORY_FULL,
        QUANTITY_OVERFLOW
    }

    static final class Result {
        final Status status;
        final ShopSellbackService.SaleSnapshot sale;
        final AtomicTransactionService.TransactionId transactionId;
        final int soldItemId;
        final int payoutItemId;
        final long quantity;
        final long payout;
        final String saveReason;
        final String detail;
        final String authority;

        private Result(
            Status status,
            ShopSellbackService.SaleSnapshot sale,
            AtomicTransactionService.TransactionId transactionId,
            int soldItemId,
            int payoutItemId,
            long quantity,
            long payout,
            String saveReason,
            String detail
        ){
            this.status=status;
            this.sale=sale;
            this.transactionId=transactionId;
            this.soldItemId=soldItemId;
            this.payoutItemId=payoutItemId;
            this.quantity=quantity;
            this.payout=payout;
            this.saveReason=saveReason;
            this.detail=detail;
            this.authority=AUTHORITY;
        }

        boolean sold(){
            return status==Status.SOLD;
        }

        @Override public String toString(){
            return "Result{status="+status+
                ",sale="+
                (sale==null?"null":sale.saleId)+
                ",transaction="+transactionId+
                ",soldItem="+soldItemId+
                ",payoutItem="+payoutItemId+
                ",quantity="+quantity+
                ",payout="+payout+
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
            Status status,
            String detail
        ){
            return new Plan(
                Objects.requireNonNull(
                    status,
                    "status"
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
    private final ShopSellbackService sellbacks;
    private final AtomicTransactionService transactions;

    G2ShopSellbackInventoryService(
        World world,
        WorldPlayer player,
        ShopSellbackService sellbacks
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.player=Objects.requireNonNull(player,"player");
        this.sellbacks=Objects.requireNonNull(sellbacks,"sellbacks");
        this.transactions=Objects.requireNonNull(
            sellbacks.transactions(),
            "transactions"
        );
    }

    Result sell(
        ShopService.ShopId shopId,
        String itemRef,
        long quantity
    ){
        final long generation=player.generation();
        final Result[] result=new Result[1];
        final boolean current;

        try{
            current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    player,
                    generation,
                    ()->result[0]=
                        sellOwned(
                            shopId,
                            itemRef,
                            quantity
                        )
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Shop sellback ownership execution failed",
                failure
            );
        }

        if(!current)
            return rejected(
                Status.STALE_PLAYER,
                null,
                -1,
                -1,
                quantity,
                0L,
                "player ownership is not current"
            );

        if(result[0]==null)
            throw new IllegalStateException(
                "Shop sellback ownership action produced no result"
            );

        return result[0];
    }

    private Result sellOwned(
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
                quantity,
                0L,
                "dead players cannot sell"
            );

        String sellerRef=
            PartyService.requireRef(
                player.username()
            );

        ShopSellbackService.SaleSnapshot reservation=
            sellbacks.requestSale(
                Objects.requireNonNull(
                    shopId,
                    "shopId"
                ),
                sellerRef,
                itemRef,
                quantity
            );

        final int soldItemId;
        final int payoutItemId;

        try{
            soldItemId=parseItemRef(reservation.itemRef);
            payoutItemId=parseItemRef(reservation.currencyRef);
        }catch(IllegalArgumentException unsupported){
            cancelReserved(reservation.saleId);
            return rejected(
                Status.UNSUPPORTED_SEMANTIC_KEY,
                reservation,
                -1,
                -1,
                reservation.quantity,
                reservation.totalPayout,
                unsupported.getMessage()
            );
        }

        if(!ItemCatalog.exists(soldItemId)||
           !ItemCatalog.exists(payoutItemId)){
            cancelReserved(reservation.saleId);
            return rejected(
                Status.UNKNOWN_ITEM,
                reservation,
                soldItemId,
                payoutItemId,
                reservation.quantity,
                reservation.totalPayout,
                "Shop sellback semantic item missing from current ItemCatalog"
            );
        }

        InventoryImage before=captureInventory(player.bank());
        Plan plan=
            planExchange(
                before,
                soldItemId,
                reservation.quantity,
                payoutItemId,
                reservation.totalPayout
            );

        if(!plan.accepted()){
            cancelReserved(reservation.saleId);
            return rejected(
                plan.rejection,
                reservation,
                soldItemId,
                payoutItemId,
                reservation.quantity,
                reservation.totalPayout,
                plan.detail
            );
        }

        AtomicTransactionService.TransactionId transactionId=null;

        try{
            transactionId=
                transactions.create(
                    sellerRef,
                    "shop-sellback:"+
                        reservation.saleId,
                    reservation.sourceAuthority
                );

            transactions.reserve(
                transactionId,
                Arrays.asList(
                    new EscrowAsset(
                        EscrowAsset.Kind.ITEM,
                        reservation.itemRef,
                        reservation.quantity,
                        sellerRef,
                        reservation.sourceAuthority
                    ),
                    new EscrowAsset(
                        EscrowAsset.Kind.CURRENCY,
                        reservation.currencyRef,
                        reservation.totalPayout,
                        reservation.shopStockOwnerRef,
                        reservation.sourceAuthority
                    )
                )
            );

            player.bank().replaceInventorySemantic(
                plan.items,
                plan.quantities
            );

            ShopSellbackService.SaleSnapshot settled=
                sellbacks.commitReservedSettlement(
                    reservation.saleId,
                    transactionId
                );

            if(settled.state!=
                    ShopSellbackService.SaleState.SETTLED||
               !transactionId.equals(
                    settled.settlementTransactionId))
                throw new IllegalStateException(
                    "Shop sellback did not settle exact transaction "+
                    reservation.saleId
                );

            return new Result(
                Status.SOLD,
                settled,
                transactionId,
                soldItemId,
                payoutItemId,
                reservation.quantity,
                reservation.totalPayout,
                SAVE_REASON,
                "SOLD"
            );
        }catch(RuntimeException failure){
            player.bank().replaceInventorySemantic(
                before.items,
                before.quantities
            );

            if(transactionId!=null)
                cancelTransactionIfOpen(transactionId);

            ShopSellbackService.SaleSnapshot current=
                sellbacks.get(
                    reservation.saleId
                );

            if(current!=null&&
               current.state==
                    ShopSellbackService.SaleState.RESERVED)
                sellbacks.cancelSale(
                    reservation.saleId
                );

            throw failure;
        }
    }

    private void cancelReserved(
        ShopSellbackService.SaleId saleId
    ){
        ShopSellbackService.SaleSnapshot current=
            sellbacks.get(saleId);

        if(current!=null&&
           current.state==
                ShopSellbackService.SaleState.RESERVED)
            sellbacks.cancelSale(saleId);
    }

    private void cancelTransactionIfOpen(
        AtomicTransactionService.TransactionId id
    ){
        AtomicTransactionService.Snapshot snapshot=
            transactions.snapshot(id);

        if(snapshot.state==
                AtomicTransactionService.TransactionState.CREATED||
           snapshot.state==
                AtomicTransactionService.TransactionState.RESERVED)
            transactions.cancel(id);
    }

    private static InventoryImage captureInventory(
        BankState bank
    ){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);

        for(int slot=0;slot<items.length;slot++){
            BankState.InventorySlotSnapshot snapshot=
                bank.inventorySlotSnapshot(slot);

            if(!snapshot.occupied)
                continue;

            items[slot]=snapshot.itemId;
            quantities[slot]=snapshot.quantity;
        }

        return new InventoryImage(items,quantities);
    }

    private static Plan planExchange(
        InventoryImage before,
        int soldItemId,
        long soldQuantity,
        int payoutItemId,
        long payoutQuantity
    ){
        if(soldQuantity<=0L||payoutQuantity<=0L)
            return Plan.rejected(
                Status.QUANTITY_OVERFLOW,
                "non-positive sellback quantity"
            );

        int[] items=before.items.clone();
        int[] quantities=before.quantities.clone();

        long remaining=soldQuantity;

        for(int slot=0;slot<items.length&&remaining>0L;slot++){
            if(items[slot]!=soldItemId)
                continue;

            int available=quantities[slot];

            if(available<=0)
                return Plan.rejected(
                    Status.QUANTITY_OVERFLOW,
                    "invalid canonical sold-item quantity slot="+
                    slot+
                    " qty="+available
                );

            long consumed=Math.min(remaining,(long)available);
            quantities[slot]-=(int)consumed;
            remaining-=consumed;

            if(quantities[slot]==0)
                items[slot]=-1;
        }

        if(remaining>0L)
            return Plan.rejected(
                Status.INSUFFICIENT_ITEM,
                "missing sold item="+
                    soldItemId+
                    " required="+soldQuantity+
                    " missing="+remaining
            );

        if(ItemCatalog.isStackable(payoutItemId)){
            if(payoutQuantity>Integer.MAX_VALUE)
                return Plan.rejected(
                    Status.QUANTITY_OVERFLOW,
                    "stackable payout exceeds int quantity item="+
                    payoutItemId
                );

            int destination=findItem(items,payoutItemId);

            if(destination<0)
                destination=firstEmpty(items);

            if(destination<0)
                return Plan.rejected(
                    Status.INVENTORY_FULL,
                    "no inventory slot for payout item="+
                    payoutItemId
                );

            long next=
                (items[destination]<0
                    ?0L
                    :(long)quantities[destination])+
                payoutQuantity;

            if(next>Integer.MAX_VALUE)
                return Plan.rejected(
                    Status.QUANTITY_OVERFLOW,
                    "payout inventory quantity overflow item="+
                    payoutItemId
                );

            items[destination]=payoutItemId;
            quantities[destination]=(int)next;

            return Plan.accepted(items,quantities);
        }

        if(payoutQuantity>BankState.INVENTORY_CAPACITY)
            return Plan.rejected(
                Status.INVENTORY_FULL,
                "non-stackable payout exceeds inventory capacity item="+
                    payoutItemId+
                    " qty="+payoutQuantity
            );

        int free=0;
        for(int item:items)
            if(item<0)
                free++;

        if((long)free<payoutQuantity)
            return Plan.rejected(
                Status.INVENTORY_FULL,
                "inventory free slots="+free+
                " payoutRequired="+payoutQuantity
            );

        for(long n=0L;n<payoutQuantity;n++){
            int destination=firstEmpty(items);

            if(destination<0)
                throw new IllegalStateException(
                    "sellback payout free-slot planning drift"
                );

            items[destination]=payoutItemId;
            quantities[destination]=1;
        }

        return Plan.accepted(items,quantities);
    }

    private static int parseItemRef(String semanticRef){
        String normalized=
            MatchRules.normalizeKey(
                semanticRef,
                "semanticRef"
            );

        if(!normalized.startsWith("item:"))
            throw new IllegalArgumentException(
                "inventory-backed sellback semantic ref must be item:<id> actual="+
                normalized
            );

        String idText=normalized.substring("item:".length());

        try{
            int itemId=Integer.parseInt(idText);

            if(itemId<0)
                throw new NumberFormatException("negative");

            return itemId;
        }catch(NumberFormatException bad){
            throw new IllegalArgumentException(
                "invalid sellback item semantic ref="+
                normalized,
                bad
            );
        }
    }

    private static int findItem(int[] items,int itemId){
        for(int slot=0;slot<items.length;slot++)
            if(items[slot]==itemId)
                return slot;
        return -1;
    }

    private static int firstEmpty(int[] items){
        for(int slot=0;slot<items.length;slot++)
            if(items[slot]<0)
                return slot;
        return -1;
    }

    private static Result rejected(
        Status status,
        ShopSellbackService.SaleSnapshot sale,
        int soldItemId,
        int payoutItemId,
        long quantity,
        long payout,
        String detail
    ){
        return new Result(
            status,
            sale,
            null,
            soldItemId,
            payoutItemId,
            quantity,
            payout,
            null,
            detail
        );
    }

    private G2ShopSellbackInventoryService(){}
}
