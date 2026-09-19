package spk.local;

/** Exact current-client opcode-87 inventory Drop action. */
final class DropItemAction {
    final int itemId;
    final int widgetId;
    final int slot;
    DropItemAction(int itemId, int widgetId, int slot) {
        this.itemId=itemId; this.widgetId=widgetId; this.slot=slot;
    }
    @Override public String toString() {
        return "DropItemAction{itemId="+itemId+",widgetId="+widgetId+",slot="+slot+"}";
    }
}
