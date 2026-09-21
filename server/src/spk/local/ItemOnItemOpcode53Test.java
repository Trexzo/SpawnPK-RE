package spk.local;
import java.io.*;
public final class ItemOnItemOpcode53Test {
 public static void main(String[] args)throws Exception{
  int[] seed={5,7,11,13};IsaacCipher enc=new IsaacCipher(seed.clone());ByteArrayOutputStream wire=new ByteArrayOutputStream();wire.write((53+enc.nextInt())&255);
  // targetSlot=6, selectedSlot=2, targetItem=3241, selectedWidget=3214, selectedItem=28824, targetWidget=3214
  putBE(wire,6);putBEA(wire,2);putLEA(wire,3241);putBE(wire,3214);putLE(wire,28824);putBE(wire,3214);
  ClientPacketProbe p=new ClientPacketProbe(new ByteArrayInputStream(wire.toByteArray()),new IsaacCipher(seed.clone()),"[t] ");if(!p.readNextKnownPacket())throw new AssertionError();ClientRequest r=p.takeTypedRequest();if(!(r instanceof ItemOnItemClientRequest))throw new AssertionError(String.valueOf(r));ItemOnItemClientRequest typed=(ItemOnItemClientRequest)r;ItemOnItemAction a=typed.action();if(a.targetSlot!=6||a.selectedSlot!=2||a.targetItemId!=3241||a.selectedItemId!=28824||a.selectedWidget!=3214||a.targetWidget!=3214)throw new AssertionError(String.valueOf(a));ClientRequestMetadata m=typed.metadata();if(m.opcode!=53||!"FIXED12_TARGET_SLOT_BE_SELECTED_SLOT_BE_A_TARGET_ITEM_LE_A_SELECTED_WIDGET_BE_SELECTED_ITEM_LE_TARGET_WIDGET_BE".equals(m.schema)||!"PINNED_CLIENT_ITEM_ON_ITEM_WRITER".equals(m.source)||m.provenance!=ClientRequestProvenance.EXACT_CURRENT_CLIENT)throw new AssertionError(String.valueOf(m));if(p.takeTypedRequest()!=null)throw new AssertionError("queue not empty");
  System.out.println("V57_OPCODE53_ITEM_ON_ITEM_PASS fixed12=true dye28824_on_doppel3241_decode=true typedQueue=true metadata=true");
 }
 static void putBE(OutputStream o,int v)throws Exception{o.write(v>>>8);o.write(v);}static void putLE(OutputStream o,int v)throws Exception{o.write(v);o.write(v>>>8);}static void putBEA(OutputStream o,int v)throws Exception{o.write(v>>>8);o.write(v+128);}static void putLEA(OutputStream o,int v)throws Exception{o.write(v+128);o.write(v>>>8);}
}
