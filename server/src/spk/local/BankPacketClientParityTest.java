package spk.local;

import java.lang.reflect.*;

/** Verifies v3 bank S2C payloads with the pinned client's actual rs.x.e readers. */
public final class BankPacketClientParityTest {
    public static void main(String[] args) throws Exception {
        Class<?> c=Class.forName("rs.x.e");

        byte[] open=BootstrapPackets.interface97(5292);
        Object b=c.getConstructor(byte[].class).newInstance((Object)open);
        int root=((Number)c.getMethod("A").invoke(b)).intValue();
        int consumed=c.getField("h").getInt(b);
        if(root!=5292 || consumed!=2) throw new AssertionError("packet97 root="+root+" consumed="+consumed);

        int[] ids={995,560,4151};
        int[] qty={100000,2500,1};
        byte[] payload=BootstrapPackets.itemContainer53(5382,ids,qty);
        b=c.getConstructor(byte[].class).newInstance((Object)payload);
        int widget=((Number)c.getMethod("A").invoke(b)).intValue();
        int count=((Number)c.getMethod("A").invoke(b)).intValue();
        if(widget!=5382 || count!=3) throw new AssertionError("widget/count "+widget+"/"+count);
        for(int i=0;i<count;i++){
            int q=((Number)c.getMethod("y").invoke(b)).intValue();
            if(q==255) q=((Number)c.getMethod("Y").invoke(b)).intValue();
            int wireItem=((Number)c.getMethod("U").invoke(b)).intValue();
            int item=wireItem-1;
            if(q!=qty[i] || item!=ids[i]) throw new AssertionError("slot="+i+" q="+q+" item="+item);
        }
        consumed=c.getField("h").getInt(b);
        if(consumed!=payload.length) throw new AssertionError("packet53 consumed="+consumed+" len="+payload.length);
        System.out.println("V3_BANK_CLIENT_PARSER_PARITY_PASS root97=5292 container53=5382 slots=3 qty32=100000 consumed="+consumed);
    }
}
