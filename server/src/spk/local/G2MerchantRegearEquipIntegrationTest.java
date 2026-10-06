package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import spk.content.builtin.SuppliesMerchantDialogueContent;

public final class G2MerchantRegearEquipIntegrationTest {
    private static final int[] SEED={61,62,63,64};

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        world.registerPlayer(player,"merchant-regear-equip");

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(SEED.clone())
            );

        LocalLabShopRuntime runtime=world.localLabShops();
        LocalSuppliesMerchantHandler merchant=
            new LocalSuppliesMerchantHandler(
                world,
                player,
                player.movement(),
                null,
                null,
                runtime
            );
        LocalEquipmentItemActionHandler equipment=
            new LocalEquipmentItemActionHandler(
                player.bank(),
                player.equipment(),
                player.playerState(),
                new PlayerPresentationService(
                    new DevAuthorityWorkbench()
                ),
                player.combatStyles()
            );

        NpcEntity npc=
            new NpcEntity(
                88,
                LocalSuppliesMerchantHandler.NPC_ID,
                player.movement().x()+1,
                player.movement().y()
            );

        try{
            installCoins(player,100);

            require(
                player.equipment().weapon()!=
                    LocalLabShopRuntime.STARTER_WHIP,
                "fixture already has starter whip equipped"
            );

            long rocktailBefore=runtime.rocktailStock();

            require(
                merchant.beginIfSupported(
                    new NpcAction(
                        LocalSuppliesMerchantHandler.TRADE_OPCODE,
                        npc.sceneIndex
                    ),
                    npc,
                    packets,
                    "[merchant-regear-equip] "
                ),
                "NPC410 Trade was not handled"
            );

            LocalSuppliesMerchantHandler.Result openBuy=
                merchant.handleOption(1,packets);
            require(
                openBuy.handled&&
                SuppliesMerchantDialogueContent.BUY_CATALOG_NODE
                    .equals(
                        merchant
                            .semanticDialogueSnapshot()
                            .nodeKey
                    ),
                "merchant did not enter buy catalog"
            );

            LocalSuppliesMerchantHandler.Result chooseWhip=
                merchant.handleOption(2,packets);
            require(
                chooseWhip.handled&&
                SuppliesMerchantDialogueContent.WHIP_CONFIRM_NODE
                    .equals(
                        merchant
                            .semanticDialogueSnapshot()
                            .nodeKey
                    ),
                "merchant did not enter whip confirmation"
            );

            LocalSuppliesMerchantHandler.Result bought=
                merchant.handleOption(1,packets);

            require(
                bought.handled&&
                LocalSuppliesMerchantHandler.SAVE_BUY
                    .equals(bought.saveReason)&&
                player.bank().inventoryCount(
                    LocalLabShopRuntime.COINS
                )==0&&
                player.bank().inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
                )==1&&
                runtime.rocktailStock()==rocktailBefore,
                "merchant whip purchase postimage mismatch"
            );

            int purchasedSlot=findSlot(
                player.bank(),
                LocalLabShopRuntime.STARTER_WHIP
            );
            require(
                purchasedSlot>=0,
                "purchased whip has no canonical inventory slot"
            );

            int wireBeforeEquip=wire.size();
            ItemContainerAction wield=
                new ItemContainerAction(
                    41,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    purchasedSlot,
                    LocalLabShopRuntime.STARTER_WHIP,
                    0,
                    "INVENTORY_OPTION"
                );

            LocalEquipmentItemActionHandler.Result equipped=
                equipment.handle(
                    wield,
                    "merchant-regear-equip",
                    packets
                );

            require(
                equipped!=null&&
                "EQUIP_FROM_INVENTORY"
                    .equals(equipped.saveReason)&&
                !equipped.beforeSaveLogs.isEmpty()&&
                equipped.beforeSaveLogs.get(0)
                    .contains(
                        "V510_STYLE_EQUIP_RECONCILE"
                    )&&
                wire.size()>wireBeforeEquip,
                "exact Wield did not publish equipment/style reconciliation"
            );

            require(
                wield.opcode==41&&
                wield.widgetId==
                    BankState.NORMAL_INVENTORY_CONTAINER&&
                wield.slot==purchasedSlot&&
                wield.itemId==
                    LocalLabShopRuntime.STARTER_WHIP,
                "exact purchased-slot Wield identity drift"
            );

            require(
                player.bank().inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
                )==0&&
                player.equipment().weapon()==
                    LocalLabShopRuntime.STARTER_WHIP&&
                runtime.rocktailStock()==rocktailBefore,
                "purchased whip did not move from inventory into weapon slot"
            );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    "merchant-regear-equip",
                    player
                );
            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                snapshot,
                restored
            );

            require(
                restored.equipment().weapon()==
                    LocalLabShopRuntime.STARTER_WHIP&&
                restored.bank().inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
                )==0&&
                restored.bank().inventoryCount(
                    LocalLabShopRuntime.COINS
                )==0,
                "merchant regear/equip postimage did not persist"
            );

            System.out.println(
                "G2_MERCHANT_REGEAR_EQUIP_PASS"+
                " merchantNpc410=true"+
                " c2s17=true"+
                " whipPurchase=true"+
                " c2s41=true"+
                " inventoryWidget3214="+
                    (BankState.NORMAL_INVENTORY_CONTAINER==3214)+
                " purchasedSlotExact=true"+
                " weapon4151="+
                    (LocalLabShopRuntime.STARTER_WHIP==4151)+
                " shopSave=true"+
                " equipSave=true"+
                " styleReconciled=true"+
                " snapshotRoundTrip=true"+
                " rocktailStockUnchanged=true"+
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

    private static void installCoins(
        WorldPlayer player,
        int quantity
    ){
        int[] items=new int[BankState.INVENTORY_CAPACITY];
        int[] quantities=new int[BankState.INVENTORY_CAPACITY];
        Arrays.fill(items,-1);
        items[0]=LocalLabShopRuntime.COINS;
        quantities[0]=quantity;

        synchronized(player.mutationLock()){
            player.bank().replaceInventorySemantic(
                items,
                quantities
            );
        }
    }

    private static int findSlot(
        BankState bank,
        int itemId
    ){
        for(int slot=0;
            slot<BankState.INVENTORY_CAPACITY;
            slot++){
            BankState.InventorySlotSnapshot item=
                bank.inventorySlotSnapshot(slot);
            if(item.occupied&&item.itemId==itemId)
                return slot;
        }
        return -1;
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(message);
    }

    private G2MerchantRegearEquipIntegrationTest(){}
}
