package spk.local;
import java.io.*;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
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
   testSuccessfulReplacementTerminalOldPeerRetirement();
   testFinalCommitPairAdmissionAtomicity();
   testDirectFinalCommitRejectsBeforeBytes();
   testTradeXPromptFailureAtomicity();
   testOneSidedAcceptStatusAtomicity();
   testDirectOneSidedAcceptStatusTerminalFailure(false);
   testDirectOneSidedAcceptStatusTerminalFailure(true);
   testDirectOfferPostimageRejectsBeforeBytes();
   testOfferRefreshAtomicity();

   System.out.println("V5140_ENGINE_R4_TRADE_FLOW_PASS roots=3323/3443 offerWidgets=3322/3415/3416 accepts=3420/3546 atomicExchange=true cancelReservationModel=true twoSessionRootOwners=true peerOnlyCompetingClose=true hiddenTradeRejected=true competingRootFailureAtomic=true lockOrderTradeBeforePlayer=true partialStartFailureClosed=true startOwnerIoNonTerminal=true confirmPublicationFailClosed=true replacementStartFailureAtomic=true priorPeerPreserved=true replacementPeerCloseExact=true replacementTerminalOldPeerRetired=true replacementHealthyTradePreserved=true finalCommitPairAdmissionAtomic=true finalCommitRetryExactlyOnce=true directFinalCommitNonAtomicRejected=true directFinalCommitZeroBytes=true tradeOfferXPromptFailureAtomic=true tradeRemoveXPromptFailureAtomic=true tradeXPromptTerminalRetirement=true firstAcceptStatusPairAtomic=true finalAcceptStatusPairAtomic=true directFirstAcceptTerminalRetired=true directFinalAcceptTerminalRetired=true directOfferNonAtomicRejected=true directOfferZeroBytes=true offerRefreshPairAtomic=true removeRefreshPairAtomic=true xRefreshRetryPreserved=true");
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
  int[] ownerFailures={1};
  try{
   TradeService.register(
    w,p1,p1.generation(),p1.bank(),s1,()->{},
    action->action.publish()
   );
   TradeService.register(
    w,p2,p2.generation(),p2.bank(),s2,()->{},
    action->{
     action.publish();
     if(ownerFailures[0]>0){
      ownerFailures[0]--;
      throw new IOException("EXPECTED_SECOND_ROOT_FAILURE");
     }
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

   drain(q1);drain(q2);
   need(
    TradeService.start(w,p1,p2),
    "TRADE_UI_OPEN"
   );
   if(!TradeService.active(p1)||!TradeService.active(p2))
    throw new AssertionError(
     "post-publish RootOwner IOException incorrectly terminal-retired healthy writer"
    );
   TradeService.cancelIfActive(
    p1,
    "OWNER_FAILURE_PROVENANCE_CLEANUP"
   );
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

 static void testSuccessfulReplacementTerminalOldPeerRetirement()throws Exception{
  World w=World.isolatedForTest(608L);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer(),cPeer=new WorldPlayer();
  long ga=w.registerPlayer(a,"replace-terminal-a");
  long gb=w.registerPlayer(b,"replace-terminal-b");
  long gc=w.registerPlayer(cPeer,"replace-terminal-c");

  OutboundPacketQueue qa=new OutboundPacketQueue(),qb=new OutboundPacketQueue();
  ServerPacketWriter wa=new ServerPacketWriter(qa,new IsaacCipher(new int[]{81,82,83,84}));
  ServerPacketWriter wb=new ServerPacketWriter(qb,new IsaacCipher(new int[]{85,86,87,88}));
  SwitchFailOutputStream outC=new SwitchFailOutputStream();
  ServerPacketWriter wc=new ServerPacketWriter(outC,new IsaacCipher(new int[]{89,90,91,92}));

  NpcRegistry cNpcs=new NpcRegistry(new DevAuthorityWorkbench());

  Player81WorldSync.register(
   wc,w,cPeer,new DevAuthorityWorkbench()
  );
  SharedNpcWorldRelay.register(
   wc,w,cPeer,cNpcs,cPeer.movement()
  );

  TradeService.register(w,a,ga,a.bank(),wa,()->{});
  TradeService.register(w,b,gb,b.bank(),wb,()->{});
  TradeService.register(w,cPeer,gc,cPeer.bank(),wc,()->{});

  try{
   need(TradeService.start(w,a,cPeer),"TRADE_UI_OPEN");
   drain(qa);
   int attemptsBefore=outC.attempts;

   outC.fail=true;

   need(TradeService.start(w,a,b),"TRADE_UI_OPEN");

   if(!TradeService.active(a)||
      !TradeService.active(b)||
      TradeService.active(cPeer))
    throw new AssertionError(
     "successful replacement did not preserve new A-B Trade while retiring old C Trade"
    );

   if(!wc.terminal())
    throw new AssertionError(
     "failed old replacement peer writer was not terminal-latched"
    );

   if(outC.attempts!=attemptsBefore+1)
    throw new AssertionError(
     "old replacement peer close attempts expected exactly one before="+
     attemptsBefore+" after="+outC.attempts
    );

   if(player81ContextFor(wc)!=null)
    throw new AssertionError(
     "terminal old replacement peer retained Player81 authority"
    );

   Object relay=relayContextFor(wc);
   if(relay==null)
    throw new AssertionError(
     "terminal old replacement peer lost SharedNpc fail-closed sentinel"
    );

   int attemptsAfter=outC.attempts;
   boolean rejected=false;
   try{
    SharedNpcWorldRelay.preflightRegistration(
     wc,w,cPeer
    );
   }catch(SharedNpcWorldRelay.TerminalRegistrationException expected){
    rejected=expected.owner==cPeer&&expected.writer==wc;
   }

   if(!rejected)
    throw new AssertionError(
     "terminal old replacement peer writer was resurrectable"
    );

   if(outC.attempts!=attemptsAfter)
    throw new AssertionError(
     "terminal old replacement peer preflight retouched transport"
    );

   need(TradeService.handleWidget(a,3420),"WAITING_OTHER");
   need(TradeService.handleWidget(b,3420),"CONFIRM_OPEN");
  }finally{
   outC.fail=false;
   TradeService.unregister(a);
   TradeService.unregister(b);
   TradeService.unregister(cPeer);
   SharedNpcWorldRelay.unregister(wc);
   Player81WorldSync.unregister(wc);
   if(a.registered())w.unregisterPlayer(a,ga);
   if(b.registered())w.unregisterPlayer(b,gb);
   if(cPeer.registered())w.unregisterPlayer(cPeer,gc);
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

 static void testTradeXPromptFailureAtomicity()throws Exception{
  testTradeXPromptTerminalFailure(false,605L);
  testTradeXPromptTerminalFailure(true,611L);
 }

 static void testTradeXPromptTerminalFailure(
  boolean remove,
  long seed
 )throws Exception{
  World w=World.isolatedForTest(seed);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
  long ga=w.registerPlayer(
   a,
   remove?"xprompt-remove-a":"xprompt-offer-a"
  );
  long gb=w.registerPlayer(
   b,
   remove?"xprompt-remove-b":"xprompt-offer-b"
  );

  OutboundPacketQueue qa=new OutboundPacketQueue(1024);
  OutboundPacketQueue qb=new OutboundPacketQueue(1024);
  ServerPacketWriter wa=
   new ServerPacketWriter(
    qa,
    new IsaacCipher(
     remove
      ?new int[]{81,82,83,84}
      :new int[]{121,122,123,124}
    )
   );
  ServerPacketWriter wb=
   new ServerPacketWriter(
    qb,
    new IsaacCipher(
     remove
      ?new int[]{85,86,87,88}
      :new int[]{125,126,127,128}
    )
   );

  try{
   a.bank().spawnItem(
    995,
    1000,
    wa
   );
   drain(qa);

   TradeService.register(
    w,
    a,
    ga,
    a.bank(),
    wa,
    ()->{}
   );
   TradeService.register(
    w,
    b,
    gb,
    b.bank(),
    wb,
    ()->{}
   );

   need(
    TradeService.start(
     w,
     a,
     b
    ),
    "TRADE_UI_OPEN"
   );
   drain(qa);
   drain(qb);

   int coinSlot=
    find(
     a.bank(),
     995
    );

   if(remove){
    need(
     TradeService.handleItemAction(
      a,
      new ItemContainerAction(
       145,
       3322,
       coinSlot,
       995,
       0,
       "ITEM_ACTION_1"
      )
     ),
     "TRADE_OFFER_OK"
    );
    drain(qa);
    drain(qb);
   }

   qa.offer(
    new byte[1024]
   );

   int healthyBytesBefore=
    qb.queuedBytes();

   boolean promptFailed=false;
   try{
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      135,
      remove?3415:3322,
      remove?0:coinSlot,
      995,
      0,
      "ITEM_ACTION_X"
     )
    );
   }catch(IOException expected){
    promptFailed=true;
   }

   if(!promptFailed)
    throw new AssertionError(
     "Trade X prompt failure not propagated remove="+remove
    );

   if(!wa.terminal())
    throw new AssertionError(
     "Trade X prompt failure did not terminal-latch writer remove="+remove
    );

   if(TradeService.active(a)||
      TradeService.active(b))
    throw new AssertionError(
     "terminal Trade X prompt kept Trade live remove="+remove
    );

   if(TradeService.handleAmount(
       a,
       1
      )!=null)
    throw new AssertionError(
     "failed Trade X prompt left hidden pending authority remove="+remove
    );

   if(qa.queuedBytes()!=1024)
    throw new AssertionError(
     "terminal Trade X prompt retouched failed queue remove="+
     remove+
     " bytes="+qa.queuedBytes()
    );

   if(qb.queuedBytes()!=healthyBytesBefore+1)
    throw new AssertionError(
     "healthy Trade X peer close expected exactly one byte remove="+
     remove+
     " before="+healthyBytesBefore+
     " after="+qb.queuedBytes()
    );
  }finally{
   TradeService.unregister(a);
   TradeService.unregister(b);
   if(a.registered())
    w.unregisterPlayer(a,ga);
   if(b.registered())
    w.unregisterPlayer(b,gb);
   w.close();
  }
 }

 static void testOneSidedAcceptStatusAtomicity()throws Exception{
  World w=World.isolatedForTest(606L);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
  w.registerPlayer(a,"accept-a");
  w.registerPlayer(b,"accept-b");

  OutboundPacketQueue qa=
   new OutboundPacketQueue(2048);
  OutboundPacketQueue qb=
   new OutboundPacketQueue(1024);
  ServerPacketWriter wa=
   new ServerPacketWriter(
    qa,
    new IsaacCipher(
     new int[]{89,90,91,92}
    )
   );
  ServerPacketWriter wb=
   new ServerPacketWriter(
    qb,
    new IsaacCipher(
     new int[]{93,94,95,96}
    )
   );

  try{
   TradeService.register(
    w,a,a.generation(),a.bank(),wa,()->{}
   );
   TradeService.register(
    w,b,b.generation(),b.bank(),wb,()->{}
   );

   need(
    TradeService.start(w,a,b),
    "TRADE_UI_OPEN"
   );
   drain(qa);
   drain(qb);

   qb.offer(
    new byte[1024]
   );

   String firstRejected=
    TradeService.handleWidget(
     a,
     3420
    );

   need(
    firstRejected,
    "TRADE_FIRST_ACCEPT_REJECTED_PRESENTATION_ADMISSION"
   );

   if(qa.queuedBytes()!=0||
      qb.queuedBytes()!=1024)
    throw new AssertionError(
     "first accept rejection leaked one-sided status bytes a="+
     qa.queuedBytes()+
     " b="+
     qb.queuedBytes()
    );

   drain(qb);

   need(
    TradeService.handleWidget(
     b,
     3420
    ),
    "TRADE_FIRST_ACCEPT_WAITING_OTHER"
   );

   if(qa.queuedBytes()==0||
      qb.queuedBytes()==0)
    throw new AssertionError(
     "first accept retry emitted no paired status"
    );

   drain(qa);
   drain(qb);

   need(
    TradeService.handleWidget(
     a,
     3420
    ),
    "TRADE_FIRST_ACCEPT_BOTH_CONFIRM_OPEN"
   );

   drain(qa);
   drain(qb);

   qb.offer(
    new byte[1024]
   );

   String finalRejected=
    TradeService.handleWidget(
     a,
     3546
    );

   need(
    finalRejected,
    "TRADE_FINAL_ACCEPT_REJECTED_PRESENTATION_ADMISSION"
   );

   if(qa.queuedBytes()!=0||
      qb.queuedBytes()!=1024)
    throw new AssertionError(
     "final accept rejection leaked one-sided status bytes a="+
     qa.queuedBytes()+
     " b="+
     qb.queuedBytes()
    );

   drain(qb);

   need(
    TradeService.handleWidget(
     b,
     3546
    ),
    "TRADE_FINAL_ACCEPT_WAITING_OTHER"
   );

   if(qa.queuedBytes()==0||
      qb.queuedBytes()==0)
    throw new AssertionError(
     "final accept retry emitted no paired status"
    );

   drain(qa);
   drain(qb);

   need(
    TradeService.handleWidget(
     a,
     3546
    ),
    "TRADE_COMMITTED"
   );

   if(TradeService.active(a)||
      TradeService.active(b))
    throw new AssertionError(
     "acceptance retry fixture did not commit final Trade"
    );
  }finally{
   TradeService.unregister(a);
   TradeService.unregister(b);
   if(a.registered())
    w.unregisterPlayer(a);
   if(b.registered())
    w.unregisterPlayer(b);
   w.close();
  }
 }

 static void testOfferRefreshAtomicity()throws Exception{
  World w=World.isolatedForTest(607L);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
  w.registerPlayer(a,"refresh-a");
  w.registerPlayer(b,"refresh-b");

  OutboundPacketQueue qa=
   new OutboundPacketQueue(4096);
  OutboundPacketQueue qb=
   new OutboundPacketQueue(1024);
  ServerPacketWriter wa=
   new ServerPacketWriter(
    qa,
    new IsaacCipher(
     new int[]{97,98,99,100}
    )
   );
  ServerPacketWriter wb=
   new ServerPacketWriter(
    qb,
    new IsaacCipher(
     new int[]{101,102,103,104}
    )
   );

  try{
   a.bank().spawnItem(
    995,
    1000,
    wa
   );
   drain(qa);

   TradeService.register(
    w,a,a.generation(),a.bank(),wa,()->{}
   );
   TradeService.register(
    w,b,b.generation(),b.bank(),wb,()->{}
   );

   // Phase 1: rejected offer refresh preserves the already-committed
   // first-accept bit.
   need(
    TradeService.start(w,a,b),
    "TRADE_UI_OPEN"
   );
   drain(qa);drain(qb);

   need(
    TradeService.handleWidget(a,3420),
    "TRADE_FIRST_ACCEPT_WAITING_OTHER"
   );
   drain(qa);drain(qb);

   qb.offer(new byte[1024]);

   String rejectedOffer=
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      145,
      3322,
      find(a.bank(),995),
      995,
      0,
      "ITEM_ACTION_1"
     )
    );

   need(
    rejectedOffer,
    "TRADE_OFFER_CHANGE_REJECTED_PRESENTATION_ADMISSION"
   );

   if(qa.queuedBytes()!=0||
      qb.queuedBytes()!=1024)
    throw new AssertionError(
     "rejected offer refresh leaked postimage bytes a="+
     qa.queuedBytes()+
     " b="+
     qb.queuedBytes()
    );

   drain(qb);

   need(
    TradeService.handleWidget(b,3420),
    "TRADE_FIRST_ACCEPT_BOTH_CONFIRM_OPEN"
   );

   TradeService.cancelIfActive(
    a,
    "REFRESH_ACCEPTANCE_PHASE_CLEANUP"
   );
   drain(qa);drain(qb);

   // Phase 2: rejected direct Offer 1 preserves an existing Offer-X
   // request and does not mutate the canonical offer map.
   need(
    TradeService.start(w,a,b),
    "TRADE_UI_OPEN"
   );
   drain(qa);drain(qb);

   int coinSlot=find(a.bank(),995);

   need(
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      135,
      3322,
      coinSlot,
      995,
      0,
      "ITEM_ACTION_X"
     )
    ),
    "TRADE_OFFER_X_PROMPT"
   );
   drain(qa);

   qb.offer(new byte[1024]);

   String rejectedDirectOffer=
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      145,
      3322,
      coinSlot,
      995,
      0,
      "ITEM_ACTION_1"
     )
    );

   need(
    rejectedDirectOffer,
    "TRADE_OFFER_CHANGE_REJECTED_PRESENTATION_ADMISSION"
   );

   if(qa.queuedBytes()!=0||
      qb.queuedBytes()!=1024)
    throw new AssertionError(
     "rejected direct offer leaked postimage bytes"
    );

   drain(qb);

   need(
    TradeService.handleAmount(a,2),
    "TRADE_OFFER_X_OK item=995 qty=2"
   );
   drain(qa);drain(qb);

   String removeAllTwo=
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      129,
      3415,
      0,
      995,
      0,
      "ITEM_ACTION_ALL"
     )
    );

   need(
    removeAllTwo,
    "TRADE_REMOVE_OK item=995 qty=2"
   );
   drain(qa);drain(qb);

   // Phase 3: rejected direct Remove 1 preserves pending Remove-X and
   // leaves the canonical offer quantity unchanged.
   need(
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      117,
      3322,
      coinSlot,
      995,
      0,
      "ITEM_ACTION_5"
     )
    ),
    "TRADE_OFFER_OK item=995 qty=5"
   );
   drain(qa);drain(qb);

   need(
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      135,
      3415,
      0,
      995,
      0,
      "ITEM_ACTION_X"
     )
    ),
    "TRADE_REMOVE_X_PROMPT"
   );
   drain(qa);

   qb.offer(new byte[1024]);

   String rejectedDirectRemove=
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      145,
      3415,
      0,
      995,
      0,
      "ITEM_ACTION_1"
     )
    );

   need(
    rejectedDirectRemove,
    "TRADE_OFFER_CHANGE_REJECTED_PRESENTATION_ADMISSION"
   );

   if(qa.queuedBytes()!=0||
      qb.queuedBytes()!=1024)
    throw new AssertionError(
     "rejected direct remove leaked postimage bytes"
    );

   drain(qb);

   need(
    TradeService.handleAmount(a,2),
    "TRADE_REMOVE_X_OK item=995 qty=2"
   );
   drain(qa);drain(qb);

   String removeRemaining=
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      129,
      3415,
      0,
      995,
      0,
      "ITEM_ACTION_ALL"
     )
    );

   need(
    removeRemaining,
    "TRADE_REMOVE_OK item=995 qty=3"
   );
  }finally{
   TradeService.unregister(a);
   TradeService.unregister(b);
   if(a.registered())
    w.unregisterPlayer(a);
   if(b.registered())
    w.unregisterPlayer(b);
   w.close();
  }
 }

 static void testDirectOneSidedAcceptStatusTerminalFailure(
  boolean finalStage
 )throws Exception{
  World w=World.isolatedForTest(finalStage?610L:609L);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
  long ga=w.registerPlayer(a,finalStage?"direct-final-a":"direct-first-a");
  long gb=w.registerPlayer(b,finalStage?"direct-final-b":"direct-first-b");

  SwitchFailOutputStream outA=new SwitchFailOutputStream();
  SwitchFailOutputStream outB=new SwitchFailOutputStream();
  ServerPacketWriter wa=
   new ServerPacketWriter(
    outA,
    new IsaacCipher(finalStage
     ?new int[]{101,102,103,104}
     :new int[]{93,94,95,96})
   );
  ServerPacketWriter wb=
   new ServerPacketWriter(
    outB,
    new IsaacCipher(finalStage
     ?new int[]{105,106,107,108}
     :new int[]{97,98,99,100})
   );

  NpcRegistry aNpcs=new NpcRegistry(new DevAuthorityWorkbench());
  NpcRegistry bNpcs=new NpcRegistry(new DevAuthorityWorkbench());

  Player81WorldSync.Context aSync=
   Player81WorldSync.register(
    wa,w,a,new DevAuthorityWorkbench()
   );
  Player81WorldSync.register(
   wb,w,b,new DevAuthorityWorkbench()
  );
  SharedNpcWorldRelay.register(
   wa,w,a,aNpcs,a.movement()
  );
  SharedNpcWorldRelay.register(
   wb,w,b,bNpcs,b.movement()
  );

  TradeService.register(w,a,ga,a.bank(),wa,()->{});
  TradeService.register(w,b,gb,b.bank(),wb,()->{});

  try{
   need(TradeService.start(w,a,b),"TRADE_UI_OPEN");

   if(finalStage){
    need(
     TradeService.handleWidget(a,3420),
     "WAITING_OTHER"
    );
    need(
     TradeService.handleWidget(b,3420),
     "CONFIRM_OPEN"
    );
   }

   Object aRelay=relayContextFor(wa);
   Object bRelay=relayContextFor(wb);
   int aAttemptsBefore=outA.attempts;
   int bAttemptsBefore=outB.attempts;

   outB.fail=true;

   boolean failed=false;
   try{
    TradeService.handleWidget(
     a,
     finalStage?3546:3420
    );
   }catch(IOException expected){
    failed="SWITCH_FAIL".equals(expected.getMessage());
   }

   if(!failed)
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " accept-status peer failure was not propagated"
    );

   if(outB.attempts!=bAttemptsBefore+1)
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " failed peer writer retouched attemptsBefore="+
     bAttemptsBefore+" after="+outB.attempts
    );

   if(outA.attempts!=aAttemptsBefore+2)
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " healthy accept writer expected status+close attemptsBefore="+
     aAttemptsBefore+" after="+outA.attempts
    );

   if(TradeService.active(a)||TradeService.active(b))
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " accept-status failure retained live Trade"
    );

   if(!wb.terminal())
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " failed peer writer not terminal-latched"
    );

   if(player81ContextFor(wb)!=null)
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " failed peer retained Player81 authority"
    );

   if(relayContextFor(wb)!=bRelay)
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " failed peer SharedNpc sentinel changed"
    );

   if(wa.terminal()||
      player81ContextFor(wa)!=aSync||
      relayContextFor(wa)!=aRelay)
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " failure retired healthy accepting runtime"
    );

   int bAttemptsAfter=outB.attempts;
   boolean rejected=false;
   try{
    SharedNpcWorldRelay.preflightRegistration(
     wb,w,b
    );
   }catch(SharedNpcWorldRelay.TerminalRegistrationException expected){
    rejected=expected.owner==b&&expected.writer==wb;
   }

   if(!rejected)
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " terminal peer writer was resurrectable"
    );

   if(outB.attempts!=bAttemptsAfter)
    throw new AssertionError(
     "direct "+(finalStage?"final":"first")+
     " preflight retouched terminal peer writer"
    );
  }finally{
   outB.fail=false;
   TradeService.unregister(a);
   TradeService.unregister(b);
   SharedNpcWorldRelay.unregister(wa);
   SharedNpcWorldRelay.unregister(wb);
   Player81WorldSync.unregister(wa);
   Player81WorldSync.unregister(wb);
   if(a.registered())w.unregisterPlayer(a,ga);
   if(b.registered())w.unregisterPlayer(b,gb);
   w.close();
  }
 }

 static void testDirectOfferPostimageRejectsBeforeBytes()throws Exception{
  World w=World.isolatedForTest(611L);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
  long ga=w.registerPlayer(a,"direct-offer-a");
  long gb=w.registerPlayer(b,"direct-offer-b");

  SwitchFailOutputStream outA=new SwitchFailOutputStream();
  SwitchFailOutputStream outB=new SwitchFailOutputStream();
  ServerPacketWriter wa=
   new ServerPacketWriter(
    outA,
    new IsaacCipher(new int[]{109,110,111,112})
   );
  ServerPacketWriter wb=
   new ServerPacketWriter(
    outB,
    new IsaacCipher(new int[]{113,114,115,116})
   );

  try{
   a.bank().spawnItem(995,100,wa);

   TradeService.register(w,a,ga,a.bank(),wa,()->{});
   TradeService.register(w,b,gb,b.bank(),wb,()->{});

   need(TradeService.start(w,a,b),"TRADE_UI_OPEN");

   int coinSlot=find(a.bank(),995);
   if(coinSlot<0)
    throw new AssertionError("direct offer fixture missing coins");

   int aAttemptsBefore=outA.attempts;
   int bAttemptsBefore=outB.attempts;

   String rejected=
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      145,
      3322,
      coinSlot,
      995,
      0,
      "ITEM_ACTION_1"
     )
    );

   need(
    rejected,
    "TRADE_OFFER_CHANGE_REJECTED_DIRECT_NONATOMIC"
   );

   if(outA.attempts!=aAttemptsBefore||
      outB.attempts!=bAttemptsBefore)
    throw new AssertionError(
     "direct non-atomic offer rejection emitted bytes A="+
     (outA.attempts-aAttemptsBefore)+
     " B="+
     (outB.attempts-bAttemptsBefore)
    );

   if(!TradeService.active(a)||!TradeService.active(b))
    throw new AssertionError(
     "direct non-atomic offer rejection retired live Trade"
    );

   String remove=
    TradeService.handleItemAction(
     a,
     new ItemContainerAction(
      145,
      3415,
      0,
      995,
      0,
      "ITEM_ACTION_1"
     )
    );

   need(
    remove,
    "TRADE_REMOVE_REJECTED_SLOT_MISMATCH"
   );

   if(outA.attempts!=aAttemptsBefore||
      outB.attempts!=bAttemptsBefore)
    throw new AssertionError(
     "direct rejected offer canonical-empty probe emitted bytes"
    );
  }finally{
   TradeService.unregister(a);
   TradeService.unregister(b);
   if(a.registered())w.unregisterPlayer(a,ga);
   if(b.registered())w.unregisterPlayer(b,gb);
   w.close();
  }
 }

 static void testDirectFinalCommitRejectsBeforeBytes()throws Exception{
  World w=World.isolatedForTest(612L);
  WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
  long ga=w.registerPlayer(a,"direct-final-commit-a");
  long gb=w.registerPlayer(b,"direct-final-commit-b");

  SwitchFailOutputStream outA=new SwitchFailOutputStream();
  SwitchFailOutputStream outB=new SwitchFailOutputStream();
  ServerPacketWriter wa=
   new ServerPacketWriter(
    outA,
    new IsaacCipher(new int[]{117,118,119,120})
   );
  ServerPacketWriter wb=
   new ServerPacketWriter(
    outB,
    new IsaacCipher(new int[]{121,122,123,124})
   );

  try{
   a.bank().spawnItem(995,100,wa);
   b.bank().spawnItem(385,2,wb);

   TradeService.register(w,a,ga,a.bank(),wa,()->{});
   TradeService.register(w,b,gb,b.bank(),wb,()->{});

   need(TradeService.start(w,a,b),"TRADE_UI_OPEN");
   need(TradeService.handleWidget(a,3420),"WAITING_OTHER");
   need(TradeService.handleWidget(b,3420),"CONFIRM_OPEN");
   need(TradeService.handleWidget(a,3546),"WAITING_OTHER");

   int aCoinsBefore=a.bank().inventoryCount(995);
   int aSharksBefore=a.bank().inventoryCount(385);
   int bCoinsBefore=b.bank().inventoryCount(995);
   int bSharksBefore=b.bank().inventoryCount(385);
   int aAttemptsBefore=outA.attempts;
   int bAttemptsBefore=outB.attempts;

   String rejected=
    TradeService.handleWidget(
     b,
     3546
    );

   need(
    rejected,
    "TRADE_COMMIT_REJECTED_DIRECT_NONATOMIC"
   );

   if(outA.attempts!=aAttemptsBefore||
      outB.attempts!=bAttemptsBefore)
    throw new AssertionError(
     "direct final commit rejection emitted final bytes A="+
     (outA.attempts-aAttemptsBefore)+
     " B="+
     (outB.attempts-bAttemptsBefore)
    );

   if(a.bank().inventoryCount(995)!=aCoinsBefore||
      a.bank().inventoryCount(385)!=aSharksBefore||
      b.bank().inventoryCount(995)!=bCoinsBefore||
      b.bank().inventoryCount(385)!=bSharksBefore)
    throw new AssertionError(
     "direct final commit rejection mutated canonical inventories"
    );

   if(!TradeService.active(a)||!TradeService.active(b))
    throw new AssertionError(
     "direct final commit rejection retired retryable Trade"
    );

   if(wa.terminal()||wb.terminal())
    throw new AssertionError(
     "zero-byte direct final commit rejection terminal-latched writer"
    );

   String retry=
    TradeService.handleWidget(
     b,
     3546
    );

   need(
    retry,
    "TRADE_COMMIT_REJECTED_DIRECT_NONATOMIC"
   );

   if(outA.attempts!=aAttemptsBefore||
      outB.attempts!=bAttemptsBefore)
    throw new AssertionError(
     "direct final commit retry emitted final bytes"
    );
  }finally{
   TradeService.unregister(a);
   TradeService.unregister(b);
   if(a.registered())w.unregisterPlayer(a,ga);
   if(b.registered())w.unregisterPlayer(b,gb);
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
  int attempts;
  boolean fail;

  @Override public void write(int value)throws IOException{
   attempts++;
   if(fail)throw new IOException("SWITCH_FAIL");
   bytes.write(value);
  }

  @Override public void write(byte[] data,int offset,int length)throws IOException{
   attempts++;
   if(fail)throw new IOException("SWITCH_FAIL");
   writes++;
   bytes.write(data,offset,length);
  }
 }

 static Object relayContextFor(ServerPacketWriter writer)throws Exception{
  Field f=SharedNpcWorldRelay.class.getDeclaredField("BY_WRITER");
  f.setAccessible(true);
  synchronized(SharedNpcWorldRelay.class){
   @SuppressWarnings("unchecked")
   IdentityHashMap<ServerPacketWriter,Object> m=
    (IdentityHashMap<ServerPacketWriter,Object>)f.get(null);
   return m.get(writer);
  }
 }
 static Object player81ContextFor(ServerPacketWriter writer)throws Exception{
  Field f=Player81WorldSync.class.getDeclaredField("BY_WRITER");
  f.setAccessible(true);
  synchronized(Player81WorldSync.class){
   @SuppressWarnings("unchecked")
   IdentityHashMap<ServerPacketWriter,Object> m=
    (IdentityHashMap<ServerPacketWriter,Object>)f.get(null);
   return m.get(writer);
  }
 }

 static byte[] drain(OutboundPacketQueue q)throws Exception{java.io.ByteArrayOutputStream o=new java.io.ByteArrayOutputStream();q.drainTo(o,1<<20);return o.toByteArray();}
 static int find(BankState b,int id){for(int i=0;i<b.inventoryCapacity();i++){BankState.Stack s=b.inventoryAt(i);if(s!=null&&s.itemId==id)return i;}return -1;}
 static void need(String s,String n){if(s==null||!s.contains(n))throw new AssertionError("expected "+n+" got "+s);}
}
