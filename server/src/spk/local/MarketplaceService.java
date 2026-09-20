package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Protocol-independent Marketplace listing lifecycle above the #159 escrow foundation. */
final class MarketplaceService {
    static final class SettlementRequest {
        final MarketplaceListing.Id listingId;
        final String sellerRef;
        final String buyerRef;
        final String itemRef;
        final long quantity;
        final long priceEach;
        final long totalPrice;
        final String currencyRef;
        final AtomicTransactionService.TransactionId sellerEscrowTransactionId;

        SettlementRequest(MarketplaceListing listing,String buyerRef,long quantity){
            this.listingId=listing.id;this.sellerRef=listing.ownerRef;this.buyerRef=buyerRef;this.itemRef=listing.itemRef;this.quantity=quantity;this.priceEach=listing.priceEach;this.totalPrice=Math.multiplyExact(quantity,listing.priceEach);this.currencyRef=listing.currencyRef;this.sellerEscrowTransactionId=listing.sellerEscrowTransactionId;
        }
    }

    static final class CancellationRequest {
        final MarketplaceListing.Id listingId;
        final String ownerRef;
        final AtomicTransactionService.TransactionId sellerEscrowTransactionId;
        CancellationRequest(MarketplaceListing listing){listingId=listing.id;ownerRef=listing.ownerRef;sellerEscrowTransactionId=listing.sellerEscrowTransactionId;}
    }

    private final AtomicLong listingSequence=new AtomicLong();
    private final LinkedHashMap<MarketplaceListing.Id,MarketplaceListing> listings=new LinkedHashMap<>();
    private final MarketplaceListingRepository repository;

    MarketplaceService(MarketplaceListingRepository repository){if(repository==null)throw new NullPointerException("repository");this.repository=repository;}

    synchronized MarketplaceListing.Id createDraft(String ownerRef,String itemRef,long totalQuantity,long priceEach,String currencyRef,AtomicTransactionService.Snapshot escrowReservation,AtomicTransactionService.SourceAuthority sourceAuthority){
        String owner=requireText(ownerRef,"ownerRef"),item=requireText(itemRef,"itemRef"),currency=requireText(currencyRef,"currencyRef");
        if(totalQuantity<=0)throw new IllegalArgumentException("totalQuantity="+totalQuantity);
        if(priceEach<=0)throw new IllegalArgumentException("priceEach="+priceEach);
        if(sourceAuthority==null)throw new NullPointerException("sourceAuthority");
        verifySellerEscrow(owner,escrowReservation,null);
        MarketplaceListing.Id id=new MarketplaceListing.Id(listingSequence.incrementAndGet());
        MarketplaceListing listing=new MarketplaceListing(id,owner,item,totalQuantity,priceEach,currency,escrowReservation.transactionId,sourceAuthority);
        listings.put(id,listing);save(listing);return id;
    }

    synchronized MarketplaceListing.Snapshot activate(MarketplaceListing.Id id,String ownerRef,AtomicTransactionService.Snapshot escrowReservation){
        MarketplaceListing listing=required(id);requireOwner(listing,ownerRef);
        if(listing.state==MarketplaceListing.State.ACTIVE)return listing.snapshot();
        if(listing.state!=MarketplaceListing.State.DRAFT)throw invalid(listing,"activate");
        verifySellerEscrow(listing.ownerRef,escrowReservation,listing.sellerEscrowTransactionId);
        listing.state=MarketplaceListing.State.ACTIVE;save(listing);return listing.snapshot();
    }

    synchronized SettlementRequest requestFill(MarketplaceListing.Id id,String buyerRef,long quantity){
        MarketplaceListing listing=required(id);String buyer=requireText(buyerRef,"buyerRef");
        requireFillable(listing,quantity);
        return new SettlementRequest(listing,buyer,quantity);
    }

