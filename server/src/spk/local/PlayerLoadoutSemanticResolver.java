package spk.local;

/**
 * Server-owned semantic resolver used when materializing PlayerLoadout desired
 * state. No packet/widget/client-slot identity belongs here.
 */
interface PlayerLoadoutSemanticResolver {
    static final class Item {
        final int itemId;
        final boolean stackable;

        Item(
            int itemId,
            boolean stackable
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId="+itemId
                );

            this.itemId=itemId;
            this.stackable=stackable;
        }
    }

    Item resolveItem(String itemKey);

    EquipmentSlot resolveEquipmentSlot(
        String slotKey
    );

    String authority();
}
