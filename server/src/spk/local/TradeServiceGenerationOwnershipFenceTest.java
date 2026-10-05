package spk.local;

public final class TradeServiceGenerationOwnershipFenceTest {
    public static void main(String[] args)throws Exception{
        assertFinalCommitDoesNotAcquireParticipantMutationLock();
        assertTerminalCleanupLatchInsideRegistryFence();
        assertCancellationCloseSerializesGenerationChange();
        assertTerminalPeerCloseSerializesGenerationChange();
        assertCompetingRootPeerCloseSerializesGenerationChange();
        assertReplacementOldPeerCloseSerializesGenerationChange();

        World world=
            World.isolatedForTest(600L);

        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();

        long generationA1=
            world.registerPlayer(
                a,
                "trade-generation-a"
            );
        long generationB=
            world.registerPlayer(
                b,
                "trade-generation-b"
            );

        OutboundPacketQueue oldAOut=
            new OutboundPacketQueue();
        OutboundPacketQueue bOut=
            new OutboundPacketQueue();

        ServerPacketWriter oldAWriter=
            writer(oldAOut,1);
        ServerPacketWriter bWriter=
            writer(bOut,5);

        ServerPacketWriter freshAWriter=null;

        try{
            a.bank().spawnItem(
                995,
                100,
                oldAWriter
            );
            b.bank().spawnItem(
                385,
                2,
                bWriter
            );

            TradeService.register(
                world,
                a,
                generationA1,
                a.bank(),
                oldAWriter,
                ()->{}
            );
            TradeService.register(
                world,
                b,
                generationB,
                b.bank(),
                bWriter,
                ()->{}
            );

            String initial=
                TradeService.start(
                    world,
                    a,
                    b
                );

            requireContains(
                initial,
                "TRADE_UI_OPEN",
                "generation A initial trade"
            );

            if(!TradeService.active(a)||
               !TradeService.active(b))
                throw new AssertionError(
                    "generation A trade not active"
                );

            if(!world.unregisterPlayer(
                    a,
                    generationA1
                ))
                throw new AssertionError(
                    "generation A unregister failed"
                );

            long generationA2=
                world.registerPlayer(
                    a,
                    "trade-generation-a"
                );

            if(generationA2==generationA1)
                throw new AssertionError(
                    "generation did not advance"
                );

            int oldABytes=
                oldAOut.queuedBytes();
            int bBytesBeforeReplacement=
                bOut.queuedBytes();

            if(TradeService.active(a))
                throw new AssertionError(
                    "stale generation remained active"
                );

            String staleWidget=
                TradeService.handleWidget(
                    a,
                    TradeService.FIRST_ACCEPT
                );

            if(staleWidget!=null)
                throw new AssertionError(
                    "stale generation handled widget: "+
                    staleWidget
                );

            String staleStart=
                TradeService.start(
                    world,
                    a,
                    b
                );

            requireContains(
                staleStart,
                "TRADE_UI_REJECTED_STALE_CONTEXT",
                "stale context trade start"
            );

            if(oldAOut.queuedBytes()!=oldABytes)
                throw new AssertionError(
                    "stale generation wrote old writer"
                );

            OutboundPacketQueue freshAOut=
                new OutboundPacketQueue();
            freshAWriter=
                writer(
                    freshAOut,
                    9
                );

            TradeService.register(
                world,
                a,
                generationA2,
                a.bank(),
                freshAWriter,
                ()->{}
            );

            if(oldAOut.queuedBytes()!=oldABytes)
                throw new AssertionError(
                    "replacement registration notified stale writer"
                );

            if(bOut.queuedBytes()!=bBytesBeforeReplacement)
                throw new AssertionError(
                    "replacement registration notified stale trade peer"
                );

            String replacement=
                TradeService.start(
                    world,
                    a,
                    b
                );

            requireContains(
                replacement,
                "TRADE_UI_OPEN",
                "replacement trade"
            );

            TradeService.unregister(
                a,
                oldAWriter
            );

            if(!TradeService.active(a)||
               !TradeService.active(b))
                throw new AssertionError(
                    "stale writer unregister removed replacement trade"
                );

            int coinSlot=
                find(
                    a.bank(),
                    995
                );
            int sharkSlot=
                find(
                    b.bank(),
                    385
                );

            requireContains(
                TradeService.handleItemAction(
                    a,
                    new ItemContainerAction(
                        145,
                        TradeService.INVENTORY_GRID,
                        coinSlot,
                        995,
                        0,
                        "ITEM_ACTION_1"
                    )
                ),
                "TRADE_OFFER_OK",
                "replacement coin offer"
            );

            requireContains(
                TradeService.handleItemAction(
                    b,
                    new ItemContainerAction(
                        145,
                        TradeService.INVENTORY_GRID,
                        sharkSlot,
                        385,
                        0,
                        "ITEM_ACTION_1"
                    )
                ),
                "TRADE_OFFER_OK",
                "replacement shark offer"
            );

            requireContains(
                TradeService.handleWidget(
                    a,
                    TradeService.FIRST_ACCEPT
                ),
                "WAITING_OTHER",
                "first accept A"
            );

            requireContains(
                TradeService.handleWidget(
                    b,
                    TradeService.FIRST_ACCEPT
                ),
                "CONFIRM_OPEN",
                "first accept B"
            );

            requireContains(
                TradeService.handleWidget(
                    a,
                    TradeService.FINAL_ACCEPT
                ),
                "WAITING_OTHER",
                "final accept A"
            );

            int aCoinsBefore=
                a.bank().inventoryCount(995);
            int aSharksBefore=
                a.bank().inventoryCount(385);
            int bCoinsBefore=
                b.bank().inventoryCount(995);
            int bSharksBefore=
                b.bank().inventoryCount(385);

            int freshABytesBeforeStaleCommit=
                freshAOut.queuedBytes();
            int bBytesBeforeStaleCommit=
                bOut.queuedBytes();

            if(!world.unregisterPlayer(
                    a,
                    generationA2
                ))
                throw new AssertionError(
                    "generation A2 unregister failed"
                );

            long generationA3=
                world.registerPlayer(
                    a,
                    "trade-generation-a"
                );

            String staleCommit=
                TradeService.handleWidget(
                    b,
                    TradeService.FINAL_ACCEPT
                );

            if(staleCommit!=null)
                throw new AssertionError(
                    "stale participant trade progressed: "+
                    staleCommit
                );

            if(a.bank().inventoryCount(995)!=
                    aCoinsBefore||
               a.bank().inventoryCount(385)!=
                    aSharksBefore||
               b.bank().inventoryCount(995)!=
                    bCoinsBefore||
               b.bank().inventoryCount(385)!=
                    bSharksBefore)
                throw new AssertionError(
                    "stale trade mutated inventory"
                );

            if(freshAOut.queuedBytes()!=
                    freshABytesBeforeStaleCommit||
               bOut.queuedBytes()!=
                    bBytesBeforeStaleCommit)
                throw new AssertionError(
                    "stale trade cancellation emitted UI output"
                );

            if(TradeService.active(a)||
               TradeService.active(b))
                throw new AssertionError(
                    "stale trade remained active"
                );

            TradeService.unregister(
                a,
                freshAWriter
            );
            TradeService.unregister(b);

            world.unregisterPlayer(
                a,
                generationA3
            );
            world.unregisterPlayer(
                b,
                generationB
            );

            System.out.println(
                "TRADE_GENERATION_OWNERSHIP_FENCE_PASS "+
                "staleContextHidden=true "+
                "staleStartRejected=true "+
                "replacementNoStaleNotify=true "+
                "staleWriterUnregisterFenced=true "+
                "cancellationCloseRegistryLinearized=true "+
                "terminalPeerCloseRegistryLinearized=true "+
                "competingRootPeerCloseRegistryLinearized=true "+
                "replacementOldPeerCloseGenerationSerialized=true "+
                "terminalCleanupLatchedInsideRegistry=true "+
                "terminalCleanupWriterNotReusable=true "+
                "cleanupGenerationFenceUsesRegistry=true "+
                "finalCommitMutationLockFree=true "+
                "postCommitSavesOutsideTradeLocks=true "+
                "staleFinalCommitRejected=true "+
                "inventoryUnchanged=true"
            );
        }finally{
            TradeService.unregister(a);
            TradeService.unregister(b);

            if(a.registered())
                world.unregisterPlayer(
                    a,
                    a.generation()
                );

            if(b.registered())
                world.unregisterPlayer(
                    b,
                    b.generation()
                );

            world.close();
        }
    }

