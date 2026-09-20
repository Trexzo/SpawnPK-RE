package spk.local;

import java.util.*;

/** Mutable aggregate owned exclusively by MarketplaceService; callers receive immutable snapshots. */
final class MarketplaceListing {
    enum State { DRAFT, ACTIVE, PARTIALLY_FILLED, FILLED, CANCELLED }

    static final class Id {
        final long value;
        Id(long value){if(value<=0)throw new IllegalArgumentException("value="+value);this.value=value;}
        @Override public boolean equals(Object o){return o instanceof Id&&((Id)o).value==value;}
        @Override public int hashCode(){return Long.hashCode(value);}
        @Override public String toString(){return "market-"+Long.toUnsignedString(value);}
    }

    static final class Snapshot {
        final Id listingId;
        final String ownerRef;
        final String itemRef;
        final long totalQuantity;
        final long remainingQuantity;
        final long soldQuantity;
        final long priceEach;
        final String currencyRef;
        final State state;
        final AtomicTransactionService.TransactionId sellerEscrowTransactionId;
        final AtomicTransactionService.SourceAuthority sourceAuthority;

        Snapshot(MarketplaceListing listing){
            listingId=listing.id;
            ownerRef=listing.ownerRef;
            itemRef=listing.itemRef;
            totalQuantity=listing.totalQuantity;
            remainingQuantity=listing.remainingQuantity;
            soldQuantity=listing.totalQuantity-listing.remainingQuantity;
            priceEach=listing.priceEach;
            currencyRef=listing.currencyRef;
            state=listing.state;
            sellerEscrowTransactionId=listing.sellerEscrowTransactionId;
            sourceAuthority=listing.sourceAuthority;
        }
        @Override public String toString(){return "MarketplaceListingSnapshot{id="+listingId+",state="+state+",owner="+ownerRef+",item="+itemRef+",sold="+soldQuantity+"/"+totalQuantity+",priceEach="+priceEach+",currency="+currencyRef+",authority="+sourceAuthority+"}";}
    }

    final Id id;
    final String ownerRef;
    final String itemRef;
    final long totalQuantity;
    final long priceEach;
    final String currencyRef;
    final AtomicTransactionService.TransactionId sellerEscrowTransactionId;
    final AtomicTransactionService.SourceAuthority sourceAuthority;
    long remainingQuantity;
    State state=State.DRAFT;

    MarketplaceListing(Id id,String ownerRef,String itemRef,long totalQuantity,long priceEach,String currencyRef,AtomicTransactionService.TransactionId sellerEscrowTransactionId,AtomicTransactionService.SourceAuthority sourceAuthority){
        this.id=id;this.ownerRef=ownerRef;this.itemRef=itemRef;this.totalQuantity=totalQuantity;this.remainingQuantity=totalQuantity;this.priceEach=priceEach;this.currencyRef=currencyRef;this.sellerEscrowTransactionId=sellerEscrowTransactionId;this.sourceAuthority=sourceAuthority;
    }
    Snapshot snapshot(){return new Snapshot(this);}
}
