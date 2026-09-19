package spk.local;

/**
 * Definition-driven semantic view of the client's five ordinary inventory options.
 *
 * Wire opcodes are transports, not semantics.  The clicked option is resolved back
 * through the exact current ItemCatalog action array before gameplay routing.
 */
final class InventoryActionRouter {
    static final class Resolution {
        final int opcode;
        final int optionIndex; // 0-based item-definition action slot
        final int itemId;
        final String action;

        Resolution(int opcode, int optionIndex, int itemId, String action) {
            this.opcode = opcode;
            this.optionIndex = optionIndex;
            this.itemId = itemId;
            this.action = action;
        }

        boolean resolved() { return optionIndex >= 0 && action != null && !action.trim().isEmpty(); }
        boolean is(String expected) { return action != null && action.equalsIgnoreCase(expected); }

        public String toString() {
            return "Resolution{opcode=" + opcode + ",option=" + (optionIndex + 1) +
                   ",itemId=" + itemId + ",action=" + action + "}";
        }
    }

    private InventoryActionRouter() {}

    static int optionIndexForOpcode(int opcode) {
        switch (opcode) {
            case 122: return 0; // inventory option 1
            case 41:  return 1; // inventory option 2
            case 16:  return 2; // inventory option 3
            case 75:  return 3; // inventory option 4
            case 87:  return 4; // inventory option 5
            default:  return -1;
        }
    }

    static Resolution resolve(ItemContainerAction action) {
        int index = optionIndexForOpcode(action.opcode);
        if (index < 0) return new Resolution(action.opcode, -1, action.itemId, null);
        String semantic = index == 4
            ? ItemActionResolver.inventoryOption5Semantic(action.itemId)
            : ItemActionResolver.inventoryAction(action.itemId, index);
        return new Resolution(action.opcode, index, action.itemId, semantic);
    }
}
