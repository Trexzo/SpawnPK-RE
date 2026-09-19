package spk.local;

final class ItemContainerAction {
    final int opcode;
    final int widgetId;
    final int slot;
    final int itemId;
    final int extra;
    final String semantic;

    ItemContainerAction(int opcode, int widgetId, int slot, int itemId, int extra, String semantic) {
        this.opcode = opcode;
        this.widgetId = widgetId;
        this.slot = slot;
        this.itemId = itemId;
        this.extra = extra;
        this.semantic = semantic;
    }

    @Override public String toString() {
        return "ItemContainerAction{opcode="+opcode+", widgetId="+widgetId+", slot="+slot
             + ", itemId="+itemId+", semantic="+semantic+(extra!=0?", extra="+extra:"")+"}";
    }
}
