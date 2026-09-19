package spk.local;

final class GroundItemInteraction {
    final int opcode,option,itemId,worldX,worldY;
    GroundItemInteraction(int opcode,int option,int itemId,int worldX,int worldY){this.opcode=opcode;this.option=option;this.itemId=itemId;this.worldX=worldX;this.worldY=worldY;}
    @Override public String toString(){return "GroundItemInteraction{opcode="+opcode+",option="+option+",item="+itemId+",world="+worldX+","+worldY+"}";}
}
