package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class MarketplaceServiceTest {
    public static void main(String[] args){
        AtomicTransactionService tx=new AtomicTransactionService();
        MemoryRepository repository=new MemoryRepository();
        MarketplaceService market=new MarketplaceService(repository);

        AtomicTransactionService.TransactionId sellerTxn=tx.create("player:alice","fixture-seller-escrow",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        AtomicTransactionService.Snapshot sellerReserved=tx.reserve(sellerTxn,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.ITEM,"item:fixture_sword",10,"player:alice",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        AtomicTransactionService.TransactionId wrongItemEscrowTxn=tx.create("player:alice","fixture-wrong-item-escrow",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        AtomicTransactionService.Snapshot wrongItemEscrow=tx.reserve(wrongItemEscrowTxn,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.ITEM,"item:not_the_sword",10,"player:alice",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        expect(IllegalArgumentException.class,()->market.createDraft("player:alice","item:fixture_sword",10,1000,"currency:fixture_coins",wrongItemEscrow,AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB),"seller escrow wrong item");
        AtomicTransactionService.TransactionId shortEscrowTxn=tx.create("player:alice","fixture-short-escrow",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        AtomicTransactionService.Snapshot shortEscrow=tx.reserve(shortEscrowTxn,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.ITEM,"item:fixture_sword",9,"player:alice",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        expect(IllegalArgumentException.class,()->market.createDraft("player:alice","item:fixture_sword",10,1000,"currency:fixture_coins",shortEscrow,AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB),"seller escrow short quantity");
        MarketplaceListing.Id listingId=market.createDraft("player:alice","item:fixture_sword",10,1000,"currency:fixture_coins",sellerReserved,AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        MarketplaceListing.Snapshot draft=market.snapshot(listingId);
        eq(MarketplaceListing.State.DRAFT,draft.state,"draft state");eq(10L,draft.remainingQuantity,"draft remaining");eq(0L,draft.soldQuantity,"draft sold");eq(sellerTxn,draft.sellerEscrowTransactionId,"escrow ref");
        expect(SecurityException.class,()->market.activate(listingId,"player:mallory",sellerReserved),"unauthorized activate");

        AtomicTransactionService.TransactionId wrongTxn=tx.create("player:alice","fixture-wrong",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        AtomicTransactionService.Snapshot wrongReserved=tx.reserve(wrongTxn,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.ITEM,"item:other",1,"player:alice",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        expect(IllegalArgumentException.class,()->market.activate(listingId,"player:alice",wrongReserved),"wrong escrow activate");
        MarketplaceListing.Snapshot active=market.activate(listingId,"player:alice",sellerReserved);eq(MarketplaceListing.State.ACTIVE,active.state,"active state");
        eq(MarketplaceListing.State.ACTIVE,market.activate(listingId,"player:alice",sellerReserved).state,"idempotent activate");

        MarketplaceService.SettlementRequest request=market.requestFill(listingId,"player:bob",3);
        eq(3L,request.quantity,"request qty");eq(3000L,request.totalPrice,"request total");eq("item:fixture_sword",request.itemRef,"request item");
        eq(10L,market.snapshot(listingId).remainingQuantity,"request does not mutate");

        AtomicTransactionService.TransactionId badBuyerTxn=tx.create("player:bob","fixture-wrong-currency",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        tx.reserve(badBuyerTxn,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.CURRENCY,"currency:wrong",3000,"player:bob",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        AtomicTransactionService.Snapshot badBuyerCommitted=tx.commit(badBuyerTxn);
        expect(IllegalArgumentException.class,()->market.confirmFill(listingId,"player:bob",3,badBuyerCommitted),"wrong currency settlement");
        AtomicTransactionService.TransactionId bobTxn=tx.create("player:bob","fixture-buyer-settlement",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        tx.reserve(bobTxn,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.CURRENCY,"currency:fixture_coins",3000,"player:bob",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        AtomicTransactionService.Snapshot bobCommitted=tx.commit(bobTxn);
        MarketplaceListing.Snapshot partial=market.confirmFill(listingId,"player:bob",3,bobCommitted);
        eq(MarketplaceListing.State.PARTIALLY_FILLED,partial.state,"partial state");eq(7L,partial.remainingQuantity,"partial remaining");eq(3L,partial.soldQuantity,"partial sold");
        eq(7L,market.confirmFill(listingId,"player:bob",3,bobCommitted).remainingQuantity,"idempotent fill");
        expect(IllegalStateException.class,()->market.confirmFill(listingId,"player:bob",2,bobCommitted),"reused settlement mismatch");
        expect(IllegalArgumentException.class,()->market.requestFill(listingId,"player:eve",8),"overfill request");
        AtomicTransactionService.TransactionId sellerTxn3=tx.create("player:frank","fixture-seller-escrow-3",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        AtomicTransactionService.Snapshot sellerReserved3=tx.reserve(sellerTxn3,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.ITEM,"item:fixture_other",3,"player:frank",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        MarketplaceListing.Id listing3=market.createDraft("player:frank","item:fixture_other",3,1000,"currency:fixture_coins",sellerReserved3,AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        market.activate(listing3,"player:frank",sellerReserved3);
        expect(IllegalStateException.class,()->market.confirmFill(listing3,"player:bob",3,bobCommitted),"settlement reused across listings");

        AtomicTransactionService.TransactionId carolTxn=tx.create("player:carol","fixture-final-settlement",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        tx.reserve(carolTxn,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.CURRENCY,"currency:fixture_coins",7000,"player:carol",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        AtomicTransactionService.Snapshot carolCommitted=tx.commit(carolTxn);
        MarketplaceListing.Snapshot filled=market.confirmFill(listingId,"player:carol",7,carolCommitted);
        eq(MarketplaceListing.State.FILLED,filled.state,"filled state");eq(0L,filled.remainingQuantity,"filled remaining");eq(10L,filled.soldQuantity,"filled sold");
        eq(MarketplaceListing.State.FILLED,market.confirmFill(listingId,"player:carol",7,carolCommitted).state,"idempotent final fill");
        expect(IllegalStateException.class,()->market.cancel(listingId,"player:alice"),"cancel filled");

        AtomicTransactionService.TransactionId sellerTxn2=tx.create("player:dave","fixture-seller-escrow-2",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        AtomicTransactionService.Snapshot sellerReserved2=tx.reserve(sellerTxn2,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.ITEM,"item:fixture_cape",5,"player:dave",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        MarketplaceListing.Id listing2=market.createDraft("player:dave","item:fixture_cape",5,250,"currency:fixture_tokens",sellerReserved2,AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        expect(IllegalStateException.class,()->market.cancel(listing2,"player:dave"),"cancel draft");
        market.activate(listing2,"player:dave",sellerReserved2);
        AtomicTransactionService.TransactionId eveTxn=tx.create("player:eve","fixture-partial-2",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        tx.reserve(eveTxn,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.CURRENCY,"currency:fixture_tokens",500,"player:eve",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        market.confirmFill(listing2,"player:eve",2,tx.commit(eveTxn));
        expect(SecurityException.class,()->market.cancel(listing2,"player:mallory"),"unauthorized cancel");
        MarketplaceService.CancellationRequest cancelled=market.cancel(listing2,"player:dave");eq(listing2,cancelled.listingId,"cancel id");eq(sellerTxn2,cancelled.sellerEscrowTransactionId,"cancel escrow ref");eq(MarketplaceListing.State.CANCELLED,market.snapshot(listing2).state,"cancel state");
        eq(listing2,market.cancel(listing2,"player:dave").listingId,"idempotent cancel");
        expect(IllegalStateException.class,()->market.requestFill(listing2,"player:eve",1),"fill cancelled");

        AtomicTransactionService.TransactionId committedSeller=tx.create("player:zoe","fixture-not-reserved",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        tx.reserve(committedSeller,Collections.singletonList(new EscrowAsset(EscrowAsset.Kind.ITEM,"item:fixture",1,"player:zoe",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        AtomicTransactionService.Snapshot committedSellerSnapshot=tx.commit(committedSeller);
        expect(IllegalArgumentException.class,()->market.createDraft("player:zoe","item:fixture",1,1,"currency:x",committedSellerSnapshot,AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB),"create with committed escrow");
        expect(IllegalArgumentException.class,()->market.createDraft("player:x","item:x",0,1,"currency:x",sellerReserved,AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB),"zero quantity");
        expect(IllegalArgumentException.class,()->market.createDraft("player:x","item:x",1,0,"currency:x",sellerReserved,AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB),"zero price");

        eq(MarketplaceListing.State.FILLED,repository.find(listingId).state,"repository latest filled");
        eq(MarketplaceListing.State.CANCELLED,repository.find(listing2).state,"repository latest cancelled");
        eq(3L,market.size(),"market size");
        assertNoProtocolLeaks(MarketplaceListing.class);assertNoProtocolLeaks(MarketplaceService.class);assertNoProtocolLeaks(MarketplaceListingRepository.class);
        System.out.println("ISSUE165_MARKETPLACE_LIFECYCLE_PASS draft=true escrowGate=true partialFill=true settlementIdempotent=true ownerGuard=true immutableSnapshots=true repository=true protocolIndependent=true listings="+market.size());
    }

    static final class MemoryRepository implements MarketplaceListingRepository {
        final LinkedHashMap<MarketplaceListing.Id,MarketplaceListing.Snapshot> rows=new LinkedHashMap<>();
        public void save(MarketplaceListing.Snapshot snapshot){rows.put(snapshot.listingId,snapshot);}
        public MarketplaceListing.Snapshot find(MarketplaceListing.Id listingId){return rows.get(listingId);}
    }
    private static void assertNoProtocolLeaks(Class<?> root){ArrayDeque<Class<?>> q=new ArrayDeque<>();q.add(root);while(!q.isEmpty()){Class<?> c=q.removeFirst();for(Field f:c.getDeclaredFields()){if(f.isEnumConstant())continue;String s=(f.getName()+" "+f.getType().getName()).toLowerCase(Locale.ROOT);for(String x:new String[]{"widget","opcode","subtype","packet"})if(s.contains(x))fail("protocol leak "+c.getName()+"."+f.getName());}Collections.addAll(q,c.getDeclaredClasses());}}
    private static void expect(Class<? extends Throwable> type,Throwing run,String label){try{run.run();fail(label+" did not throw "+type.getSimpleName());}catch(Throwable t){if(!type.isInstance(t))fail(label+" threw "+t);}}
    private static void eq(Object want,Object got,String label){if(!Objects.equals(want,got))fail(label+" expected="+want+" got="+got);}
    private static void eq(long want,long got,String label){if(want!=got)fail(label+" expected="+want+" got="+got);}
    private static void fail(String text){throw new AssertionError(text);}private interface Throwing{void run()throws Exception;}
}
