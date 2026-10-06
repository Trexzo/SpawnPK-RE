package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caller-defined Shop sell/buyback lifecycle above ShopService.
 *
 * Inventory/currency mutation is external. A sale settles only after the
 * canonical AtomicTransactionService proves seller item transfer and Shop
 * currency payout.
 */
final class ShopSellbackService {
    enum SaleState {
        RESERVED,
        SETTLED,
        CANCELLED
    }

    static final class SaleId
        implements Comparable<SaleId> {

        private final long value;

        SaleId(long value){
            if(value<=0L)
                throw new IllegalArgumentException(
                    "saleId="+value
                );
            this.value=value;
        }

        long value(){
            return value;
        }

        @Override public int compareTo(SaleId other){
            return Long.compare(
                value,
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(Object other){
            return other instanceof SaleId&&
                value==((SaleId)other).value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return "shop-sale-"+
                Long.toUnsignedString(value);
        }
    }

    static final class BuybackDefinition {
        final ShopService.ShopId shopId;
        final String itemRef;
        final String currencyRef;
        final long priceEach;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;

        BuybackDefinition(
            ShopService.ShopId shopId,
            String itemRef,
            String currencyRef,
            long priceEach,
            AtomicTransactionService.SourceAuthority
                sourceAuthority
        ){
            this.shopId=
                Objects.requireNonNull(
                    shopId,
                    "shopId"
                );
            this.itemRef=
                normalizeKey(
                    itemRef,
                    "itemRef"
                );
            this.currencyRef=
                normalizeKey(
                    currencyRef,
                    "currencyRef"
                );

            if(priceEach<=0L)
                throw new IllegalArgumentException(
                    "priceEach="+priceEach
                );

            this.priceEach=priceEach;
            this.sourceAuthority=
                Objects.requireNonNull(
                    sourceAuthority,
                    "sourceAuthority"
                );
        }
    }

    static final class SaleSnapshot {
        final SaleId saleId;
        final ShopService.ShopId shopId;
        final String sellerRef;
        final String shopStockOwnerRef;
        final String itemRef;
        final String currencyRef;
        final long quantity;
        final long priceEach;
        final long totalPayout;
        final ShopService.StockMode stockMode;
        final SaleState state;
        final AtomicTransactionService.TransactionId
            settlementTransactionId;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;

        SaleSnapshot(Sale sale){
            this.saleId=sale.id;
            this.shopId=
                sale.definition.shopId;
            this.sellerRef=sale.sellerRef;
            this.shopStockOwnerRef=
                sale.shopStockOwnerRef;
            this.itemRef=
                sale.definition.itemRef;
            this.currencyRef=
                sale.definition.currencyRef;
            this.quantity=sale.quantity;
            this.priceEach=
                sale.definition.priceEach;
            this.totalPayout=
                sale.totalPayout;
            this.stockMode=sale.stockMode;
            this.state=sale.state;
            this.settlementTransactionId=
                sale.settlementTransactionId;
            this.sourceAuthority=
                sale.definition.sourceAuthority;
        }

        boolean terminal(){
            return state==SaleState.SETTLED||
                state==SaleState.CANCELLED;
        }
    }

    private static final class Key {
        final ShopService.ShopId shopId;
        final String itemRef;

        Key(
            ShopService.ShopId shopId,
            String itemRef
        ){
            this.shopId=shopId;
            this.itemRef=itemRef;
        }

        @Override public boolean equals(Object other){
            return other instanceof Key&&
                shopId.equals(((Key)other).shopId)&&
                itemRef.equals(((Key)other).itemRef);
        }

        @Override public int hashCode(){
            return Objects.hash(shopId,itemRef);
        }
    }

    private static final class Sale {
        final SaleId id;
        final BuybackDefinition definition;
        final String sellerRef;
        final String shopStockOwnerRef;
        final long quantity;
        final long totalPayout;
        final ShopService.StockMode stockMode;
        final String stockReservationRef;

        SaleState state=SaleState.RESERVED;
        boolean externalOperationInFlight;
        AtomicTransactionService.TransactionId
            settlementTransactionId;

        Sale(
            SaleId id,
            BuybackDefinition definition,
            String sellerRef,
            String shopStockOwnerRef,
            long quantity,
            long totalPayout,
            ShopService.StockMode stockMode
        ){
            this.id=id;
            this.definition=definition;
            this.sellerRef=sellerRef;
            this.shopStockOwnerRef=
                shopStockOwnerRef;
            this.quantity=quantity;
            this.totalPayout=totalPayout;
            this.stockMode=stockMode;
            this.stockReservationRef=
                "sellback:"+id.value();
        }

        SaleSnapshot snapshot(){
            return new SaleSnapshot(this);
        }
    }

    private final ShopService shops;
    private final AtomicTransactionService transactions;
    private final AtomicLong saleSequence=
        new AtomicLong();

    private final LinkedHashMap<Key,BuybackDefinition>
        definitions=
            new LinkedHashMap<>();

    private final LinkedHashMap<SaleId,Sale>
        sales=
            new LinkedHashMap<>();

    ShopSellbackService(ShopService shops){
        this.shops=
            Objects.requireNonNull(
                shops,
                "shops"
            );
        this.transactions=
            shops.transactions();
    }

    BuybackDefinition register(
        BuybackDefinition definition
    ){
        BuybackDefinition checked=
            Objects.requireNonNull(
                definition,
                "definition"
            );

        ShopService.ShopSnapshot shop=
            shops.getShop(
                checked.shopId
            );

        if(shop==null)
            throw new IllegalArgumentException(
                "unknown Shop for buyback "+
                checked.shopId
            );

        ShopService.OfferSnapshot offer=
            shop.offer(
                checked.itemRef
            );

        if(offer==null)
            throw new IllegalArgumentException(
                "buyback item is not a Shop offer "+
                checked.itemRef
            );

        if(!offer.currencyRef.equals(
                checked.currencyRef))
            throw new IllegalArgumentException(
                "buyback currency mismatch item="+
                checked.itemRef
            );

        if(shop.sourceAuthority!=
                checked.sourceAuthority)
            throw new IllegalArgumentException(
                "buyback authority mismatch shop="+
                shop.sourceAuthority+
                " definition="+
                checked.sourceAuthority
            );

        Key key=
            new Key(
                checked.shopId,
                checked.itemRef
            );

        synchronized(this){
            if(definitions.putIfAbsent(
                    key,
                    checked)!=null)
                throw new IllegalStateException(
                    "duplicate Shop buyback "+
                    checked.shopId+
                    " item="+checked.itemRef
                );
        }

        return checked;
    }

    SaleSnapshot requestSale(
        ShopService.ShopId shopId,
        String sellerRef,
        String itemRef,
        long quantity
    ){
        if(quantity<=0L)
            throw new IllegalArgumentException(
                "quantity="+quantity
            );

        ShopService.ShopId checkedShopId=
            Objects.requireNonNull(
                shopId,
                "shopId"
            );
        String item=
            normalizeKey(
                itemRef,
                "itemRef"
            );
        String seller=
            requireText(
                sellerRef,
                "sellerRef"
            );

        final BuybackDefinition definition;

        synchronized(this){
            definition=
                definitions.get(
                    new Key(
                        checkedShopId,
                        item
                    )
                );
        }

        if(definition==null)
            throw new IllegalArgumentException(
                "item has no Shop buyback definition "+
                checkedShopId+
                " item="+item
            );

        long total=
            multiply(
                quantity,
                definition.priceEach
            );

        ShopService.ShopSnapshot shop=
            Objects.requireNonNull(
                shops.getShop(
                    checkedShopId
                ),
                "Shop disappeared"
            );

        ShopService.OfferSnapshot offer=
            Objects.requireNonNull(
                shop.offer(item),
                "Shop offer disappeared"
            );

        SaleId id=nextSaleId();

        Sale sale=
            new Sale(
                id,
                definition,
                seller,
                shop.stockOwnerRef,
                quantity,
                total,
                offer.stockMode
            );

        shops.reserveIncomingStock(
            checkedShopId,
            item,
            sale.stockReservationRef,
            quantity
        );

        synchronized(this){
            sales.put(
                id,
                sale
            );
        }

        return sale.snapshot();
    }

    /**
     * Finalize one caller-owned RESERVED transaction for a reserved sale.
     *
     * The sale's external-operation fence prevents its incoming stock
     * reservation from being cancelled through ShopSellbackService while
     * validation/commit is in progress. Incoming stock capacity and
     * transaction-use identity are preflighted before the transaction becomes
     * final.
     */
    SaleSnapshot commitReservedSettlement(
        SaleId saleId,
        AtomicTransactionService.TransactionId
            transactionId
    ){
        final Sale live;
        final AtomicTransactionService.TransactionId
            requested=
                Objects.requireNonNull(
                    transactionId,
                    "transactionId"
                );
        final String useKey;

        synchronized(this){
            live=requireSale(saleId);

            if(live.externalOperationInFlight)
                throw new IllegalStateException(
                    "Shop sale external operation already in flight "+
                    live.id
                );

            if(live.state==SaleState.CANCELLED)
                throw new IllegalStateException(
                    "cannot settle cancelled sale "+
                    live.id
                );

            if(live.state==SaleState.SETTLED){
                if(!requested.equals(
                        live.settlementTransactionId))
                    throw new IllegalStateException(
                        "sale already settled with "+
                        live.settlementTransactionId
                    );

                return live.snapshot();
            }

            if(live.state!=SaleState.RESERVED)
                throw new IllegalStateException(
                    "Shop sale settlement invalid from "+
                    live.state+
                    " for "+
                    live.id
                );

            live.externalOperationInFlight=true;
            useKey="sale:"+live.id.value();
        }

        AtomicTransactionService.Snapshot settlement=null;
        RuntimeException failure=null;

        try{
            AtomicTransactionService.Snapshot reserved=
                transactions.snapshot(
                    requested
                );

            verifyReservedSettlement(
                live,
                reserved
            );

            shops.preflightIncomingStockSettlement(
                live.definition.shopId,
                live.definition.itemRef,
                live.stockReservationRef,
                requested,
                useKey
            );

            settlement=
                transactions.commit(
                    requested
                );

            verifySettlement(
                live,
                settlement
            );

            shops.commitIncomingStockSettlement(
                live.definition.shopId,
                live.definition.itemRef,
                live.stockReservationRef,
                settlement.transactionId,
                useKey
            );
        }catch(RuntimeException error){
            failure=error;
        }

        final SaleSnapshot result;

        synchronized(this){
            Sale current=
                requireSale(
                    live.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Shop sale identity changed "+
                    live.id
                );

            try{
                if(failure==null){
                    if(current.state!=
                            SaleState.RESERVED)
                        throw new IllegalStateException(
                            "Shop sale settlement invalid from "+
                            current.state+
                            " for "+
                            current.id
                        );

                    current.settlementTransactionId=
                        settlement.transactionId;
                    current.state=
                        SaleState.SETTLED;
                }

                result=current.snapshot();
            }finally{
                current.externalOperationInFlight=false;
            }
        }

        if(failure!=null)
            throw failure;

        return result;
    }

    SaleSnapshot confirmSettlement(
        SaleId saleId,
        AtomicTransactionService.TransactionId
            transactionId
    ){
        final Sale live;
        final AtomicTransactionService.TransactionId
            requested=
                Objects.requireNonNull(
                    transactionId,
                    "transactionId"
                );
        final boolean alreadySettled;

        synchronized(this){
            live=requireSale(saleId);

            if(live.externalOperationInFlight)
                throw new IllegalStateException(
                    "Shop sale external operation already in flight "+
                    live.id
                );

            if(live.state==SaleState.CANCELLED)
                throw new IllegalStateException(
                    "cannot settle cancelled sale "+
                    live.id
                );

            alreadySettled=
                live.state==SaleState.SETTLED;

            if(alreadySettled&&
               !live.settlementTransactionId
                    .equals(requested))
                throw new IllegalStateException(
                    "sale already settled with "+
                    live.settlementTransactionId
                );

            live.externalOperationInFlight=
                true;
        }

        AtomicTransactionService.Snapshot settlement=null;
        RuntimeException failure=null;

        try{
            settlement=
                transactions.snapshot(
                    requested
                );

            verifySettlement(
                live,
                settlement
            );

            if(!alreadySettled)
                shops.commitIncomingStockSettlement(
                    live.definition.shopId,
                    live.definition.itemRef,
                    live.stockReservationRef,
                    settlement.transactionId,
                    "sale:"+live.id.value()
                );
        }catch(RuntimeException error){
            failure=error;
        }

        final SaleSnapshot result;

        synchronized(this){
            Sale current=
                requireSale(
                    live.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Shop sale identity changed "+
                    live.id
                );

            try{
                if(failure==null&&
                   !alreadySettled){
                    if(current.state!=
                            SaleState.RESERVED)
                        throw new IllegalStateException(
                            "Shop sale settlement invalid from "+
                            current.state+
                            " for "+
                            current.id
                        );

                    current.settlementTransactionId=
                        settlement.transactionId;
                    current.state=
                        SaleState.SETTLED;
                }

                result=
                    current.snapshot();
            }finally{
                current.externalOperationInFlight=
                    false;
            }
        }

        if(failure!=null)
            throw failure;

        return result;
    }

    SaleSnapshot cancelSale(
        SaleId saleId
    ){
        final Sale live;

        synchronized(this){
            live=requireSale(saleId);

            if(live.externalOperationInFlight)
                throw new IllegalStateException(
                    "Shop sale external operation already in flight "+
                    live.id
                );

            if(live.state==SaleState.CANCELLED)
                return live.snapshot();

            if(live.state==SaleState.SETTLED)
                throw new IllegalStateException(
                    "cannot cancel settled sale "+
                    live.id
                );

            live.externalOperationInFlight=
                true;
        }

        boolean released=false;
        RuntimeException failure=null;

        try{
            released=
                shops.cancelIncomingStock(
                    live.definition.shopId,
                    live.definition.itemRef,
                    live.stockReservationRef
                );

            if(live.stockMode==
                    ShopService.StockMode.FINITE&&
               !released)
                throw new IllegalStateException(
                    "finite incoming stock reservation missing "+
                    live.stockReservationRef
                );
        }catch(RuntimeException error){
            failure=error;
        }

        final SaleSnapshot result;

        synchronized(this){
            Sale current=
                requireSale(
                    live.id
                );

            if(current!=live)
                throw new IllegalStateException(
                    "Shop sale identity changed "+
                    live.id
                );

            try{
                if(failure==null){
                    if(current.state!=
                            SaleState.RESERVED)
                        throw new IllegalStateException(
                            "Shop sale cancellation invalid from "+
                            current.state+
                            " for "+
                            current.id
                        );

                    current.state=
                        SaleState.CANCELLED;
                }

                result=
                    current.snapshot();
            }finally{
                current.externalOperationInFlight=
                    false;
            }
        }

        if(failure!=null)
            throw failure;

        return result;
    }

    synchronized SaleSnapshot get(
        SaleId saleId
    ){
        Sale sale=
            sales.get(
                Objects.requireNonNull(
                    saleId,
                    "saleId"
                )
            );

        return sale==null
            ?null
            :sale.snapshot();
    }

    synchronized int size(){
        return sales.size();
    }

    synchronized int definitionCount(){
        return definitions.size();
    }

    AtomicTransactionService transactions(){
        return transactions;
    }

    synchronized List<SaleSnapshot> snapshot(){
        ArrayList<Sale> ordered=
            new ArrayList<>(
                sales.values()
            );

        ordered.sort(
            Comparator.comparing(
                sale->sale.id
            )
        );

        ArrayList<SaleSnapshot> out=
            new ArrayList<>();

        for(Sale sale:ordered)
            out.add(sale.snapshot());

        return Collections.unmodifiableList(out);
    }

    private Sale requireSale(SaleId saleId){
        SaleId id=
            Objects.requireNonNull(
                saleId,
                "saleId"
            );

        Sale sale=sales.get(id);

        if(sale==null)
            throw new IllegalArgumentException(
                "unknown Shop sale "+id
            );

        return sale;
    }

    private static void verifySettlement(
        Sale sale,
        AtomicTransactionService.Snapshot settlement
    ){
        verifySettlementState(
            sale,
            settlement,
            AtomicTransactionService
                .TransactionState
                .COMMITTED
        );
    }

    private static void verifyReservedSettlement(
        Sale sale,
        AtomicTransactionService.Snapshot settlement
    ){
        verifySettlementState(
            sale,
            settlement,
            AtomicTransactionService
                .TransactionState
                .RESERVED
        );
    }

    private static void verifySettlementState(
        Sale sale,
        AtomicTransactionService.Snapshot settlement,
        AtomicTransactionService.TransactionState requiredState
    ){
        if(settlement.state!=requiredState)
            throw new IllegalArgumentException(
                "sellback settlement must be "+
                requiredState+
                " state="+
                settlement.state
            );

        if(!sale.sellerRef.equals(
                settlement.ownerRef))
            throw new SecurityException(
                "sellback seller mismatch expected="+
                sale.sellerRef+
                " actual="+settlement.ownerRef
            );

        if(settlement.sourceAuthority!=
                sale.definition.sourceAuthority)
            throw new IllegalArgumentException(
                "sellback settlement authority mismatch"
            );

        if(!containsAsset(
                settlement,
                EscrowAsset.Kind.ITEM,
                sale.definition.itemRef,
                sale.quantity,
                sale.sellerRef,
                sale.definition.sourceAuthority))
            throw new IllegalArgumentException(
                "sellback settlement missing seller item coverage"
            );

        if(!containsAsset(
                settlement,
                EscrowAsset.Kind.CURRENCY,
                sale.definition.currencyRef,
                sale.totalPayout,
                sale.shopStockOwnerRef,
                sale.definition.sourceAuthority))
            throw new IllegalArgumentException(
                "sellback settlement missing Shop currency payout"
            );
    }

    private static boolean containsAsset(
        AtomicTransactionService.Snapshot snapshot,
        EscrowAsset.Kind kind,
        String semanticKey,
        long minimumQuantity,
        String ownerRef,
        AtomicTransactionService.SourceAuthority authority
    ){
        long total=0L;

        for(AtomicTransactionService.Reservation reservation:
                snapshot.reservations){
            EscrowAsset asset=reservation.asset;

            if(asset.kind!=kind||
               !semanticKey.equals(
                    normalizeKey(
                        asset.semanticKey,
                        "semanticKey"
                    ))||
               !ownerRef.equals(
                    asset.ownerRef)||
               asset.sourceAuthority!=authority)
                continue;

            try{
                total=Math.addExact(
                    total,
                    asset.quantity
                );
            }catch(ArithmeticException overflow){
                return true;
            }

            if(total>=minimumQuantity)
                return true;
        }

        return false;
    }

    private SaleId nextSaleId(){
        long value=
            saleSequence.incrementAndGet();

        if(value<=0L)
            throw new IllegalStateException(
                "Shop sale sequence exhausted"
            );

        return new SaleId(value);
    }

    private static long multiply(
        long quantity,
        long priceEach
    ){
        try{
            return Math.multiplyExact(
                quantity,
                priceEach
            );
        }catch(ArithmeticException overflow){
            throw new IllegalArgumentException(
                "sellback total payout overflow quantity="+
                quantity+
                " priceEach="+priceEach,
                overflow
            );
        }
    }

    private static String normalizeKey(
        String value,
        String field
    ){
        return MatchRules.normalizeKey(
            value,
            field
        );
    }

    private static String requireText(
        String value,
        String field
    ){
        return MatchRules.requireText(
            value,
            field
        );
    }
}
