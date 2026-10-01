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

   boolean[] lockOrder={false,false};
   String competing=
    LocalSession.replaceMonsterSpawnerRootForCurrentSession(
     w,
     p1,
     p1.generation(),
     ()->{
      lockOrder[0]=Thread.holdsLock(TradeService.class);
      lockOrder[1]=Thread.holdsLock(p1.mutationLock());
      s1.fixed(97,BootstrapPackets.interface97(15106));
      return "TEST_COMPETING_ROOT";
     }
    );
   need(competing,"TEST_COMPETING_ROOT");
   if(!lockOrder[0]||!lockOrder[1])
    throw new AssertionError("competing root lock order ownership missing trade="+lockOrder[0]+" player="+lockOrder[1]);

   byte[] currentAfterRetire=drain(q1),peerAfterRetire=drain(q2);
   if(currentAfterRetire.length!=3)
    throw new AssertionError("competing root current bytes expected root-only 3 got="+currentAfterRetire.length);
   if(peerAfterRetire.length!=1)
    throw new AssertionError("competing root peer close bytes expected 1 got="+peerAfterRetire.length);
   if(TradeService.active(p1)||TradeService.active(p2))
    throw new AssertionError("retired trade remained active");
   if(TradeService.handleWidget(p1,3420)!=null||
      TradeService.handleAmount(p1,1)!=null||
      TradeService.handleItemAction(
       p1,
       new ItemContainerAction(145,3322,0,995,0,"ITEM_ACTION_1")
      )!=null)
    throw new AssertionError("hidden trade action accepted after competing root");

   String failureTrade=TradeService.start(w,p1,p2);need(failureTrade,"TRADE_UI_OPEN");
   drain(q1);drain(q2);
   boolean failedRootThrown=false;
   try{
    LocalSession.replaceMonsterSpawnerRootForCurrentSession(
     w,
     p1,
     p1.generation(),
     ()->{throw new IOException("EXPECTED_TRADE_ROOT_FAILURE");}
    );
   }catch(IOException expected){
    failedRootThrown="EXPECTED_TRADE_ROOT_FAILURE".equals(expected.getMessage());
   }
   if(!failedRootThrown)
    throw new AssertionError("throwing competing root was not propagated");
   if(!TradeService.active(p1)||!TradeService.active(p2))
    throw new AssertionError("throwing competing root retired live trade");
   if(drain(q1).length!=0||drain(q2).length!=0)
    throw new AssertionError("throwing competing root emitted trade close/output");
   TradeService.cancelIfActive(p1,"TEST_CLEANUP");
   drain(q1);drain(q2);

   testSecondRootPublicationFailureAtomicity();
   testConfirmRootPublicationFailureClosesTrade();
   testReplacementTradeStartFailureAtomicity();
   testFinalCommitPairAdmissionAtomicity();
   testOneSidedAcceptancePairAdmissionAtomicity();

   System.out.println("V5140_ENGINE_R4_TRADE_FLOW_PASS roots=3323/3443 offerWidgets=3322/3415/3416 accepts=3420/3546 atomicExchange=true cancelReservationModel=true twoSessionRootOwners=true peerOnlyCompetingClose=true hiddenTradeRejected=true competingRootFailureAtomic=true lockOrderTradeBeforePlayer=true partialStartFailureClosed=true confirmPublicationFailClosed=true replacementStartFailureAtomic=true priorPeerPreserved=true replacementPeerCloseExact=true finalCommitPairAdmissionAtomic=true finalCommitRetryExactlyOnce=true oneSidedFirstAcceptAtomic=true oneSidedFinalAcceptAtomic=true acceptanceRetryRoutable=true");
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

 static void testConfirmRootPublicationFailureClosesTrade()throws Exception{
  World w=World.isolatedForTest(602L);
  WorldPlayer p1=new WorldPlayer(),p2=new WorldPlayer();
  w.registerPlayer(p1,"confirm-a");
  w.registerPlayer(p2,"confirm-b");
  SwitchFailOutputStream out1=new SwitchFailOutputStream();
  SwitchFailOutputStream out2=new SwitchFailOutputStream();
  ServerPacketWriter s1=
   new ServerPacketWriter(out1,new IsaacCipher(new int[]{17,18,19,20}));
  ServerPacketWriter s2=
   new ServerPacketWriter(out2,new IsaacCipher(new int[]{21,22,23,24}));
  try{
   TradeService.register(
    w,p1,p1.generation(),p1.bank(),s1,()->{}
   );
   TradeService.register(
    w,p2,p2.generation(),p2.bank(),s2,()->{}
   );
   need(TradeService.start(w,p1,p2),"TRADE_UI_OPEN");
   need(TradeService.handleWidget(p1,3420),"WAITING_OTHER");

   int p1WritesBefore=out1.writes;
   out2.fail=true;
   boolean failed=false;
   try{
    TradeService.handleWidget(p2,3420);
   }catch(IOException expected){
    failed="SWITCH_FAIL".equals(expected.getMessage());
   }

   if(!failed)
    throw new AssertionError("confirmation root publication failure not propagated");
   if(out1.writes-p1WritesBefore!=7)
    throw new AssertionError(
     "first participant did not receive 6 confirm packets + close delta="+
     (out1.writes-p1WritesBefore)
    );
   if(TradeService.active(p1)||TradeService.active(p2))
    throw new AssertionError("failed confirmation publication left Trade live");
   if(TradeService.handleWidget(p1,3546)!=null||
      TradeService.handleWidget(p2,3546)!=null)
    throw new AssertionError("failed confirmation publication accepted hidden final accept");
  }finally{
   out2.fail=false;
   TradeService.unregister(p1);
   TradeService.unregister(p2);
   w.unregisterPlayer(p1);
   w.unregisterPlayer(p2);
   w.close();
  }
 }

 static void testReplacementTradeStartFailureAtomicity()throws Exception{
  World w=World.isolatedForTest(603L);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer(),c=new WorldPlayer(),d=new WorldPlayer();
  w.registerPlayer(a,"replace-a");
  w.registerPlayer(b,"replace-b");
  w.registerPlayer(c,"replace-c");
  w.registerPlayer(d,"replace-d");

  OutboundPacketQueue qa=new OutboundPacketQueue(),qb=new OutboundPacketQueue(),qc=new OutboundPacketQueue(),qd=new OutboundPacketQueue();
  ServerPacketWriter wa=new ServerPacketWriter(qa,new IsaacCipher(new int[]{25,26,27,28}));
  ServerPacketWriter wb=new ServerPacketWriter(qb,new IsaacCipher(new int[]{29,30,31,32}));
  ServerPacketWriter wc=new ServerPacketWriter(qc,new IsaacCipher(new int[]{33,34,35,36}));
  ServerPacketWriter wd=new ServerPacketWriter(qd,new IsaacCipher(new int[]{37,38,39,40}));

  int[] failA={0};
  int[] failB={0};

  try{
   TradeService.register(
    w,a,a.generation(),a.bank(),wa,()->{},
    action->{
     if(failA[0]==1)throw new IOException("EXPECTED_FIRST_REPLACEMENT_ROOT_FAILURE");
     action.publish();
     if(failA[0]==2)throw new IOException("EXPECTED_FIRST_REPLACEMENT_ROOT_FAILURE_AFTER");
    }
   );
   TradeService.register(
    w,b,b.generation(),b.bank(),wb,()->{},
    action->{
     if(failB[0]==1)throw new IOException("EXPECTED_SECOND_REPLACEMENT_ROOT_FAILURE");
     action.publish();
     if(failB[0]==2)throw new IOException("EXPECTED_SECOND_REPLACEMENT_ROOT_FAILURE_AFTER");
    }
   );
   TradeService.register(w,c,c.generation(),c.bank(),wc,()->{});
   TradeService.register(w,d,d.generation(),d.bank(),wd,()->{});

   need(TradeService.start(w,a,c),"TRADE_UI_OPEN");
   need(TradeService.start(w,b,d),"TRADE_UI_OPEN");
   drain(qa);drain(qb);drain(qc);drain(qd);

   failA[0]=1;
   boolean firstFailed=false;
   try{
    TradeService.start(w,a,b);
   }catch(IOException expected){
    firstFailed="EXPECTED_FIRST_REPLACEMENT_ROOT_FAILURE".equals(expected.getMessage());
   }
   failA[0]=0;

   if(!firstFailed||
      !TradeService.active(a)||
      !TradeService.active(b)||
      !TradeService.active(c)||
      !TradeService.active(d))
    throw new AssertionError("first replacement-root failure destroyed prior trades");
   if(drain(qa).length!=0||drain(qb).length!=0||
      drain(qc).length!=0||drain(qd).length!=0)
    throw new AssertionError("first replacement-root failure emitted unexpected output");

   failB[0]=2;
   boolean secondFailed=false;
   try{
    TradeService.start(w,a,b);
   }catch(IOException expected){
    secondFailed="EXPECTED_SECOND_REPLACEMENT_ROOT_FAILURE_AFTER".equals(expected.getMessage());
   }
   failB[0]=0;

   byte[] restoredA=drain(qa);
   byte[] restoredB=drain(qb);
   byte[] untouchedC=drain(qc);
   byte[] untouchedD=drain(qd);

   if(!secondFailed||
      !TradeService.active(a)||
      !TradeService.active(b)||
      !TradeService.active(c)||
      !TradeService.active(d))
    throw new AssertionError("second replacement-root failure destroyed prior trades");

   if(!contains(restoredA,"Trading With: replace-c")||
      !contains(restoredB,"Trading With: replace-d"))
    throw new AssertionError("failed replacement did not restore prior Trade presentation");

   if(untouchedC.length!=0||untouchedD.length!=0)
    throw new AssertionError("failed replacement disturbed prior peers");

   need(TradeService.start(w,a,b),"TRADE_UI_OPEN");

   byte[] replacementA=drain(qa);
   byte[] replacementB=drain(qb);

   if(!contains(replacementA,"Trading With: replace-b")||
      !contains(replacementB,"Trading With: replace-a"))
    throw new AssertionError("successful replacement did not publish A-B Trade");

   if(!TradeService.active(a)||
      !TradeService.active(b)||
      TradeService.active(c)||
      TradeService.active(d))
    throw new AssertionError("successful replacement did not retire prior peer trades");

   if(qc.queuedPackets()!=1||qd.queuedPackets()!=1)
    throw new AssertionError("successful replacement peer close count="+qc.queuedPackets()+"/"+qd.queuedPackets());

   drain(qc);drain(qd);

   need(TradeService.handleWidget(a,3420),"WAITING_OTHER");
   need(TradeService.handleWidget(b,3420),"CONFIRM_OPEN");
  }finally{
   TradeService.unregister(a);
   TradeService.unregister(b);
   TradeService.unregister(c);
   TradeService.unregister(d);
   if(a.registered())w.unregisterPlayer(a);
   if(b.registered())w.unregisterPlayer(b);
   if(c.registered())w.unregisterPlayer(c);
   if(d.registered())w.unregisterPlayer(d);
   w.close();
  }
 }

 static void testFinalCommitPairAdmissionAtomicity()throws Exception{
  World w=World.isolatedForTest(604L);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
  w.registerPlayer(a,"final-atomic-a");
  w.registerPlayer(b,"final-atomic-b");

  OutboundPacketQueue qa=new OutboundPacketQueue(1024);
  OutboundPacketQueue qb=new OutboundPacketQueue(1024);
  ServerPacketWriter wa=
   new ServerPacketWriter(qa,new IsaacCipher(new int[]{41,42,43,44}));
  ServerPacketWriter wb=
   new ServerPacketWriter(qb,new IsaacCipher(new int[]{45,46,47,48}));
  int[] saves={0,0};

  try{
   a.bank().spawnItem(995,1000,wa);
   b.bank().spawnItem(385,2,wb);
   drain(qa);drain(qb);

   TradeService.register(
    w,a,a.generation(),a.bank(),wa,()->saves[0]++
   );
   TradeService.register(
    w,b,b.generation(),b.bank(),wb,()->saves[1]++
   );

   need(TradeService.start(w,a,b),"TRADE_UI_OPEN");
   drain(qa);drain(qb);

   int coinSlot=find(a.bank(),995);
   int sharkSlot=find(b.bank(),385);

   need(
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      145,3322,coinSlot,995,0,"ITEM_ACTION_1"
     )
    ),
    "TRADE_OFFER_OK"
   );
   need(
    TradeService.handleItemAction(
     b,
     new ItemContainerAction(
      145,3322,sharkSlot,385,0,"ITEM_ACTION_1"
     )
    ),
    "TRADE_OFFER_OK"
   );
   drain(qa);drain(qb);

   need(TradeService.handleWidget(a,3420),"WAITING_OTHER");
   need(TradeService.handleWidget(b,3420),"CONFIRM_OPEN");
   drain(qa);drain(qb);

   need(TradeService.handleWidget(a,3546),"WAITING_OTHER");
   drain(qa);drain(qb);

   int aCoinsBefore=a.bank().inventoryCount(995);
   int aSharksBefore=a.bank().inventoryCount(385);
   int bCoinsBefore=b.bank().inventoryCount(995);
   int bSharksBefore=b.bank().inventoryCount(385);

   qb.offer(new byte[1000]);
   int fillerBytes=qb.queuedBytes();

   String rejected=
    TradeService.handleWidget(
     b,
     3546
    );

   need(
    rejected,
    "TRADE_COMMIT_REJECTED_PRESENTATION_ADMISSION"
   );

   if(qa.queuedBytes()!=0||
      qb.queuedBytes()!=fillerBytes)
    throw new AssertionError(
     "rejected final commit leaked one-sided packet bytes="+
     qa.queuedBytes()+"/"+qb.queuedBytes()
    );

   if(a.bank().inventoryCount(995)!=aCoinsBefore||
      a.bank().inventoryCount(385)!=aSharksBefore||
      b.bank().inventoryCount(995)!=bCoinsBefore||
      b.bank().inventoryCount(385)!=bSharksBefore)
    throw new AssertionError(
     "rejected final commit mutated inventory"
    );

   if(!TradeService.active(a)||
      !TradeService.active(b)||
      saves[0]!=0||
      saves[1]!=0)
    throw new AssertionError(
     "rejected final commit changed trade/persistence state saves="+
     saves[0]+"/"+saves[1]
    );

   drain(qb);

   need(
    TradeService.handleWidget(
     b,
     3546
    ),
    "TRADE_COMMITTED"
   );

   if(a.bank().inventoryCount(995)!=
        aCoinsBefore-1||
      a.bank().inventoryCount(385)!=
        aSharksBefore+1||
      b.bank().inventoryCount(995)!=
        bCoinsBefore+1||
      b.bank().inventoryCount(385)!=
        bSharksBefore-1)
    throw new AssertionError(
     "successful final commit postimage wrong"
    );

   if(TradeService.active(a)||
      TradeService.active(b)||
      saves[0]!=1||
      saves[1]!=1)
    throw new AssertionError(
     "successful final commit did not detach/save exactly once saves="+
     saves[0]+"/"+saves[1]
    );

   if(qa.queuedBytes()==0||
      qb.queuedBytes()==0)
    throw new AssertionError(
     "successful final commit emitted no paired postimage"
    );
  }finally{
   TradeService.unregister(a);
   TradeService.unregister(b);
   if(a.registered())w.unregisterPlayer(a);
   if(b.registered())w.unregisterPlayer(b);
   w.close();
  }
 }

 static void testOneSidedAcceptancePairAdmissionAtomicity()throws Exception{
  World w=World.isolatedForTest(605L);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
  w.registerPlayer(a,"accept-atomic-a");
  w.registerPlayer(b,"accept-atomic-b");

  OutboundPacketQueue qa=new OutboundPacketQueue(1024);
  OutboundPacketQueue qb=new OutboundPacketQueue(1024);
  ServerPacketWriter wa=
   new ServerPacketWriter(qa,new IsaacCipher(new int[]{49,50,51,52}));
  ServerPacketWriter wb=
   new ServerPacketWriter(qb,new IsaacCipher(new int[]{53,54,55,56}));

  try{
   TradeService.register(
    w,a,a.generation(),a.bank(),wa,()->{}
   );
   TradeService.register(
    w,b,b.generation(),b.bank(),wb,()->{}
   );

   need(TradeService.start(w,a,b),"TRADE_UI_OPEN");
   drain(qa);drain(qb);

   qb.offer(new byte[1000]);
   boolean firstFailed=false;

   try{
    TradeService.handleWidget(
     a,
     TradeService.FIRST_ACCEPT
    );
   }catch(IOException expected){
    firstFailed=
     expected.getMessage()!=null&&
     expected.getMessage().contains(
      "pair reservation unavailable"
     );
   }

   if(!firstFailed||
      qa.queuedBytes()!=0||
      qb.queuedBytes()!=1000||
      qb.overflowed())
    throw new AssertionError(
     "first-stage one-sided acceptance failure was not atomic bytes="+
     qa.queuedBytes()+"/"+qb.queuedBytes()+
     " overflowed="+qb.overflowed()
    );

   drain(qb);

   need(
    TradeService.handleWidget(
     b,
     TradeService.FIRST_ACCEPT
    ),
    "WAITING_OTHER"
   );
   drain(qa);drain(qb);

   need(
    TradeService.handleWidget(
     a,
     TradeService.FIRST_ACCEPT
    ),
    "CONFIRM_OPEN"
   );
   drain(qa);drain(qb);

   qb.offer(new byte[1000]);
   boolean finalFailed=false;

   try{
    TradeService.handleWidget(
     a,
     TradeService.FINAL_ACCEPT
    );
   }catch(IOException expected){
    finalFailed=
     expected.getMessage()!=null&&
     expected.getMessage().contains(
      "pair reservation unavailable"
     );
   }

   if(!finalFailed||
      qa.queuedBytes()!=0||
      qb.queuedBytes()!=1000||
      qb.overflowed())
    throw new AssertionError(
     "final-stage one-sided acceptance failure was not atomic bytes="+
     qa.queuedBytes()+"/"+qb.queuedBytes()+
     " overflowed="+qb.overflowed()
    );

   drain(qb);

   need(
    TradeService.handleWidget(
     b,
     TradeService.FINAL_ACCEPT
    ),
    "WAITING_OTHER"
   );
   drain(qa);drain(qb);

   need(
    TradeService.handleWidget(
     a,
     TradeService.FINAL_ACCEPT
    ),
    "TRADE_COMMITTED"
   );

   if(TradeService.active(a)||
      TradeService.active(b))
    throw new AssertionError(
     "acceptance retry flow did not complete Trade"
    );
  }finally{
   TradeService.unregister(a);
   TradeService.unregister(b);
   if(a.registered())w.unregisterPlayer(a);
   if(b.registered())w.unregisterPlayer(b);
   w.close();
  }
 }

 static boolean contains(byte[] data,String value){
  byte[] needle=
   value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
  outer:
  for(int i=0;i+needle.length<=data.length;i++){
   for(int j=0;j<needle.length;j++)
    if(data[i+j]!=needle[j])continue outer;
   return true;
  }
  return false;
 }

 static final class SwitchFailOutputStream extends OutputStream{
  final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  int writes;
  boolean fail;

  @Override public void write(int value)throws IOException{
   if(fail)throw new IOException("SWITCH_FAIL");
   bytes.write(value);
  }

  @Override public void write(byte[] data,int offset,int length)throws IOException{
   if(fail)throw new IOException("SWITCH_FAIL");
   writes++;
   bytes.write(data,offset,length);
  }
 }

 static byte[] drain(OutboundPacketQueue q)throws Exception{java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream();q.drainTo(o,1<<20);return o.toByteArray();}
 static int find(BankState b,int id){for(int i=0;i<b.inventoryCapacity();i++){BankState.Stack s=b.inventoryAt(i);if(s!=null&&s.itemId==id)return i;}return -1;}
 static void need(String s,String n){if(s==null||!s.contains(n))throw new AssertionError("expected "+n+" got "+s);}
}
