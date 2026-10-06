package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Protocol-independent buy-side Shop stock and purchase reservation lifecycle.
 *
 * Shop content is caller-defined. Inventory/currency mutation and presentation
 * remain outside this service. A purchase settles only after an external
 * AtomicTransactionService transaction is COMMITTED and proves both sides of
 * the requested exchange.
 */
final class ShopService {
    static final long UNLIMITED_STOCK=-1L;

    enum StockMode {
        FINITE,
        UNLIMITED
    }

    enum PurchaseState {
        RESERVED,
        SETTLED,
        CANCELLED
    }

    static final class ShopId
        implements Comparable<ShopId> {

        private final String value;

        ShopId(String value){
            this.value=normalizeKey(
                value,
                "shopId"
            );
        }

        static ShopId of(String value){
            return new ShopId(value);
        }

        String value(){
            return value;
        }

        @Override public int compareTo(ShopId other){
            return value.compareTo(
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(Object other){
            return other instanceof ShopId&&
                value.equals(
                    ((ShopId)other).value
                );
        }

        @Override public int hashCode(){
            return value.hashCode();
        }

        @Override public String toString(){
            return value;
        }
    }

    static final class PurchaseId
        implements Comparable<PurchaseId> {

        private final long value;

        PurchaseId(long value){
            if(value<=0L)
                throw new IllegalArgumentException(
                    "purchaseId="+value
                );
            this.value=value;
        }

        long value(){
            return value;
        }

        @Override public int compareTo(PurchaseId other){
            return Long.compare(
                value,
                Objects.requireNonNull(
                    other,
                    "other"
                ).value
            );
        }

        @Override public boolean equals(Object other){
            return other instanceof PurchaseId&&
                value==
                    ((PurchaseId)other).value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return "shop-purchase-"+
                Long.toUnsignedString(
                    value
                );
        }
    }

    static final class Offer {
        final String itemRef;
        final String currencyRef;
        final long priceEach;
        final StockMode stockMode;
        final long initialStock;

        private Offer(
            String itemRef,
            String currencyRef,
            long priceEach,
            StockMode stockMode,
            long initialStock
        ){
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
            this.stockMode=
                Objects.requireNonNull(
                    stockMode,
                    "stockMode"
                );

            if(stockMode==StockMode.FINITE){
                if(initialStock<0L)
                    throw new IllegalArgumentException(
                        "initialStock="+
                        initialStock
                    );
                this.initialStock=
                    initialStock;
            }else{
                if(initialStock!=
                        UNLIMITED_STOCK)
                    throw new IllegalArgumentException(
                        "unlimited offer initialStock must be "+
                        UNLIMITED_STOCK
                    );
                this.initialStock=
                    UNLIMITED_STOCK;
            }
        }

        static Offer finite(
            String itemRef,
            String currencyRef,
            long priceEach,
            long initialStock
        ){
            return new Offer(
                itemRef,
                currencyRef,
                priceEach,
                StockMode.FINITE,
                initialStock
            );
        }

        static Offer unlimited(
            String itemRef,
            String currencyRef,
            long priceEach
        ){
            return new Offer(
                itemRef,
                currencyRef,
                priceEach,
                StockMode.UNLIMITED,
                UNLIMITED_STOCK
            );
        }
    }

    static final class ShopDefinition {
        final ShopId id;
        final String name;
        final String stockOwnerRef;
        final List<Offer> offers;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;

        ShopDefinition(
            ShopId id,
            String name,
            String stockOwnerRef,
            Collection<Offer> offers,
            AtomicTransactionService.SourceAuthority
                sourceAuthority
        ){
            this.id=
                Objects.requireNonNull(
                    id,
                    "id"
                );
            this.name=
                requireText(
                    name,
                    "name"
                );
            this.stockOwnerRef=
                requireText(
                    stockOwnerRef,
                    "stockOwnerRef"
                );
            this.sourceAuthority=
                Objects.requireNonNull(
                    sourceAuthority,
                    "sourceAuthority"
                );

            Objects.requireNonNull(
                offers,
                "offers"
            );

            if(offers.isEmpty())
                throw new IllegalArgumentException(
                    "offers empty"
                );

            ArrayList<Offer> copy=
                new ArrayList<>();
            HashSet<String> items=
                new HashSet<>();

            for(Offer offer:offers){
                Offer checked=
                    Objects.requireNonNull(
                        offer,
                        "offer"
                    );

                if(!items.add(
                        checked.itemRef))
                    throw new IllegalArgumentException(
                        "duplicate shop item "+
                        checked.itemRef
                    );

                copy.add(checked);
            }

            this.offers=
                Collections.unmodifiableList(
                    copy
                );
        }
    }

    static final class OfferSnapshot {
        final String itemRef;
        final String currencyRef;
        final long priceEach;
        final StockMode stockMode;
        final OptionalLong availableStock;

        OfferSnapshot(OfferState state){
            this.itemRef=state.offer.itemRef;
            this.currencyRef=
                state.offer.currencyRef;
            this.priceEach=
                state.offer.priceEach;
            this.stockMode=
                state.offer.stockMode;
            this.availableStock=
                state.offer.stockMode==
                    StockMode.FINITE
                        ?OptionalLong.of(
                            state.availableStock
                        )
                        :OptionalLong.empty();
        }
    }

    static final class ShopSnapshot {
        final ShopId id;
        final String name;
        final String stockOwnerRef;
        final List<OfferSnapshot> offers;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;

        ShopSnapshot(ShopEntry entry){
            this.id=entry.definition.id;
            this.name=entry.definition.name;
            this.stockOwnerRef=
                entry.definition.stockOwnerRef;
            this.sourceAuthority=
                entry.definition.sourceAuthority;

            ArrayList<OfferSnapshot> out=
                new ArrayList<>();

            for(OfferState state:
                    entry.offers.values())
                out.add(
                    new OfferSnapshot(
                        state
                    )
                );

            this.offers=
                Collections.unmodifiableList(
                    out
                );
        }

        OfferSnapshot offer(String itemRef){
            String key=
                normalizeKey(
                    itemRef,
                    "itemRef"
                );

            for(OfferSnapshot offer:offers)
                if(offer.itemRef.equals(key))
                    return offer;

            return null;
        }
    }

    static final class PurchaseSnapshot {
        final PurchaseId purchaseId;
        final ShopId shopId;
        final String buyerRef;
        final String stockOwnerRef;
        final String itemRef;
        final String currencyRef;
        final long quantity;
        final long priceEach;
        final long totalPrice;
        final StockMode stockMode;
        final PurchaseState state;
        final AtomicTransactionService.TransactionId
            settlementTransactionId;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;

        PurchaseSnapshot(Purchase purchase){
            this.purchaseId=purchase.id;
            this.shopId=purchase.shop.definition.id;
            this.buyerRef=purchase.buyerRef;
            this.stockOwnerRef=
                purchase.shop.definition
                    .stockOwnerRef;
            this.itemRef=
                purchase.offer.offer.itemRef;
            this.currencyRef=
                purchase.offer.offer.currencyRef;
            this.quantity=purchase.quantity;
            this.priceEach=
                purchase.offer.offer.priceEach;
            this.totalPrice=
                purchase.totalPrice;
            this.stockMode=
                purchase.offer.offer.stockMode;
            this.state=purchase.state;
            this.settlementTransactionId=
                purchase.settlementTransactionId;
            this.sourceAuthority=
                purchase.shop.definition
                    .sourceAuthority;
        }

        boolean terminal(){
            return state==PurchaseState.SETTLED||
                state==PurchaseState.CANCELLED;
        }

        boolean settled(){
            return state==PurchaseState.SETTLED;
        }
    }

    private static final class OfferState {
        final Offer offer;
        long availableStock;
        final LinkedHashMap<String,Long> incomingReservations=
            new LinkedHashMap<>();

        OfferState(Offer offer){
            this.offer=offer;
            this.availableStock=
                offer.stockMode==StockMode.FINITE
                    ?offer.initialStock
                    :UNLIMITED_STOCK;
        }
    }

    private static final class ShopEntry {
        final ShopDefinition definition;
        final LinkedHashMap<String,OfferState>
            offers=
                new LinkedHashMap<>();

        ShopEntry(ShopDefinition definition){
            this.definition=definition;

            for(Offer offer:
                    definition.offers)
                offers.put(
                    offer.itemRef,
                    new OfferState(offer)
                );
        }

        ShopSnapshot snapshot(){
            return new ShopSnapshot(this);
        }
    }

    private static final class Purchase {
        final PurchaseId id;
        final ShopEntry shop;
        final OfferState offer;
        final String buyerRef;
        final long quantity;
        final long totalPrice;

        PurchaseState state=
            PurchaseState.RESERVED;

        boolean settlementCheckInFlight;

        AtomicTransactionService.TransactionId
            settlementTransactionId;

        Purchase(
            PurchaseId id,
            ShopEntry shop,
            OfferState offer,
            String buyerRef,
            long quantity,
            long totalPrice
        ){
            this.id=id;
            this.shop=shop;
            this.offer=offer;
            this.buyerRef=buyerRef;
            this.quantity=quantity;
            this.totalPrice=totalPrice;
        }

        PurchaseSnapshot snapshot(){
            return new PurchaseSnapshot(this);
        }
    }

    private final AtomicTransactionService transactions;

    private final AtomicLong purchaseSequence=
        new AtomicLong();

    private final LinkedHashMap<ShopId,ShopEntry>
        shops=
            new LinkedHashMap<>();

    private final LinkedHashMap<
        PurchaseId,
        Purchase
    > purchases=
        new LinkedHashMap<>();

    private final LinkedHashMap<
        AtomicTransactionService.TransactionId,
        String
    > settlementUses=
        new LinkedHashMap<>();

    ShopService(
        AtomicTransactionService transactions
    ){
        this.transactions=
            Objects.requireNonNull(
                transactions,
                "transactions"
            );
    }

    synchronized ShopSnapshot register(
        ShopDefinition definition
    ){
        ShopDefinition checked=
            Objects.requireNonNull(
                definition,
                "definition"
            );

        if(shops.containsKey(
                checked.id))
            throw new IllegalStateException(
                "duplicate shop id="+
                checked.id
            );

        ShopEntry entry=
            new ShopEntry(checked);

        shops.put(
            checked.id,
            entry
        );

        return entry.snapshot();
    }

    synchronized PurchaseSnapshot requestPurchase(
        ShopId shopId,
        String buyerRef,
        String itemRef,
        long quantity
    ){
        if(quantity<=0L)
            throw new IllegalArgumentException(
                "quantity="+quantity
            );

        ShopEntry shop=
            requireShop(
                shopId
            );

        String buyer=
            requireText(
                buyerRef,
                "buyerRef"
            );

        String item=
            normalizeKey(
                itemRef,
                "itemRef"
            );

        OfferState offer=
            shop.offers.get(item);

        if(offer==null)
            throw new IllegalArgumentException(
                "unknown shop item "+
                item+
                " shop="+shop.definition.id
            );

        long total=
            multiplyPrice(
                quantity,
                offer.offer.priceEach
            );

        if(offer.offer.stockMode==
                StockMode.FINITE){
            if(offer.availableStock<
                    quantity)
                throw new IllegalStateException(
                    "insufficient shop stock item="+
                    item+
                    " requested="+quantity+
                    " available="+
                    offer.availableStock
                );

            offer.availableStock=
                Math.subtractExact(
                    offer.availableStock,
                    quantity
                );
        }

        PurchaseId id=
            nextPurchaseId();

        Purchase purchase=
            new Purchase(
                id,
                shop,
                offer,
                buyer,
                quantity,
                total
            );

        purchases.put(
            id,
            purchase
        );

        return purchase.snapshot();
    }

    PurchaseSnapshot confirmSettlement(
        PurchaseId purchaseId,
        AtomicTransactionService.TransactionId
            settlementTransactionId
    ){
        final Purchase live;
        final AtomicTransactionService.TransactionId
            requestedTransaction=
                Objects.requireNonNull(
                    settlementTransactionId,
                    "settlementTransactionId"
                );

        synchronized(this){
            live=
                requirePurchase(
                    purchaseId
                );

            if(live.settlementCheckInFlight)
                throw new IllegalStateException(
                    "purchase settlement check already in flight "+
                    live.id
                );

            if(live.state==
                    PurchaseState.CANCELLED)
                throw new IllegalStateException(
                    "cannot settle cancelled purchase "+
                    live.id
                );

            live.settlementCheckInFlight=
                true;
        }

        AtomicTransactionService.Snapshot settlement=null;
        RuntimeException failure=null;

        try{
            /*
             * AtomicTransactionService owns its own monitor. Read and validate
             * the immutable proof without holding the ShopService monitor.
             */
            settlement=
                transactions.snapshot(
                    requestedTransaction
                );

            verifySettlement(
                live,
                settlement
            );
        }catch(RuntimeException error){
            failure=error;
        }

        synchronized(this){
            Purchase purchase=
                requirePurchase(
                    purchaseId
                );

            if(purchase!=live)
                throw new IllegalStateException(
                    "purchase identity changed "+
                    purchaseId
                );

            try{
                if(failure==null){
                    if(purchase.state==
                            PurchaseState.SETTLED){
                        if(!purchase.settlementTransactionId
                                .equals(
                                    settlement.transactionId))
                            throw new IllegalStateException(
                                "purchase already settled with "+
                                purchase.settlementTransactionId
                            );

                        return purchase.snapshot();
                    }

                    if(purchase.state!=
                            PurchaseState.RESERVED)
                        throw new IllegalStateException(
                            "purchase settlement invalid from "+
                            purchase.state+
                            " for "+
                            purchase.id
                        );

                    claimSettlementUse(
                        settlement.transactionId,
                        "purchase:"+
                            purchase.id.value()
                    );

                    purchase.settlementTransactionId=
                        settlement.transactionId;
                    purchase.state=
                        PurchaseState.SETTLED;

                    return purchase.snapshot();
                }
            }finally{
                purchase.settlementCheckInFlight=
                    false;
            }
        }

        throw failure;
    }

    /**
     * Composition boundary for callers that own the carried-state mutation.
     *
     * The transaction must still be RESERVED.  Shop validation and single-use
     * binding happen before the transaction becomes final; while the purchase
     * is fenced in-flight no concurrent cancel/settle can change its state.
     * After commit succeeds, the remaining Shop mutation is deterministic.
     */
    PurchaseSnapshot commitReservedSettlement(
        PurchaseId purchaseId,
        AtomicTransactionService.TransactionId
            settlementTransactionId
    ){
        final Purchase live;
        final AtomicTransactionService.TransactionId
            requestedTransaction=
                Objects.requireNonNull(
                    settlementTransactionId,
                    "settlementTransactionId"
                );
        final String useKey;

        synchronized(this){
            live=
                requirePurchase(
                    purchaseId
                );

            if(live.settlementCheckInFlight)
                throw new IllegalStateException(
                    "purchase settlement check already in flight "+
                    live.id
                );

            if(live.state==
                    PurchaseState.CANCELLED)
                throw new IllegalStateException(
                    "cannot settle cancelled purchase "+
                    live.id
                );

            if(live.state==
                    PurchaseState.SETTLED){
                if(!requestedTransaction.equals(
                        live.settlementTransactionId))
                    throw new IllegalStateException(
                        "purchase already settled with "+
                        live.settlementTransactionId
                    );

                return live.snapshot();
            }

            if(live.state!=
                    PurchaseState.RESERVED)
                throw new IllegalStateException(
                    "purchase settlement invalid from "+
                    live.state+
                    " for "+
                    live.id
                );

            live.settlementCheckInFlight=
                true;
            useKey=
                "purchase:"+
                live.id.value();
        }

        RuntimeException failure=null;
        boolean useClaimed=false;

        try{
            AtomicTransactionService.Snapshot
                reserved=
                    transactions.snapshot(
                        requestedTransaction
                    );

            verifyReservedSettlement(
                live,
                reserved
            );

            synchronized(this){
                Purchase current=
                    requirePurchase(
                        purchaseId
                    );

                if(current!=live||
                   current.state!=
                        PurchaseState.RESERVED)
                    throw new IllegalStateException(
                        "purchase changed during reserved settlement "+
                        purchaseId
                    );

                String existingUse=
                    settlementUses.get(
                        requestedTransaction
                    );

                if(existingUse!=null&&
                   !existingUse.equals(
                       useKey))
                    throw new IllegalStateException(
                        "settlement transaction already used by "+
                        existingUse
                    );

                if(existingUse==null){
                    settlementUses.put(
                        requestedTransaction,
                        useKey
                    );
                    useClaimed=true;
                }
            }

            AtomicTransactionService.Snapshot
                committed=
                    transactions.commit(
                        requestedTransaction
                    );

            verifySettlement(
                live,
                committed
            );
        }catch(RuntimeException error){
            failure=error;
        }

        synchronized(this){
            Purchase purchase=
                requirePurchase(
                    purchaseId
                );

            try{
                if(failure==null){
                    if(purchase!=live||
                       purchase.state!=
                            PurchaseState.RESERVED)
                        throw new IllegalStateException(
                            "purchase changed after transaction commit "+
                            purchaseId
                        );

                    purchase.settlementTransactionId=
                        requestedTransaction;
                    purchase.state=
                        PurchaseState.SETTLED;

                    return purchase.snapshot();
                }

                if(useClaimed&&
                   useKey.equals(
                       settlementUses.get(
                           requestedTransaction
                       )))
                    settlementUses.remove(
                        requestedTransaction
                    );
            }finally{
                purchase.settlementCheckInFlight=
                    false;
            }
        }

        throw failure;
    }

    synchronized PurchaseSnapshot cancelPurchase(
        PurchaseId purchaseId
    ){
        Purchase purchase=
            requirePurchase(
                purchaseId
            );

        if(purchase.settlementCheckInFlight)
            throw new IllegalStateException(
                "purchase settlement check already in flight "+
                purchase.id
            );

        if(purchase.state==
                PurchaseState.CANCELLED)
            return purchase.snapshot();

        if(purchase.state==
                PurchaseState.SETTLED)
            throw new IllegalStateException(
                "cannot cancel settled purchase "+
                purchase.id
            );

        if(purchase.offer.offer.stockMode==
                StockMode.FINITE){
            long restored=
                Math.addExact(
                    purchase.offer.availableStock,
                    purchase.quantity
                );

            purchase.offer.availableStock=
                restored;
        }

        purchase.state=
            PurchaseState.CANCELLED;

        return purchase.snapshot();
    }

    synchronized ShopSnapshot getShop(
        ShopId shopId
    ){
        ShopEntry entry=
            shops.get(
                Objects.requireNonNull(
                    shopId,
                    "shopId"
                )
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized PurchaseSnapshot getPurchase(
        PurchaseId purchaseId
    ){
        Purchase purchase=
            purchases.get(
                Objects.requireNonNull(
                    purchaseId,
                    "purchaseId"
                )
            );

        return purchase==null
            ?null
            :purchase.snapshot();
    }

    synchronized int shopCount(){
        return shops.size();
    }

    synchronized int purchaseCount(){
        return purchases.size();
    }

    synchronized List<ShopSnapshot>
        shopSnapshot(){
        ArrayList<ShopEntry> ordered=
            new ArrayList<>(
                shops.values()
            );

        ordered.sort(
            Comparator.comparing(
                entry->
                    entry.definition.id
            )
        );

        ArrayList<ShopSnapshot> out=
            new ArrayList<>();

        for(ShopEntry entry:ordered)
            out.add(
                entry.snapshot()
            );

        return Collections.unmodifiableList(
            out
        );
    }

    synchronized List<PurchaseSnapshot>
        purchaseSnapshot(){
        ArrayList<Purchase> ordered=
            new ArrayList<>(
                purchases.values()
            );

        ordered.sort(
            Comparator.comparing(
                purchase->
                    purchase.id
            )
        );

        ArrayList<PurchaseSnapshot> out=
            new ArrayList<>();

        for(Purchase purchase:ordered)
            out.add(
                purchase.snapshot()
            );

        return Collections.unmodifiableList(
            out
        );
    }

    AtomicTransactionService transactions(){
        return transactions;
    }

    synchronized OfferSnapshot offerSnapshot(
        ShopId shopId,
        String itemRef
    ){
        return new OfferSnapshot(
            requireOffer(
                requireShop(shopId),
                itemRef
            )
        );
    }

    synchronized void reserveIncomingStock(
        ShopId shopId,
        String itemRef,
        String reservationRef,
        long quantity
    ){
        if(quantity<=0L)
            throw new IllegalArgumentException(
                "incoming quantity="+quantity
            );

        OfferState offer=
            requireOffer(
                requireShop(shopId),
                itemRef
            );

        if(offer.offer.stockMode==
                StockMode.UNLIMITED)
            return;

        String ref=
            requireText(
                reservationRef,
                "reservationRef"
            );

        Long existing=
            offer.incomingReservations.get(
                ref
            );

        if(existing!=null){
            if(existing.longValue()==quantity)
                return;

            throw new IllegalStateException(
                "incoming stock reservation conflict ref="+
                ref+
                " existing="+existing+
                " requested="+quantity
            );
        }

        long pending=0L;

        try{
            for(long value:
                    offer.incomingReservations.values())
                pending=Math.addExact(
                    pending,
                    value
                );

            Math.addExact(
                Math.addExact(
                    offer.availableStock,
                    pending
                ),
                quantity
            );
        }catch(ArithmeticException overflow){
            throw new IllegalStateException(
                "incoming Shop stock capacity overflow item="+
                offer.offer.itemRef,
                overflow
            );
        }

        offer.incomingReservations.put(
            ref,
            quantity
        );
    }

    synchronized boolean cancelIncomingStock(
        ShopId shopId,
        String itemRef,
        String reservationRef
    ){
        OfferState offer=
            requireOffer(
                requireShop(shopId),
                itemRef
            );

        if(offer.offer.stockMode==
                StockMode.UNLIMITED)
            return false;

        return offer.incomingReservations.remove(
            requireText(
                reservationRef,
                "reservationRef"
            )
        )!=null;
    }

    synchronized boolean claimSettlementUse(
        AtomicTransactionService.TransactionId transactionId,
        String useKey
    ){
        AtomicTransactionService.TransactionId id=
            Objects.requireNonNull(
                transactionId,
                "transactionId"
            );

        String key=
            requireText(
                useKey,
                "useKey"
            );

        String existing=
            settlementUses.get(id);

        if(existing!=null){
            if(existing.equals(key))
                return false;

            throw new IllegalStateException(
                "settlement transaction already used by "+
                existing
            );
        }

        settlementUses.put(
            id,
            key
        );

        return true;
    }

    synchronized boolean releaseSettlementUse(
        AtomicTransactionService.TransactionId transactionId,
        String useKey
    ){
        AtomicTransactionService.TransactionId id=
            Objects.requireNonNull(
                transactionId,
                "transactionId"
            );
        String key=
            requireText(
                useKey,
                "useKey"
            );

        String existing=
            settlementUses.get(id);

        if(existing==null)
            return false;

        if(!existing.equals(key))
            throw new IllegalStateException(
                "settlement transaction owned by "+
                existing+
                " not "+key
            );

        settlementUses.remove(id);
        return true;
    }

    synchronized void commitIncomingStockSettlement(
        ShopId shopId,
        String itemRef,
        String reservationRef,
        AtomicTransactionService.TransactionId transactionId,
        String useKey
    ){
        OfferState offer=
            requireOffer(
                requireShop(shopId),
                itemRef
            );

        AtomicTransactionService.TransactionId id=
            Objects.requireNonNull(
                transactionId,
                "transactionId"
            );
        String key=
            requireText(
                useKey,
                "useKey"
            );

        String existingUse=
            settlementUses.get(id);

        if(existingUse!=null&&
           !existingUse.equals(key))
            throw new IllegalStateException(
                "settlement transaction already used by "+
                existingUse
            );

        String ref=
            requireText(
                reservationRef,
                "reservationRef"
            );

        Long quantity=null;
        long nextStock=offer.availableStock;

        if(offer.offer.stockMode==
                StockMode.FINITE){
            quantity=
                offer.incomingReservations.get(
                    ref
                );

            if(quantity==null)
                throw new IllegalStateException(
                    "unknown incoming stock reservation "+
                    ref
                );

            try{
                nextStock=
                    Math.addExact(
                        offer.availableStock,
                        quantity.longValue()
                    );
            }catch(ArithmeticException overflow){
                throw new IllegalStateException(
                    "committed Shop stock overflow item="+
                    offer.offer.itemRef,
                    overflow
                );
            }
        }

        if(existingUse==null)
            settlementUses.put(
                id,
                key
            );

        if(offer.offer.stockMode==
                StockMode.FINITE){
            offer.incomingReservations.remove(
                ref
            );
            offer.availableStock=
                nextStock;
        }
    }

    private static OfferState requireOffer(
        ShopEntry shop,
        String itemRef
    ){
        String item=
            normalizeKey(
                itemRef,
                "itemRef"
            );

        OfferState offer=
            shop.offers.get(item);

        if(offer==null)
            throw new IllegalArgumentException(
                "unknown shop item "+
                item+
                " shop="+shop.definition.id
            );

        return offer;
    }

    private ShopEntry requireShop(
        ShopId shopId
    ){
        ShopId id=
            Objects.requireNonNull(
                shopId,
                "shopId"
            );

        ShopEntry entry=
            shops.get(id);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown shop id="+id
            );

        return entry;
    }

    private Purchase requirePurchase(
        PurchaseId purchaseId
    ){
        PurchaseId id=
            Objects.requireNonNull(
                purchaseId,
                "purchaseId"
            );

        Purchase purchase=
            purchases.get(id);

        if(purchase==null)
            throw new IllegalArgumentException(
                "unknown purchase id="+id
            );

        return purchase;
    }

    private PurchaseId nextPurchaseId(){
        long value=
            purchaseSequence
                .incrementAndGet();

        if(value<=0L)
            throw new IllegalStateException(
                "shop purchase sequence exhausted"
            );

        return new PurchaseId(value);
    }

    private static long multiplyPrice(
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
                "shop total price overflow quantity="+
                quantity+
                " priceEach="+priceEach,
                overflow
            );
        }
    }

    private static void verifySettlement(
        Purchase purchase,
        AtomicTransactionService.Snapshot
            settlement
    ){
        verifySettlementState(
            purchase,
            settlement,
            AtomicTransactionService
                .TransactionState
                .COMMITTED
        );
    }

    private static void verifyReservedSettlement(
        Purchase purchase,
        AtomicTransactionService.Snapshot
            settlement
    ){
        verifySettlementState(
            purchase,
            settlement,
            AtomicTransactionService
                .TransactionState
                .RESERVED
        );
    }

    private static void verifySettlementState(
        Purchase purchase,
        AtomicTransactionService.Snapshot
            settlement,
        AtomicTransactionService.TransactionState
            requiredState
    ){
        if(settlement.state!=
                requiredState)
            throw new IllegalArgumentException(
                "shop settlement must be "+
                requiredState+
                " state="+
                settlement.state
            );

        if(!purchase.buyerRef.equals(
                settlement.ownerRef))
            throw new SecurityException(
                "shop settlement buyer mismatch expected="+
                purchase.buyerRef+
                " actual="+settlement.ownerRef
            );

        AtomicTransactionService.SourceAuthority
            expectedAuthority=
                purchase.shop.definition
                    .sourceAuthority;

        if(settlement.sourceAuthority!=
                expectedAuthority)
            throw new IllegalArgumentException(
                "shop settlement authority mismatch expected="+
                expectedAuthority+
                " actual="+
                settlement.sourceAuthority
            );

        if(!containsAsset(
                settlement,
                EscrowAsset.Kind.CURRENCY,
                purchase.offer.offer
                    .currencyRef,
                purchase.totalPrice,
                purchase.buyerRef,
                expectedAuthority))
            throw new IllegalArgumentException(
                "shop settlement missing buyer currency coverage"
            );

        if(!containsAsset(
                settlement,
                EscrowAsset.Kind.ITEM,
                purchase.offer.offer.itemRef,
                purchase.quantity,
                purchase.shop.definition
                    .stockOwnerRef,
                expectedAuthority))
            throw new IllegalArgumentException(
                "shop settlement missing shop item coverage"
            );
    }

    private static boolean containsAsset(
        AtomicTransactionService.Snapshot snapshot,
        EscrowAsset.Kind kind,
        String semanticKey,
        long minimumQuantity,
        String ownerRef,
        AtomicTransactionService.SourceAuthority
            authority
    ){
        long total=0L;

        for(AtomicTransactionService.Reservation
                reservation:
                snapshot.reservations){
            EscrowAsset asset=
                reservation.asset;

            if(asset.kind!=kind||
               !semanticKey.equals(
                   normalizeKey(
                       asset.semanticKey,
                       "semanticKey"
                   ))||
               !ownerRef.equals(
                   asset.ownerRef)||
               asset.sourceAuthority!=
                    authority)
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
