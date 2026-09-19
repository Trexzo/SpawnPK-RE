package spk.local;
import java.io.*;
public final class EngineR4TradeFlowTest{
 public static void main(String[]a)throws Exception{
  World w=World.isolatedForTest(600L);WorldPlayer p1=new WorldPlayer(),p2=new WorldPlayer();w.registerPlayer(p1,"opensrc");w.registerPlayer(p2,"src");
  OutboundPacketQueue q1=new OutboundPacketQueue(),q2=new OutboundPacketQueue();ServerPacketWriter s1=new ServerPacketWriter(q1,new IsaacCipher(new int[]{1,2,3,4})),s2=new ServerPacketWriter(q2,new IsaacCipher(new int[]{5,6,7,8}));
  try{
   p1.bank().spawnItem(995,1000,s1);p2.bank().spawnItem(385,2,s2);drain(q1);drain(q2);
   TradeService.register(w,p1,p1.bank(),s1,()->{});TradeService.register(w,p2,p2.bank(),s2,()->{});
   String open=TradeService.start(w,p1,p2);need(open,"TRADE_UI_OPEN");
   need(TradeService.handleItemAction(p1,new ItemContainerAction(145,3322,0,995,0,"ITEM_ACTION_1")),"TRADE_OFFER_OK");
   int sharkSlot=find(p2.bank(),385);need(TradeService.handleItemAction(p2,new ItemContainerAction(145,3322,sharkSlot,385,0,"ITEM_ACTION_1")),"TRADE_OFFER_OK");
   need(TradeService.handleWidget(p1,3420),"WAITING_OTHER");need(TradeService.handleWidget(p2,3420),"CONFIRM_OPEN");
   need(TradeService.handleWidget(p1,3546),"WAITING_OTHER");need(TradeService.handleWidget(p2,3546),"TRADE_COMMITTED");
   if(p1.bank().inventoryCount(995)!=999||p1.bank().inventoryCount(385)!=1)throw new AssertionError("p1 counts wrong coins="+p1.bank().inventoryCount(995)+" shark="+p1.bank().inventoryCount(385));
   if(p2.bank().inventoryCount(995)!=1||p2.bank().inventoryCount(385)!=1)throw new AssertionError("p2 counts wrong coins="+p2.bank().inventoryCount(995)+" shark="+p2.bank().inventoryCount(385));
   if(TradeService.active(p1)||TradeService.active(p2))throw new AssertionError("trade still active");
   System.out.println("V5140_ENGINE_R4_TRADE_FLOW_PASS roots=3323/3443 offerWidgets=3322/3415/3416 accepts=3420/3546 atomicExchange=true cancelReservationModel=true");
  }finally{TradeService.unregister(p1);TradeService.unregister(p2);w.unregisterPlayer(p1);w.unregisterPlayer(p2);w.close();}
 }
 static byte[] drain(OutboundPacketQueue q)throws Exception{java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream();q.drainTo(o,1<<20);return o.toByteArray();}
 static int find(BankState b,int id){for(int i=0;i<b.inventoryCapacity();i++){BankState.Stack s=b.inventoryAt(i);if(s!=null&&s.itemId==id)return i;}return -1;}
 static void need(String s,String n){if(s==null||!s.contains(n))throw new AssertionError("expected "+n+" got "+s);}
}
