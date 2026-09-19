package spk.local;

import java.io.*;

/** Exact current equipment root exposes cosmetic item container widget 27701 independently of 1688/ammo. */
public final class CosmeticEquipmentWidgetTest {
    public static void main(String[] args)throws Exception{
        BankState b=new BankState(); CosmeticState cosmetic=new CosmeticState(); cosmetic.set(27454);
        int[] seed={9,8,7,6}; IsaacCipher expectedCipher=new IsaacCipher(seed.clone());
        ByteArrayOutputStream out=new ByteArrayOutputStream(); ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(seed.clone()));
        b.sendCosmetic(w,cosmetic);
        byte[] raw=out.toByteArray();
        if(raw.length<10)throw new AssertionError("short packet "+raw.length);
        int opcode=((raw[0]&255)-expectedCipher.nextInt())&255; if(opcode!=53)throw new AssertionError("opcode="+opcode);
        int len=((raw[1]&255)<<8)|(raw[2]&255); if(len!=raw.length-3)throw new AssertionError("len="+len+" raw="+raw.length);
        int widget=((raw[3]&255)<<8)|(raw[4]&255); if(widget!=27701)throw new AssertionError("widget="+widget);
        int slots=((raw[5]&255)<<8)|(raw[6]&255); if(slots!=1)throw new AssertionError("slots="+slots);
        int qty=raw[7]&255; if(qty!=1)throw new AssertionError("qty="+qty);
        int low=((raw[8]&255)-128)&255, high=raw[9]&255; int itemPlus1=low|(high<<8); if(itemPlus1-1!=27454)throw new AssertionError("item="+(itemPlus1-1));
        System.out.println("V5124_COSMETIC_EQUIPMENT_WIDGET_PASS widget27701=true item27454=true independentContainer=true");
    }
}
