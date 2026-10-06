package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import spk.content.api.ContentProvenance;
import spk.content.builtin.LocalLabCoreContentModule;
import spk.content.builtin.SuppliesMerchantDialogueContent;

public final class LocalSuppliesMerchantHandlerTest {
    private static final int[] SEED={31,32,33,34};

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        WorldPlayer observer=new WorldPlayer();

        world.registerPlayer(player,"opensrc");
        world.registerPlayer(observer,"observer");

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(SEED.clone())
            );

        LocalLabShopRuntime runtime=
            world.localLabShops();

        LocalSuppliesMerchantHandler handler=
            new LocalSuppliesMerchantHandler(
                world,
                player,
                player.movement(),
                null,
                null,
                runtime
            );
        LocalSuppliesMerchantHandler observerHandler=
            new LocalSuppliesMerchantHandler(
                world,
                observer,
                observer.movement(),
                null,
                null,
                runtime
            );

        try{
            ContentRegistry.BindingInfo binding=
                world.content()
                    .npcOptionBinding(
                        LocalLabCoreContentModule
                            .SUPPLIES_MERCHANT_NPC,
                        3
                    );

            require(
                binding!=null&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB&&
                world.content()
                    .dialogueDefinition(
                        SuppliesMerchantDialogueContent
                            .DIALOGUE_KEY
                    )!=null,
                "merchant content/dialogue ownership missing"
            );

            NpcEntity exactMerchant=
                adjacentMerchant(
                    player,
                    40
                );
            NpcInteractionRouter.Route exactRoute=
                NpcInteractionRouter.resolve(
                    new NpcAction(
                        LocalSuppliesMerchantHandler
                            .TRADE_OPCODE,
                        exactMerchant.sceneIndex
                    ),
                    exactMerchant
                );

            require(
                exactRoute.option==
                    LocalSuppliesMerchantHandler.TRADE_OPTION&&
                exactRoute.service==
                    NpcInteractionRouter.Service.TRADE,
                "NPC410 exact Trade route drift "+exactRoute
            );

            NpcEntity far=
                new NpcEntity(
                    41,
                    LocalSuppliesMerchantHandler.NPC_ID,
                    player.movement().x()+3,
                    player.movement().y()
                );

            require(
                handler.beginIfSupported(
                    new NpcAction(
                        LocalSuppliesMerchantHandler.TRADE_OPCODE,
                        far.sceneIndex
                    ),
                    far,
                    packets,
                    "[merchant-test] "
                )&&
                handler.pending()&&
                player.movement().queued()>0,
                "far merchant did not use authoritative server approach"
            );

            require(
                handler.cancelForManualMovement(
                    packets,
                    "[merchant-test] "
                )&&
                !handler.pending()&&
                !handler.active(),
                "manual movement did not cancel pending merchant"
            );
            player.movement().clearQueuedPath();

            installCoins(
                player,
                100
            );

            int beforeOpen=wire.size();

            require(
                handler.beginIfSupported(
                    new NpcAction(
                        LocalSuppliesMerchantHandler.TRADE_OPCODE,
                        exactMerchant.sceneIndex
                    ),
                    exactMerchant,
                    packets,
                    "[merchant-test] "
                )&&
                handler.active()&&
                wire.size()>beforeOpen&&
                SuppliesMerchantDialogueContent
                    .ACTION_NODE
                    .equals(
                        handler
                            .semanticDialogueSnapshot()
                            .nodeKey
                    ),
                "adjacent merchant did not open standard dialogue"
            );

            LocalSuppliesMerchantHandler.Result chooseRocktail=
                handler.handleOption(
                    1,
                    packets
                );

            require(
                chooseRocktail.handled&&
                chooseRocktail.saveReason==null&&
                chooseRocktail.feedback==null&&
                SuppliesMerchantDialogueContent
                    .ROCKTAIL_ACTION_NODE
                    .equals(
                        handler
                            .semanticDialogueSnapshot()
                            .nodeKey
                    ),
                "Rocktail catalog branch did not open action dialogue"
            );

            LocalSuppliesMerchantHandler.Result chooseBuy=
                handler.handleOption(
                    1,
                    packets
                );

            require(
                chooseBuy.handled&&
                chooseBuy.saveReason==null&&
                chooseBuy.feedback==null&&
                SuppliesMerchantDialogueContent
                    .BUY_QUANTITY_NODE
                    .equals(
                        handler
                            .semanticDialogueSnapshot()
                            .nodeKey
                    ),
                "Buy branch did not enter quantity dialogue"
            );

            LocalSuppliesMerchantHandler.Result bought=
                handler.handleOption(
                    1,
                    packets
                );

            require(
                bought.handled&&
                LocalSuppliesMerchantHandler
                    .SAVE_BUY
                    .equals(
                        bought.saveReason
                    )&&
                bought.feedback!=null&&
                !handler.active()&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.COINS
                    )==90&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.ROCKTAIL
                    )==1&&
                runtime.rocktailStock()==99L,
                "merchant Buy 1 did not settle canonical G2 state"
            );

            require(
                !containsAscii(
                    wire.toByteArray(),
                    bought.feedback
                ),
                "advisory feedback was published inside mutation before persistence caller"
            );

            installCoins(
                observer,
                100
            );
            NpcEntity observerMerchant=
                adjacentMerchant(
                    observer,
                    42
                );

            observerHandler.beginIfSupported(
                new NpcAction(
                    LocalSuppliesMerchantHandler.TRADE_OPCODE,
                    observerMerchant.sceneIndex
                ),
                observerMerchant,
                packets,
                "[merchant-test] "
            );
            observerHandler.handleOption(
                1,
                packets
            );
            observerHandler.handleOption(
                1,
                packets
            );
            LocalSuppliesMerchantHandler.Result
                observerBought=
                    observerHandler.handleOption(
                        1,
                        packets
                    );

            require(
                observerBought.handled&&
                observerBought.saveReason!=null&&
                runtime==world.localLabShops()&&
                runtime.rocktailStock()==98L,
                "second player did not mutate shared World-owned stock"
            );

            NpcEntity sellMerchant=
                adjacentMerchant(
                    player,
                    43
                );
            handler.beginIfSupported(
                new NpcAction(
                    LocalSuppliesMerchantHandler.TRADE_OPCODE,
                    sellMerchant.sceneIndex
                ),
                sellMerchant,
                packets,
                "[merchant-test] "
            );
            handler.handleOption(
                1,
                packets
            );
            handler.handleOption(
                2,
                packets
            );
            LocalSuppliesMerchantHandler.Result sold=
                handler.handleOption(
                    1,
                    packets
                );

            require(
                sold.handled&&
                LocalSuppliesMerchantHandler
                    .SAVE_SELL
                    .equals(
                        sold.saveReason
                    )&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.COINS
                    )==94&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.ROCKTAIL
                    )==0&&
                runtime.rocktailStock()==99L,
                "merchant Sell 1 did not settle canonical G2 state"
            );

            NpcEntity cancelMerchant=
                adjacentMerchant(
                    player,
                    44
                );
            handler.beginIfSupported(
                new NpcAction(
                    LocalSuppliesMerchantHandler.TRADE_OPCODE,
                    cancelMerchant.sceneIndex
                ),
                cancelMerchant,
                packets,
                "[merchant-test] "
            );

            require(
                handler.cancelForInterfaceClose(
                    "[merchant-test] "
                )&&
                !handler.active(),
                "interface close did not cancel merchant dialogue"
            );

            handler.beginIfSupported(
                new NpcAction(
                    LocalSuppliesMerchantHandler.TRADE_OPCODE,
                    cancelMerchant.sceneIndex
                ),
                cancelMerchant,
                packets,
                "[merchant-test] "
            );

            require(
                handler.cancelForNewNpcAction(
                    packets,
                    "[merchant-test] "
                )&&
                !handler.active(),
                "new NPC action did not cancel merchant dialogue"
            );

            System.out.println(
                "G2_HOME_MERCHANT_SHOP_PASS "+
                "npc410=true "+
                "tradeOption3=true "+
                "c2s17=true "+
                "serverApproach=true "+
                "standardDialogue=true "+
                "buy=true "+
                "sell=true "+
                "worldOwnedStock=true "+
                "persistence=true "+
                "feedbackSeparated=true "+
                "cancelSafe=true "+
                "nativeShopWidgetOwned=false "+
                "originalSpawnpkEconomyClaim=false "+
                "authority="+
                LocalSuppliesMerchantHandler
                    .POLICY_AUTHORITY
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );
            if(observer.registered())
                world.unregisterPlayer(
                    observer,
                    observer.generation()
                );
            world.close();
        }
    }

    private static NpcEntity adjacentMerchant(
        WorldPlayer player,
        int scene
    ){
        return new NpcEntity(
            scene,
            LocalSuppliesMerchantHandler.NPC_ID,
            player.movement().x()+1,
            player.movement().y()
        );
    }

    private static void installCoins(
        WorldPlayer player,
        int quantity
    ){
        int[] items=
            new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=
            new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);
        items[0]=LocalLabShopRuntime.COINS;
        quantities[0]=quantity;

        synchronized(player.mutationLock()){
            player.bank()
                .replaceInventorySemantic(
                    items,
                    quantities
                );
        }
    }

    private static boolean containsAscii(
        byte[] bytes,
        String text
    ){
        if(text==null)
            return false;

        byte[] needle=
            text.getBytes(
                java.nio.charset.StandardCharsets
                    .ISO_8859_1
            );

        outer:
        for(int i=0;
            i+needle.length<=bytes.length;
            i++){
            for(int j=0;j<needle.length;j++)
                if(bytes[i+j]!=needle[j])
                    continue outer;
            return true;
        }
        return false;
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private LocalSuppliesMerchantHandlerTest(){}
}
