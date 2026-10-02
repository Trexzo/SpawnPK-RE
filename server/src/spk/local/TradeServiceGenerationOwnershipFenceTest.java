package spk.local;

public final class TradeServiceGenerationOwnershipFenceTest {
    public static void main(String[] args)throws Exception{
        assertCancellationCloseSkipsStaleGeneration();

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
                "staleCancellationCloseFenced=true "+
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

    private static void assertCancellationCloseSkipsStaleGeneration()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                601L
            );
        WorldPlayer stale=
            new WorldPlayer();
        WorldPlayer peer=
            new WorldPlayer();

        long staleGeneration=
            world.registerPlayer(
                stale,
                "trade-cancel-fence-a"
            );
        long peerGeneration=
            world.registerPlayer(
                peer,
                "trade-cancel-fence-b"
            );

        OutboundPacketQueue staleOut=
            new OutboundPacketQueue();
        OutboundPacketQueue peerOut=
            new OutboundPacketQueue();
        ServerPacketWriter staleWriter=
            writer(
                staleOut,
                21
            );
        ServerPacketWriter peerWriter=
            writer(
                peerOut,
                25
            );

        long replacementGeneration=0L;
        final Throwable[] threadFailure={
            null
        };
        Thread cancelThread=null;

        try{
            TradeService.register(
                world,
                stale,
                staleGeneration,
                stale.bank(),
                staleWriter,
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
                    stale,
                    peer
                ),
                "TRADE_UI_OPEN",
                "cancellation fence trade open"
            );

            int staleBytesBefore=
                staleOut.queuedBytes();
            int peerBytesBefore=
                peerOut.queuedBytes();

            synchronized(stale.mutationLock()){
                cancelThread=
                    new Thread(
                        ()->{
                            try{
                                String result=
                                    TradeService.handleWidget(
                                        stale,
                                        TradeService.FIRST_DECLINE
                                    );

                                if(result==null||
                                   !result.contains(
                                        "TRADE_CANCELLED_DECLINE"
                                   ))
                                    throw new AssertionError(
                                        "cancellation fence result="+
                                        result
                                    );
                            }catch(Throwable failure){
                                threadFailure[0]=failure;
                            }
                        },
                        "trade-cancel-generation-fence"
                    );

                cancelThread.start();

                long deadline=
                    System.nanoTime()+
                    5_000_000_000L;

                while(cancelThread.isAlive()&&
                      cancelThread.getState()!=
                          Thread.State.BLOCKED&&
                      System.nanoTime()<deadline)
                    Thread.yield();

                if(cancelThread.getState()!=
                        Thread.State.BLOCKED)
                    throw new AssertionError(
                        "cancellation did not block at participant ownership fence state="+
                        cancelThread.getState()
                    );

                if(!world.unregisterPlayer(
                        stale,
                        staleGeneration
                    ))
                    throw new AssertionError(
                        "cancellation fence stale generation unregister failed"
                    );

                replacementGeneration=
                    world.registerPlayer(
                        stale,
                        "trade-cancel-fence-a"
                    );

                if(replacementGeneration==
                        staleGeneration)
                    throw new AssertionError(
                        "cancellation fence generation did not advance"
                    );
            }

            cancelThread.join(
                5000L
            );

            if(cancelThread.isAlive())
                throw new AssertionError(
                    "cancellation fence thread did not terminate"
                );

            if(threadFailure[0]!=null)
                throw new AssertionError(
                    "cancellation fence thread failed",
                    threadFailure[0]
                );

            if(staleOut.queuedBytes()!=
                    staleBytesBefore)
                throw new AssertionError(
                    "stale cancellation close touched old writer before="+
                    staleBytesBefore+
                    " after="+
                    staleOut.queuedBytes()
                );

            if(peerOut.queuedBytes()!=
                    peerBytesBefore+1)
                throw new AssertionError(
                    "current peer did not receive exactly one cancellation close before="+
                    peerBytesBefore+
                    " after="+
                    peerOut.queuedBytes()
                );

            if(TradeService.active(
                    stale
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

            TradeService.unregister(
                stale,
                staleWriter
            );
            TradeService.unregister(
                peer,
                peerWriter
            );

            if(stale.registered())
                world.unregisterPlayer(
                    stale,
                    replacementGeneration!=0L
                        ?replacementGeneration
                        :stale.generation()
                );

            if(peer.registered())
                world.unregisterPlayer(
                    peer,
                    peerGeneration
                );

            world.close();
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
