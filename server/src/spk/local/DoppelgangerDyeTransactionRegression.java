package spk.local;

import java.io.*;
import java.util.*;

/**
 * Existing narrow live item-on-item dye interaction, not a donor recipe.
 * Tests real LocalItemOnItemHandler -> BankState -> packet writer behavior.
 */
public final class DoppelgangerDyeTransactionRegression {
    private DoppelgangerDyeTransactionRegression(){}
    private static final int REGULAR=3241;
    private static final int DYE=28824;
    private static final int DYED=28807;
    private static final int INVENTORY=BankState.NORMAL_INVENTORY_CONTAINER;

    private static void ok(boolean yes,String reason){
        if(!yes)throw new AssertionError("DOPPELGANGER_DYE_TRANSACTION "+reason);
    }

    private static BankState withItems(
        int regularSlot,int regularAmount,
        int dyeSlot,int dyeAmount
    ){
        BankState bank=new BankState();
        int[] ids=new int[bank.inventoryCapacity()];
        int[] qty=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[regularSlot]=REGULAR;qty[regularSlot]=regularAmount;
        ids[dyeSlot]=DYE;qty[dyeSlot]=dyeAmount;
        bank.replaceInventorySemantic(ids,qty);
        return bank;
    }

    private static ServerPacketWriter writer(OutboundPacketQueue queue){
        return new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{0,0,0,0}));
    }

    private static ItemOnItemAction action(
        int selectedSlot,int targetSlot,int selectedId,int targetId
    ){
        return new ItemOnItemAction(
            selectedSlot,targetSlot,selectedId,INVENTORY,targetId,INVENTORY);
    }

    private static void success(
        boolean reverse,boolean bankOpen,int dyeCount
    )throws Exception{
        BankState bank=withItems(3,1,9,dyeCount);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter writer=writer(queue);
        if(bankOpen){
            bank.open(writer);
            queue.drainTo(new ByteArrayOutputStream(),8192);
        }
        long previous=bank.inventoryMutationRevision();
        long bankView=bank.bankViewRevision();
        ItemOnItemAction a=reverse
            ?action(9,3,DYE,REGULAR)
            :action(3,9,REGULAR,DYE);
        LocalItemOnItemHandler.Result result=
            new LocalItemOnItemHandler(bank).handle(a,writer);
        ok(result!=null&&
            "DOPPELGANGER_APPLY_DYE".equals(result.saveReason)&&
            result.logText.contains("INVENTORY_COMBINE_OK")&&
            bank.inventoryAt(3).itemId==DYED&&
            bank.inventoryAt(3).qty==1,
            "existing dye action did not produce one dyed pet");
        BankState.Stack dye=bank.inventoryAt(9);
        ok(dyeCount==1?dye==null:
            dye!=null&&dye.itemId==DYE&&dye.qty==dyeCount-1,
            "existing dye action consumed wrong dye quantity");
        ok(bank.inventoryMutationRevision()==previous+1&&
            bank.bankViewRevision()==bankView,
            "existing dye action did not commit exactly one inventory revision");
        ok(queue.queuedPackets()==1&&queue.queuedBytes()>0,
            "existing dye action did not atomically queue postimage");
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ok(queue.drainTo(wire,8192)>0&&wire.size()>0&&
            queue.queuedPackets()==0,
            "existing dye queued bytes not drainable");
    }

    private static void rejectRegularStack()throws Exception{
        BankState bank=withItems(2,2,9,1);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter writer=writer(queue);
        long revision=bank.inventoryMutationRevision();
        LocalItemOnItemHandler.Result result=
            new LocalItemOnItemHandler(bank).handle(
                action(2,9,REGULAR,DYE),writer);
        ok(result!=null&&result.saveReason==null&&
            result.logText.contains("REJECTED_COMBINE_REGULAR_STACK_REMAINS")&&
            bank.inventoryAt(2).itemId==REGULAR&&
            bank.inventoryAt(2).qty==2&&
            bank.inventoryAt(9).itemId==DYE&&
            bank.inventoryAt(9).qty==1&&
            bank.inventoryMutationRevision()==revision&&
            queue.queuedBytes()==0,
            "invalid regular stack caused partial dye conversion");
    }

    private static void queueFailure(boolean bankOpen)throws Exception{
        BankState bank=withItems(3,1,9,2);
        // Give bank-open interface packets enough room to initialize.
        // Saturate only AFTER draining that legitimate initialization.
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter writer=writer(queue);
        if(bankOpen){
            bank.open(writer);
            queue.drainTo(new ByteArrayOutputStream(),8192);
        }
        // Occupy all admission capacity with an earlier unrelated packet.
        queue.offerBatch(new byte[8192]);
        long revision=bank.inventoryMutationRevision();
        long viewRevision=bank.bankViewRevision();
        boolean failed=false;
        try{
            new LocalItemOnItemHandler(bank).handle(
                action(3,9,REGULAR,DYE),writer);
        }catch(IOException expected){
            failed=true;
        }
        ok(failed&&
            bank.inventoryMutationRevision()==revision&&
            bank.bankViewRevision()==viewRevision&&
            bank.inventoryAt(3).itemId==REGULAR&&
            bank.inventoryAt(3).qty==1&&
            bank.inventoryAt(9).itemId==DYE&&
            bank.inventoryAt(9).qty==2&&
            queue.queuedBytes()==8192,
            "failed outbound admission consumed an item or emitted dye batch");
    }

    private static void writerAdmissionGuard(
        boolean bankOpen,boolean terminal
    )throws Exception{
        BankState bank=withItems(3,1,9,2);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter writer=writer(queue);
        if(bankOpen){
            bank.open(writer);
            queue.drainTo(new ByteArrayOutputStream(),8192);
        }
        long oldInventory=bank.inventoryMutationRevision();
        long oldView=bank.bankViewRevision();
        if(terminal)writer.markTerminal();
        else writer.beginBatch();
        LocalItemOnItemHandler.Result result=
            new LocalItemOnItemHandler(bank).handle(
                action(3,9,REGULAR,DYE),writer);
        String token=terminal
            ?"REJECTED_COMBINE_WRITER_TERMINAL"
            :"REJECTED_COMBINE_WRITER_BUSY";
        ok(result!=null&&result.saveReason==null&&
            result.logText.contains(token)&&
            bank.inventoryAt(3).itemId==REGULAR&&
            bank.inventoryAt(3).qty==1&&
            bank.inventoryAt(9).itemId==DYE&&
            bank.inventoryAt(9).qty==2&&
            bank.inventoryMutationRevision()==oldInventory&&
            bank.bankViewRevision()==oldView&&
            queue.queuedBytes()==0,
            "busy/terminal writer admitted dye while unpublished");
        if(!terminal){
            writer.abortBatch();
            // Once the unrelated batch is aborted, a fresh legitimate
            // action may proceed and queue exactly one conversion.
            result=new LocalItemOnItemHandler(bank).handle(
                action(3,9,REGULAR,DYE),writer);
            ok(result!=null&&
                "DOPPELGANGER_APPLY_DYE".equals(result.saveReason)&&
                queue.queuedPackets()==1&&
                bank.inventoryAt(3).itemId==DYED&&
                bank.inventoryAt(9).qty==1,
                "busy writer rejection did not permit later clean dye");
        }
    }

    private static void unverifiedCombinationDenied()throws Exception{
        BankState bank=withItems(3,1,9,2);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter writer=writer(queue);
        long originalRevision=bank.inventoryMutationRevision();
        String wrongResult=bank.combineInventoryOne(
            3,REGULAR,9,DYE,400,writer);
        String wrongPair=bank.combineInventoryOne(
            3,REGULAR,9,100,DYED,writer);
        String reversedInvented=bank.combineInventoryOne(
            9,DYE,3,REGULAR,999,writer);
        ok("REJECTED_COMBINE_UNVERIFIED_PAIR".equals(wrongResult)&&
            "REJECTED_COMBINE_UNVERIFIED_PAIR".equals(wrongPair)&&
            "REJECTED_COMBINE_UNVERIFIED_PAIR".equals(reversedInvented)&&
            queue.queuedBytes()==0&&
            bank.inventoryMutationRevision()==originalRevision&&
            bank.inventoryAt(3).itemId==REGULAR&&
            bank.inventoryAt(3).qty==1&&
            bank.inventoryAt(9).itemId==DYE&&
            bank.inventoryAt(9).qty==2,
            "unverified direct combination granted or consumed items");
    }

    private static void simultaneousDyeRequests(
        boolean reversed,boolean bankOpen
    )throws Exception{
        java.util.concurrent.ExecutorService workers=
            java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<64;trial++){
                BankState bank=withItems(3,1,9,1);
                OutboundPacketQueue queue=new OutboundPacketQueue(8192);
                ServerPacketWriter writer=writer(queue);
                if(bankOpen){
                    bank.open(writer);
                    queue.drainTo(new ByteArrayOutputStream(),8192);
                }
                long initialRevision=bank.inventoryMutationRevision();
                LocalItemOnItemHandler handler=
                    new LocalItemOnItemHandler(bank);
                ItemOnItemAction a=reversed
                    ?action(9,3,DYE,REGULAR)
                    :action(3,9,REGULAR,DYE);
                java.util.concurrent.CountDownLatch ready=
                    new java.util.concurrent.CountDownLatch(2),
                    start=new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Callable<
                    LocalItemOnItemHandler.Result> attempt=()->{
                    ready.countDown();start.await();
                    return handler.handle(a,writer);
                };
                java.util.concurrent.Future<
                    LocalItemOnItemHandler.Result> left=
                    workers.submit(attempt),right=workers.submit(attempt);
                ok(ready.await(5,
                    java.util.concurrent.TimeUnit.SECONDS),
                    "dye contenders not ready trial="+trial);
                start.countDown();
                LocalItemOnItemHandler.Result l=left.get(5,
                    java.util.concurrent.TimeUnit.SECONDS);
                LocalItemOnItemHandler.Result r=right.get(5,
                    java.util.concurrent.TimeUnit.SECONDS);
                int grants=("DOPPELGANGER_APPLY_DYE".equals(
                    l.saveReason)?1:0)+
                    ("DOPPELGANGER_APPLY_DYE".equals(
                    r.saveReason)?1:0);
                ok(grants==1&&queue.queuedPackets()==1&&
                    bank.inventoryAt(3).itemId==DYED&&
                    bank.inventoryAt(9)==null&&
                    bank.inventoryMutationRevision()==initialRevision+1&&
                    (l.logText.contains("REJECTED_COMBINE_STALE_ITEM")||
                     r.logText.contains("REJECTED_COMBINE_STALE_ITEM")||
                     l.logText.contains("REJECTED_COMBINE_ITEM_MISMATCH")||
                     r.logText.contains("REJECTED_COMBINE_ITEM_MISMATCH")),
                    "unexpected concurrent dye transaction trial="+trial+
                    " reversed="+reversed+" bankOpen="+bankOpen+
                    " grants="+grants+" packets="+queue.queuedPackets()+
                    " revision="+bank.inventoryMutationRevision()+
                    " regularId="+bank.inventoryAt(3).itemId+
                    " dyePresent="+(bank.inventoryAt(9)!=null)+
                    " left="+l.logText+" right="+r.logText);
            }
        }finally{
            workers.shutdownNow();
        }
    }

    private static void directWriterFailsClosed(
        boolean bankOpen
    )throws Exception{
        BankState bank=withItems(3,1,9,2);
        ByteArrayOutputStream directWire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            directWire,new IsaacCipher(new int[]{0,0,0,0}));
        // A direct writer cannot guarantee item/packet atomicity.
        // Opening a bank on a direct writer is not needed for the fixture:
        // test closed-bank rejection with the actual direct transport.
        if(bankOpen){
            // Set the overlay using a separate queue-backed writer. The
            // packet transport being tested remains the direct writer.
            OutboundPacketQueue bootstrap=new OutboundPacketQueue(8192);
            ServerPacketWriter initializer=writer(bootstrap);
            bank.open(initializer);
            bootstrap.drainTo(new ByteArrayOutputStream(),8192);
        }
        long before=bank.inventoryMutationRevision();
        boolean refused=false;
        try{
            new LocalItemOnItemHandler(bank).handle(
                action(3,9,REGULAR,DYE),writer);
        }catch(IOException expected){
            refused=String.valueOf(expected.getMessage()).contains(
                "RECIPE_DIRECT_WRITER_NONATOMIC_PUBLICATION_DENIED");
        }
        ok(refused&&directWire.size()==0&&
            bank.inventoryMutationRevision()==before&&
            bank.inventoryAt(3).itemId==REGULAR&&
            bank.inventoryAt(9).qty==2,
            "direct writer granted dye without atomic queue authority");
    }

    private static void fullQueueRetryPreservesWire(
        boolean bankOpen
    )throws Exception{
        BankState bank=withItems(3,1,9,2);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter writer=writer(queue);
        if(bankOpen){
            bank.open(writer);
            queue.drainTo(new ByteArrayOutputStream(),8192);
        }
        long before=bank.inventoryMutationRevision();
        queue.offerBatch(new byte[8192]);
        boolean retractable=false;
        try{
            new LocalItemOnItemHandler(bank).handle(
                action(3,9,REGULAR,DYE),writer);
        }catch(IOException expected){
            retractable=String.valueOf(expected.getMessage()).contains(
                "DOPPELGANGER_DYE_PACKET_ADMISSION_RETRYABLE");
        }
        ok(retractable&&bank.inventoryMutationRevision()==before&&
            bank.inventoryAt(3).itemId==REGULAR&&
            bank.inventoryAt(9).qty==2&&
            queue.queuedBytes()==8192,
            "full-queue dye attempt was not retried atomically");
        queue.drainTo(new ByteArrayOutputStream(),8192);
        LocalItemOnItemHandler.Result retried=
            new LocalItemOnItemHandler(bank).handle(
                action(3,9,REGULAR,DYE),writer);
        ok(retried!=null&&
            "DOPPELGANGER_APPLY_DYE".equals(retried.saveReason)&&
            queue.queuedPackets()==1&&
            bank.inventoryMutationRevision()==before+1&&
            bank.inventoryAt(3).itemId==DYED&&
            bank.inventoryAt(9).qty==1,
            "recoverable queue refusal poisoned later dye transaction");
        ByteArrayOutputStream actual=new ByteArrayOutputStream();
        queue.drainTo(actual,8192);
        byte[] expected=encodedDyeOutcome(false,bankOpen,2);
        ok(Arrays.equals(actual.toByteArray(),expected),
            "retracted dye changed outbound ISAAC or bank mirrors");
    }

    private static void competingDistinctWriters()throws Exception{
        java.util.concurrent.ExecutorService workers=
            java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<64;trial++){
                BankState bank=withItems(3,1,9,1);
                OutboundPacketQueue leftQueue=new OutboundPacketQueue(8192);
                OutboundPacketQueue rightQueue=new OutboundPacketQueue(8192);
                ServerPacketWriter leftWriter=writer(leftQueue);
                ServerPacketWriter rightWriter=writer(rightQueue);
                LocalItemOnItemHandler handler=
                    new LocalItemOnItemHandler(bank);
                long before=bank.inventoryMutationRevision();
                java.util.concurrent.CountDownLatch ready=
                    new java.util.concurrent.CountDownLatch(2),
                    start=new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Future<LocalItemOnItemHandler.Result> left=
                    workers.submit(()->{
                        ready.countDown();start.await();
                        return handler.handle(
                            action(3,9,REGULAR,DYE),leftWriter);
                    });
                java.util.concurrent.Future<LocalItemOnItemHandler.Result> right=
                    workers.submit(()->{
                        ready.countDown();start.await();
                        return handler.handle(
                            action(9,3,DYE,REGULAR),rightWriter);
                    });
                ok(ready.await(5,
                    java.util.concurrent.TimeUnit.SECONDS),
                    "distinct writers not ready trial="+trial);
                start.countDown();
                LocalItemOnItemHandler.Result first=left.get(
                    5,java.util.concurrent.TimeUnit.SECONDS);
                LocalItemOnItemHandler.Result second=right.get(
                    5,java.util.concurrent.TimeUnit.SECONDS);
                int grants=("DOPPELGANGER_APPLY_DYE".equals(
                    first.saveReason)?1:0)+
                    ("DOPPELGANGER_APPLY_DYE".equals(
                    second.saveReason)?1:0);
                ok(grants==1&&
                    leftQueue.queuedPackets()+rightQueue.queuedPackets()==1&&
                    bank.inventoryMutationRevision()==before+1&&
                    bank.inventoryAt(3)!=null&&
                    bank.inventoryAt(3).itemId==DYED&&
                    bank.inventoryAt(9)==null,
                    "competing writers double-granted trial="+trial+
                    " grants="+grants+
                    " leftPackets="+leftQueue.queuedPackets()+
                    " rightPackets="+rightQueue.queuedPackets()+
                    " left="+first.logText+
                    " right="+second.logText);
            }
        }finally{
            workers.shutdownNow();
        }
    }

    private static void competingDyeWithFullQueue()throws Exception{
        java.util.concurrent.ExecutorService workers=
            java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            for(int trial=0;trial<32;trial++){
                BankState bank=withItems(3,1,9,2);
                OutboundPacketQueue queue=new OutboundPacketQueue(8192);
                ServerPacketWriter writer=writer(queue);
                queue.offerBatch(new byte[8192]);
                long oldRevision=bank.inventoryMutationRevision();
                java.util.concurrent.CountDownLatch ready=
                    new java.util.concurrent.CountDownLatch(2),
                    start=new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.Callable<String> task=()->{
                    ready.countDown();start.await();
                    try{
                        LocalItemOnItemHandler.Result result=
                            new LocalItemOnItemHandler(bank).handle(
                                action(3,9,REGULAR,DYE),writer);
                        return result==null?"null":result.logText;
                    }catch(IOException denied){
                        return "IO_DENIED";
                    }
                };
                java.util.concurrent.Future<String> left=workers.submit(task),
                    right=workers.submit(task);
                ok(ready.await(5,
                    java.util.concurrent.TimeUnit.SECONDS),
                    "full-queue contenders not ready trial="+trial);
                start.countDown();
                String a=left.get(5,
                    java.util.concurrent.TimeUnit.SECONDS);
                String b=right.get(5,
                    java.util.concurrent.TimeUnit.SECONDS);
                ok(!a.contains("INVENTORY_COMBINE_OK")&&
                    !b.contains("INVENTORY_COMBINE_OK")&&
                    bank.inventoryMutationRevision()==oldRevision&&
                    bank.inventoryAt(3).itemId==REGULAR&&
                    bank.inventoryAt(3).qty==1&&
                    bank.inventoryAt(9).itemId==DYE&&
                    bank.inventoryAt(9).qty==2&&
                    queue.queuedBytes()==8192,
                    "full-queue competing dyes consumed items trial="+trial+
                    " left="+a+" right="+b);
            }
        }finally{
            workers.shutdownNow();
        }
    }

    private static byte[] encodedDyeOutcome(
        boolean reversed,boolean bankOpen,int dyeQuantity
    )throws Exception{
        BankState bank=withItems(3,1,9,dyeQuantity);
        OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        ServerPacketWriter writer=writer(queue);
        if(bankOpen){
            bank.open(writer);
            ok(queue.drainTo(new ByteArrayOutputStream(),8192)>0,
                "bank-open byte-parity initialization failed");
        }
        long initialRevision=bank.inventoryMutationRevision();
        LocalItemOnItemHandler.Result result=
            new LocalItemOnItemHandler(bank).handle(
                reversed?action(9,3,DYE,REGULAR):
                         action(3,9,REGULAR,DYE),writer);
        ok(result!=null&&
            "DOPPELGANGER_APPLY_DYE".equals(result.saveReason)&&
            bank.inventoryMutationRevision()==initialRevision+1&&
            bank.inventoryAt(3).itemId==DYED&&
            bank.inventoryAt(3).qty==1&&
            bank.inventoryAt(9).qty==dyeQuantity-1&&
            queue.queuedPackets()==1,
            "byte-parity fixture did not make one real dye conversion");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ok(queue.drainTo(bytes,8192)>0&&bytes.size()>0,
            "byte-parity fixture produced no packet");
        return bytes.toByteArray();
    }

    private static void verifyDirectionWireParity()throws Exception{
        for(boolean bankOpen:new boolean[]{false,true}){
            for(int dyeQuantity:new int[]{2,3}){
                byte[] regularSelected=encodedDyeOutcome(
                    false,bankOpen,dyeQuantity);
                byte[] dyeSelected=encodedDyeOutcome(
                    true,bankOpen,dyeQuantity);
                ok(Arrays.equals(regularSelected,dyeSelected),
                    "reversed source item changed canonical queued dye "+
                    "packet bankOpen="+bankOpen+" count="+dyeQuantity);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        ok(ItemDefinitionRepository.exists(DYED),
            "verified current dye result missing from item definitions");
        for(boolean bankOpen:new boolean[]{false,true}){
            for(boolean reverse:new boolean[]{false,true}){
                for(int dyes:new int[]{1,3})
                    success(reverse,bankOpen,dyes);
            }
            queueFailure(bankOpen);
            writerAdmissionGuard(bankOpen,false);
            writerAdmissionGuard(bankOpen,true);
            directWriterFailsClosed(bankOpen);
            fullQueueRetryPreservesWire(bankOpen);
        }
        rejectRegularStack();
        verifyDirectionWireParity();
        unverifiedCombinationDenied();
        competingDistinctWriters();
        competingDyeWithFullQueue();
        for(boolean bankOpen:new boolean[]{false,true})
            for(boolean reverse:new boolean[]{false,true})
                simultaneousDyeRequests(reverse,bankOpen);
        System.out.println("DOPPELGANGER_DYE_TRANSACTION_PASS"+
            " realHandlerSuccess=8 bothDirections=true"+
            " bankOpenAndClosed=true dyeConservation=true"+
            " stackedRegularDenied=true failedQueueAtomic=true"+
            " busyAndTerminalWriterDenied=true cleanRetryAfterBusy=true"+
            " concurrentSingleDyeCommitTrials=256"+
            " arbitraryDirectCombinationsDenied=true"+
            " distinctWriterSingleGrantTrials=64"+
            " competingFullQueueNoGrantTrials=32"+
            " directionWireParityBankOpenClosed=true"+
            " directWriterDenied=true"+
            " fullQueueRetryIsaacParity=true"+
            " noNewDonorRecipeRegistered=true");
    }
}
