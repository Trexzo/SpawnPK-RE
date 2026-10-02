package spk.local;

public final class TradeServiceGenerationOwnershipFenceTest {
    public static void main(String[] args)throws Exception{
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
