package spk.local;
public final class PositionRestorePacket81ProjectionTest {
    private static final class Bits {
        private final byte[] data; private int bit;
        Bits(byte[] data){this.data=data;}
        int read(int n){int v=0;for(int i=0;i<n;i++){int b=(data[bit>>>3] >>> (7-(bit&7))) & 1;v=(v<<1)|b;bit++;}return v;}
    }
    public static void main(String[] args)throws Exception{
        int worldX=3104, worldY=3506;
        int localX=worldX-MovementState.REGION_BASE_X;
        int localY=worldY-MovementState.REGION_BASE_Y;
        if(localX==localY) throw new AssertionError("fixture must be asymmetric");
        // Exact current-client teleport grammar consumes the first 7-bit coordinate as Y
        // and the second as X when it finally places the actor. Therefore the serializer
        // must receive Y first, X second.
        byte[] payload=BootstrapPackets.player81TeleportNoAppearance(0,localY,localX);
        Bits b=new Bits(payload);
        if(b.read(1)!=1)throw new AssertionError("local update bit");
        if(b.read(2)!=3)throw new AssertionError("teleport move type");
        int plane=b.read(2); if(plane!=0)throw new AssertionError("plane="+plane);
        b.read(1); // placement/reset queue flag
        int mask=b.read(1); if(mask!=0)throw new AssertionError("unexpected local mask="+mask);
        int first7=b.read(7);
        int second7=b.read(7);
        int decodedX=second7;
        int decodedY=first7;
        if(decodedX!=localX||decodedY!=localY)throw new AssertionError("decoded local="+decodedX+","+decodedY+" expected="+localX+","+localY);
        int remoteCount=b.read(8); if(remoteCount!=0)throw new AssertionError("remoteCount="+remoteCount);
        int sentinel=b.read(11); if(sentinel!=2047)throw new AssertionError("sentinel="+sentinel);
        if(MovementState.REGION_BASE_X+decodedX!=worldX || MovementState.REGION_BASE_Y+decodedY!=worldY)
            throw new AssertionError("world projection mismatch");
        System.out.println("V51214_POSITION_RESTORE_PACKET81_XY_PASS world=3104,3506 localX="+localX+" localY="+localY+" wireFirstY="+first7+" wireSecondX="+second7+" clientDecodedX="+decodedX+" clientDecodedY="+decodedY);
    }
}
