package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import spk.content.builtin.SuppliesMerchantDialogueContent;

public final class LocalSuppliesMerchantRegearShopTest {
    private static final int[] SEED={51,52,53,54};

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        world.registerPlayer(player,"regear-shop");

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(SEED.clone())
            );

        LocalLabShopRuntime runtime=world.localLabShops();
        LocalSuppliesMerchantHandler handler=
            new LocalSuppliesMerchantHandler(
                world,
                player,
                player.movement(),
                null,
                null,
                runtime
            );

        NpcEntity merchant=
            new NpcEntity(
                77,
                LocalSuppliesMerchantHandler.NPC_ID,
                player.movement().x()+1,
                player.movement().y()
            );

        try{
            ShopService.OfferSnapshot whipOffer=
                runtime.shops()
                    .getShop(LocalLabShopRuntime.SUPPLIES)
                    .offer(
                        "item:"+
                        LocalLabShopRuntime.STARTER_WHIP
                    );

            require(
                LocalLabShopRuntime.STARTER_WHIP==
                    G1DefaultLoadoutRegearService.STARTER_WEAPON,
                "merchant regear weapon drifted from starter weapon"
            );
            require(
                whipOffer!=null&&
                whipOffer.stockMode==
                    ShopService.StockMode.UNLIMITED&&
                !whipOffer.availableStock.isPresent()&&
                whipOffer.priceEach==
                    LocalLabShopRuntime.STARTER_WHIP_BUY_PRICE,
                "starter whip is not the explicit unlimited LocalLab Shop offer"
            );

            long rocktailBefore=runtime.rocktailStock();

            installCoinsOnly(player,100);
            LocalSuppliesMerchantHandler.Result bought=
                buyWhip(
                    handler,
                    merchant,
                    packets
                );

            require(
                bought.handled&&
                LocalSuppliesMerchantHandler.SAVE_BUY
                    .equals(bought.saveReason)&&
                bought.feedback!=null&&
                bought.feedback.contains("Abyssal whip")&&
                bought.feedback.contains("Stock=UNLIMITED")&&
                player.bank().inventoryCount(
                    LocalLabShopRuntime.COINS
                )==0&&
                player.bank().inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
                )==1&&
                runtime.rocktailStock()==rocktailBefore,
                "successful starter whip purchase postimage mismatch"
            );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    "regear-shop",
                    player
                );
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                snapshot,
                restored
            );
            require(
                restored.bank().inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
                )==1&&
                restored.bank().inventoryCount(
                    LocalLabShopRuntime.COINS
                )==0,
                "starter whip purchase did not round-trip through account snapshot"
            );

            installCoinsOnly(player,99);
            InventoryImage beforePoor=captureInventory(player);
            LocalSuppliesMerchantHandler.Result poor=
                buyWhip(
                    handler,
                    merchant,
                    packets
                );
            require(
                poor.handled&&
                poor.saveReason==null&&
                poor.logText.contains("INSUFFICIENT_CURRENCY")&&
                sameInventory(
                    beforePoor,
                    captureInventory(player)
                )&&
                runtime.rocktailStock()==rocktailBefore,
                "insufficient-currency whip purchase was not atomic"
            );

            installFullInventory(player);
            InventoryImage beforeFull=captureInventory(player);
            LocalSuppliesMerchantHandler.Result full=
                buyWhip(
                    handler,
                    merchant,
                    packets
                );
            require(
                full.handled&&
                full.saveReason==null&&
                full.logText.contains("INVENTORY_FULL")&&
                sameInventory(
                    beforeFull,
                    captureInventory(player)
                )&&
                runtime.rocktailStock()==rocktailBefore,
                "inventory-full whip purchase was not atomic"
            );

            installCoinsOnly(player,100);
            InventoryImage beforeCancel=captureInventory(player);
            LocalSuppliesMerchantHandler.Result cancelled=
                cancelWhip(
                    handler,
                    merchant,
                    packets
                );
            require(
                cancelled.handled&&
                cancelled.saveReason==null&&
                cancelled.feedback==null&&
                !handler.active()&&
                sameInventory(
                    beforeCancel,
                    captureInventory(player)
                )&&
                runtime.rocktailStock()==rocktailBefore,
                "whip confirmation cancel mutated state"
            );

            require(
                !containsAscii(
                    wire.toByteArray(),
                    bought.feedback
                ),
                "successful whip advisory feedback was emitted inside mutation path"
            );

            System.out.println(
                "G2_HOME_MERCHANT_REGEAR_SHOP_PASS "+
                "starterWeapon="+
                    LocalLabShopRuntime.STARTER_WHIP+
                " merchantNpc410=true"+
                " standardDialogue=true"+
                " whipPurchase=true"+
                " canonicalInventory=true"+
                " coinsDebited=true"+
                " unlimitedWhipStock=true"+
                " rocktailFiniteStockPreserved=true"+
                " persistence=true"+
                " snapshotRoundTrip=true"+
                " insufficientCurrencyAtomic=true"+
                " inventoryFullAtomic=true"+
                " cancelAtomic=true"+
                " failureAtomic=true"+
                " nativeShopWidgetOwned=false"+
                " originalSpawnpkEconomyClaim=false"+
                " authority="+
                    LocalSuppliesMerchantHandler.POLICY_AUTHORITY
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

    private static LocalSuppliesMerchantHandler.Result buyWhip(
        LocalSuppliesMerchantHandler handler,
        NpcEntity merchant,
        ServerPacketWriter packets
    )throws Exception{
        begin(handler,merchant,packets);

        LocalSuppliesMerchantHandler.Result buy=
            handler.handleOption(1,packets);
        require(
            buy.handled&&
            SuppliesMerchantDialogueContent.BUY_CATALOG_NODE
                .equals(
                    handler.semanticDialogueSnapshot()
                        .nodeKey
                ),
            "merchant buy did not enter catalog"
        );

        LocalSuppliesMerchantHandler.Result whip=
            handler.handleOption(2,packets);
        require(
            whip.handled&&
            SuppliesMerchantDialogueContent.WHIP_CONFIRM_NODE
                .equals(
                    handler.semanticDialogueSnapshot()
                        .nodeKey
                ),
            "merchant catalog did not enter whip confirmation"
        );

        return handler.handleOption(1,packets);
    }

    private static LocalSuppliesMerchantHandler.Result cancelWhip(
        LocalSuppliesMerchantHandler handler,
        NpcEntity merchant,
        ServerPacketWriter packets
    )throws Exception{
        begin(handler,merchant,packets);
        handler.handleOption(1,packets);
        handler.handleOption(2,packets);
        return handler.handleOption(2,packets);
    }

    private static void begin(
        LocalSuppliesMerchantHandler handler,
        NpcEntity merchant,
        ServerPacketWriter packets
    )throws Exception{
        require(
            handler.beginIfSupported(
                new NpcAction(
                    LocalSuppliesMerchantHandler.TRADE_OPCODE,
                    merchant.sceneIndex
                ),
                merchant,
                packets,
                "[regear-shop-test] "
            )&&
            handler.active(),
            "merchant did not open"
        );
    }

    private static void installCoinsOnly(
        WorldPlayer player,
        int coins
    ){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);
        items[0]=LocalLabShopRuntime.COINS;
        quantities[0]=coins;

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(
                items,
                quantities
            );
        }
    }

    private static void installFullInventory(
        WorldPlayer player
    ){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];

        items[0]=LocalLabShopRuntime.COINS;
        quantities[0]=101;

        for(int slot=1;slot<items.length;slot++){
            items[slot]=385;
            quantities[slot]=1;
        }

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(
                items,
                quantities
            );
        }
    }

    private static InventoryImage captureInventory(
        WorldPlayer player
    ){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);

        synchronized(player.mutationLock()){
            for(int slot=0;slot<items.length;slot++){
                BankState.InventorySlotSnapshot snapshot=
                    player.bank().inventorySlotSnapshot(slot);
                if(!snapshot.occupied)
                    continue;
                items[slot]=snapshot.itemId;
                quantities[slot]=snapshot.quantity;
            }
        }

        return new InventoryImage(items,quantities);
    }

    private static boolean sameInventory(
        InventoryImage a,
        InventoryImage b
    ){
        return Arrays.equals(a.items,b.items)&&
            Arrays.equals(a.quantities,b.quantities);
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
        for(int i=0;i+needle.length<=bytes.length;i++){
            for(int j=0;j<needle.length;j++)
                if(bytes[i+j]!=needle[j])
                    continue outer;
            return true;
        }

        return false;
    }

    private static final class InventoryImage {
        final int[] items;
        final int[] quantities;

        InventoryImage(int[] items,int[] quantities){
            this.items=items;
            this.quantities=quantities;
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private LocalSuppliesMerchantRegearShopTest(){}
}
