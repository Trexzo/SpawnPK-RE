package spk.local;

import java.util.Objects;

/**
 * Clean-G2 composition from the recovered Blood Pool semantic slot projection
 * into canonical inventory-backed Shop settlement.
 *
 * This layer deliberately owns no raw widget/opcode identity and no sellback
 * behavior. Exact-current Blood Pool presentation remains in
 * BloodPoolStoreService; canonical carried-state mutation remains in
 * G2ShopPurchaseService.
 */
final class G2BloodPoolStorePurchaseService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2_BLOOD_POOL_PURCHASE_V1";

    enum Status {
        PURCHASED,
        UNKNOWN_SLOT,
        SHOP_REJECTED
    }

    static final class Result {
        final Status status;
        final String slotKey;
        final String itemRef;
        final G2ShopPurchaseService.Result purchase;
        final String detail;
        final String authority;

        private Result(
            Status status,
            String slotKey,
            String itemRef,
            G2ShopPurchaseService.Result purchase,
            String detail
        ){
            this.status=Objects.requireNonNull(status,"status");
            this.slotKey=slotKey;
            this.itemRef=itemRef;
            this.purchase=purchase;
            this.detail=detail;
            this.authority=AUTHORITY;
        }

        boolean purchased(){
            return status==Status.PURCHASED&&
                purchase!=null&&
                purchase.purchased();
        }
    }

    private final BloodPoolStoreService bloodPool;
    private final ShopService shops;
    private final G2ShopPurchaseService purchases;

    G2BloodPoolStorePurchaseService(
        World world,
        WorldPlayer player,
        BloodPoolStoreService bloodPool
    ){
        this.bloodPool=
            Objects.requireNonNull(
                bloodPool,
                "bloodPool"
            );
        this.shops=
            Objects.requireNonNull(
                bloodPool.shopAuthority(),
                "bloodPool.shopAuthority"
            );
        this.purchases=
            new G2ShopPurchaseService(
                Objects.requireNonNull(
                    world,
                    "world"
                ),
                Objects.requireNonNull(
                    player,
                    "player"
                ),
                shops
            );

        requireSharedShopAuthority();
    }

    Result purchase(
        String slotKey,
        long quantity
    ){
        requireSharedShopAuthority();

        String key=
            MatchRules.normalizeKey(
                slotKey,
                "slotKey"
            );

        BloodPoolStoreService.Snapshot projection=
            bloodPool.snapshot();

        BloodPoolStoreService.SlotSnapshot slot=
            projection.slot(key);

        if(slot==null)
            return new Result(
                Status.UNKNOWN_SLOT,
                key,
                null,
                null,
                "Blood Pool slot is not configured"
            );

        G2ShopPurchaseService.Result purchase=
            purchases.purchase(
                projection.shopId,
                slot.itemRef,
                quantity
            );

        if(!purchase.purchased())
            return new Result(
                Status.SHOP_REJECTED,
                key,
                slot.itemRef,
                purchase,
                "canonical Shop purchase rejected: "+
                    purchase.status
            );

        return new Result(
            Status.PURCHASED,
            key,
            slot.itemRef,
            purchase,
            null
        );
    }

    boolean sharedShopAuthority(){
        return shops==bloodPool.shopAuthority();
    }

    private void requireSharedShopAuthority(){
        if(!sharedShopAuthority())
            throw new IllegalStateException(
                "Blood Pool canonical purchase Shop authority drift"
            );
    }
}
