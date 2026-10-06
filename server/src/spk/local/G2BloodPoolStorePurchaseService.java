package spk.local;

import java.util.Objects;

/**
 * Clean-G2 composition from a recovered Blood Pool semantic slot to the
 * canonical inventory-backed Shop purchase authority.
 *
 * This class deliberately owns no raw widget/opcode identity, presentation
 * record schema, production catalog, eligibility, restock, or sellback policy.
 */
final class G2BloodPoolStorePurchaseService {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2_BLOOD_POOL_PURCHASE_V1";

    enum Status {
        PURCHASED,
        MISSING_SLOT,
        PURCHASE_REJECTED
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
    private final G2ShopPurchaseService purchases;

    G2BloodPoolStorePurchaseService(
        BloodPoolStoreService bloodPool,
        G2ShopPurchaseService purchases
    ){
        this.bloodPool=Objects.requireNonNull(
            bloodPool,
            "bloodPool"
        );
        this.purchases=Objects.requireNonNull(
            purchases,
            "purchases"
        );

        if(this.bloodPool.shops()!=
                this.purchases.shops())
            throw new IllegalArgumentException(
                "Blood Pool/G2 purchase composition requires exact same ShopService instance"
            );

        if(this.bloodPool.snapshot().sourceAuthority!=
                AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)
            throw new IllegalArgumentException(
                "G2 Blood Pool purchase requires CUSTOM_LOCALLAB Shop authority"
            );
    }

    Result purchase(
        String slotKey,
        long quantity
    ){
        BloodPoolStoreService.Snapshot snapshot=
            bloodPool.snapshot();

        BloodPoolStoreService.SlotSnapshot slot=
            snapshot.slot(slotKey);

        if(slot==null)
            return new Result(
                Status.MISSING_SLOT,
                slotKey,
                null,
                null,
                "unknown Blood Pool slot"
            );

        G2ShopPurchaseService.Result purchase=
            purchases.purchase(
                snapshot.shopId,
                slot.itemRef,
                quantity
            );

        return new Result(
            purchase.purchased()
                ?Status.PURCHASED
                :Status.PURCHASE_REJECTED,
            slot.slotKey,
            slot.itemRef,
            purchase,
            purchase.detail
        );
    }
}
