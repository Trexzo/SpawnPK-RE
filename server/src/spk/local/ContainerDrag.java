package spk.local;

final class ContainerDrag {
    final int widgetId;
    final int mode;
    final int sourceSlot;
    final int destinationSlot;

    ContainerDrag(int widgetId, int mode, int sourceSlot, int destinationSlot) {
        this.widgetId = widgetId;
        this.mode = mode;
        this.sourceSlot = sourceSlot;
        this.destinationSlot = destinationSlot;
    }

    @Override public String toString() {
        return "ContainerDrag{widgetId="+widgetId+", mode="+mode+", sourceSlot="+sourceSlot
             + ", destinationSlot="+destinationSlot+"}";
    }
}
