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
        }
        rejectRegularStack();
        System.out.println("DOPPELGANGER_DYE_TRANSACTION_PASS"+
            " realHandlerSuccess=8 bothDirections=true"+
            " bankOpenAndClosed=true dyeConservation=true"+
            " stackedRegularDenied=true failedQueueAtomic=true"+
            " busyAndTerminalWriterDenied=true cleanRetryAfterBusy=true"+
            " noNewDonorRecipeRegistered=true");
    }
}
