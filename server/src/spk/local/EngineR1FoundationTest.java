package spk.local;

import java.io.*;

public final class EngineR1FoundationTest {
    public static void main(String[] args)throws Exception{
        byte[] x=new PacketPayloadWriter().putU8Neg(19).putU8_128Minus(24).putU16LELowAdd128(995).putU16BE(7).toByteArray();
        if(x.length!=6||(x[0]&255)!=237||(x[1]&255)!=104||(x[2]&255)!=99||(x[3]&255)!=3)throw new AssertionError("payload transforms");

        ByteArrayOutputStream out=new ByteArrayOutputStream();int[] seed={1,2,3,4};
        ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(seed.clone()));
        SceneCoordinateContext ctx=new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0);
        SceneUpdatePublisher pub=new SceneUpdatePublisher(w,ctx);
        GroundItem g=new GroundItem(1,995,7,new Tile(MovementState.REGION_BASE_X+55,MovementState.REGION_BASE_Y+55,0),"opensrc",0,false);
        pub.groundSpawn(g);
        byte[] wire=out.toByteArray();
        IsaacCipher dec=new IsaacCipher(seed.clone());int p=0;
        int op1=((wire[p++]&255)-dec.nextInt())&255;if(op1!=85)throw new AssertionError("expected 85 got "+op1);p+=2;
        int op2=((wire[p++]&255)-dec.nextInt())&255;if(op2!=44)throw new AssertionError("expected 44 got "+op2);p+=5;
        if(p!=wire.length)throw new AssertionError("wire length "+wire.length+" consumed "+p);
        out.reset();pub.groundRemove(g);wire=out.toByteArray();dec=new IsaacCipher(seed.clone());
        // decoder seed must be advanced over the two packets already emitted by writer
        dec.nextInt();dec.nextInt();
        int op3=((wire[0]&255)-dec.nextInt())&255;if(op3!=156)throw new AssertionError("expected 156 got "+op3);

        ByteArrayOutputStream vb=new ByteArrayOutputStream();int[] seed2={9,8,7,6};
        ServerPacketWriter vw=new ServerPacketWriter(vb,new IsaacCipher(seed2.clone()));vw.varByte(104,new byte[]{1,2,3});
        byte[] v=vb.toByteArray();IsaacCipher vd=new IsaacCipher(seed2.clone());if((((v[0]&255)-vd.nextInt())&255)!=104||(v[1]&255)!=3)throw new AssertionError("varByte");
        if(World.shared().groundItems()==null||World.shared().events()==null||World.shared().objects()==null)throw new AssertionError("world skeleton");
        System.out.println("V511_ENGINE_R1_FOUNDATION_PASS varByte=true packetPayloadWriter=true sceneBase85Before44=true worldSkeleton=true");
    }
}
