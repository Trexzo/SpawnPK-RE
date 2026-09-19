package spk.local;

/** Exact current-client opcode-53 inventory item-on-item action. */
final class ItemOnItemAction {
    final int targetSlot,selectedSlot,targetItemId,selectedWidget,selectedItemId,targetWidget;
    ItemOnItemAction(int targetSlot,int selectedSlot,int targetItemId,int selectedWidget,int selectedItemId,int targetWidget){
        this.targetSlot=targetSlot; this.selectedSlot=selectedSlot; this.targetItemId=targetItemId;
        this.selectedWidget=selectedWidget; this.selectedItemId=selectedItemId; this.targetWidget=targetWidget;
    }
    @Override public String toString(){return "ItemOnItemAction{selected="+selectedItemId+"@"+selectedSlot+"/"+selectedWidget+
        ",target="+targetItemId+"@"+targetSlot+"/"+targetWidget+"}";}
}
