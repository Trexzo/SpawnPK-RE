package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalPetInventoryDialogHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        PetState petState=player.petState();
        MiniPetService miniPets=player.miniPets();
        MovementState movement=player.movement();
        NpcRegistry npcs=new NpcRegistry(new DevAuthorityWorkbench());
        PetAccessoryState accessory=new PetAccessoryState();

        LocalPetInventoryDialogHandler handler=
            new LocalPetInventoryDialogHandler(
                bank,
                miniPets,
                petState,
                npcs,
                movement,
                accessory
            );

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        bank.spawnItem(22088,1,writer);
        int miniSlot=findSlot(bank,22088);
        if(miniSlot<0)throw new AssertionError("mini item not spawned");

        ItemContainerAction miniRead=new ItemContainerAction(
            122,
            BankState.NORMAL_INVENTORY_CONTAINER,
            miniSlot,
            22088,
            0,
            "INVENTORY_OPTION_1"
        );

        LocalPetInventoryDialogHandler.Result miniOpen=
            handler.handleItemAction(miniRead,writer);

        if(miniOpen==null||
           miniOpen.keyAction!=
               LocalPetInventoryDialogHandler.KeyAction.PUBLISH_2482_2485||
           !miniOpen.logText.contains("V5127_MINIPET_CONFIGURE"))
            throw new AssertionError("mini open="+
                (miniOpen==null?null:miniOpen.logText));

        LocalPetInventoryDialogHandler.Result miniActivate=
            handler.handleWidget(2482,writer);

        if(miniActivate==null||
           !"MINIPET_CONFIGURE".equals(miniActivate.saveReason)||
           miniActivate.keyAction!=
               LocalPetInventoryDialogHandler.KeyAction.CLEAR_AFTER_LOG||
           !petState.miniConfigured()||
           petState.miniItemId()!=22088)
            throw new AssertionError("mini activate failure");

        bank.spawnItem(24016,1,writer);
        int colorSlot=findSlot(bank,24016);
        if(colorSlot<0)throw new AssertionError("color item not spawned");

        ItemContainerAction colorAction=new ItemContainerAction(
            75,
            BankState.NORMAL_INVENTORY_CONTAINER,
            colorSlot,
            24016,
            0,
            "INVENTORY_OPTION_3"
        );

        LocalPetInventoryDialogHandler.Result colorOpen=
            handler.handleItemAction(colorAction,writer);

        if(colorOpen==null||
           !colorOpen.logText.contains(
               "family=SCOOBY_BEHEMOTH")||
           colorOpen.keyAction!=
               LocalPetInventoryDialogHandler.KeyAction.PUBLISH_2482_2485)
            throw new AssertionError("color open="+
                (colorOpen==null?null:colorOpen.logText));

        LocalPetInventoryDialogHandler.Result colorSwitch=
            handler.handleWidget(2483,writer);

        if(colorSwitch==null||
           !"PET_SWITCH_COLOR".equals(colorSwitch.saveReason)||
           bank.inventoryAt(colorSlot)==null||
           bank.inventoryAt(colorSlot).itemId!=24017)
            throw new AssertionError("color switch="+
                (colorSwitch==null?null:colorSwitch.logText));

        LocalPetInventoryDialogHandler.Result compat=
            handler.openScoobyColorCompat(24017,writer);

        if(compat==null||
           !compat.logText.contains("result=DIALOG_OPEN")||
           !handler.hasAnyOpen())
            throw new AssertionError("compat color open");

        LocalPetInventoryDialogHandler.CloseState close=
            handler.clearAll();

        if(!close.petColorWasOpen||
           close.miniConfigWasOpen||
           close.petAccessoryWasOpen||
           handler.hasAnyOpen())
            throw new AssertionError("close state");

        System.out.println(
            "LOCAL_PET_INVENTORY_DIALOG_HANDLER_PASS mini=true color=true compat=true pendingStateOwned=true");
    }

    private static int findSlot(BankState bank,int itemId){
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&stack.itemId==itemId&&stack.qty>0)return i;
        }
        return -1;
    }
}
