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

        System.out.println(
            "LOCAL_EQUIPMENT_ITEM_ACTION_HANDLER_PASS equip=true unequip=true persistenceSignals=true unrelatedRejected=true");
    }

    private static int findSlot(BankState bank,int itemId){
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==itemId&&stack.qty>0)return i;
        }
        return -1;
    }
}
