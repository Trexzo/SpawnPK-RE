package spk.local;

final class SpellTargetRequest {
    enum Kind {
        GROUND_ITEM(1), NPC(2), OBJECT(4), PLAYER(8), INVENTORY_ITEM(16);
        final int mask; Kind(int mask){this.mask=mask;}
    }
    final int opcode;
    final Kind kind;
    final int spellWidget;
    final int targetIndex;
    final int targetId;
    final int targetWidget;
    final int targetSlot;
    final int worldX,worldY;

    SpellTargetRequest(int opcode,Kind kind,int spellWidget,int targetIndex,int targetId,int targetWidget,int targetSlot,int worldX,int worldY){
        this.opcode=opcode;this.kind=kind;this.spellWidget=spellWidget;this.targetIndex=targetIndex;this.targetId=targetId;
        this.targetWidget=targetWidget;this.targetSlot=targetSlot;this.worldX=worldX;this.worldY=worldY;
    }
    @Override public String toString(){
        return "SpellTargetRequest{opcode="+opcode+",kind="+kind+",spell="+spellWidget+",targetIndex="+targetIndex+
            ",targetId="+targetId+",targetWidget="+targetWidget+",targetSlot="+targetSlot+",world="+worldX+","+worldY+"}";
    }
}
