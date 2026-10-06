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
        boolean snapshotRoundTrip=false;
        boolean insufficientCurrencyAtomic=false;
        boolean inventoryFullAtomic=false;
        boolean cancelAtomic=false;
        boolean feedbackSeparated=false;
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
            snapshotRoundTrip=true;

            require(
                !containsAscii(
                    wire.toByteArray(),
                    bought.feedback
                ),
                "successful whip advisory feedback was emitted inside mutation path"
            );
            feedbackSeparated=true;

            installCoins(
                player,
                19
            );
            long insufficientStock=
                runtime.rocktailStock();
            InventoryImage beforeInsufficient=
                captureInventory(player);

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
                    insufficientStock&&
                sameInventory(
                    beforeInsufficient,
                    captureInventory(player)
                ),
                "insufficient-coin whip rejection mutated state"
            );
            insufficientCurrencyAtomic=true;

            installFullInventory(
                player
            );
            long fullStock=
                runtime.rocktailStock();
            InventoryImage beforeFull=
                captureInventory(player);

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
                    fullStock&&
                sameInventory(
                    beforeFull,
                    captureInventory(player)
                ),
                "inventory-full whip rejection mutated state"
            );
            inventoryFullAtomic=true;

            installCoins(
                player,
                40
            );
            int coinsBeforeCancel=
                player.bank()
                    .inventoryCount(
                        LocalLabShopRuntime.COINS
                    );
            InventoryImage beforeCancel=
                captureInventory(player);

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
                    )==0&&
                sameInventory(
                    beforeCancel,
                    captureInventory(player)
                ),
                "whip cancel mutated canonical inventory"
            );

            cancelAtomic=true;
            failureAtomic=
                insufficientCurrencyAtomic&&
                inventoryFullAtomic&&
                cancelAtomic;

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
                " snapshotRoundTrip="+snapshotRoundTrip+
                " insufficientCurrencyAtomic="+
                    insufficientCurrencyAtomic+
                " inventoryFullAtomic="+
                    inventoryFullAtomic+
                " cancelAtomic="+cancelAtomic+
                " feedbackSeparated="+feedbackSeparated+
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

    private static InventoryImage captureInventory(
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
            -1
        );

        synchronized(player.mutationLock()){
            for(int slot=0;
                slot<items.length;
                slot++){
                BankState.InventorySlotSnapshot snapshot=
                    player.bank()
                        .inventorySlotSnapshot(
                            slot
                        );

                if(!snapshot.occupied)
                    continue;

                items[slot]=snapshot.itemId;
                quantities[slot]=snapshot.quantity;
            }
        }

        return new InventoryImage(
            items,
            quantities
        );
    }

    private static boolean sameInventory(
        InventoryImage first,
        InventoryImage second
    ){
        return Arrays.equals(
                first.items,
                second.items
            )&&
            Arrays.equals(
                first.quantities,
                second.quantities
            );
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
            for(int j=0;
                j<needle.length;
                j++)
                if(bytes[i+j]!=needle[j])
                    continue outer;

            return true;
        }

        return false;
    }

    private static final class InventoryImage {
        final int[] items;
        final int[] quantities;

        InventoryImage(
            int[] items,
            int[] quantities
        ){
            this.items=items;
            this.quantities=quantities;
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
