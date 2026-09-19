package spk.local;
public final class ClientPacket57ItemOnNpcTest{
  public static void main(String[] args){
    int item=20542,target=4,slot=7,widget=3214;byte[] b=new byte[8];putBeA(b,0,item);putBeA(b,2,target);putLe(b,4,slot);putBeA(b,6,widget);
    ItemOnNpcAction a=ClientPacketProbe.decodeItemOnNpc(b);
    if(a.itemId!=item||a.targetNpcIndex!=target||a.slot!=slot||a.widgetId!=widget)throw new AssertionError(a);
    if(ClientPacketProbe.framingOnlyFixedLength(57)!=8)throw new AssertionError("framing57");
    System.out.println("V5128_C2S57_ITEM_ON_NPC_PASS fields=4 transforms=BEA,BEA,LE,BEA framing=8");
  }
  static void putBeA(byte[] b,int o,int v){b[o]=(byte)(v>>>8);b[o+1]=(byte)((v+128)&255);}static void putLe(byte[] b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>>8);}
}
