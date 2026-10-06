package spk.local;

import java.util.*;

/**
 * Blood Pool Store application composition over the purchase-only ShopService.
 *
 * Exact-current client authority proves a reset + append slot presentation
 * lifecycle. Shop content/economics remain caller-defined ShopService state.
 */
final class BloodPoolStoreService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final class SlotSpec {
        final String slotKey;
        final String itemRef;

        SlotSpec(
            String slotKey,
            String itemRef
        ){
            this.slotKey=
                MatchRules.normalizeKey(
                    slotKey,
                    "slotKey"
                );
            this.itemRef=
                MatchRules.normalizeKey(
                    itemRef,
                    "itemRef"
                );
        }
    }

    static final class SlotSnapshot {
        final String slotKey;
        final String itemRef;
        final String currencyRef;
        final long priceEach;
        final ShopService.StockMode stockMode;
        final OptionalLong availableStock;

        SlotSnapshot(
            SlotSpec spec,
            ShopService.OfferSnapshot offer
        ){
            this.slotKey=spec.slotKey;
            this.itemRef=spec.itemRef;
            this.currencyRef=
                offer.currencyRef;
            this.priceEach=
                offer.priceEach;
            this.stockMode=
                offer.stockMode;
            this.availableStock=
                offer.availableStock;
        }
    }

    static final class Snapshot {
        final ShopService.ShopId shopId;
        final String shopName;
        final List<SlotSnapshot> slots;
        final AtomicTransactionService.SourceAuthority
            sourceAuthority;
        final String presentationAuthority;

        Snapshot(
            ShopService.ShopSnapshot shop,
            Collection<SlotSnapshot> slots
        ){
            this.shopId=shop.id;
            this.shopName=shop.name;
            this.slots=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        slots
                    )
                );
            this.sourceAuthority=
                shop.sourceAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        SlotSnapshot slot(String slotKey){
            String key=
                MatchRules.normalizeKey(
                    slotKey,
                    "slotKey"
                );

            for(SlotSnapshot slot:slots)
                if(slot.slotKey.equals(key))
                    return slot;

            return null;
        }
    }

    private final ShopService shops;
    private final ShopService.ShopId shopId;
    private final AtomicTransactionService.SourceAuthority
        policyAuthority;

    private LinkedHashMap<String,SlotSpec>
        slots=
            new LinkedHashMap<>();

    BloodPoolStoreService(
        ShopService shops,
        ShopService.ShopId shopId,
        AtomicTransactionService.SourceAuthority
            policyAuthority
    ){
        this.shops=
            Objects.requireNonNull(
                shops,
                "shops"
            );
        this.shopId=
            Objects.requireNonNull(
                shopId,
                "shopId"
            );
        this.policyAuthority=
            Objects.requireNonNull(
                policyAuthority,
                "policyAuthority"
            );

        ShopService.ShopSnapshot shop=
            requireBoundShop();

        if(shop.sourceAuthority!=
                policyAuthority)
            throw new IllegalArgumentException(
                "Blood Pool Store authority mismatch shop="+
                shop.id
            );
    }

    Snapshot replaceSlots(
        Collection<SlotSpec> nextSlots
    ){
        Objects.requireNonNull(
            nextSlots,
            "nextSlots"
        );

        ShopService.ShopSnapshot shop=
            requireBoundShop();

        LinkedHashMap<String,SlotSpec> next=
            new LinkedHashMap<>();

        HashSet<String> items=
            new HashSet<>();

        for(SlotSpec spec:nextSlots){
            SlotSpec checked=
                Objects.requireNonNull(
                    spec,
                    "slot"
                );

            if(next.put(
                    checked.slotKey,
                    checked)!=null)
                throw new IllegalArgumentException(
                    "duplicate Blood Pool slotKey "+
                    checked.slotKey
                );

            if(!items.add(
                    checked.itemRef))
                throw new IllegalArgumentException(
                    "duplicate Blood Pool itemRef "+
                    checked.itemRef
                );

            if(shop.offer(
                    checked.itemRef)==null)
                throw new IllegalArgumentException(
                    "Blood Pool slot item not offered by Shop "+
                    checked.itemRef
                );
        }

        synchronized(this){
            slots=
                new LinkedHashMap<>(
                    next
                );
        }

        return snapshotFrom(
            shop,
            next.values()
        );
    }

    Snapshot snapshot(){
        final ArrayList<SlotSpec> captured=
            new ArrayList<>();

        synchronized(this){
            captured.addAll(
                slots.values()
            );
        }

        ShopService.ShopSnapshot shop=
            requireBoundShop();

        return snapshotFrom(
            shop,
            captured
        );
    }

    ShopService.PurchaseSnapshot requestPurchase(
        String buyerRef,
        String slotKey,
        long quantity
    ){
        String buyer=
            normalizePlayer(
                buyerRef
            );
        String key=
            MatchRules.normalizeKey(
                slotKey,
                "slotKey"
            );

        final SlotSpec slot;

        synchronized(this){
            slot=
                slots.get(key);
        }

        if(slot==null)
            throw new IllegalArgumentException(
                "unknown Blood Pool slot "+
                key
            );

        return shops.requestPurchase(
            shopId,
            buyer,
            slot.itemRef,
            quantity
        );
    }

    ShopService.PurchaseSnapshot confirmSettlement(
        ShopService.PurchaseId purchaseId,
        AtomicTransactionService.TransactionId
            transactionId
    ){
        ShopService.PurchaseSnapshot before=
            requireBoundPurchase(
                purchaseId
            );

        ShopService.PurchaseSnapshot settled=
            shops.confirmSettlement(
                before.purchaseId,
                transactionId
            );

        requireSameShop(settled);

        return settled;
    }

    ShopService.PurchaseSnapshot cancelPurchase(
        ShopService.PurchaseId purchaseId
    ){
        ShopService.PurchaseSnapshot before=
            requireBoundPurchase(
                purchaseId
            );

        ShopService.PurchaseSnapshot cancelled=
            shops.cancelPurchase(
                before.purchaseId
            );

        requireSameShop(cancelled);

        return cancelled;
    }

    ShopService.PurchaseSnapshot getPurchase(
        ShopService.PurchaseId purchaseId
    ){
        ShopService.PurchaseSnapshot purchase=
            shops.getPurchase(
                Objects.requireNonNull(
                    purchaseId,
                    "purchaseId"
                )
            );

        if(purchase==null)
            return null;

        requireSameShop(purchase);
        return purchase;
    }

    synchronized int slotCount(){
        return slots.size();
    }

    /**
     * Package-local composition boundary. Higher gameplay layers may reuse the
     * exact same ShopService authority instead of reconstructing parallel Shop
     * state from the presentation projection.
     */
    ShopService shopAuthority(){
        return shops;
    }

    private Snapshot snapshotFrom(
        ShopService.ShopSnapshot shop,
        Collection<SlotSpec> configured
    ){
        ArrayList<SlotSnapshot> out=
            new ArrayList<>();

        for(SlotSpec spec:configured){
            ShopService.OfferSnapshot offer=
                shop.offer(
                    spec.itemRef
                );

            if(offer==null)
                throw new IllegalStateException(
                    "Blood Pool Shop offer disappeared "+
                    spec.itemRef
                );

            out.add(
                new SlotSnapshot(
                    spec,
                    offer
                )
            );
        }

        return new Snapshot(
            shop,
            out
        );
    }

    private ShopService.ShopSnapshot requireBoundShop(){
        ShopService.ShopSnapshot shop=
            shops.getShop(shopId);

        if(shop==null)
            throw new IllegalStateException(
                "Blood Pool Shop missing "+
                shopId
            );

        if(shop.sourceAuthority!=
                policyAuthority)
            throw new IllegalStateException(
                "Blood Pool Shop authority drift "+
                shopId
            );

        return shop;
    }

    private ShopService.PurchaseSnapshot
        requireBoundPurchase(
            ShopService.PurchaseId purchaseId
        ){
        ShopService.PurchaseSnapshot purchase=
            shops.getPurchase(
                Objects.requireNonNull(
                    purchaseId,
                    "purchaseId"
                )
            );

        if(purchase==null)
            throw new IllegalArgumentException(
                "unknown Blood Pool purchase "+
                purchaseId
            );

        requireSameShop(purchase);
        return purchase;
    }

    private void requireSameShop(
        ShopService.PurchaseSnapshot purchase
    ){
        if(!shopId.equals(
                purchase.shopId))
            throw new IllegalArgumentException(
                "purchase does not belong to Blood Pool Shop "+
                purchase.purchaseId+
                " shop="+purchase.shopId
            );

        if(purchase.sourceAuthority!=
                policyAuthority)
            throw new IllegalStateException(
                "Blood Pool purchase authority drift "+
                purchase.purchaseId
            );
    }

    private static String normalizePlayer(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "buyerRef"
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "buyerRef blank"
            );

        return normalized;
    }
}
