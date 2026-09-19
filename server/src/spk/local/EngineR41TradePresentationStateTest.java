package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class EngineR41TradePresentationStateTest{
 public static void main(String[]a)throws Exception{
  World w=World.isolatedForTest(600L);WorldPlayer p1=new WorldPlayer(),p2=new WorldPlayer();w.registerPlayer(p1,"opensrc");w.registerPlayer(p2,"src");
  OutboundPacketQueue q1=new OutboundPacketQueue(),q2=new OutboundPacketQueue();ServerPacketWriter s1=new ServerPacketWriter(q1,new IsaacCipher(new int[]{21,22,23,24})),s2=new ServerPacketWriter(q2,new IsaacCipher(new int[]{31,32,33,34}));
  try{
   p1.bank().spawnItem(995,1000,s1);p2.bank().spawnItem(385,2,s2);drain(q1);drain(q2);
   TradeService.register(w,p1,p1.bank(),s1,()->{});TradeService.register(w,p2,p2.bank(),s2,()->{});
   TradeService.start(w,p1,p2);byte[] initial1=drain(q1),initial2=drain(q2);
   lacks(initial1,"Waiting for other player","initial p1 waiting");lacks(initial2,"Waiting for other player","initial p2 waiting");
   TradeService.handleItemAction(p1,new ItemContainerAction(145,3322,0,995,0,"ITEM_ACTION_1"));drain(q1);drain(q2);
   int sharkSlot=find(p2.bank(),385);TradeService.handleItemAction(p2,new ItemContainerAction(145,3322,sharkSlot,385,0,"ITEM_ACTION_1"));drain(q1);drain(q2);
   TradeService.handleWidget(p1,3420);byte[] a1=drain(q1),a2=drain(q2);has(a1,"Waiting for other player...\n","first accepter wait");has(a2,"Other player has accepted.\n","first peer accepted");
   // Offer mutation must clear both acceptance statuses again.
   TradeService.handleItemAction(p1,new ItemContainerAction(145,3322,0,995,0,"ITEM_ACTION_1"));byte[] reset1=drain(q1),reset2=drain(q2);lacks(reset1,"Waiting for other player","reset p1");lacks(reset2,"Other player has accepted.","reset p2");
   TradeService.handleWidget(p1,3420);drain(q1);drain(q2);TradeService.handleWidget(p2,3420);byte[] c1=drain(q1),c2=drain(q2);
   has(c1,"Are you sure you want to make this trade?\n","confirm question p1");has(c2,"Are you sure you want to make this trade?\n","confirm question p2");
   lacks(c1,"Review the items above.","bad confirm helper p1");lacks(c2,"Review the items above.","bad confirm helper p2");
   lacks(c1,"Absolutely nothing!","nonempty fallback p1");lacks(c2,"Absolutely nothing!","nonempty fallback p2");
   TradeService.handleWidget(p1,3546);byte[] f1=drain(q1),f2=drain(q2);has(f1,"Waiting for other player...\n","final accepter wait");has(f2,"Other player has accepted.\n","final peer accepted");
   TradeService.handleWidget(p1,3548);if(TradeService.active(p1)||TradeService.active(p2))throw new AssertionError("decline did not cancel");
   System.out.println("V5141_ENGINE_R41_TRADE_PRESENTATION_PASS initialBlank=true firstAcceptStates=true acceptanceReset=true confirm3535=true itemReview3538_3539=true emptyFallback3557_3558=true finalAcceptStates=true decline=true");
  }finally{TradeService.unregister(p1);TradeService.unregister(p2);w.unregisterPlayer(p1);w.unregisterPlayer(p2);w.close();}
 }
 static byte[] drain(OutboundPacketQueue q)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();q.drainTo(o,1<<20);return o.toByteArray();}
 static int find(BankState b,int id){for(int i=0;i<b.inventoryCapacity();i++){BankState.Stack s=b.inventoryAt(i);if(s!=null&&s.itemId==id)return i;}return -1;}
 static void has(byte[] b,String s,String label){byte[] n=s.getBytes(StandardCharsets.ISO_8859_1);outer:for(int i=0;i+n.length<=b.length;i++){for(int j=0;j<n.length;j++)if(b[i+j]!=n[j])continue outer;return;}throw new AssertionError(label+" missing bytes="+b.length);}
 static void lacks(byte[] b,String s,String label){byte[] n=s.getBytes(StandardCharsets.ISO_8859_1);outer:for(int i=0;i+n.length<=b.length;i++){for(int j=0;j<n.length;j++)if(b[i+j]!=n[j])continue outer;throw new AssertionError(label+" unexpectedly present");}}
}
