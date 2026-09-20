package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class AtomicTransactionServiceTest {
    public static void main(String[] args){
        AtomicTransactionService service=new AtomicTransactionService();
        AtomicTransactionService.TransactionId first=service.create(
            "player:alice","fixture-market-listing",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        AtomicTransactionService.Snapshot created=service.snapshot(first);
        eq(AtomicTransactionService.TransactionState.CREATED,created.state,"created state");
        eq("player:alice",created.ownerRef,"owner");
        eq("fixture-market-listing",created.reference,"reference");
        eq(AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB,created.sourceAuthority,"authority");
        if(!created.reservations.isEmpty())fail("new transaction should not have reservations");

        ArrayList<EscrowAsset> requested=new ArrayList<>();
        requested.add(new EscrowAsset(EscrowAsset.Kind.ITEM,"item:fixture_sword",2,"player:alice",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB));
        requested.add(new EscrowAsset(EscrowAsset.Kind.CURRENCY,"currency:fixture_coins",500,"player:bob",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB));
        AtomicTransactionService.Snapshot reserved=service.reserve(first,requested);
        requested.clear();
        eq(AtomicTransactionService.TransactionState.RESERVED,reserved.state,"reserved state");
        eq(2,reserved.reservations.size(),"reservation count");
        if(reserved.reservations.get(0).escrowId.equals(reserved.reservations.get(1).escrowId))fail("escrow ids must be unique");
        eq(2L,reserved.reservations.get(0).asset.quantity,"item quantity");
        eq(500L,reserved.reservations.get(1).asset.quantity,"currency quantity");
        immutable(reserved.reservations);
        expect(IllegalStateException.class,()->service.reserve(first,Collections.singletonList(
            new EscrowAsset(EscrowAsset.Kind.GENERIC,"duplicate",1,"player:alice",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB))),"second reserve");

        AtomicTransactionService.Snapshot committed=service.commit(first);
        eq(AtomicTransactionService.TransactionState.COMMITTED,committed.state,"commit state");
        AtomicTransactionService.Snapshot committedAgain=service.commit(first);
        eq(AtomicTransactionService.TransactionState.COMMITTED,committedAgain.state,"idempotent commit");
        eq(committed.reservations.get(0).escrowId,committedAgain.reservations.get(0).escrowId,"stable escrow id");
        expect(IllegalStateException.class,()->service.cancel(first),"cancel committed");

        AtomicTransactionService.TransactionId second=service.create(
            "player:bob","fixture-cancel",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB);
        service.reserve(second,Collections.singletonList(
            new EscrowAsset(EscrowAsset.Kind.GENERIC,"fixture:token",1,"player:bob",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)));
        eq(AtomicTransactionService.TransactionState.CANCELLED,service.cancel(second).state,"cancel reserved");
        eq(AtomicTransactionService.TransactionState.CANCELLED,service.cancel(second).state,"idempotent cancel");
        expect(IllegalStateException.class,()->service.commit(second),"commit cancelled");

        AtomicTransactionService.TransactionId third=service.create(
            "player:carol","fixture-created-cancel",AtomicTransactionService.SourceAuthority.UNKNOWN_SERVER_AUTHORITY);
        eq(AtomicTransactionService.TransactionState.CANCELLED,service.cancel(third).state,"cancel created");

        expect(IllegalStateException.class,()->service.commit(service.create(
            "player:dave","fixture-unreserved",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB)),"commit created");
        expect(IllegalArgumentException.class,()->service.snapshot(new AtomicTransactionService.TransactionId(999999)),"unknown transaction");
        expect(IllegalArgumentException.class,()->new EscrowAsset(EscrowAsset.Kind.ITEM,"item:x",0,"player:x",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB),"zero quantity");
        expect(IllegalArgumentException.class,()->service.reserve(service.create(
            "player:eve","fixture-empty",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB),Collections.emptyList()),"empty reserve");
        expect(IllegalArgumentException.class,()->service.create("   ","fixture",AtomicTransactionService.SourceAuthority.CUSTOM_LOCALLAB),"blank owner");

        assertNoProtocolLeaks(AtomicTransactionService.class);
        assertNoProtocolLeaks(EscrowAsset.class);
        if(service.size()!=5)fail("transaction count expected=5 actual="+service.size());

        System.out.println("ISSUE159_ATOMIC_TRANSACTION_ESCROW_PASS lifecycle=true idempotent=true immutable=true protocolIndependent=true authorityPreserved=true transactions="+service.size());
    }

    private static void assertNoProtocolLeaks(Class<?> root){
        ArrayDeque<Class<?>> q=new ArrayDeque<>();q.add(root);
        while(!q.isEmpty()){
            Class<?> c=q.removeFirst();
            for(Field f:c.getDeclaredFields()){
                if(f.isEnumConstant())continue;
                String text=(f.getName()+" "+f.getType().getName()).toLowerCase(Locale.ROOT);
                for(String forbidden:new String[]{"widget","opcode","subtype","packet"})if(text.contains(forbidden))fail("protocol leak "+c.getName()+"."+f.getName());
            }
            Collections.addAll(q,c.getDeclaredClasses());
        }
    }
    private static void immutable(List<?> list){
        try{((List)list).clear();fail("snapshot reservations mutable");}catch(UnsupportedOperationException expected){}
    }
    private static void expect(Class<? extends Throwable> type,Throwing action,String label){
        try{action.run();fail(label+" did not throw "+type.getSimpleName());}catch(Throwable t){if(!type.isInstance(t))fail(label+" threw "+t);}
    }
    private static void eq(Object want,Object got,String label){if(!Objects.equals(want,got))fail(label+" expected="+want+" got="+got);}
    private static void eq(long want,long got,String label){if(want!=got)fail(label+" expected="+want+" got="+got);}
    private static void fail(String m){throw new AssertionError(m);}
    private interface Throwing{void run()throws Exception;}
}
