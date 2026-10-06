package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.builtin.SuppliesMerchantDialogueContent;

public final class G2BankFundedMerchantRegearIntegrationTest {
    private static final int[] SEED={71,72,73,74};

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        world.registerPlayer(player,"bank-funded-regear");

        BankState bank=player.bank();
        MovementState movement=player.movement();

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter packets=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(SEED.clone())
            );

        LocalBankObjectInteractionHandler bankObject=
            new LocalBankObjectInteractionHandler(
                bank,
                movement,
                world.content()
            );
        LocalBankRequestHandler bankRequests=
            new LocalBankRequestHandler(
                player,
                bank
            );

        LocalLabShopRuntime runtime=world.localLabShops();
        LocalSuppliesMerchantHandler merchant=
            new LocalSuppliesMerchantHandler(
                world,
                player,
                movement,
                null,
                null,
                runtime
            );
        LocalEquipmentItemActionHandler equipment=
            new LocalEquipmentItemActionHandler(
                bank,
                player.equipment(),
                player.playerState(),
                new PlayerPresentationService(
                    new DevAuthorityWorkbench()
                ),
                player.combatStyles()
            );

        NpcEntity npc=
            new NpcEntity(
                99,
                LocalSuppliesMerchantHandler.NPC_ID,
                movement.x()+1,
                movement.y()
            );

        try{
            require(
                bank.inventorySlots()==0&&
                bank.inventoryCount(
                    LocalLabShopRuntime.COINS
                )==0&&
                player.equipment().weapon()!=
                    LocalLabShopRuntime.STARTER_WHIP,
                "fixture must start unfunded and unequipped"
            );

            ObjectInteraction bankClick=
                new ObjectInteraction(
                    132,
                    BankState.BANK_OBJECT_ID,
                    movement.x()+1,
                    movement.y()
                );

            String opened=
                onWorld(
                    world,
                    player,
                    ()->bankObject.handle(
                        bankClick,
                        packets
                    )
                );

            require(
                bankClick.opcode==132&&
                bankClick.objectId==26972&&
                opened!=null&&
                opened.contains("V5_BANK_OPEN")&&
                bank.isOpen(),
                "exact HOME bank object path did not open"
            );

            BankState.Stack coinStack=
                bank.bankAt(0);

            require(
                coinStack!=null&&
                coinStack.itemId==
                    LocalLabShopRuntime.COINS&&
                coinStack.qty>=100,
                "bank coin fixture missing"
            );

            int bankCoinsBefore=
                coinStack.qty;

            ItemContainerAction withdrawX=
                new ItemContainerAction(
                    135,
                    BankState.BANK_CONTAINER,
                    0,
                    LocalLabShopRuntime.COINS,
                    0,
                    "ITEM_ACTION_X"
                );

            String prompt=
                bank.apply(
                    withdrawX,
                    packets
                );

            require(
                withdrawX.opcode==135&&
                withdrawX.widgetId==
                    BankState.BANK_CONTAINER&&
                BankState.BANK_CONTAINER==5382&&
                prompt!=null&&
                prompt.contains(
                    "WITHDRAW_X_PROMPT_SENT"
                ),
                "exact Withdraw-X did not establish amount authority"
            );

            AmountEntryClientRequest amount208=
                new AmountEntryClientRequest(
                    100,
                    ClientRequestMetadata.exactCurrent(
                        208,
                        "i32 amount",
                        "G2_BANK_FUNDED_REGEAR_TEST"
                    )
                );

            LocalBankRequestHandler.Result withdrew=
                bankRequests.handleAmount(
                    amount208.amount(),
                    packets
                );

            require(
                amount208.metadata().opcode==208&&
                amount208.metadata().provenance==
                    ClientRequestProvenance
                        .EXACT_CURRENT_CLIENT&&
                "BANK_AMOUNT".equals(
                    withdrew.saveReason
                )&&
                withdrew.logText.contains(
                    "opcode=208 amount=100"
                )&&
                withdrew.logText.contains(
                    "WITHDRAW_X_OK amount=100"
                )&&
                bank.inventoryCount(
                    LocalLabShopRuntime.COINS
                )==100&&
                bank.bankAt(0)!=null&&
                bank.bankAt(0).itemId==
                    LocalLabShopRuntime.COINS&&
                bank.bankAt(0).qty==
                    bankCoinsBefore-100,
                "exact amount-entry withdrawal did not conserve bank -> inventory coins"
            );

            bank.close(packets);

            require(
                !bank.isOpen()&&
                bank.inventoryCount(
                    LocalLabShopRuntime.COINS
                )==100,
                "bank close lost withdrawn coins"
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
                    "[bank-funded-regear] "
                ),
                "NPC410 Trade not handled"
            );

            LocalSuppliesMerchantHandler.Result buyMenu=
                merchant.handleOption(
                    1,
                    packets
                );
            require(
                buyMenu.handled&&
                SuppliesMerchantDialogueContent
                    .BUY_CATALOG_NODE
                    .equals(
                        merchant
                            .semanticDialogueSnapshot()
                            .nodeKey
                    ),
                "merchant buy catalog not reached"
            );

            LocalSuppliesMerchantHandler.Result whipMenu=
                merchant.handleOption(
                    2,
                    packets
                );
            require(
                whipMenu.handled&&
                SuppliesMerchantDialogueContent
                    .WHIP_CONFIRM_NODE
                    .equals(
                        merchant
                            .semanticDialogueSnapshot()
                            .nodeKey
                    ),
                "whip confirmation not reached"
            );

            LocalSuppliesMerchantHandler.Result bought=
                merchant.handleOption(
                    1,
                    packets
                );

            require(
                LocalSuppliesMerchantHandler.SAVE_BUY
                    .equals(
                        bought.saveReason
                    )&&
                bank.inventoryCount(
                    LocalLabShopRuntime.COINS
                )==0&&
                bank.inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
                )==1&&
                runtime.rocktailStock()==rocktailBefore,
                "bank-funded merchant purchase mismatch"
            );

            int whipSlot=findSlot(
                bank,
                LocalLabShopRuntime.STARTER_WHIP
            );
            require(
                whipSlot>=0,
                "purchased whip has no inventory slot"
            );

            ItemContainerAction wield=
                new ItemContainerAction(
                    41,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    whipSlot,
                    LocalLabShopRuntime.STARTER_WHIP,
                    0,
                    "INVENTORY_OPTION"
                );

            LocalEquipmentItemActionHandler.Result equipped=
                equipment.handle(
                    wield,
                    "bank-funded-regear",
                    packets
                );

            require(
                wield.opcode==41&&
                wield.widgetId==3214&&
                wield.slot==whipSlot&&
                equipped!=null&&
                "EQUIP_FROM_INVENTORY"
                    .equals(
                        equipped.saveReason
                    )&&
                player.equipment().weapon()==
                    LocalLabShopRuntime.STARTER_WHIP&&
                bank.inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
                )==0,
                "exact Wield did not equip purchased whip"
            );

            PlayerSnapshot snapshot=
                PlayerSnapshotCodec.capture(
                    "bank-funded-regear",
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
                    LocalLabShopRuntime.COINS
                )==0&&
                restored.bank().inventoryCount(
                    LocalLabShopRuntime.STARTER_WHIP
                )==0,
                "bank-funded regear postimage did not persist"
            );

            System.out.println(
                "G2_BANK_FUNDED_MERCHANT_REGEAR_PASS"+
                " bankObject26972="+
                    (BankState.BANK_OBJECT_ID==26972)+
                " c2s132="+
                    (bankClick.opcode==132)+
                " withdrawX=true"+
                " c2s135="+
                    (withdrawX.opcode==135)+
                " amountEntry208="+
                    (amount208.metadata().opcode==208)+
                " withdrew100Coins=true"+
                " bankDebitExact=true"+
                " bankCloseRetainedCoins=true"+
                " merchantNpc410=true"+
                " c2s17=true"+
                " whipPurchase=true"+
                " c2s41="+
                    (wield.opcode==41)+
                " inventoryWidget3214="+
                    (wield.widgetId==3214)+
                " weapon4151="+
                    (player.equipment().weapon()==4151)+
                " bankSave=true"+
                " shopSave=true"+
                " equipSave=true"+
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

    private static String onWorld(
        World world,
        WorldPlayer player,
        ThrowingString action
    )throws Exception{
        AtomicReference<String> result=
            new AtomicReference<>();
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    result.set(
                        action.run()
                    );
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "world action failed",
                failure.get()
            );

        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingString {
        String run()throws Exception;
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

    private G2BankFundedMerchantRegearIntegrationTest(){}
}
