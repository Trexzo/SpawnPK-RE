package spk.local;

import java.io.*;
import java.util.*;

/**
 * Source-only mixed recipe -> BankState -> recoverable opcode-53 queue path.
 * No original-client recipe, XP, item grant API, save intent or live handler.
 */
public final class RecipeMixedBankAdapterTest {
    private RecipeMixedBankAdapterTest(){}
    private static void ok(boolean value,String reason){
        if(!value)throw new AssertionError("RECIPE_MIXED_BANK "+reason);
    }
    private static final Set<Integer> STACKABLE=
        new HashSet<>(Arrays.asList(100,300));
    private static final Set<Integer> NONSTACKABLE=
        new HashSet<>(Arrays.asList(200,400));
    private static int[] ids(){
        int[] ids=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(ids,-1);
        ids[0]=100;ids[1]=200;ids[2]=200;ids[3]=300;ids[4]=400;
        return ids;
    }
    private static int[] qty(){
        int[] qty=new int[BankState.INVENTORY_CAPACITY];
        qty[0]=5;qty[1]=1;qty[2]=1;qty[3]=7;qty[4]=1;
        return qty;
    }
    private static Map<Integer,Integer> ingredients(){
        Map<Integer,Integer> inputs=new HashMap<>();
        inputs.put(100,1);
        inputs.put(200,1);
        return inputs;
    }
    private static RecipeBankSemanticAdapter.Prepared prepare(BankState bank){
        return RecipeBankSemanticAdapter.prepareMixedWithTool(
            bank,ingredients(),400,1,2,
            STACKABLE,NONSTACKABLE,null,false);
    }

