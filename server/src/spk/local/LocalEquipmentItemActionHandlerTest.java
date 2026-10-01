package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalEquipmentItemActionHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        EquipmentState equipment=player.equipment();
        PlayerState playerState=player.playerState();

        LocalEquipmentItemActionHandler handler=
            new LocalEquipmentItemActionHandler(
                bank,
                equipment,
                playerState,
                new PlayerPresentationService(new DevAuthorityWorkbench()),
                player.combatStyles()
            );

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        String spawn=bank.spawnItem(4151,1,writer);
        int whipSlot=findSlot(bank,4151);
        if(whipSlot<0)
            throw new AssertionError("whip spawn failed: "+spawn);

        ItemContainerAction equip=new ItemContainerAction(
            41,
            BankState.NORMAL_INVENTORY_CONTAINER,
            whipSlot,
            4151,
            0,
            "INVENTORY_OPTION"
        );

        LocalEquipmentItemActionHandler.Result equipped=
            handler.handle(equip,"opensrc",writer);

        if(equipped==null)
            throw new AssertionError("equip route unhandled");
        if(!"EQUIP_FROM_INVENTORY".equals(equipped.saveReason))
            throw new AssertionError("equip save reason="+equipped.saveReason);
        if(equipment.weapon()!=4151)
            throw new AssertionError("weapon not equipped="+equipment.weapon());
        if(equipped.afterSaveLogs.isEmpty()||
           !equipped.afterSaveLogs.get(0).contains(
               "V522_EQUIPMENT_ITEM_ACTION"))
            throw new AssertionError("equip result log="+equipped.afterSaveLogs);

        ItemContainerAction remove=new ItemContainerAction(
            145,
            EquipmentState.EQUIPMENT_WIDGET,
            EquipmentSlot.WEAPON.equipmentIndex,
            4151,
            0,
            "REMOVE"
        );

        LocalEquipmentItemActionHandler.Result removed=
            handler.handle(remove,"opensrc",writer);

        if(removed==null)
            throw new AssertionError("unequip route unhandled");
        if(!"UNEQUIP_TO_INVENTORY".equals(removed.saveReason))
            throw new AssertionError("unequip save reason="+removed.saveReason);
        if(equipment.weapon()==4151)
            throw new AssertionError("weapon still equipped");
        if(bank.inventoryCount(4151)!=1)
            throw new AssertionError("whip not returned to inventory");

        ItemContainerAction unrelated=new ItemContainerAction(
            122,
            BankState.NORMAL_INVENTORY_CONTAINER,
            findSlot(bank,4151),
            4151,
            0,
            "OPTION_1"
        );

        if(handler.handle(unrelated,"opensrc",writer)!=null)
            throw new AssertionError(
                "unrelated option should remain outside equipment handler");

        testCosmeticPublicationAtomicity();
        testOverridePublicationAtomicity();

        System.out.println(
            "LOCAL_EQUIPMENT_ITEM_ACTION_HANDLER_PASS equip=true unequip=true persistenceSignals=true unrelatedRejected=true cosmeticPublicationAtomic=true cosmeticReplacementAtomic=true cosmeticUnequipAtomic=true cosmeticOpenBankMirrorAtomic=true cosmeticOverridePublicationAtomic=true cosmeticOverrideReplacementAtomic=true cosmeticOverrideOpenBankMirrorAtomic=true");
    }

    private static void testCosmeticPublicationAtomicity()
        throws Exception
    {
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        EquipmentState equipment=player.equipment();
        PlayerState playerState=player.playerState();
        LocalEquipmentItemActionHandler handler=
            new LocalEquipmentItemActionHandler(
                bank,
                equipment,
                playerState,
                new PlayerPresentationService(
                    new DevAuthorityWorkbench()
                ),
                player.combatStyles()
            );

        ServerPacketWriter good=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{21,22,23,24}
                )
            );

        final int firstIcon=10556;
        final int secondIcon=10557;

        if(!ItemCatalog.isNativePlayerIcon(firstIcon)||
           !ItemCatalog.isNativePlayerIcon(secondIcon))
            throw new AssertionError(
                "native cosmetic fixtures lost authority"
            );

        bank.spawnItem(
            firstIcon,
            1,
            good
        );
        int firstSlot=findSlot(bank,firstIcon);
        int firstBefore=bank.inventoryCount(firstIcon);

        ItemContainerAction equipFirst=
            new ItemContainerAction(
                41,
                BankState.NORMAL_INVENTORY_CONTAINER,
                firstSlot,
                firstIcon,
                0,
                "INVENTORY_OPTION"
            );

        boolean firstFailed=false;
        try{
            handler.handle(
                equipFirst,
                "cosmetic-atomic",
                fullQueueWriter(
                    new int[]{25,26,27,28}
                )
            );
        }catch(java.io.IOException expected){
            firstFailed=true;
        }

        if(!firstFailed||
           playerState.cosmetic().active()||
           bank.inventoryCount(firstIcon)!=firstBefore)
            throw new AssertionError(
                "failed cosmetic equip mutated canonical state"
            );

        LocalEquipmentItemActionHandler.Result firstRetry=
            handler.handle(
                equipFirst,
                "cosmetic-atomic",
                good
            );

        if(firstRetry==null||
           !"COSMETIC_EQUIP".equals(
                firstRetry.saveReason
           )||
           playerState.cosmetic().itemId()!=firstIcon||
           bank.inventoryCount(firstIcon)!=firstBefore-1)
            throw new AssertionError(
                "cosmetic equip retry did not commit"
            );

        bank.spawnItem(
            secondIcon,
            1,
            good
        );
        int secondSlot=findSlot(bank,secondIcon);
        int secondBefore=bank.inventoryCount(secondIcon);
        int firstInventoryBeforeReplace=
            bank.inventoryCount(firstIcon);

        ItemContainerAction replace=
            new ItemContainerAction(
                41,
                BankState.NORMAL_INVENTORY_CONTAINER,
                secondSlot,
                secondIcon,
                0,
                "INVENTORY_OPTION"
            );

        boolean replaceFailed=false;
        try{
            handler.handle(
                replace,
                "cosmetic-atomic",
                fullQueueWriter(
                    new int[]{29,30,31,32}
                )
            );
        }catch(java.io.IOException expected){
            replaceFailed=true;
        }

        if(!replaceFailed||
           playerState.cosmetic().itemId()!=firstIcon||
           bank.inventoryCount(secondIcon)!=secondBefore||
           bank.inventoryCount(firstIcon)!=
                firstInventoryBeforeReplace)
            throw new AssertionError(
                "failed cosmetic replacement mutated canonical state"
            );

        LocalEquipmentItemActionHandler.Result replaceRetry=
            handler.handle(
                replace,
                "cosmetic-atomic",
                good
            );

        if(replaceRetry==null||
           !"COSMETIC_EQUIP".equals(
                replaceRetry.saveReason
           )||
           playerState.cosmetic().itemId()!=secondIcon||
           bank.inventoryCount(secondIcon)!=secondBefore-1||
           bank.inventoryCount(firstIcon)!=
                firstInventoryBeforeReplace+1)
            throw new AssertionError(
                "cosmetic replacement retry did not commit exact swap"
            );

        int secondInventoryBeforeUnequip=
            bank.inventoryCount(secondIcon);

        ItemContainerAction unequip=
            new ItemContainerAction(
                145,
                BankState.COSMETIC_WIDGET,
                0,
                secondIcon,
                0,
                "REMOVE"
            );

        boolean unequipFailed=false;
        try{
            handler.handle(
                unequip,
                "cosmetic-atomic",
                fullQueueWriter(
                    new int[]{33,34,35,36}
                )
            );
        }catch(java.io.IOException expected){
            unequipFailed=true;
        }

        if(!unequipFailed||
           playerState.cosmetic().itemId()!=secondIcon||
           bank.inventoryCount(secondIcon)!=
                secondInventoryBeforeUnequip)
            throw new AssertionError(
                "failed cosmetic unequip mutated canonical state"
            );

        LocalEquipmentItemActionHandler.Result unequipRetry=
            handler.handle(
                unequip,
                "cosmetic-atomic",
                good
            );

        if(unequipRetry==null||
           !"COSMETIC_WIDGET_REMOVE".equals(
                unequipRetry.saveReason
           )||
           playerState.cosmetic().active()||
           bank.inventoryCount(secondIcon)!=
                secondInventoryBeforeUnequip+1)
            throw new AssertionError(
                "cosmetic unequip retry did not commit"
            );

        WorldPlayer mirroredPlayer=
            new WorldPlayer();
        BankState mirroredBank=mirroredPlayer.bank();
        EquipmentState mirroredEquipment=
            mirroredPlayer.equipment();
        PlayerState mirroredState=
            mirroredPlayer.playerState();
        LocalEquipmentItemActionHandler mirroredHandler=
            new LocalEquipmentItemActionHandler(
                mirroredBank,
                mirroredEquipment,
                mirroredState,
                new PlayerPresentationService(
                    new DevAuthorityWorkbench()
                ),
                mirroredPlayer.combatStyles()
            );
        ServerPacketWriter mirroredGood=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{37,38,39,40}
                )
            );

        mirroredBank.spawnItem(
            firstIcon,
            1,
            mirroredGood
        );
        mirroredBank.open(
            mirroredGood
        );
        int mirroredSlot=
            findSlot(
                mirroredBank,
                firstIcon
            );
        int mirroredBefore=
            mirroredBank.inventoryCount(firstIcon);

        boolean mirroredFailed=false;
        try{
            mirroredHandler.handle(
                new ItemContainerAction(
                    41,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    mirroredSlot,
                    firstIcon,
                    0,
                    "INVENTORY_OPTION"
                ),
                "cosmetic-open-bank",
                fullQueueWriter(
                    new int[]{41,42,43,44}
                )
            );
        }catch(java.io.IOException expected){
            mirroredFailed=true;
        }

        if(!mirroredFailed||
           mirroredState.cosmetic().active()||
           mirroredBank.inventoryCount(firstIcon)!=
                mirroredBefore)
            throw new AssertionError(
                "failed open-bank cosmetic equip mutated canonical state"
            );

        mirroredHandler.handle(
            new ItemContainerAction(
                41,
                BankState.NORMAL_INVENTORY_CONTAINER,
                mirroredSlot,
                firstIcon,
                0,
                "INVENTORY_OPTION"
            ),
            "cosmetic-open-bank",
            mirroredGood
        );

        if(mirroredState.cosmetic().itemId()!=firstIcon||
           mirroredBank.inventoryCount(firstIcon)!=
                mirroredBefore-1)
            throw new AssertionError(
                "open-bank cosmetic retry did not commit"
            );
    }

    private static void testOverridePublicationAtomicity()
        throws Exception
    {
        final int firstOverride=21560;
        final int secondOverride=22132;

        if(!CosmeticOverrideService
                .definitionAllowsOverride(
                    firstOverride
                )||
           !CosmeticOverrideService
                .definitionAllowsOverride(
                    secondOverride
                ))
            throw new AssertionError(
                "Override fixtures lost exact action authority"
            );

        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        EquipmentState equipment=player.equipment();
        PlayerState playerState=player.playerState();
        LocalEquipmentItemActionHandler handler=
            new LocalEquipmentItemActionHandler(
                bank,
                equipment,
                playerState,
                new PlayerPresentationService(
                    new DevAuthorityWorkbench()
                ),
                player.combatStyles()
            );
        ServerPacketWriter good=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{45,46,47,48}
                )
            );

        bank.spawnItem(
            firstOverride,
            1,
            good
        );
        int firstSlot=
            findSlot(
                bank,
                firstOverride
            );
        int firstBefore=
            bank.inventoryCount(
                firstOverride
            );
        int firstOpcode=
            overrideOpcode(
                firstOverride
            );

        ItemContainerAction firstAction=
            new ItemContainerAction(
                firstOpcode,
                BankState.NORMAL_INVENTORY_CONTAINER,
                firstSlot,
                firstOverride,
                0,
                "OVERRIDE"
            );

        boolean firstFailed=false;
        try{
            handler.handle(
                firstAction,
                "override-atomic",
                fullQueueWriter(
                    new int[]{49,50,51,52}
                )
            );
        }catch(java.io.IOException expected){
            firstFailed=true;
        }

        if(!firstFailed||
           playerState.cosmetic().active()||
           bank.inventoryCount(firstOverride)!=
                firstBefore)
            throw new AssertionError(
                "failed Override mutated canonical state"
            );

        LocalEquipmentItemActionHandler.Result firstRetry=
            handler.handle(
                firstAction,
                "override-atomic",
                good
            );

        if(firstRetry==null||
           !"COSMETIC_OVERRIDE".equals(
                firstRetry.saveReason
           )||
           playerState.cosmetic().itemId()!=
                firstOverride||
           bank.inventoryCount(firstOverride)!=
                firstBefore-1)
            throw new AssertionError(
                "Override retry did not commit"
            );

        bank.spawnItem(
            secondOverride,
            1,
            good
        );
        int secondSlot=
            findSlot(
                bank,
                secondOverride
            );
        int secondBefore=
            bank.inventoryCount(
                secondOverride
            );
        int oldInventoryBefore=
            bank.inventoryCount(
                firstOverride
            );
        int secondOpcode=
            overrideOpcode(
                secondOverride
            );

        ItemContainerAction replace=
            new ItemContainerAction(
                secondOpcode,
                BankState.NORMAL_INVENTORY_CONTAINER,
                secondSlot,
                secondOverride,
                0,
                "OVERRIDE"
            );

        boolean replaceFailed=false;
        try{
            handler.handle(
                replace,
                "override-atomic",
                fullQueueWriter(
                    new int[]{53,54,55,56}
                )
            );
        }catch(java.io.IOException expected){
            replaceFailed=true;
        }

        if(!replaceFailed||
           playerState.cosmetic().itemId()!=
                firstOverride||
           bank.inventoryCount(secondOverride)!=
                secondBefore||
           bank.inventoryCount(firstOverride)!=
                oldInventoryBefore)
            throw new AssertionError(
                "failed Override replacement mutated canonical state"
            );

        LocalEquipmentItemActionHandler.Result replaceRetry=
            handler.handle(
                replace,
                "override-atomic",
                good
            );

        if(replaceRetry==null||
           !"COSMETIC_OVERRIDE".equals(
                replaceRetry.saveReason
           )||
           playerState.cosmetic().itemId()!=
                secondOverride||
           bank.inventoryCount(secondOverride)!=
                secondBefore-1||
           bank.inventoryCount(firstOverride)!=
                oldInventoryBefore+1||
           bank.inventoryAt(secondSlot)==null||
           bank.inventoryAt(secondSlot).itemId!=
                firstOverride)
            throw new AssertionError(
                "Override replacement retry did not commit exact swap"
            );

        WorldPlayer mirroredPlayer=
            new WorldPlayer();
        BankState mirroredBank=
            mirroredPlayer.bank();
        LocalEquipmentItemActionHandler mirroredHandler=
            new LocalEquipmentItemActionHandler(
                mirroredBank,
                mirroredPlayer.equipment(),
                mirroredPlayer.playerState(),
                new PlayerPresentationService(
                    new DevAuthorityWorkbench()
                ),
                mirroredPlayer.combatStyles()
            );
        ServerPacketWriter mirroredGood=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{57,58,59,60}
                )
            );

        mirroredBank.spawnItem(
            firstOverride,
            1,
            mirroredGood
        );
        mirroredBank.open(
            mirroredGood
        );
        int mirroredSlot=
            findSlot(
                mirroredBank,
                firstOverride
            );
        int mirroredBefore=
            mirroredBank.inventoryCount(
                firstOverride
            );

        boolean mirroredFailed=false;
        try{
            mirroredHandler.handle(
                new ItemContainerAction(
                    firstOpcode,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    mirroredSlot,
                    firstOverride,
                    0,
                    "OVERRIDE"
                ),
                "override-open-bank",
                fullQueueWriter(
                    new int[]{61,62,63,64}
                )
            );
        }catch(java.io.IOException expected){
            mirroredFailed=true;
        }

        if(!mirroredFailed||
           mirroredPlayer.playerState()
               .cosmetic().active()||
           mirroredBank.inventoryCount(
               firstOverride
           )!=mirroredBefore)
            throw new AssertionError(
                "failed open-bank Override mutated canonical state"
            );

        mirroredHandler.handle(
            new ItemContainerAction(
                firstOpcode,
                BankState.NORMAL_INVENTORY_CONTAINER,
                mirroredSlot,
                firstOverride,
                0,
                "OVERRIDE"
            ),
            "override-open-bank",
            mirroredGood
        );

        if(mirroredPlayer.playerState()
               .cosmetic().itemId()!=
                    firstOverride||
           mirroredBank.inventoryCount(
               firstOverride
           )!=mirroredBefore-1)
            throw new AssertionError(
                "open-bank Override retry did not commit"
            );
    }

    private static int overrideOpcode(
        int itemId
    ){
        int[] opcodes=
            new int[]{
                122,
                41,
                16,
                75,
                87
            };

        for(int opcode:
                opcodes){
            InventoryActionRouter.Resolution resolution=
                InventoryActionRouter.resolve(
                    new ItemContainerAction(
                        opcode,
                        BankState.NORMAL_INVENTORY_CONTAINER,
                        0,
                        itemId,
                        0,
                        "OVERRIDE_PROBE"
                    )
                );

            if(resolution.is("Override"))
                return opcode;
        }

        throw new AssertionError(
            "Override opcode unresolved item="+
            itemId
        );
    }

    private static ServerPacketWriter fullQueueWriter(
        int[] seed
    )throws Exception{
        OutboundPacketQueue queue=
            new OutboundPacketQueue(1024);
        queue.offer(
            new byte[1024]
        );
        return new ServerPacketWriter(
            queue,
            new IsaacCipher(seed)
        );
    }

    private static int findSlot(BankState bank,int itemId){
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==itemId&&stack.qty>0)return i;
        }
        return -1;
    }
}
