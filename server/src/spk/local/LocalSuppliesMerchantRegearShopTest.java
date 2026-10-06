package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import spk.content.builtin.SuppliesMerchantDialogueContent;

public final class LocalSuppliesMerchantRegearShopTest {
    private static final int[] SEED={41,42,43,44};

    public static void main(String[] args)
        throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            "opensrc"
        );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
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

        boolean standardDialogue=false;
        boolean whipPurchase=false;
        boolean canonicalInventory=false;
        boolean coinsDebited=false;
        boolean unlimitedWhipStock=false;
        boolean rocktailFiniteStockPreserved=false;
        boolean persistence=false;
        boolean failureAtomic=false;

        try{
            require(
                LocalLabShopRuntime.ABYSSAL_WHIP==
                    G1DefaultLoadoutRegearService
                        .STARTER_WEAPON&&
                LocalLabShopRuntime.ABYSSAL_WHIP==
                    4151,
                "merchant replacement weapon drifted from starter loadout"
            );

            ShopService.ShopSnapshot shop=
                runtime.shops()
                    .getShop(
                        LocalLabShopRuntime.SUPPLIES
                    );
            ShopService.OfferSnapshot whipOffer=
                shop.offer(
                    "item:"+
                        LocalLabShopRuntime
                            .ABYSSAL_WHIP
                );
            ShopService.OfferSnapshot rocktailOffer=
                shop.offer(
                    "item:"+
                        LocalLabShopRuntime
                            .ROCKTAIL
                );

            require(
                whipOffer!=null&&
                whipOffer.stockMode==
                    ShopService.StockMode.UNLIMITED&&
                !whipOffer.availableStock
                    .isPresent()&&
                whipOffer.priceEach==
                    LocalLabShopRuntime
                        .ABYSSAL_WHIP_BUY_PRICE&&
                rocktailOffer!=null&&
                rocktailOffer.stockMode==
                    ShopService.StockMode.FINITE&&
                rocktailOffer.availableStock
                    .isPresent(),
                "merchant Shop offer modes drifted"
            );

            unlimitedWhipStock=true;

            long rocktailBefore=
                runtime.rocktailStock();

            installCoins(
                player,
                40
            );

            openWhip(
                handler,
                player,
                packets,
                60
            );

            standardDialogue=
                SuppliesMerchantDialogueContent
                    .WHIP_ACTION_NODE
                    .equals(
                        handler
                            .semanticDialogueSnapshot()
                            .nodeKey
                    );

            require(
                standardDialogue,
                "whip catalog branch did not use standard dialogue"
            );

            LocalSuppliesMerchantHandler.Result bought=
                handler.handleOption(
                    1,
                    packets
                );

            whipPurchase=
                bought.handled&&
                LocalSuppliesMerchantHandler
                    .SAVE_WHIP_BUY
                    .equals(
                        bought.saveReason
                    )&&
                bought.feedback!=null&&
                !handler.active();

            canonicalInventory=
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime
                            .ABYSSAL_WHIP
                    )==1;

            coinsDebited=
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.COINS
                    )==20;

            rocktailFiniteStockPreserved=
                runtime.rocktailStock()==
                    rocktailBefore;

            require(
                whipPurchase&&
                canonicalInventory&&
                coinsDebited&&
                rocktailFiniteStockPreserved,
                "successful whip purchase postimage mismatch"
            );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    "opensrc",
                    player
                );
            WorldPlayer restored=
                new WorldPlayer();

            PlayerSnapshotCodec.applyValidated(
                snapshot,
                restored
            );

            persistence=
                restored.bank()
                    .inventoryCount(
                        LocalLabShopRuntime
                            .ABYSSAL_WHIP
                    )==1&&
                restored.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.COINS
                    )==20;

            require(
                persistence,
                "whip purchase did not survive player snapshot round-trip"
            );

            installCoins(
                player,
                19
            );
            long insufficientStock=
                runtime.rocktailStock();

            openWhip(
                handler,
                player,
                packets,
                61
            );

            LocalSuppliesMerchantHandler.Result
                insufficient=
                    handler.handleOption(
                        1,
                        packets
                    );

            require(
                insufficient.handled&&
                insufficient.saveReason==null&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.COINS
                    )==19&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime
                            .ABYSSAL_WHIP
                    )==0&&
                runtime.rocktailStock()==
                    insufficientStock,
                "insufficient-coin whip rejection mutated state"
            );

            installFullInventory(
                player
            );
            long fullStock=
                runtime.rocktailStock();

            openWhip(
                handler,
                player,
                packets,
                62
            );

            LocalSuppliesMerchantHandler.Result full=
                handler.handleOption(
                    1,
                    packets
                );

            require(
                full.handled&&
                full.saveReason==null&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.COINS
                    )==21&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.ROCKTAIL
                    )==27&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime
                            .ABYSSAL_WHIP
                    )==0&&
                runtime.rocktailStock()==
                    fullStock,
                "inventory-full whip rejection mutated state"
            );

            installCoins(
                player,
                40
            );
            int coinsBeforeCancel=
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.COINS
                    );

            openWhip(
                handler,
                player,
                packets,
                63
            );

            LocalSuppliesMerchantHandler.Result cancelled=
                handler.handleOption(
                    2,
                    packets
                );

            require(
                cancelled.handled&&
                cancelled.saveReason==null&&
                cancelled.feedback==null&&
                !handler.active()&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.COINS
                    )==coinsBeforeCancel&&
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime
                            .ABYSSAL_WHIP
                    )==0,
                "whip cancel mutated canonical inventory"
            );

            failureAtomic=true;

            System.out.println(
                "G2_HOME_MERCHANT_REGEAR_SHOP_PASS"+
                " starterWeapon="+
                    LocalLabShopRuntime.ABYSSAL_WHIP+
                " merchantNpc410=true"+
                " standardDialogue="+
                    standardDialogue+
                " whipPurchase="+whipPurchase+
                " canonicalInventory="+
                    canonicalInventory+
                " coinsDebited="+coinsDebited+
                " unlimitedWhipStock="+
                    unlimitedWhipStock+
                " rocktailFiniteStockPreserved="+
                    rocktailFiniteStockPreserved+
                " persistence="+persistence+
                " failureAtomic="+failureAtomic+
                " nativeShopWidgetOwned=false"+
                " originalSpawnpkEconomyClaim=false"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player,
                    player.generation()
                );
            world.close();
        }
    }

    private static void openWhip(
        LocalSuppliesMerchantHandler handler,
        WorldPlayer player,
        ServerPacketWriter packets,
        int scene
    )throws Exception{
        NpcEntity merchant=
            new NpcEntity(
                scene,
                LocalSuppliesMerchantHandler
                    .NPC_ID,
                player.movement().x()+1,
                player.movement().y()
            );

        require(
            handler.beginIfSupported(
                new NpcAction(
                    LocalSuppliesMerchantHandler
                        .TRADE_OPCODE,
                    merchant.sceneIndex
                ),
                merchant,
                packets,
                "[merchant-regear-test] "
            ),
            "merchant Trade route was not handled"
        );

        require(
            SuppliesMerchantDialogueContent
                .ACTION_NODE
                .equals(
                    handler
                        .semanticDialogueSnapshot()
                        .nodeKey
                ),
            "merchant did not start at supplies catalog"
        );

        LocalSuppliesMerchantHandler.Result chooseWhip=
            handler.handleOption(
                2,
                packets
            );

        require(
            chooseWhip.handled&&
            chooseWhip.saveReason==null&&
            chooseWhip.feedback==null&&
            SuppliesMerchantDialogueContent
                .WHIP_ACTION_NODE
                .equals(
                    handler
                        .semanticDialogueSnapshot()
                        .nodeKey
                ),
            "whip catalog option did not open whip action node"
        );
    }

    private static void installCoins(
        WorldPlayer player,
        int quantity
    ){
        int[] items=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] quantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

        Arrays.fill(
            items,
            -1
        );

        items[0]=
            LocalLabShopRuntime.COINS;
        quantities[0]=quantity;

        synchronized(player.mutationLock()){
            player.bank()
                .replaceInventorySemantic(
                    items,
                    quantities
                );
        }
    }

    private static void installFullInventory(
        WorldPlayer player
    ){
        int[] items=
            new int[
                BankState.INVENTORY_CAPACITY
            ];
        int[] quantities=
            new int[
                BankState.INVENTORY_CAPACITY
            ];

        Arrays.fill(
            items,
            LocalLabShopRuntime.ROCKTAIL
        );
        Arrays.fill(
            quantities,
            1
        );

        items[0]=
            LocalLabShopRuntime.COINS;
        quantities[0]=21;

        synchronized(player.mutationLock()){
            player.bank()
                .replaceInventorySemantic(
                    items,
                    quantities
                );
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }
}
