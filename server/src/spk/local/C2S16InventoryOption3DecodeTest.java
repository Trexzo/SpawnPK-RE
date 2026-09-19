package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class C2S16InventoryOption3DecodeTest {
    private static void req(boolean ok, String msg){ if(!ok) throw new AssertionError(msg); }
    private static void putBeA(ByteArrayOutputStream o,int v){ o.write((v>>>8)&255); o.write(((v&255)+128)&255); }
    private static void putLeA(ByteArrayOutputStream o,int v){ o.write(((v&255)+128)&255); o.write((v>>>8)&255); }
    public static void main(String[] args) throws Exception {
        int[] seed={0x01020304,0x11223344,0x01234567,0x89abcdef};
        IsaacCipher enc=new IsaacCipher(seed.clone());
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        wire.write((16 + enc.nextInt()) & 255);
        putBeA(wire,22132); // itemId
        putLeA(wire,7);     // slot
        putLeA(wire,3214);  // widget

        ClientPacketProbe p=new ClientPacketProbe(new ByteArrayInputStream(wire.toByteArray()),new IsaacCipher(seed.clone()),"[c2s16-test] ");
        req(p.readNextKnownPacket(),"packet not decoded");
        req(p.isAligned(),"decoder lost alignment");
        ItemContainerAction a=p.takeItemAction();
        req(a!=null,"missing item action");
        req(a.opcode==16,"opcode="+a.opcode);
        req(a.widgetId==3214,"widget="+a.widgetId);
        req(a.slot==7,"slot="+a.slot);
        req(a.itemId==22132,"item="+a.itemId);
        InventoryActionRouter.Resolution r=InventoryActionRouter.resolve(a);
        req(r.is("Override"),"semantic="+r);
        System.out.println("V51842_C2S16_DECODE_PASS item22132=true slot7=true widget3214=true transform=BE_A_LE_A_LE_A semantic=Override aligned=true");
    }
}