    private static void assertFinalCommitDoesNotAcquireParticipantMutationLock()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                605L
            );
        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                a,
                "trade-final-lock-a"
            );
        long generationB=
            world.registerPlayer(
                b,
                "trade-final-lock-b"
            );

        OutboundPacketQueue aOut=
            new OutboundPacketQueue();
        OutboundPacketQueue bOut=
            new OutboundPacketQueue();
        ServerPacketWriter aWriter=
            writer(
                aOut,
                61
            );
        ServerPacketWriter bWriter=
            writer(
                bOut,
                65
            );

        final Throwable[] threadFailure={
            null
        };
        final String[] commitResult={
            null
        };
        final int[] saveOrder={
            0
        };
        final boolean[] saveOutsideTradeLocks={
            false,
            false
        };
        Thread commitThread=null;
        Thread registryProbeThread=null;
        final java.util.concurrent.CountDownLatch saveAEntered=
            new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch releaseSaveA=
            new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch registryProbeAcquired=
            new java.util.concurrent.CountDownLatch(1);

        try{
            a.bank().spawnItem(
                995,
                100,
                aWriter
            );
            b.bank().spawnItem(
                385,
                2,
                bWriter
            );

            TradeService.register(
                world,
                a,
                generationA,
                a.bank(),
                aWriter,
                ()->{
                    if(Thread.holdsLock(
                            TradeService.class
                        )||
                       Thread.holdsLock(
                            world.players()
                        ))
                        throw new AssertionError(
                            "Trade A save callback retained Trade/registry lock"
                        );

                    if(saveOrder[0]!=0)
                        throw new AssertionError(
                            "Trade A save callback order="+
                            saveOrder[0]
                        );

                    saveOrder[0]=1;
                    saveOutsideTradeLocks[0]=true;
                    saveAEntered.countDown();

                    try{
                        if(!releaseSaveA.await(
                                5L,
                                java.util.concurrent.TimeUnit.SECONDS
                            ))
                            throw new AssertionError(
                                "Trade A save callback release timed out"
                            );
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new AssertionError(
                            "Trade A save callback interrupted",
                            interrupted
                        );
                    }
                }
            );
            TradeService.register(
                world,
                b,
                generationB,
                b.bank(),
                bWriter,
                ()->{
                    if(Thread.holdsLock(
                            TradeService.class
                        )||
                       Thread.holdsLock(
                            world.players()
                        ))
                        throw new AssertionError(
                            "Trade B save callback retained Trade/registry lock"
                        );

                    if(saveOrder[0]!=1)
                        throw new AssertionError(
                            "Trade B save callback order="+
                            saveOrder[0]
                        );

                    saveOrder[0]=2;
                    saveOutsideTradeLocks[1]=true;
                }
            );

            requireContains(
                TradeService.start(
                    world,
                    a,
                    b
                ),
                "TRADE_UI_OPEN",
                "final lock trade open"
            );

            requireContains(
                TradeService.handleItemAction(
                    a,
                    new ItemContainerAction(
                        145,
                        TradeService.INVENTORY_GRID,
                        find(a.bank(),995),
                        995,
                        0,
                        "ITEM_ACTION_1"
                    )
                ),
                "TRADE_OFFER_OK",
                "final lock A offer"
            );

            requireContains(
                TradeService.handleItemAction(
                    b,
                    new ItemContainerAction(
                        145,
                        TradeService.INVENTORY_GRID,
                        find(b.bank(),385),
                        385,
                        0,
                        "ITEM_ACTION_1"
                    )
                ),
                "TRADE_OFFER_OK",
                "final lock B offer"
            );

            requireContains(
                TradeService.handleWidget(
                    a,
                    TradeService.FIRST_ACCEPT
                ),
                "WAITING_OTHER",
                "final lock first accept A"
            );
            requireContains(
                TradeService.handleWidget(
                    b,
                    TradeService.FIRST_ACCEPT
                ),
                "CONFIRM_OPEN",
                "final lock first accept B"
            );
            requireContains(
                TradeService.handleWidget(
                    a,
                    TradeService.FINAL_ACCEPT
                ),
                "WAITING_OTHER",
                "final lock final accept A"
            );

            synchronized(b.mutationLock()){
                commitThread=
                    new Thread(
                        ()->{
                            try{
                                commitResult[0]=
                                    TradeService.handleWidget(
                                        b,
                                        TradeService.FINAL_ACCEPT
                                    );
                            }catch(Throwable failure){
                                threadFailure[0]=failure;
                            }
                        },
                        "trade-final-commit-lock-order"
                    );

                commitThread.start();

                if(!saveAEntered.await(
                        5L,
                        java.util.concurrent.TimeUnit.SECONDS
                    ))
                    throw new AssertionError(
                        "final Trade commit did not reach deferred A save"
                    );

                registryProbeThread=
                    new Thread(
                        ()->{
                            synchronized(world.players()){
                                registryProbeAcquired.countDown();
                            }
                        },
                        "trade-final-save-registry-probe"
                    );
                registryProbeThread.start();

                if(!registryProbeAcquired.await(
                        5L,
                        java.util.concurrent.TimeUnit.SECONDS
                    ))
                    throw new AssertionError(
                        "Trade A save callback retained PlayerRegistry monitor"
                    );

                releaseSaveA.countDown();

                commitThread.join(
                    5000L
                );
                registryProbeThread.join(
                    5000L
                );

                if(commitThread.isAlive())
                    throw new AssertionError(
                        "final Trade commit waited on participant mutation lock"
                    );

                if(registryProbeThread.isAlive())
                    throw new AssertionError(
                        "PlayerRegistry probe did not terminate"
                    );
            }

            if(threadFailure[0]!=null)
                throw new AssertionError(
                    "final Trade commit thread failed",
                    threadFailure[0]
                );

            requireContains(
                commitResult[0],
                "TRADE_COMMITTED",
                "final Trade commit result"
            );

            if(saveOrder[0]!=2||
               !saveOutsideTradeLocks[0]||
               !saveOutsideTradeLocks[1])
                throw new AssertionError(
                    "Trade post-commit saves did not run outside locks/order="+
                    saveOrder[0]+
                    " outsideA="+
                    saveOutsideTradeLocks[0]+
                    " outsideB="+
                    saveOutsideTradeLocks[1]
                );

            if(TradeService.active(a)||
               TradeService.active(b))
                throw new AssertionError(
                    "final Trade commit remained active"
                );

            if(a.bank().inventoryCount(385)!=1||
               b.bank().inventoryCount(995)!=1)
                throw new AssertionError(
                    "final Trade commit inventory transfer missing"
                );
        }finally{
            releaseSaveA.countDown();

            if(commitThread!=null&&
               commitThread.isAlive())
                commitThread.interrupt();
            if(registryProbeThread!=null&&
               registryProbeThread.isAlive())
                registryProbeThread.interrupt();

            TradeService.unregister(
                a,
                aWriter
            );
            TradeService.unregister(
                b,
                bWriter
            );

            if(a.registered())
                world.unregisterPlayer(
                    a,
                    generationA
                );
            if(b.registered())
                world.unregisterPlayer(
                    b,
                    generationB
                );

            world.close();
        }
    }

    private static void assertTerminalCleanupLatchInsideRegistryFence()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                605L
            );
        WorldPlayer participant=
            new WorldPlayer();
        WorldPlayer peer=
            new WorldPlayer();

        long participantGeneration=
            world.registerPlayer(
                participant,
                "trade-terminal-latch-a"
            );
        long peerGeneration=
            world.registerPlayer(
                peer,
                "trade-terminal-latch-b"
            );

        SwitchableFailOutputStream participantOut=
            new SwitchableFailOutputStream();
        ServerPacketWriter participantWriter=
            new ServerPacketWriter(
                participantOut,
                new IsaacCipher(
                    new int[]{61,62,63,64}
                )
            );

        OutboundPacketQueue peerOut=
            new OutboundPacketQueue();
        ServerPacketWriter peerWriter=
            writer(
                peerOut,
                65
            );

        long replacementGeneration=0L;

        try{
            TradeService.register(
                world,
                participant,
                participantGeneration,
                participant.bank(),
                participantWriter,
                ()->{}
            );
            TradeService.register(
                world,
                peer,
                peerGeneration,
                peer.bank(),
                peerWriter,
                ()->{}
            );

            requireContains(
                TradeService.start(
                    world,
                    participant,
                    peer
                ),
                "TRADE_UI_OPEN",
                "terminal cleanup latch trade open"
            );

            int attemptsBeforeFailure=
                participantOut.attempts;
            int peerBytesBefore=
                peerOut.queuedBytes();

            participantOut.fail=true;

            String cancelled=
                TradeService.handleWidget(
                    participant,
                    TradeService.FIRST_DECLINE
                );

            requireContains(
                cancelled,
                "TRADE_CANCELLED_DECLINE",
                "terminal cleanup latch cancellation"
            );

            if(!participantWriter.terminal())
                throw new AssertionError(
                    "terminal cleanup failure did not latch exact writer"
                );

            if(participantOut.attempts!=
                    attemptsBeforeFailure+1)
                throw new AssertionError(
                    "terminal cleanup failure transport attempts changed expected="+
                    (attemptsBeforeFailure+1)+
                    " actual="+
                    participantOut.attempts
                );

            if(peerOut.queuedBytes()!=
                    peerBytesBefore+1)
                throw new AssertionError(
                    "terminal cleanup healthy peer did not receive exactly one close"
                );

            participantOut.fail=false;
            int attemptsBeforeProbe=
                participantOut.attempts;
            boolean probeRejected=false;

            try{
                participantWriter.fixed(
                    97,
                    new byte[0]
                );
            }catch(java.io.IOException expected){
                probeRejected=true;
            }

            if(!probeRejected)
                throw new AssertionError(
                    "terminal cleanup writer accepted later direct publication"
                );

            if(participantOut.attempts!=
                    attemptsBeforeProbe)
                throw new AssertionError(
                    "terminal cleanup writer retouched underlying transport"
                );

            if(!world.unregisterPlayer(
                    participant,
                    participantGeneration
                ))
                throw new AssertionError(
                    "terminal cleanup generation unregister failed"
                );

            replacementGeneration=
                world.registerPlayer(
                    participant,
                    "trade-terminal-latch-a"
                );

            boolean reuseRejected=false;

            try{
                Player81WorldSync
                    .preparePlayerOptionsForRegistration(
                        world,
                        participant,
                        participantWriter
                    );
            }catch(
                Player81WorldSync
                    .TerminalPlayerOptionsException expected
            ){
                reuseRejected=
                    expected.owner==participant&&
                    expected.writer==participantWriter;
            }

            if(!reuseRejected)
                throw new AssertionError(
                    "replacement generation reused terminal Trade cleanup writer"
                );
        }finally{
            participantOut.fail=false;

            TradeService.unregister(
                participant,
                participantWriter
            );
            TradeService.unregister(
                peer,
                peerWriter
            );
            Player81WorldSync.unregister(
                participantWriter
            );
            Player81WorldSync.unregister(
                peerWriter
            );

            if(participant.registered())
                world.unregisterPlayer(
                    participant,
                    replacementGeneration!=0L
                        ?replacementGeneration
                        :participant.generation()
                );

            if(peer.registered())
                world.unregisterPlayer(
                    peer,
                    peerGeneration
                );

            world.close();
        }
    }

    private static void assertCancellationCloseSerializesGenerationChange()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                601L
            );
        WorldPlayer participant=
            new WorldPlayer();
        WorldPlayer peer=
            new WorldPlayer();

        long participantGeneration=
            world.registerPlayer(
                participant,
                "trade-cancel-fence-a"
            );
        long peerGeneration=
            world.registerPlayer(
                peer,
                "trade-cancel-fence-b"
            );

        OutboundPacketQueue participantOut=
            new OutboundPacketQueue();
        OutboundPacketQueue peerOut=
            new OutboundPacketQueue();
        ServerPacketWriter participantWriter=
            writer(
                participantOut,
                21
            );
        ServerPacketWriter peerWriter=
            writer(
                peerOut,
                25
            );

        final long[] replacementGeneration={
            0L
        };
        final Throwable[] cancelFailure={
            null
        };
        final Throwable[] rebindFailure={
            null
        };
        final String[] cancelResult={
            null
        };
        Thread cancelThread=null;
        Thread rebindThread=null;

        try{
            TradeService.register(
                world,
                participant,
                participantGeneration,
                participant.bank(),
                participantWriter,
                ()->{}
            );
            TradeService.register(
                world,
                peer,
                peerGeneration,
                peer.bank(),
                peerWriter,
                ()->{}
            );

            requireContains(
                TradeService.start(
                    world,
                    participant,
                    peer
                ),
                "TRADE_UI_OPEN",
                "cancellation fence trade open"
            );

            int participantBytesBefore=
                participantOut.queuedBytes();
            int peerBytesBefore=
                peerOut.queuedBytes();

            synchronized(participantWriter){
                cancelThread=
                    new Thread(
                        ()->{
                            try{
                                cancelResult[0]=
                                    TradeService.handleWidget(
                                        participant,
                                        TradeService.FIRST_DECLINE
                                    );
                            }catch(Throwable failure){
                                cancelFailure[0]=failure;
                            }
                        },
                        "trade-cancel-registry-linearization"
                    );

                cancelThread.start();

                awaitBlocked(
                    cancelThread,
                    "cancellation close writer boundary"
                );

                rebindThread=
                    new Thread(
                        ()->{
                            try{
                                if(!world.unregisterPlayer(
                                        participant,
                                        participantGeneration
                                    ))
                                    throw new AssertionError(
                                        "cancellation rebind unregister failed"
                                    );

                                replacementGeneration[0]=
                                    world.registerPlayer(
                                        participant,
                                        "trade-cancel-fence-a"
                                    );
                            }catch(Throwable failure){
                                rebindFailure[0]=failure;
                            }
                        },
                        "trade-cancel-registry-rebind"
                    );

                rebindThread.start();

                awaitBlocked(
                    rebindThread,
                    "cancellation rebind registry boundary"
                );
            }

            joinThread(
                cancelThread,
                "cancellation thread"
            );
            joinThread(
                rebindThread,
                "cancellation rebind thread"
            );

            if(cancelFailure[0]!=null)
                throw new AssertionError(
                    "cancellation thread failed",
                    cancelFailure[0]
                );
            if(rebindFailure[0]!=null)
                throw new AssertionError(
                    "cancellation rebind failed",
                    rebindFailure[0]
                );

            requireContains(
                cancelResult[0],
                "TRADE_CANCELLED_DECLINE",
                "cancellation result"
            );

            if(replacementGeneration[0]==0L||
               replacementGeneration[0]==
                    participantGeneration)
                throw new AssertionError(
                    "cancellation generation did not advance"
                );

            if(participantOut.queuedBytes()!=
                    participantBytesBefore+1)
                throw new AssertionError(
                    "cancellation participant close did not linearize before rebind before="+
                    participantBytesBefore+
                    " after="+
                    participantOut.queuedBytes()
                );

            if(peerOut.queuedBytes()!=
                    peerBytesBefore+1)
                throw new AssertionError(
                    "cancellation peer did not receive exactly one close before="+
                    peerBytesBefore+
                    " after="+
                    peerOut.queuedBytes()
                );

            if(TradeService.active(
                    participant
                )||
               TradeService.active(
                    peer
               ))
                throw new AssertionError(
                    "cancellation fence retained live Trade"
                );
        }finally{
            if(cancelThread!=null&&
               cancelThread.isAlive())
                cancelThread.interrupt();
            if(rebindThread!=null&&
               rebindThread.isAlive())
                rebindThread.interrupt();

            TradeService.unregister(
                participant,
                participantWriter
            );
            TradeService.unregister(
                peer,
                peerWriter
            );

            if(participant.registered())
                world.unregisterPlayer(
                    participant,
                    replacementGeneration[0]!=0L
                        ?replacementGeneration[0]
                        :participant.generation()
                );

            if(peer.registered())
                world.unregisterPlayer(
                    peer,
                    peerGeneration
                );

            world.close();
        }
    }

    private static void assertTerminalPeerCloseSerializesGenerationChange()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                602L
            );
        WorldPlayer broken=
            new WorldPlayer();
        WorldPlayer peer=
            new WorldPlayer();

        long brokenGeneration=
            world.registerPlayer(
                broken,
                "trade-terminal-fence-broken"
            );
        long peerGeneration=
            world.registerPlayer(
                peer,
                "trade-terminal-fence-peer"
            );

        OutboundPacketQueue brokenOut=
            new OutboundPacketQueue();
        OutboundPacketQueue peerOut=
            new OutboundPacketQueue();
        ServerPacketWriter brokenWriter=
            writer(
                brokenOut,
                31
            );
        ServerPacketWriter peerWriter=
            writer(
                peerOut,
                35
            );

        final long[] replacementPeerGeneration={
            0L
        };
        final Throwable[] retireFailure={
            null
        };
        final Throwable[] rebindFailure={
            null
        };
        final TradeService.BrokenWriterRetirement[] retirement={
            null
        };
        Thread retireThread=null;
        Thread rebindThread=null;

        try{
            TradeService.register(
                world,
                broken,
                brokenGeneration,
                broken.bank(),
                brokenWriter,
                ()->{}
            );
            TradeService.register(
                world,
                peer,
                peerGeneration,
                peer.bank(),
                peerWriter,
                ()->{}
            );

            requireContains(
                TradeService.start(
                    world,
                    broken,
                    peer
                ),
                "TRADE_UI_OPEN",
                "terminal peer fence trade open"
            );

            int peerBytesBefore=
                peerOut.queuedBytes();

            synchronized(peerWriter){
                retireThread=
                    new Thread(
                        ()->{
                            try{
                                retirement[0]=
                                    TradeService.retireBrokenWriter(
                                        broken,
                                        brokenWriter
                                    );
                            }catch(Throwable failure){
                                retireFailure[0]=failure;
                            }
                        },
                        "trade-terminal-peer-registry-linearization"
                    );

                retireThread.start();

                awaitBlocked(
                    retireThread,
                    "terminal peer close writer boundary"
                );

                rebindThread=
                    new Thread(
                        ()->{
                            try{
                                if(!world.unregisterPlayer(
                                        peer,
                                        peerGeneration
                                    ))
                                    throw new AssertionError(
                                        "terminal peer rebind unregister failed"
                                    );

                                replacementPeerGeneration[0]=
                                    world.registerPlayer(
                                        peer,
                                        "trade-terminal-fence-peer"
                                    );
                            }catch(Throwable failure){
                                rebindFailure[0]=failure;
                            }
                        },
                        "trade-terminal-peer-registry-rebind"
                    );

                rebindThread.start();

                awaitBlocked(
                    rebindThread,
                    "terminal peer rebind registry boundary"
                );
            }

            joinThread(
                retireThread,
                "terminal peer retirement thread"
            );
            joinThread(
                rebindThread,
                "terminal peer rebind thread"
            );

            if(retireFailure[0]!=null)
                throw new AssertionError(
                    "terminal peer retirement failed",
                    retireFailure[0]
                );
            if(rebindFailure[0]!=null)
                throw new AssertionError(
                    "terminal peer rebind failed",
                    rebindFailure[0]
                );

            if(retirement[0]==null||
               retirement[0].peerClose!=
                    TradeService.BrokenWriterPeerClose.COMMITTED||
               retirement[0].peerOwner!=peer||
               retirement[0].peerWriter!=peerWriter)
                throw new AssertionError(
                    "terminal peer close did not commit before rebind"
                );

            if(replacementPeerGeneration[0]==0L||
               replacementPeerGeneration[0]==
                    peerGeneration)
                throw new AssertionError(
                    "terminal peer generation did not advance"
                );

            if(peerOut.queuedBytes()!=
                    peerBytesBefore+1)
                throw new AssertionError(
                    "terminal peer close did not linearize before rebind before="+
                    peerBytesBefore+
                    " after="+
                    peerOut.queuedBytes()
                );

            if(TradeService.active(
                    broken
                )||
               TradeService.active(
                    peer
               ))
                throw new AssertionError(
                    "terminal peer fence retained live Trade"
                );
        }finally{
            if(retireThread!=null&&
               retireThread.isAlive())
                retireThread.interrupt();
            if(rebindThread!=null&&
               rebindThread.isAlive())
                rebindThread.interrupt();

            TradeService.unregister(
                broken,
                brokenWriter
            );
            TradeService.unregister(
                peer,
                peerWriter
            );

            if(broken.registered())
                world.unregisterPlayer(
                    broken,
                    brokenGeneration
                );

            if(peer.registered())
                world.unregisterPlayer(
                    peer,
                    replacementPeerGeneration[0]!=0L
                        ?replacementPeerGeneration[0]
                        :peer.generation()
                );

            world.close();
        }
    }

    private static void assertCompetingRootPeerCloseSerializesGenerationChange()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                603L
            );
        WorldPlayer owner=
            new WorldPlayer();
        WorldPlayer peer=
            new WorldPlayer();

        long ownerGeneration=
            world.registerPlayer(
                owner,
                "trade-competing-fence-owner"
            );
        long peerGeneration=
            world.registerPlayer(
                peer,
                "trade-competing-fence-peer"
            );

        OutboundPacketQueue ownerOut=
            new OutboundPacketQueue();
        OutboundPacketQueue peerOut=
            new OutboundPacketQueue();
        ServerPacketWriter ownerWriter=
            writer(
                ownerOut,
                41
            );
        ServerPacketWriter peerWriter=
            writer(
                peerOut,
                45
            );

        final long[] replacementPeerGeneration={
            0L
        };
        final Throwable[] replacementFailure={
            null
        };
        final Throwable[] rebindFailure={
            null
        };
        final boolean[] published={
            false
        };
        Thread replacementThread=null;
        Thread rebindThread=null;

        try{
            TradeService.register(
                world,
                owner,
                ownerGeneration,
                owner.bank(),
                ownerWriter,
                ()->{}
            );
            TradeService.register(
                world,
                peer,
                peerGeneration,
                peer.bank(),
                peerWriter,
                ()->{}
            );

            requireContains(
                TradeService.start(
                    world,
                    owner,
                    peer
                ),
                "TRADE_UI_OPEN",
                "competing-root peer fence trade open"
            );

            int ownerBytesBefore=
                ownerOut.queuedBytes();
            int peerBytesBefore=
                peerOut.queuedBytes();

            synchronized(peerWriter){
                replacementThread=
                    new Thread(
                        ()->{
                            try{
                                published[0]=
                                    TradeService.publishCompetingRoot(
                                        owner,
                                        ()->{
                                            ownerWriter.fixed(
                                                97,
                                                BootstrapPackets.interface97(
                                                    15106
                                                )
                                            );
                                            return true;
                                        }
                                    );
                            }catch(Throwable failure){
                                replacementFailure[0]=failure;
                            }
                        },
                        "trade-competing-root-registry-linearization"
                    );

                replacementThread.start();

                awaitBlocked(
                    replacementThread,
                    "competing-root peer close writer boundary"
                );

                rebindThread=
                    new Thread(
                        ()->{
                            try{
                                if(!world.unregisterPlayer(
                                        peer,
                                        peerGeneration
                                    ))
                                    throw new AssertionError(
                                        "competing-root peer rebind unregister failed"
                                    );

                                replacementPeerGeneration[0]=
                                    world.registerPlayer(
                                        peer,
                                        "trade-competing-fence-peer"
                                    );
                            }catch(Throwable failure){
                                rebindFailure[0]=failure;
                            }
                        },
                        "trade-competing-root-registry-rebind"
                    );

                rebindThread.start();

                awaitBlocked(
                    rebindThread,
                    "competing-root peer rebind registry boundary"
                );
            }

            joinThread(
                replacementThread,
                "competing-root replacement thread"
            );
            joinThread(
                rebindThread,
                "competing-root rebind thread"
            );

            if(replacementFailure[0]!=null)
                throw new AssertionError(
                    "competing-root replacement failed",
                    replacementFailure[0]
                );
            if(rebindFailure[0]!=null)
                throw new AssertionError(
                    "competing-root peer rebind failed",
                    rebindFailure[0]
                );

            if(!published[0])
                throw new AssertionError(
                    "competing replacement root did not remain committed"
                );

            if(replacementPeerGeneration[0]==0L||
               replacementPeerGeneration[0]==
                    peerGeneration)
                throw new AssertionError(
                    "competing-root peer generation did not advance"
                );

            if(ownerOut.queuedBytes()!=
                    ownerBytesBefore+3)
                throw new AssertionError(
                    "competing replacement root bytes changed expected="+
                    (ownerBytesBefore+3)+
                    " actual="+
                    ownerOut.queuedBytes()
                );

            if(peerOut.queuedBytes()!=
                    peerBytesBefore+1)
                throw new AssertionError(
                    "competing-root peer close did not linearize before rebind before="+
                    peerBytesBefore+
                    " after="+
                    peerOut.queuedBytes()
                );

            if(TradeService.active(
                    owner
                )||
               TradeService.active(
                    peer
               ))
                throw new AssertionError(
                    "competing-root peer fence retained live Trade"
                );
        }finally{
            if(replacementThread!=null&&
               replacementThread.isAlive())
                replacementThread.interrupt();
            if(rebindThread!=null&&
               rebindThread.isAlive())
                rebindThread.interrupt();

            TradeService.unregister(
                owner,
                ownerWriter
            );
            TradeService.unregister(
                peer,
                peerWriter
            );

            if(owner.registered())
                world.unregisterPlayer(
                    owner,
                    ownerGeneration
                );

            if(peer.registered())
                world.unregisterPlayer(
                    peer,
                    replacementPeerGeneration[0]!=0L
                        ?replacementPeerGeneration[0]
                        :peer.generation()
                );

            world.close();
        }
    }

    private static void assertReplacementOldPeerCloseSerializesGenerationChange()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                604L
            );
        WorldPlayer a=
            new WorldPlayer();
        WorldPlayer b=
            new WorldPlayer();
        WorldPlayer c=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                a,
                "trade-replacement-fence-a"
            );
        long generationB=
            world.registerPlayer(
                b,
                "trade-replacement-fence-b"
            );
        long generationC=
            world.registerPlayer(
                c,
                "trade-replacement-fence-c"
            );

        OutboundPacketQueue aOut=
            new OutboundPacketQueue();
        OutboundPacketQueue bOut=
            new OutboundPacketQueue();
        OutboundPacketQueue cOut=
            new OutboundPacketQueue();

        ServerPacketWriter aWriter=
            writer(
                aOut,
                51
            );
        ServerPacketWriter bWriter=
            writer(
                bOut,
                55
            );
        ServerPacketWriter cWriter=
            writer(
                cOut,
                59
            );

        final long[] replacementGenerationC={
            0L
        };
        final Throwable[] replacementFailure={
            null
        };
        final Throwable[] rebindFailure={
            null
        };
        final String[] replacementResult={
            null
        };
        Thread replacementThread=null;
        Thread rebindThread=null;

        try{
            TradeService.register(
                world,
                a,
                generationA,
                a.bank(),
                aWriter,
                ()->{}
            );
            TradeService.register(
                world,
                b,
                generationB,
                b.bank(),
                bWriter,
                ()->{}
            );
            TradeService.register(
                world,
                c,
                generationC,
                c.bank(),
                cWriter,
                ()->{}
            );

            requireContains(
                TradeService.start(
                    world,
                    a,
                    c
                ),
                "TRADE_UI_OPEN",
                "replacement old-peer fixture initial trade"
            );

            int cBytesBefore=
                cOut.queuedBytes();

            synchronized(cWriter){
                replacementThread=
                    new Thread(
                        ()->{
                            try{
                                replacementResult[0]=
                                    TradeService.start(
                                        world,
                                        a,
                                        b
                                    );
                            }catch(Throwable failure){
                                replacementFailure[0]=failure;
                            }
                        },
                        "trade-replacement-old-peer-registry-linearization"
                    );

                replacementThread.start();

                awaitBlocked(
                    replacementThread,
                    "replacement old-peer close writer boundary"
                );

                rebindThread=
                    new Thread(
                        ()->{
                            try{
                                if(!world.unregisterPlayer(
                                        c,
                                        generationC
                                    ))
                                    throw new AssertionError(
                                        "replacement old-peer rebind unregister failed"
                                    );

                                replacementGenerationC[0]=
                                    world.registerPlayer(
                                        c,
                                        "trade-replacement-fence-c"
                                    );
                            }catch(Throwable failure){
                                rebindFailure[0]=failure;
                            }
                        },
                        "trade-replacement-old-peer-registry-rebind"
                    );

                rebindThread.start();

                awaitBlocked(
                    rebindThread,
                    "replacement old-peer rebind registry boundary"
                );
            }

            joinThread(
                replacementThread,
                "replacement old-peer start thread"
            );
            joinThread(
                rebindThread,
                "replacement old-peer rebind thread"
            );

            if(replacementFailure[0]!=null)
                throw new AssertionError(
                    "replacement old-peer start failed",
                    replacementFailure[0]
                );
            if(rebindFailure[0]!=null)
                throw new AssertionError(
                    "replacement old-peer rebind failed",
                    rebindFailure[0]
                );

            requireContains(
                replacementResult[0],
                "TRADE_UI_OPEN",
                "replacement old-peer new trade"
            );

            if(replacementGenerationC[0]==0L||
               replacementGenerationC[0]==
                    generationC)
                throw new AssertionError(
                    "replacement old-peer generation did not advance"
                );

            if(cOut.queuedBytes()!=
                    cBytesBefore+1)
                throw new AssertionError(
                    "replacement old-peer close did not linearize before rebind before="+
                    cBytesBefore+
                    " after="+
                    cOut.queuedBytes()
                );

            if(!TradeService.active(
                    a
                )||
               !TradeService.active(
                    b
               ))
                throw new AssertionError(
                    "replacement A/B Trade did not remain live"
                );

            if(TradeService.active(
                    c
                ))
                throw new AssertionError(
                    "replacement old peer retained live Trade"
                );
        }finally{
            if(replacementThread!=null&&
               replacementThread.isAlive())
                replacementThread.interrupt();
            if(rebindThread!=null&&
               rebindThread.isAlive())
                rebindThread.interrupt();

            TradeService.unregister(
                a,
                aWriter
            );
            TradeService.unregister(
                b,
                bWriter
            );
            TradeService.unregister(
                c,
                cWriter
            );

            if(a.registered())
                world.unregisterPlayer(
                    a,
                    generationA
                );

            if(b.registered())
                world.unregisterPlayer(
                    b,
                    generationB
                );

            if(c.registered())
                world.unregisterPlayer(
                    c,
                    replacementGenerationC[0]!=0L
                        ?replacementGenerationC[0]
                        :c.generation()
                );

            world.close();
        }
    }

    private static void awaitBlocked(
        Thread thread,
        String phase
    )throws InterruptedException{
        long deadline=
            System.nanoTime()+
            5_000_000_000L;

        while(thread.isAlive()&&
              thread.getState()!=
                  Thread.State.BLOCKED&&
              System.nanoTime()<deadline)
            Thread.yield();

        if(thread.getState()!=
                Thread.State.BLOCKED)
            throw new AssertionError(
                phase+
                " state="+
                thread.getState()
            );
    }

    private static void joinThread(
        Thread thread,
        String phase
    )throws InterruptedException{
        thread.join(
            5000L
        );

        if(thread.isAlive())
            throw new AssertionError(
                phase+
                " did not terminate"
            );
    }

    private static final class SwitchableFailOutputStream
        extends java.io.OutputStream {

        int attempts;
        boolean fail;

        @Override public void write(
            int value
        )throws java.io.IOException{
            attempts++;
            if(fail)
                throw new java.io.IOException(
                    "EXPECTED_TRADE_TERMINAL_LATCH_FAILURE"
                );
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws java.io.IOException{
            attempts++;
            if(fail)
                throw new java.io.IOException(
                    "EXPECTED_TRADE_TERMINAL_LATCH_FAILURE"
                );
        }
    }

    private static ServerPacketWriter writer(
        OutboundPacketQueue out,
        int seed
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private static int find(
        BankState bank,
        int item
    ){
        for(int slot=0;
            slot<bank.inventoryCapacity();
            slot++){
            BankState.Stack stack=
                bank.inventoryAt(slot);
            if(stack!=null&&
               stack.itemId==item)
                return slot;
        }
        throw new AssertionError(
            "item missing "+item
        );
    }

    private static void requireContains(
        String actual,
        String expected,
        String phase
    ){
        if(actual==null||
           !actual.contains(expected))
            throw new AssertionError(
                phase+
                " expected="+expected+
                " actual="+actual
            );
    }

    private TradeServiceGenerationOwnershipFenceTest(){}
}
