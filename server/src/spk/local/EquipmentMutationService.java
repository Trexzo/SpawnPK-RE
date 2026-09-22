package spk.local;

import java.util.Objects;

/**
 * Semantic protocol-independent compare-and-set mutation over canonical
 * EquipmentState.
 */
final class EquipmentMutationService {
    static final class SlotSnapshot {
        final EquipmentSlot slot;
        final boolean occupied;
        final int itemId;
        final int quantity;
        final String policyAuthority;

        private SlotSnapshot(
            EquipmentSlot slot,
            int itemId,
            int quantity,
            String policyAuthority
        ){
            this.slot=slot;
            this.occupied=itemId>=0;
            this.itemId=itemId;
            this.quantity=itemId>=0?quantity:0;
            this.policyAuthority=policyAuthority;
        }
    }

    static final class ReplaceResult {
        final EquipmentSlot slot;
        final int previousItemId;
        final int previousQuantity;
        final int nextItemId;
        final int nextQuantity;
        final String policyAuthority;

        private ReplaceResult(
            EquipmentSlot slot,
            int previousItemId,
            int previousQuantity,
            int nextItemId,
            int nextQuantity,
            String policyAuthority
        ){
            this.slot=slot;
            this.previousItemId=previousItemId;
            this.previousQuantity=previousQuantity;
            this.nextItemId=nextItemId;
            this.nextQuantity=nextQuantity;
            this.policyAuthority=policyAuthority;
        }

        boolean cleared(){
            return nextItemId<0;
        }
    }

    private final EquipmentState equipment;
    private final String policyAuthority;

    EquipmentMutationService(
        EquipmentState equipment,
        String policyAuthority
    ){
        this.equipment=
            Objects.requireNonNull(
                equipment,
                "equipment"
            );
        this.policyAuthority=
            requireGameplayAuthority(
                policyAuthority
            );
    }

    synchronized SlotSnapshot inspect(
        EquipmentSlot slot
    ){
        EquipmentSlot checked=
            Objects.requireNonNull(
                slot,
                "slot"
            );

        return new SlotSnapshot(
            checked,
            equipment.itemAt(checked),
            equipment.quantityAt(checked),
            policyAuthority
        );
    }

    synchronized ReplaceResult replace(
        EquipmentSlot slot,
        int expectedItemId,
        int expectedQuantity,
        int nextItemId,
        int nextQuantity
    ){
        EquipmentSlot checked=
            Objects.requireNonNull(
                slot,
                "slot"
            );

        validateState(
            expectedItemId,
            expectedQuantity,
            "expected"
        );
        validateState(
            nextItemId,
            nextQuantity,
            "next"
        );

        int currentItem=
            equipment.itemAt(
                checked
            );
        int currentQuantity=
            equipment.quantityAt(
                checked
            );

        if(currentItem!=expectedItemId||
           currentQuantity!=expectedQuantity)
            throw new IllegalStateException(
                "equipment compare-and-set mismatch slot="+
                checked+
                " expected="+
                expectedItemId+"x"+expectedQuantity+
                " actual="+
                currentItem+"x"+currentQuantity
            );

        equipment.setStack(
            checked,
            nextItemId,
            nextQuantity
        );

        int observedItem=
            equipment.itemAt(
                checked
            );
        int observedQuantity=
            equipment.quantityAt(
                checked
            );

        if(observedItem!=nextItemId||
           observedQuantity!=(nextItemId<0?0:nextQuantity))
            throw new IllegalStateException(
                "equipment replacement write mismatch slot="+
                checked
            );

        return new ReplaceResult(
            checked,
            currentItem,
            currentQuantity,
            observedItem,
            observedQuantity,
            policyAuthority
        );
    }

    synchronized ReplaceResult remove(
        EquipmentSlot slot,
        int expectedItemId,
        int expectedQuantity
    ){
        return replace(
            slot,
            expectedItemId,
            expectedQuantity,
            -1,
            0
        );
    }

    String policyAuthority(){
        return policyAuthority;
    }

    private static void validateState(
        int itemId,
        int quantity,
        String field
    ){
        if(itemId<0){
            if(itemId!=-1||quantity!=0)
                throw new IllegalArgumentException(
                    field+
                    " empty state must be item=-1 quantity=0"
                );
            return;
        }

        if(quantity<=0)
            throw new IllegalArgumentException(
                field+
                " non-empty quantity must be positive"
            );
    }

    private static String requireGameplayAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "policyAuthority"
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "policyAuthority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define equipment mutation policy actual="+
                clean
            );

        return clean;
    }
}
