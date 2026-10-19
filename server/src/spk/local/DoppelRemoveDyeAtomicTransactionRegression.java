package spk.local;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * The existing LIVE dyed-Doppel option-75 removal is an inventory item grant.
 * Prove queue admission, ISAAC rollback, bank mirror consistency, exact-once
 * contention and fail-closed direct socket on the REAL BankState transaction.
 * Source-only; not an original-v308 GUI or crash-durable account save.
 */
public final class DoppelRemoveDyeAtomicTransactionRegression {
    private static final int DYED=28807,REGULAR=3241,DYE=28824;
    private static final int[] SEED={0,0,0,0};
    private DoppelRemoveDyeAtomicTransactionRegression(){}
    private static void ok(boolean good,String why){
        if(!good)throw new AssertionError("DOPPEL_REMOVE_ATOMIC "+why);
    }
    private static BankState fixture(){
        BankState bank=new BankState();
        int[] ids=new int[bank.inventoryCapacity()];
        int[] qty=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[3]=DYED;qty[3]=1;
        bank.replaceInventorySemantic(ids,qty);
        return bank;
    }
    private static ServerPacketWriter writer(OutboundPacketQueue queue){
        return new ServerPacketWriter(queue,new IsaacCipher(SEED.clone()));
    }
    private static String split(BankState bank,ServerPacketWriter writer)
        throws IOException{
        return bank.splitInventoryOne(3,DYED,REGULAR,DYE,writer);
    }
    private static void requirePreimage(BankState bank,long revision){
        ok(bank.inventoryMutationRevision()==revision&&
            bank.inventoryAt(3)!=null&&
            bank.inventoryAt(3).itemId==DYED&&
            bank.inventoryAt(3).qty==1&&
            bank.inventoryCount(REGULAR)==0&&
            bank.inventoryCount(DYE)==0,
            "refused dye removal changed live item state");
    }
    private static void requirePostimage(BankState bank,long revision){
        ok(bank.inventoryMutationRevision()==revision+1&&
            bank.inventoryAt(3)!=null&&
            bank.inventoryAt(3).itemId==REGULAR&&
            bank.inventoryAt(3).qty==1&&
            bank.inventoryAt(0)!=null&&
            bank.inventoryAt(0).itemId==DYE&&
            bank.inventoryAt(0).qty==1&&
            bank.inventoryCount(DYED)==0,
            "admitted dye removal not exactly one regular+returned dye");
    }
    private static byte[] golden(boolean open)throws Exception{
        BankState bank=fixture();
        OutboundPacketQueue q=new OutboundPacketQueue(8192);
        ServerPacketWriter w=writer(q);
        if(open){
            bank.open(w);
            ok(q.drainTo(new ByteArrayOutputStream(),8192)>0,
                "reference overlay init missing");
        }
        int[] ids=new int[bank.inventoryCapacity()];
        int[] qty=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=DYE;qty[0]=1;
        ids[3]=REGULAR;qty[3]=1;
        w.beginBatch();
        boolean committed=false;
        try{
            w.varShort(53,BootstrapPackets.itemContainer53(
                BankState.NORMAL_INVENTORY_CONTAINER,ids,qty));
            if(open){
                int[] bankIds=new int[bank.bankCapacity()];
                int[] bankQty=new int[bankIds.length];
                Arrays.fill(bankIds,-1);
                for(int i=0;i<bankIds.length;i++){
                    BankState.Stack stack=bank.bankAt(i);
                    if(stack!=null){
                        bankIds[i]=stack.itemId;
                        bankQty[i]=stack.qty;
                    }
                }
                w.varShort(53,BootstrapPackets.itemContainer53(
                    BankState.BANK_CONTAINER,bankIds,bankQty));
                w.varShort(53,BootstrapPackets.itemContainer53(
                    BankState.BANK_INVENTORY_CONTAINER,ids,qty));
            }
            w.endBatch();
            committed=true;
        }finally{
            if(!committed)w.abortBatch();
        }
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        ok(q.drainTo(result,8192)>0,"golden 53 batch absent");
        return result.toByteArray();
    }
    private static void queueBackpressure(boolean open)throws Exception{
        BankState bank=fixture();
        OutboundPacketQueue q=new OutboundPacketQueue(8192);
        ServerPacketWriter w=writer(q);
        if(open){
            bank.open(w);
            q.drainTo(new ByteArrayOutputStream(),8192);
        }
        long before=bank.inventoryMutationRevision();
        long view=bank.bankViewRevision();
        q.offerBatch(new byte[8192]);
        String denied=split(bank,w);
        ok("REJECTED_SPLIT_QUEUE_RETRYABLE".equals(denied)&&
            q.queuedBytes()==8192&&q.queuedPackets()==1&&
            bank.bankViewRevision()==view&&!w.terminal(),
            "full queue removal not retryable open="+open+" result="+denied);
        requirePreimage(bank,before);
        q.drainTo(new ByteArrayOutputStream(),8192);
        String admitted=split(bank,w);
        ok(admitted.startsWith("INVENTORY_SPLIT_OK")&&
            q.queuedPackets()==1&&
            bank.bankViewRevision()==view,
            "queue retry not admitted open="+open+" result="+admitted);
        requirePostimage(bank,before);
        String replay=split(bank,w);
        ok(replay.startsWith("REJECTED_INVENTORY_ITEM_MISMATCH")&&
            q.queuedPackets()==1,
            "replayed removal granted or queued twice open="+open);
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ok(q.drainTo(wire,8192)>0&&
            Arrays.equals(wire.toByteArray(),golden(open)),
            "retry did not rewind ISAAC or preserve bank mirror open="+open);
    }
    private static void guardAndDirectSocket(boolean open)throws Exception{
        for(int guard=0;guard<3;guard++){
            BankState bank=fixture();
            OutboundPacketQueue q=new OutboundPacketQueue(8192);
            ServerPacketWriter w=writer(q);
            if(open){
                bank.open(w);
                q.drainTo(new ByteArrayOutputStream(),8192);
            }
            long before=bank.inventoryMutationRevision();
            long view=bank.bankViewRevision();
            if(guard==0)w.beginBatch();
            else if(guard==1)w.markTerminal();
            String result=null;
            boolean deniedDirect=false;
            if(guard==2){
                ByteArrayOutputStream directBytes=new ByteArrayOutputStream();
                ServerPacketWriter direct=new ServerPacketWriter(
                    directBytes,new IsaacCipher(SEED.clone()));
                try{
                    split(bank,direct);
                }catch(IOException expected){
                    deniedDirect=true;
                }
                ok(deniedDirect&&directBytes.size()==0,
                    "direct writer emitted bytes or granted removal");
            }else{
                result=split(bank,w);
                ok((guard==0?
                        "REJECTED_SPLIT_WRITER_BUSY":
                        "REJECTED_SPLIT_WRITER_TERMINAL").equals(result),
                    "session writer guard mismatch "+result);
            }
            requirePreimage(bank,before);
            ok(bank.bankViewRevision()==view&&q.queuedPackets()==0,
                "guard rejection emitted packet or changed bank view");
            if(guard==0){
                w.abortBatch();
                ok(split(bank,w).startsWith("INVENTORY_SPLIT_OK"),
                    "busy writer not recoverable");
                requirePostimage(bank,before);
            }
        }
    }
    private static void invalidClaims()throws Exception{
        BankState bank=fixture();
        OutboundPacketQueue q=new OutboundPacketQueue(8192);
        ServerPacketWriter w=writer(q);
        long mutation=bank.inventoryMutationRevision();
        ok("REJECTED_SPLIT_UNVERIFIED_PAIR".equals(
            bank.splitInventoryOne(3,DYED,REGULAR,4151,w)),
            "arbitrary extra item admitted");
        ok("REJECTED_SPLIT_UNVERIFIED_PAIR".equals(
            bank.splitInventoryOne(3,DYED,995,DYE,w)),
            "arbitrary main item admitted");
        ok("REJECTED_SPLIT_UNVERIFIED_PAIR".equals(
            bank.splitInventoryOne(3,REGULAR,REGULAR,DYE,w)),
            "invented source pet admitted");
        ok("REJECTED_INVENTORY_SLOT".equals(
            bank.splitInventoryOne(28,DYED,REGULAR,DYE,w)),
            "out-of-bounds slot admitted");
        requirePreimage(bank,mutation);
        ok(q.queuedPackets()==0,"invalid split enqueued item grant");
    }
    private static void contenders(boolean open)throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<32;trial++){
                BankState bank=fixture();
                OutboundPacketQueue q=new OutboundPacketQueue(8192);
                ServerPacketWriter w=writer(q);
                if(open){
                    bank.open(w);
                    q.drainTo(new ByteArrayOutputStream(),8192);
                }
                long before=bank.inventoryMutationRevision();
                CountDownLatch ready=new CountDownLatch(2),
                    release=new CountDownLatch(1);
                Callable<String> attempt=()->{
                    ready.countDown();
                    release.await();
                    return split(bank,w);
                };
                Future<String> a=pool.submit(attempt);
                Future<String> b=pool.submit(attempt);
                ok(ready.await(5,TimeUnit.SECONDS),
                    "remove-dye contenders not ready");
                release.countDown();
                String l=a.get(5,TimeUnit.SECONDS),
                    r=b.get(5,TimeUnit.SECONDS);
                int successes=(l.startsWith("INVENTORY_SPLIT_OK")?1:0)+
                    (r.startsWith("INVENTORY_SPLIT_OK")?1:0);
                ok(successes==1&&q.queuedPackets()==1&&
                    (l.startsWith("REJECTED_INVENTORY_ITEM_MISMATCH")||
                     r.startsWith("REJECTED_INVENTORY_ITEM_MISMATCH")),
                    "concurrent remove-dye double grant "+trial+
                    " left="+l+" right="+r);
                requirePostimage(bank,before);
                ByteArrayOutputStream wire=new ByteArrayOutputStream();
                ok(q.drainTo(wire,8192)>0&&
                    Arrays.equals(wire.toByteArray(),golden(open)),
                    "concurrent remove-dye output lost parity "+trial);
            }
        }finally{
            pool.shutdownNow();
        }
    }
    public static void main(String[] args)throws Exception{
        for(boolean open:new boolean[]{false,true}){
            queueBackpressure(open);
            guardAndDirectSocket(open);
            contenders(open);
        }
        invalidClaims();
        System.out.println("DOPPEL_REMOVE_ATOMIC_PASS"+
            " queueFullRollbackBankViews=2 directWriterFailClosed=true"+
            " busyTerminalFailClosed=true exactRetryWire=true"+
            " duplicateDyeNoGrant=true unverifiableTransformsDenied=true"+
            " concurrentSingleSplitGrants=64 clientAck=false"+
            " donorRecipesEnabled=false");
    }
}