    synchronized MarketplaceListing.Snapshot confirmFill(MarketplaceListing.Id id,String buyerRef,long quantity,AtomicTransactionService.Snapshot committedSettlement){
        MarketplaceListing listing=required(id);String buyer=requireText(buyerRef,"buyerRef");
        if(committedSettlement==null)throw new NullPointerException("committedSettlement");
        if(committedSettlement.state!=AtomicTransactionService.TransactionState.COMMITTED)throw new IllegalArgumentException("settlement must be COMMITTED");
        if(!buyer.equals(committedSettlement.ownerRef))throw new SecurityException("settlement owner mismatch");
        MarketplaceListing.AppliedFill previous=listing.appliedFills.get(committedSettlement.transactionId);
        if(previous!=null){
            if(!previous.matches(buyer,quantity))throw new IllegalStateException("settlement transaction already applied with different fill data");
            return listing.snapshot();
        }
        requireFillable(listing,quantity);
        listing.remainingQuantity-=quantity;
        listing.appliedFills.put(committedSettlement.transactionId,new MarketplaceListing.AppliedFill(buyer,quantity));
        listing.state=listing.remainingQuantity==0?MarketplaceListing.State.FILLED:MarketplaceListing.State.PARTIALLY_FILLED;
        save(listing);return listing.snapshot();
    }

    synchronized CancellationRequest cancel(MarketplaceListing.Id id,String ownerRef){
        MarketplaceListing listing=required(id);requireOwner(listing,ownerRef);
        if(listing.state==MarketplaceListing.State.CANCELLED)return new CancellationRequest(listing);
        if(listing.state!=MarketplaceListing.State.ACTIVE&&listing.state!=MarketplaceListing.State.PARTIALLY_FILLED)throw invalid(listing,"cancel");
        listing.state=MarketplaceListing.State.CANCELLED;save(listing);return new CancellationRequest(listing);
    }

    synchronized MarketplaceListing.Snapshot snapshot(MarketplaceListing.Id id){return required(id).snapshot();}
    synchronized int size(){return listings.size();}

    private void save(MarketplaceListing listing){repository.save(listing.snapshot());}
    private MarketplaceListing required(MarketplaceListing.Id id){if(id==null)throw new NullPointerException("listingId");MarketplaceListing listing=listings.get(id);if(listing==null)throw new IllegalArgumentException("unknown listing "+id);return listing;}
    private static void requireFillable(MarketplaceListing listing,long quantity){if(listing.state!=MarketplaceListing.State.ACTIVE&&listing.state!=MarketplaceListing.State.PARTIALLY_FILLED)throw invalid(listing,"fill");if(quantity<=0)throw new IllegalArgumentException("quantity="+quantity);if(quantity>listing.remainingQuantity)throw new IllegalArgumentException("fill exceeds remaining quantity: "+quantity+">"+listing.remainingQuantity);Math.multiplyExact(quantity,listing.priceEach);}
    private static void verifySellerEscrow(String owner,AtomicTransactionService.Snapshot snapshot,AtomicTransactionService.TransactionId expectedId){if(snapshot==null)throw new NullPointerException("escrowReservation");if(snapshot.state!=AtomicTransactionService.TransactionState.RESERVED)throw new IllegalArgumentException("seller escrow must be RESERVED");if(!owner.equals(snapshot.ownerRef))throw new SecurityException("seller escrow owner mismatch");if(expectedId!=null&&!expectedId.equals(snapshot.transactionId))throw new IllegalArgumentException("seller escrow transaction mismatch");}
    private static void requireOwner(MarketplaceListing listing,String ownerRef){String owner=requireText(ownerRef,"ownerRef");if(!listing.ownerRef.equals(owner))throw new SecurityException("listing owner mismatch");}
    private static IllegalStateException invalid(MarketplaceListing listing,String op){return new IllegalStateException(op+" invalid from "+listing.state+" for "+listing.id);}
    private static String requireText(String value,String field){if(value==null)throw new NullPointerException(field);String clean=value.trim();if(clean.isEmpty())throw new IllegalArgumentException(field+" blank");return clean;}
}
