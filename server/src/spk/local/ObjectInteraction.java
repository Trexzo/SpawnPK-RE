package spk.local;

final class ObjectInteraction {
    final int opcode;
    final int objectId;
    final int worldX;
    final int worldY;

    ObjectInteraction(int opcode, int objectId, int worldX, int worldY) {
        this.opcode = opcode;
        this.objectId = objectId;
        this.worldX = worldX;
        this.worldY = worldY;
    }

    @Override public String toString() {
        return "ObjectInteraction{opcode="+opcode+", objectId="+objectId+", worldX="+worldX+", worldY="+worldY+"}";
    }
}
