package spk.local;
public final class R85GenericC2SDecodeTest {
 private static void eq(int a,int b,String n){if(a!=b)throw new AssertionError(n+" "+a+" != "+b);}
 private static byte[] cat(int...v){byte[]b=new byte[v.length];for(int i=0;i<v.length;i++)b[i]=(byte)v[i];return b;}
 private static int hi(int x){return x>>>8&255;} private static int lo(int x){return x&255;}
 private static void be(java.util.List<Integer>o,int v){o.add(hi(v));o.add(lo(v));}
 private static void le(java.util.List<Integer>o,int v){o.add(lo(v));o.add(hi(v));}
 private static void beA(java.util.List<Integer>o,int v){o.add(hi(v));o.add((lo(v)+128)&255);}
 private static void leA(java.util.List<Integer>o,int v){o.add((lo(v)+128)&255);o.add(hi(v));}
 private static byte[] arr(java.util.List<Integer>o){byte[]b=new byte[o.size()];for(int i=0;i<b.length;i++)b[i]=(byte)(int)o.get(i);return b;}
 public static void main(String[]z){java.util.ArrayList<Integer>o=new java.util.ArrayList<>();
  //14 widget3214 player7 selected21560 slot11
  beA(o,3214);be(o,7);be(o,21560);le(o,11);GenericInteractionEvent e=GenericInteractionPacketDecoder.decode(14,arr(o));eq(e.selectedWidget,3214,"14widget");eq(e.targetId,7,"14player");eq(e.selectedItemId,21560,"14item");eq(e.selectedSlot,11,"14slot");
  o.clear();le(o,3214);beA(o,21560);be(o,995);beA(o,3091);leA(o,11);be(o,3503);e=GenericInteractionPacketDecoder.decode(25,arr(o));eq(e.targetId,995,"25target");eq(e.worldX,3091,"25x");eq(e.worldY,3503,"25y");
  o.clear();le(o,3503);be(o,3091);leA(o,26972);e=GenericInteractionPacketDecoder.decode(70,arr(o));eq(e.option,3,"70opt");eq(e.targetId,26972,"70obj");eq(e.worldX,3091,"70x");eq(e.worldY,3503,"70y");
  o.clear();le(o,6);beA(o,36025);le(o,4151);e=GenericInteractionPacketDecoder.decode(176,arr(o));eq(e.widgetId,36025,"176widget");eq(e.slot,6,"176slot");eq(e.itemId,4151,"176item");
  o.clear();be(o,3214);le(o,26972);leA(o,3091);le(o,11);leA(o,3503);be(o,21560);e=GenericInteractionPacketDecoder.decode(192,arr(o));eq(e.selectedWidget,3214,"192widget");eq(e.targetId,26972,"192obj");eq(e.worldX,3091,"192x");eq(e.worldY,3503,"192y");eq(e.selectedItemId,21560,"192item");
  o.clear();beA(o,26972);beA(o,3091);be(o,3503);e=GenericInteractionPacketDecoder.decode(228,arr(o));eq(e.option,5,"228opt");eq(e.targetId,26972,"228obj");
  o.clear();leA(o,3503);beA(o,26972);leA(o,3091);e=GenericInteractionPacketDecoder.decode(234,arr(o));eq(e.option,4,"234opt");eq(e.targetId,26972,"234obj");
  o.clear();leA(o,26972);le(o,3091);beA(o,3503);e=GenericInteractionPacketDecoder.decode(252,arr(o));eq(e.option,2,"252opt");eq(e.targetId,26972,"252obj");
  int[]ops={14,25,70,176,192,228,234,252};int sum=0;for(int x:ops)sum+=GenericInteractionPacketDecoder.length(x);
  System.out.println("V5185_GENERIC_C2S_DECODE_PASS decoder=typed-foundation opcodes=8 bytesTotal="+sum+" itemOnPlayer14=true itemOnGround25=true objectOptions70_228_234_252=true widgetItem176=true itemOnObject192=true");
 }
}
