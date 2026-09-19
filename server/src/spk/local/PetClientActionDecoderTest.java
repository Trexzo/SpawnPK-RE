package spk.local;

import java.io.*;

/** Exact C2S pet lifecycle action decoding: opcode87 Drop + opcode155 NPC first option. */
public final class PetClientActionDecoderTest {
    public static void main(String[] args)throws Exception{
        int[] seed={11,22,33,44};
        ByteArrayOutputStream wire=new ByteArrayOutputStream(); IsaacCipher enc=new IsaacCipher(seed.clone());
        // 20776 / widget3214 / slot4 using exact o,d,o writers.
        wire.write((87+enc.nextInt())&255); putBEA(wire,20776); putBE(wire,3214); putBEA(wire,4);
        wire.write((155+enc.nextInt())&255); putLE(wire,4);
        ClientPacketProbe p=new ClientPacketProbe(new ByteArrayInputStream(wire.toByteArray()),new IsaacCipher(seed.clone()),"[pet-decoder] ");
        if(!p.readNextKnownPacket())throw new AssertionError();
        DropItemAction d=p.takeDropItem();
        if(d==null||d.itemId!=20776||d.widgetId!=3214||d.slot!=4)throw new AssertionError(String.valueOf(d));
        if(!p.readNextKnownPacket())throw new AssertionError();
        NpcAction n=p.takeNpcAction();
        if(n==null||n.opcode!=155||n.sceneIndex!=4)throw new AssertionError(String.valueOf(n));
        if(!p.isAligned())throw new AssertionError("decoder unaligned");
        System.out.println("V53_PET_CLIENT_ACTION_DECODER_PASS opcode87=20776/3214/4 opcode155=scene4 aligned=true");
    }
    private static void putBE(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write(v);}
    private static void putBEA(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write((v+128)&255);}
    private static void putLE(OutputStream o,int v)throws IOException{o.write(v);o.write(v>>>8);}
}
