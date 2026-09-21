package spk.local;

import java.io.*;
import java.util.*;

/** Exact C2S bank action serializer/decoder parity for all seven bank menu actions. */
public final class BankClientActionDecoderTest {
    public static void main(String[] args) throws Exception {
        int[] seed={7,11,13,17};
        IsaacCipher enc=new IsaacCipher(seed.clone());
        ByteArrayOutputStream w=new ByteArrayOutputStream();
        int widget=5382, slot=2, item=565;
        send145(w,enc,widget,slot,item);
        send117(w,enc,widget,slot,item);
        send43(w,enc,widget,slot,item);
        send129(w,enc,widget,slot,item);
        send135(w,enc,widget,slot,item);
        send141(w,enc,widget,slot,item,14);
        send140(w,enc,widget,slot,item);

        ClientPacketProbe p=new ClientPacketProbe(new ByteArrayInputStream(w.toByteArray()),new IsaacCipher(seed.clone()),"[bank-decoder-test] ");
        int[] ops={145,117,43,129,135,141,140};
        String[] sem={"WITHDRAW_1","WITHDRAW_5","WITHDRAW_10","WITHDRAW_ALL","WITHDRAW_X","WITHDRAW_14","WITHDRAW_ALL_BUT_ONE"};
        String[] schemas={
            "FIXED6_WIDGET_BE_A_SLOT_BE_A_ITEM_BE_A",
            "FIXED6_WIDGET_LE_A_ITEM_LE_A_SLOT_LE",
            "FIXED6_WIDGET_LE_ITEM_BE_A_SLOT_BE_A",
            "FIXED6_SLOT_BE_A_WIDGET_BE_ITEM_BE_A",
            "FIXED6_SLOT_LE_WIDGET_BE_A_ITEM_LE",
            "FIXED10_SLOT_BE_A_WIDGET_BE_ITEM_BE_A_EXTRA_BE32",
            "FIXED6_SLOT_BE_A_WIDGET_BE_ITEM_BE_A"
        };
        String[] sources={
            "PINNED_CLIENT_MENU_ACTION_632_WRITER",
            "PINNED_CLIENT_MENU_ACTION_78_WRITER",
            "PINNED_CLIENT_MENU_ACTION_867_WRITER",
            "PINNED_CLIENT_MENU_ACTION_431_WRITER",
            "PINNED_CLIENT_MENU_ACTION_53_WRITER",
            "PINNED_CLIENT_MENU_ACTION_300_WRITER",
            "PINNED_CLIENT_MENU_ACTION_291_WRITER"
        };
        for(int i=0;i<ops.length;i++){
            if(!p.readNextKnownPacket())throw new AssertionError("stopped at "+ops[i]);
            ClientRequest request=p.takeTypedRequest();
            if(!(request instanceof ItemContainerActionClientRequest))
                throw new AssertionError("typed item request missing op="+ops[i]+" got="+request);
            ItemContainerAction a=((ItemContainerActionClientRequest)request).action();
            if(a.opcode!=ops[i]||a.widgetId!=widget||a.slot!=slot||a.itemId!=item||!sem[i].equals(a.semantic))
                throw new AssertionError("mismatch op="+ops[i]+" got="+a);
            if(ops[i]==141 && a.extra!=14)throw new AssertionError("withdraw14 extra="+a.extra);
            ClientRequestMetadata metadata=request.metadata();
            if(metadata.opcode!=ops[i]||
               !schemas[i].equals(metadata.schema)||
               !sources[i].equals(metadata.source)||
               metadata.provenance!=ClientRequestProvenance.EXACT_CURRENT_CLIENT)
                throw new AssertionError("metadata op="+ops[i]+" got="+metadata);
        }
        if(!p.isAligned()||p.decodedCount()!=7)throw new AssertionError("alignment/count");
        System.out.println("V3_BANK_CLIENT_ACTION_DECODER_PASS opcodes=145,117,43,129,135,141,140 aligned=true");
    }
    private static void op(OutputStream o,IsaacCipher c,int v)throws IOException{o.write((v+c.nextInt())&255);}
    private static void be(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write(v);}
    private static void le(OutputStream o,int v)throws IOException{o.write(v);o.write(v>>>8);}
    private static void beA(OutputStream o,int v)throws IOException{o.write(v>>>8);o.write((v+128)&255);}
    private static void leA(OutputStream o,int v)throws IOException{o.write((v+128)&255);o.write(v>>>8);}
    private static void be32(OutputStream o,int v)throws IOException{o.write(v>>>24);o.write(v>>>16);o.write(v>>>8);o.write(v);}
    private static void send145(OutputStream o,IsaacCipher c,int w,int s,int i)throws IOException{op(o,c,145);beA(o,w);beA(o,s);beA(o,i);}
    private static void send117(OutputStream o,IsaacCipher c,int w,int s,int i)throws IOException{op(o,c,117);leA(o,w);leA(o,i);le(o,s);}
    private static void send43(OutputStream o,IsaacCipher c,int w,int s,int i)throws IOException{op(o,c,43);le(o,w);beA(o,i);beA(o,s);}
    private static void send129(OutputStream o,IsaacCipher c,int w,int s,int i)throws IOException{op(o,c,129);beA(o,s);be(o,w);beA(o,i);}
    private static void send135(OutputStream o,IsaacCipher c,int w,int s,int i)throws IOException{op(o,c,135);le(o,s);beA(o,w);le(o,i);}
    private static void send140(OutputStream o,IsaacCipher c,int w,int s,int i)throws IOException{op(o,c,140);beA(o,s);be(o,w);beA(o,i);}
    private static void send141(OutputStream o,IsaacCipher c,int w,int s,int i,int extra)throws IOException{op(o,c,141);beA(o,s);be(o,w);beA(o,i);be32(o,extra);}
}
