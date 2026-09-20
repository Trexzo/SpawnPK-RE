package spk.local;

import java.io.IOException;

/**
 * Typed item-on-item gameplay adapter.
 *
 * Raw opcode-53 decoding remains in ClientPacketProbe. This handler preserves
 * the current narrow recovered/custom semantic surface and fails closed for all
 * other item pairs.
 */
final class LocalItemOnItemHandler {
    private final BankState bank;

    LocalItemOnItemHandler(BankState bank){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
    }

    Result handle(ItemOnItemAction action,ServerPacketWriter serverPackets)throws IOException{
        if(action==null)return null;

        if(action.selectedWidget!=BankState.NORMAL_INVENTORY_CONTAINER ||
           action.targetWidget!=BankState.NORMAL_INVENTORY_CONTAINER){
            return new Result(
                "V57_ITEM_ON_ITEM "+action+" result=DECODED_UNSUPPORTED_WIDGET",
                null
            );
        }

        boolean doppelPair=
            (action.selectedItemId==28824&&action.targetItemId==3241)||
            (action.selectedItemId==3241&&action.targetItemId==28824);

        if(doppelPair){
            String result=bank.combineInventoryOne(
                action.selectedSlot,
                action.selectedItemId,
                action.targetSlot,
                action.targetItemId,
                28807,
                serverPackets
            );
            String saveReason=result.startsWith("INVENTORY_COMBINE_OK")
                ?"DOPPELGANGER_APPLY_DYE"
                :null;

            return new Result(
                "V57_DOPPELGANGER_APPLY_DYE "+action+
                " result="+result+" resultItem=28807",
                saveReason
            );
        }

        return new Result(
            "V57_ITEM_ON_ITEM "+action+" result=DECODED_NO_SEMANTIC_HANDLER",
            null
        );
    }

    static final class Result {
        final String logText;
        final String saveReason;

        Result(String logText,String saveReason){
            this.logText=logText;
            this.saveReason=saveReason;
        }
    }
}
