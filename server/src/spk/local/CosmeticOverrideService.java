package spk.local;

import java.io.IOException;

/** LocalLab semantic handler for exact item-definition action "Override". */
final class CosmeticOverrideService {
    private CosmeticOverrideService() {}

    static boolean definitionAllowsOverride(int itemId) {
        for (int i=0;i<5;i++) {
            String a=ItemActionResolver.inventoryAction(itemId,i);
            if (a!=null && a.equalsIgnoreCase("Override")) return true;
        }
        return false;
    }

    static String apply(BankState bank, int slot, int itemId, CosmeticState cosmetic, ServerPacketWriter out) throws IOException {
        if (bank==null || cosmetic==null) return "REJECTED_NO_COSMETIC_STATE";
        if (!definitionAllowsOverride(itemId)) return "REJECTED_NO_OVERRIDE_ACTION item="+itemId;
        BankState.Stack st=bank.inventoryAt(slot);
        if (st==null || st.itemId!=itemId || st.qty<=0) return "REJECTED_INVENTORY_MISMATCH";

        int old=cosmetic.itemId();
        String consumed=bank.consumeInventoryOne(slot,itemId,out);
        if (!consumed.startsWith("INVENTORY_CONSUME_OK")) return "REJECTED_CONSUME_FAILED "+consumed;

        int returnedSlot=-1;
        if (old>=0) {
            returnedSlot=bank.addInventoryOnePreferred(old,slot,out);
            if (returnedSlot<0) {
                int rollback=bank.addInventoryOnePreferred(itemId,slot,out);
                return "REJECTED_INVENTORY_FULL_ROLLBACK old="+old+" rollbackSlot="+rollback;
            }
        }
        cosmetic.set(itemId);
        return "COSMETIC_OVERRIDE_OK item="+itemId+" previous="+old+" sourceSlot="+slot+" returnedOldSlot="+returnedSlot;
    }
}
