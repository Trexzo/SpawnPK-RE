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
        System.out.println("RECIPE_ITEM_ON_ITEM_DISPATCH_BOUNDARY_PASS"+
            " unverifiedPairDenied=true wrongWidgetDenied=true"+
            " noInventoryMutation=true zeroPackets=true"+
            " staleDyeDenied=true sameSlotDyeDenied=true");
    }
    private RecipeItemOnItemDispatchBoundaryTest(){}
}
