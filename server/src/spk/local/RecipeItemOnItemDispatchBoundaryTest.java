package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Real opcode-53 handler boundary: unverified donor recipes must never
 * reach a conversion or silently mutate authoritative inventory.
 */
public final class RecipeItemOnItemDispatchBoundaryTest {
    private static void require(boolean condition,String reason){
        if(!condition)throw new AssertionError("RECIPE_DISPATCH "+reason);
    }
    public static void main(String[] args)throws Exception{
        BankState bank=new BankState();
        int[] ids=new int[bank.inventoryCapacity()],qty=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=100;qty[0]=5;
        ids[1]=200;qty[1]=7;
        bank.replaceInventorySemantic(ids,qty);
        LocalItemOnItemHandler handler=new LocalItemOnItemHandler(bank);
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{0,0,0,0}));
        ItemOnItemAction unsupported=new ItemOnItemAction(
            1,0,200,BankState.NORMAL_INVENTORY_CONTAINER,
            100,BankState.NORMAL_INVENTORY_CONTAINER);
        LocalItemOnItemHandler.Result result=handler.handle(unsupported,writer);
        require(result!=null&&result.saveReason==null&&
            result.logText.contains("DECODED_NO_SEMANTIC_HANDLER"),
            "unknown item pair unexpectedly admitted");
        require(bank.inventoryAt(0).qty==5&&bank.inventoryAt(1).qty==7,
            "unknown pair mutated inventory");
        require(wire.size()==0,"unknown pair emitted packet");
        ItemOnItemAction wrongWidget=new ItemOnItemAction(
            1,0,200,9999,100,BankState.NORMAL_INVENTORY_CONTAINER);
        result=handler.handle(wrongWidget,writer);
        require(result!=null&&result.saveReason==null&&
            result.logText.contains("DECODED_UNSUPPORTED_WIDGET"),
            "unsupported widget unexpectedly admitted");
        require(wire.size()==0&&bank.inventoryAt(0).qty==5,
            "unsupported widget side effect");
        // Recognized dye pair with mismatched/stale slots must stop before
        // the pre-existing dye publisher, with no save intent or bytes.
        ItemOnItemAction staleDye=new ItemOnItemAction(
            1,0,3241,BankState.NORMAL_INVENTORY_CONTAINER,
            28824,BankState.NORMAL_INVENTORY_CONTAINER);
        result=handler.handle(staleDye,writer);
        require(result!=null&&result.saveReason==null&&
            result.logText.contains("REJECTED_COMBINE_STALE_ITEM"),
            "recognized but stale dye pair not denied");
        require(wire.size()==0&&bank.inventoryAt(0).qty==5,
            "stale dye pair emitted packet or mutated inventory");
        ItemOnItemAction sameSlotDye=new ItemOnItemAction(
            0,0,3241,BankState.NORMAL_INVENTORY_CONTAINER,
            28824,BankState.NORMAL_INVENTORY_CONTAINER);
        result=handler.handle(sameSlotDye,writer);
        require(result!=null&&result.saveReason==null&&
            result.logText.contains("REJECTED_COMBINE_SLOT"),
            "same slot dye pair not denied");
        require(wire.size()==0,"same-slot dye emitted a packet");
        // Source-only donor dispatch guard: a broad deterministic corpus
        // of unknown item pairs must not turn into an accidental live
        // conversion when recipe infrastructure is refactored. The one
        // observed Doppelganger dye pair stays outside this corpus.
        java.util.Random random=new java.util.Random(0x53D0A2026L);
        int[] candidateIds={100,200,300,3241,28824,400,500,999};
        int[] candidateSlots={-1,0,1,2,3,27,28};
        long priorInventoryRevision=bank.inventoryMutationRevision();
        long priorViewRevision=bank.bankViewRevision();
        for(int trial=0;trial<1024;trial++){
            int selectedId=candidateIds[random.nextInt(candidateIds.length)];
            int targetId=candidateIds[random.nextInt(candidateIds.length)];
            if(selectedId==3241&&targetId==28824||
               selectedId==28824&&targetId==3241){
                // Existing narrow verified item pair is tested separately.
                trial--;
                continue;
            }
            int selectedSlot=candidateSlots[
                random.nextInt(candidateSlots.length)];
            int targetSlot=candidateSlots[
                random.nextInt(candidateSlots.length)];
            int selectedWidget=random.nextBoolean()
                ?BankState.NORMAL_INVENTORY_CONTAINER:9999;
            int targetWidget=random.nextBoolean()
                ?BankState.NORMAL_INVENTORY_CONTAINER:9999;
            ItemOnItemAction unverified=new ItemOnItemAction(
                selectedSlot,targetSlot,selectedId,selectedWidget,
                targetId,targetWidget);
            LocalItemOnItemHandler.Result rejection=
                handler.handle(unverified,writer);
            require(rejection!=null&&rejection.saveReason==null&&
                (rejection.logText.contains("DECODED_NO_SEMANTIC_HANDLER")||
                 rejection.logText.contains("DECODED_UNSUPPORTED_WIDGET")),
                "unverified item pair acquired a semantic handler trial="+
                trial);
            require(wire.size()==0&&
                bank.inventoryMutationRevision()==priorInventoryRevision&&
                bank.bankViewRevision()==priorViewRevision&&
                bank.inventoryAt(0).qty==5&&bank.inventoryAt(1).qty==7,
                "unverified item pair emitted or mutated trial="+trial);
        }
        System.out.println("RECIPE_ITEM_ON_ITEM_DISPATCH_BOUNDARY_PASS"+
            " unverifiedPairDenied=true wrongWidgetDenied=true"+
            " noInventoryMutation=true zeroPackets=true"+
            " staleDyeDenied=true sameSlotDyeDenied=true"+
            " unsupportedDispatchCorpus=1024 noUnverifiedRecipe=true");
    }
    private RecipeItemOnItemDispatchBoundaryTest(){}
}