    public static void main(String[] args)throws Exception{
        BankState bank=new BankState();
        bank.replaceInventorySemantic(ids(),qty());
        RecipeBankSemanticAdapter.Prepared ready=prepare(bank);
        ok(bank.inventoryAt(0).qty==5&&bank.inventoryAt(1).itemId==200,
            "mixed preparation changed inventory");

        OutboundPacketQueue queue=new OutboundPacketQueue(4096);
        ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{0,0,0,0}));
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            bank,ready,writer)==ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "mixed bank queue did not admit");
        ok(bank.inventoryAt(0).qty==3&&
            bank.inventoryAt(1).itemId==400&&
            bank.inventoryAt(2).itemId==400&&
            bank.inventoryAt(1).qty==1&&bank.inventoryAt(2).qty==1&&
            bank.inventoryAt(3).qty==7&&bank.inventoryAt(4).itemId==400,
            "nonstackable output did not conserve and reuse slots");
        ok(queue.queuedPackets()==1&&queue.queuedBytes()>0,
            "mixed inventory batch was not queued atomically");
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ok(queue.drainTo(wire,4096)>0&&wire.size()>0,
            "mixed inventory packet not encoded");
        boolean replayDenied=false;
        try{
            RecipeBankSemanticAdapter.publishAndCommitIfIdle(
                bank,ready,writer);
        }catch(IllegalStateException expected){
            replayDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_PREPARED_ALREADY_COMMITTED");
        }
        ok(replayDenied,"mixed prepared authority was replayed");

        BankState fullBank=new BankState();
        int[] fullIds=new int[BankState.INVENTORY_CAPACITY],
            fullCounts=new int[fullIds.length];
        Arrays.fill(fullIds,400);
        Arrays.fill(fullCounts,1);
        fullIds[0]=100;fullCounts[0]=5;
        fullBank.replaceInventorySemantic(fullIds,fullCounts);
        boolean fullDenied=false;
        try{
            RecipeBankSemanticAdapter.prepareMixedWithTool(
                fullBank,Collections.singletonMap(100,1),
                400,2,1,STACKABLE,NONSTACKABLE,null,false);
        }catch(IllegalStateException expected){
            fullDenied=String.valueOf(expected.getMessage()).contains(
                "RECIPE_OUTPUT_CAPACITY");
        }
        ok(fullDenied&&fullBank.inventoryAt(0).qty==5,
            "capacity rejection mutated mixed inventory");

        BankState crowdedBank=new BankState();
        crowdedBank.replaceInventorySemantic(ids(),qty());
        OutboundPacketQueue crowded=new OutboundPacketQueue(4096);
        crowded.offerBatch(new byte[4096]);
        ServerPacketWriter crowdedWriter=new ServerPacketWriter(
            crowded,new IsaacCipher(new int[]{0,0,0,0}));
        RecipeBankSemanticAdapter.Prepared delayed=prepare(crowdedBank);
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            crowdedBank,delayed,crowdedWriter)==
            ServerPacketWriter.RecoverablePacketResult.RETRACTED_RETRYABLE,
            "mixed queue backpressure unexpectedly admitted");
        ok(crowdedBank.inventoryAt(0).qty==5&&
            crowdedBank.inventoryAt(1).itemId==200&&
            crowded.queuedPackets()==1&&crowded.queuedBytes()==4096,
            "mixed queue refusal leaked bytes or items");
        crowded.drainTo(new ByteArrayOutputStream(),4096);
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            crowdedBank,delayed,crowdedWriter)==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "mixed backpressure retry did not commit");
        ok(crowdedBank.inventoryAt(0).qty==3&&
            crowdedBank.inventoryAt(1).itemId==400&&
            crowdedBank.inventoryAt(2).itemId==400,
            "mixed retry lost nonstackable products");

        // An already-open bank needs both normal and bank inventory views
        // in one queued publication. No bank contents should be altered.
        BankState openBank=new BankState();
        openBank.replaceInventorySemantic(ids(),qty());
        OutboundPacketQueue openQueue=new OutboundPacketQueue(8192);
        ServerPacketWriter openWriter=new ServerPacketWriter(
            openQueue,new IsaacCipher(new int[]{0,0,0,0}));
        openBank.open(openWriter);
        openQueue.drainTo(new ByteArrayOutputStream(),8192);
        RecipeBankSemanticAdapter.Prepared openReady=prepare(openBank);
        ok(RecipeBankSemanticAdapter.publishAndCommitIfIdle(
            openBank,openReady,openWriter)==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "open bank mixed recipe queue admission failed");
        ok(openQueue.queuedPackets()==1&&
            openBank.inventoryAt(1).itemId==400&&
            openBank.inventoryAt(2).itemId==400,
            "open-bank mixed recipe did not publish both views");
        ByteArrayOutputStream actualOpenWire=new ByteArrayOutputStream();
        ok(openQueue.drainTo(actualOpenWire,8192)>0,
            "open-bank mixed recipe packets were not available");
        // Independently reproduce the same bank-open ISAAC prefix and
        // encode both expected widget 3214/5064 inventory views. This
        // guards mirror ordering, packet contents and writer cipher state.
        BankState referenceBank=new BankState();
        referenceBank.replaceInventorySemantic(ids(),qty());
        OutboundPacketQueue referenceQueue=new OutboundPacketQueue(8192);
        ServerPacketWriter referenceWriter=new ServerPacketWriter(
            referenceQueue,new IsaacCipher(new int[]{0,0,0,0}));
        referenceBank.open(referenceWriter);
        referenceQueue.drainTo(new ByteArrayOutputStream(),8192);
        int[] expectedIds=new int[BankState.INVENTORY_CAPACITY];
        int[] expectedQty=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(expectedIds,-1);
        for(int slot=0;slot<expectedIds.length;slot++){
            BankState.Stack stack=openBank.inventoryAt(slot);
            if(stack!=null){
                expectedIds[slot]=stack.itemId;
                expectedQty[slot]=stack.qty;
            }
        }
        byte[][] expectedViews=referenceBank.previewRecipeInventoryViews(
            expectedIds,expectedQty);
        ok(expectedViews.length==2,
            "reference bank-open recipe omitted inventory mirror");
        ok(referenceWriter.publishQueueOnlyRecoverablePacketIfIdle(
            ()->{for(byte[] view:expectedViews)
                referenceWriter.varShort(53,view);})==
            ServerPacketWriter.RecoverablePacketResult.COMMITTED,
            "reference two-view publication failed");
        ByteArrayOutputStream expectedOpenWire=new ByteArrayOutputStream();
        referenceQueue.drainTo(expectedOpenWire,8192);
        ok(Arrays.equals(actualOpenWire.toByteArray(),
            expectedOpenWire.toByteArray()),
            "mixed bank-open wire bytes did not match two-view reference");

        System.out.println("RECIPE_MIXED_BANK_PASS"+
            " nonstackableSlotConservation=true queueAtomic=true"+
            " backpressureRetry=true replayDenied=true"+
            " capacityDenied=true bankOpenMirror=true"+
            " bankOpenWireParity=true"+
            " liveRecipeRegistered=false");
    }
}
