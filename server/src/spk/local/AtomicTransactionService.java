package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Protocol-independent atomic transaction / escrow lifecycle foundation.
 *
 * This service records reservation intent only.  It deliberately does not mutate
 * inventory, bank, currency, persistence, packets, or Marketplace state.
 */
final class AtomicTransactionService {
    enum SourceAuthority {
        EXACT_CURRENT_CLIENT,
        EXACT_CURRENT_CACHE,
        LOCAL_RUNTIME_PROVEN,
        HISTORICAL_CORROBORATION,
        INFERENCE,
        UNKNOWN_SERVER_AUTHORITY,
        CUSTOM_LOCALLAB
    }
    enum TransactionState { CREATED, RESERVED, COMMITTED, CANCELLED }

    static final class TransactionId {
        final long value;
        TransactionId(long value){if(value<=0)throw new IllegalArgumentException("value="+value);this.value=value;}
        @Override public boolean equals(Object o){return o instanceof TransactionId&&((TransactionId)o).value==value;}
        @Override public int hashCode(){return Long.hashCode(value);}
        @Override public String toString(){return "txn-"+Long.toUnsignedString(value);}
    }

    static final class EscrowId {
        final long value;
        EscrowId(long value){if(value<=0)throw new IllegalArgumentException("value="+value);this.value=value;}
        @Override public boolean equals(Object o){return o instanceof EscrowId&&((EscrowId)o).value==value;}
        @Override public int hashCode(){return Long.hashCode(value);}
        @Override public String toString(){return "escrow-"+Long.toUnsignedString(value);}
    }

    static final class Reservation {
        final EscrowId escrowId;
        final EscrowAsset asset;
        Reservation(EscrowId escrowId,EscrowAsset asset){this.escrowId=escrowId;this.asset=asset;}
        @Override public String toString(){return escrowId+"="+asset;}
    }

    static final class Snapshot {
        final TransactionId transactionId;
        final TransactionState state;
        final String ownerRef;
        final String reference;
        final SourceAuthority sourceAuthority;
        final List<Reservation> reservations;

        Snapshot(TransactionId transactionId,TransactionState state,String ownerRef,String reference,SourceAuthority sourceAuthority,List<Reservation> reservations){
            this.transactionId=transactionId;
            this.state=state;
            this.ownerRef=ownerRef;
            this.reference=reference;
            this.sourceAuthority=sourceAuthority;
            this.reservations=Collections.unmodifiableList(new ArrayList<>(reservations));
        }
        @Override public String toString(){return "TransactionSnapshot{id="+transactionId+",state="+state+",owner="+ownerRef+",reference="+reference+",reservations="+reservations.size()+",authority="+sourceAuthority+"}";}
    }

    private static final class Transaction {
        final TransactionId id;
        final String ownerRef;
        final String reference;
        final SourceAuthority sourceAuthority;
        final ArrayList<Reservation> reservations=new ArrayList<>();
        TransactionState state=TransactionState.CREATED;

        Transaction(TransactionId id,String ownerRef,String reference,SourceAuthority sourceAuthority){
            this.id=id;this.ownerRef=ownerRef;this.reference=reference;this.sourceAuthority=sourceAuthority;
        }
    }

    private final AtomicLong transactionSequence=new AtomicLong();
    private final AtomicLong escrowSequence=new AtomicLong();
    private final LinkedHashMap<TransactionId,Transaction> transactions=new LinkedHashMap<>();

    synchronized TransactionId create(String ownerRef,String reference,SourceAuthority sourceAuthority){
        String owner=requireText(ownerRef,"ownerRef");
        String ref=requireText(reference,"reference");
        if(sourceAuthority==null)throw new NullPointerException("sourceAuthority");
        TransactionId id=nextTransactionId();
        transactions.put(id,new Transaction(id,owner,ref,sourceAuthority));
        return id;
    }

    synchronized Snapshot reserve(TransactionId id,List<EscrowAsset> assets){
        Transaction t=required(id);
        if(t.state!=TransactionState.CREATED)throw invalid(t,"reserve");
        if(assets==null)throw new NullPointerException("assets");
        if(assets.isEmpty())throw new IllegalArgumentException("assets empty");

        ArrayList<EscrowAsset> checked=new ArrayList<>(assets.size());
        for(EscrowAsset asset:assets){
            if(asset==null)throw new NullPointerException("asset");
            checked.add(asset);
        }
        ArrayList<Reservation> pending=new ArrayList<>(checked.size());
        for(EscrowAsset asset:checked){
            pending.add(new Reservation(nextEscrowId(),asset));
        }
        t.reservations.addAll(pending);
        t.state=TransactionState.RESERVED;
        return snapshot(t);
    }

    synchronized Snapshot commit(TransactionId id){
        Transaction t=required(id);
        if(t.state==TransactionState.COMMITTED)return snapshot(t);
        if(t.state!=TransactionState.RESERVED)throw invalid(t,"commit");
        t.state=TransactionState.COMMITTED;
        return snapshot(t);
    }

    synchronized Snapshot cancel(TransactionId id){
        Transaction t=required(id);
        if(t.state==TransactionState.CANCELLED)return snapshot(t);
        if(t.state==TransactionState.COMMITTED)throw invalid(t,"cancel");
        if(t.state!=TransactionState.CREATED&&t.state!=TransactionState.RESERVED)throw invalid(t,"cancel");
        t.state=TransactionState.CANCELLED;
        return snapshot(t);
    }

    synchronized Snapshot snapshot(TransactionId id){return snapshot(required(id));}

    synchronized int size(){return transactions.size();}

    private TransactionId nextTransactionId(){
        long value=transactionSequence.incrementAndGet();
        if(value<=0)throw new IllegalStateException("transaction id sequence exhausted");
        return new TransactionId(value);
    }

    private EscrowId nextEscrowId(){
        long value=escrowSequence.incrementAndGet();
        if(value<=0)throw new IllegalStateException("escrow id sequence exhausted");
        return new EscrowId(value);
    }

    private Transaction required(TransactionId id){
        if(id==null)throw new NullPointerException("transactionId");
        Transaction t=transactions.get(id);
        if(t==null)throw new IllegalArgumentException("unknown transaction "+id);
        return t;
    }

    private static Snapshot snapshot(Transaction t){
        return new Snapshot(t.id,t.state,t.ownerRef,t.reference,t.sourceAuthority,t.reservations);
    }

    private static IllegalStateException invalid(Transaction t,String operation){
        return new IllegalStateException(operation+" invalid from "+t.state+" for "+t.id);
    }

    private static String requireText(String value,String field){
        if(value==null)throw new NullPointerException(field);
        String clean=value.trim();
        if(clean.isEmpty())throw new IllegalArgumentException(field+" blank");
        return clean;
    }
}
