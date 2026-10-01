package spk.local;
import java.io.*;
public final class EngineR4TradeFlowTest{
 public static void main(String[]a)throws Exception{
  World w=World.isolatedForTest(600L);WorldPlayer p1=new WorldPlayer(),p2=new WorldPlayer();w.registerPlayer(p1,"opensrc");w.registerPlayer(p2,"src");
  OutboundPacketQueue q1=new OutboundPacketQueue(),q2=new OutboundPacketQueue();ServerPacketWriter s1=new ServerPacketWriter(q1,new IsaacCipher(new int[]{1,2,3,4})),s2=new ServerPacketWriter(q2,new IsaacCipher(new int[]{5,6,7,8}));
  try{
   p1.bank().spawnItem(995,1000,s1);p2.bank().spawnItem(385,2,s2);drain(q1);drain(q2);
   int[] rootPublishes={0,0};
   TradeService.register(
    w,p1,p1.generation(),p1.bank(),s1,()->{},
    action->{rootPublishes[0]++;action.publish();}
   );
   TradeService.register(
    w,p2,p2.generation(),p2.bank(),s2,()->{},
    action->{rootPublishes[1]++;action.publish();}
   );
   String open=TradeService.start(w,p1,p2);need(open,"TRADE_UI_OPEN");
   if(rootPublishes[0]!=1||rootPublishes[1]!=1)throw new AssertionError("trade root owners not invoked exactly once "+rootPublishes[0]+"/"+rootPublishes[1]);
   need(TradeService.handleItemAction(p1,new ItemContainerAction(145,3322,0,995,0,"ITEM_ACTION_1")),"TRADE_OFFER_OK");
   int sharkSlot=find(p2.bank(),385);need(TradeService.handleItemAction(p2,new ItemContainerAction(145,3322,sharkSlot,385,0,"ITEM_ACTION_1")),"TRADE_OFFER_OK");
   need(TradeService.handleWidget(p1,3420),"WAITING_OTHER");need(TradeService.handleWidget(p2,3420),"CONFIRM_OPEN");
   need(TradeService.handleWidget(p1,3546),"WAITING_OTHER");need(TradeService.handleWidget(p2,3546),"TRADE_COMMITTED");
   if(p1.bank().inventoryCount(995)!=999||p1.bank().inventoryCount(385)!=1)throw new AssertionError("p1 counts wrong coins="+p1.bank().inventoryCount(995)+" shark="+p1.bank().inventoryCount(385));
   if(p2.bank().inventoryCount(995)!=1||p2.bank().inventoryCount(385)!=1)throw new AssertionError("p2 counts wrong coins="+p2.bank().inventoryCount(995)+" shark="+p2.bank().inventoryCount(385));
   if(TradeService.active(p1)||TradeService.active(p2))throw new AssertionError("trade still active");

   String reopened=TradeService.start(w,p1,p2);need(reopened,"TRADE_UI_OPEN");
   if(rootPublishes[0]!=2||rootPublishes[1]!=2)throw new AssertionError("reopened trade root owners "+rootPublishes[0]+"/"+rootPublishes[1]);
   drain(q1);drain(q2);

   if(!TradeService.retireForCompetingRoot(p1,"TEST_COMPETING_ROOT"))
    throw new AssertionError("competing root did not retire live trade");
   byte[] currentAfterRetire=drain(q1),peerAfterRetire=drain(q2);
   if(currentAfterRetire.length!=0)
    throw new AssertionError("competing root closed current replacement bytes="+currentAfterRetire.length);
   if(peerAfterRetire.length==0)
    throw new AssertionError("competing root did not close peer trade root");
   if(TradeService.active(p1)||TradeService.active(p2))
    throw new AssertionError("retired trade remained active");
   if(TradeService.handleWidget(p1,3420)!=null||
      TradeService.handleAmount(p1,1)!=null||
      TradeService.handleItemAction(
       p1,
       new ItemContainerAction(145,3322,0,995,0,"ITEM_ACTION_1")
      )!=null)
    throw new AssertionError("hidden trade action accepted after competing root");

   testSecondRootPublicationFailureAtomicity();

   System.out.println("V5140_ENGINE_R4_TRADE_FLOW_PASS roots=3323/3443 offerWidgets=3322/3415/3416 accepts=3420/3546 atomicExchange=true cancelReservationModel=true twoSessionRootOwners=true peerOnlyCompetingClose=true hiddenTradeRejected=true partialStartFailureClosed=true");
  }finally{TradeService.unregister(p1);TradeService.unregister(p2);w.unregisterPlayer(p1);w.unregisterPlayer(p2);w.close();}
 }

 static void testSecondRootPublicationFailureAtomicity()throws Exception{
  World w=World.isolatedForTest(601L);
  WorldPlayer p1=new WorldPlayer(),p2=new WorldPlayer();
  w.registerPlayer(p1,"partial-a");
  w.registerPlayer(p2,"partial-b");
  OutboundPacketQueue q1=new OutboundPacketQueue(),q2=new OutboundPacketQueue();
  ServerPacketWriter s1=
   new ServerPacketWriter(q1,new IsaacCipher(new int[]{9,10,11,12}));
  ServerPacketWriter s2=
   new ServerPacketWriter(q2,new IsaacCipher(new int[]{13,14,15,16}));
  try{
   TradeService.register(
    w,p1,p1.generation(),p1.bank(),s1,()->{},
    action->action.publish()
   );
   TradeService.register(
    w,p2,p2.generation(),p2.bank(),s2,()->{},
    action->{
     action.publish();
     throw new IOException("EXPECTED_SECOND_ROOT_FAILURE");
    }
   );

   boolean failed=false;
   try{
    TradeService.start(w,p1,p2);
   }catch(IOException expected){
    failed="EXPECTED_SECOND_ROOT_FAILURE".equals(expected.getMessage());
   }

   if(!failed)
    throw new AssertionError("second root publication failure not propagated");
   if(q1.queuedPackets()!=7||q2.queuedPackets()!=7)
    throw new AssertionError(
     "partial Trade roots not closed packets="+
     q1.queuedPackets()+"/"+q2.queuedPackets()
    );
   if(TradeService.active(p1)||TradeService.active(p2))
    throw new AssertionError("failed partial Trade start became live");
   if(TradeService.handleWidget(p1,3420)!=null||
      TradeService.handleWidget(p2,3420)!=null)
    throw new AssertionError("failed partial Trade start accepted hidden widgets");
  }finally{
   TradeService.unregister(p1);
   TradeService.unregister(p2);
   w.unregisterPlayer(p1);
   w.unregisterPlayer(p2);
   w.close();
  }
 }

 static byte[] drain(OutboundPacketQueue q)throws Exception{java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream();q.drainTo(o,1<<20);return o.toByteArray();}
 static int find(BankState b,int id){for(int i=0;i<b.inventoryCapacity();i++){BankState.Stack s=b.inventoryAt(i);if(s!=null&&s.itemId==id)return i;}return -1;}
 static void need(String s,String n){if(s==null||!s.contains(n))throw new AssertionError("expected "+n+" got "+s);}
}
