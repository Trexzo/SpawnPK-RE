package spk.local;

/** Exact-current generic client interaction decoded from a C2S packet.
 *  This is transport/trigger authority only. Unknown production outcomes remain fail-closed.
 */
final class GenericInteractionEvent {
    enum Family { ITEM_ON_PLAYER, ITEM_ON_GROUND_ITEM, OBJECT_OPTION, WIDGET_ITEM_OPTION, ITEM_ON_OBJECT }
    final Family family;
    final int opcode;
    final int option;
    final int selectedWidget;
    final int selectedSlot;
    final int selectedItemId;
    final int targetId;
    final int worldX;
    final int worldY;
    final int widgetId;
    final int slot;
    final int itemId;

    private GenericInteractionEvent(Family family,int opcode,int option,int selectedWidget,int selectedSlot,int selectedItemId,
                                    int targetId,int worldX,int worldY,int widgetId,int slot,int itemId){
        this.family=family;this.opcode=opcode;this.option=option;this.selectedWidget=selectedWidget;this.selectedSlot=selectedSlot;
        this.selectedItemId=selectedItemId;this.targetId=targetId;this.worldX=worldX;this.worldY=worldY;
        this.widgetId=widgetId;this.slot=slot;this.itemId=itemId;
    }
    static GenericInteractionEvent itemOnPlayer(int opcode,int widget,int slot,int selectedItem,int playerIndex){
        return new GenericInteractionEvent(Family.ITEM_ON_PLAYER,opcode,0,widget,slot,selectedItem,playerIndex,-1,-1,-1,-1,-1);
    }
    static GenericInteractionEvent itemOnGround(int opcode,int widget,int slot,int selectedItem,int targetItem,int x,int y){
        return new GenericInteractionEvent(Family.ITEM_ON_GROUND_ITEM,opcode,0,widget,slot,selectedItem,targetItem,x,y,-1,-1,-1);
    }
    static GenericInteractionEvent objectOption(int opcode,int option,int objectId,int x,int y){
        return new GenericInteractionEvent(Family.OBJECT_OPTION,opcode,option,-1,-1,-1,objectId,x,y,-1,-1,-1);
    }
    static GenericInteractionEvent widgetItemOption(int opcode,int option,int widget,int slot,int item){
        return new GenericInteractionEvent(Family.WIDGET_ITEM_OPTION,opcode,option,-1,-1,-1,-1,-1,-1,widget,slot,item);
    }
    static GenericInteractionEvent itemOnObject(int opcode,int widget,int slot,int selectedItem,int objectId,int x,int y){
        return new GenericInteractionEvent(Family.ITEM_ON_OBJECT,opcode,0,widget,slot,selectedItem,objectId,x,y,-1,-1,-1);
    }
    public String toString(){
        switch(family){
            case ITEM_ON_PLAYER:return "GenericInteractionEvent{ITEM_ON_PLAYER opcode="+opcode+",selected="+selectedItemId+"@"+selectedSlot+"/"+selectedWidget+",playerIndex="+targetId+"}";
            case ITEM_ON_GROUND_ITEM:return "GenericInteractionEvent{ITEM_ON_GROUND_ITEM opcode="+opcode+",selected="+selectedItemId+"@"+selectedSlot+"/"+selectedWidget+",targetItem="+targetId+",world="+worldX+","+worldY+"}";
            case OBJECT_OPTION:return "GenericInteractionEvent{OBJECT_OPTION_"+option+" opcode="+opcode+",objectId="+targetId+",world="+worldX+","+worldY+"}";
            case WIDGET_ITEM_OPTION:return "GenericInteractionEvent{WIDGET_ITEM_OPTION_"+option+" opcode="+opcode+",widget="+widgetId+",slot="+slot+",itemId="+itemId+"}";
            case ITEM_ON_OBJECT:return "GenericInteractionEvent{ITEM_ON_OBJECT opcode="+opcode+",selected="+selectedItemId+"@"+selectedSlot+"/"+selectedWidget+",objectId="+targetId+",world="+worldX+","+worldY+"}";
            default:return family+" opcode="+opcode;
        }
    }
}
