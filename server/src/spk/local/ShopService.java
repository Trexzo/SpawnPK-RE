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
        PurchaseId
    > settlementUses=
        new LinkedHashMap<>();

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

    synchronized PurchaseSnapshot confirmSettlement(
        PurchaseId purchaseId,
        AtomicTransactionService.Snapshot
            committedSettlement
    ){
        Purchase purchase=
            requirePurchase(
                purchaseId
            );

        AtomicTransactionService.Snapshot settlement=
            Objects.requireNonNull(
                committedSettlement,
                "committedSettlement"
            );

        if(purchase.state==
                PurchaseState.SETTLED){
            if(!purchase.settlementTransactionId
                    .equals(
                        settlement.transactionId))
                throw new IllegalStateException(
                    "purchase already settled with "+
                    purchase.settlementTransactionId
                );

            verifySettlement(
                purchase,
                settlement
            );

            return purchase.snapshot();
        }

        if(purchase.state==
                PurchaseState.CANCELLED)
            throw new IllegalStateException(
                "cannot settle cancelled purchase "+
                purchase.id
            );

        verifySettlement(
            purchase,
            settlement
        );

        PurchaseId existing=
            settlementUses.get(
                settlement.transactionId
            );

        if(existing!=null&&
           !existing.equals(
               purchase.id))
            throw new IllegalStateException(
                "settlement transaction already used by "+
                existing
            );

        purchase.settlementTransactionId=
            settlement.transactionId;
        purchase.state=
            PurchaseState.SETTLED;

        settlementUses.put(
            settlement.transactionId,
            purchase.id
        );

        return purchase.snapshot();
    }

    synchronized PurchaseSnapshot cancelPurchase(
        PurchaseId purchaseId
    ){
        Purchase purchase=
            requirePurchase(
                purchaseId
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

            if(restored>
                    purchase.offer.offer
                        .initialStock)
                throw new IllegalStateException(
                    "finite stock restore exceeds initial stock item="+
                    purchase.offer.offer.itemRef+
                    " restored="+restored+
                    " initial="+
                    purchase.offer.offer.initialStock
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
        if(settlement.state!=
                AtomicTransactionService
                    .TransactionState
                    .COMMITTED)
            throw new IllegalArgumentException(
                "shop settlement must be COMMITTED state="+
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
