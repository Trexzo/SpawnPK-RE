package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalPetCompatibilityCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);
        PetAccessoryState accessory=new PetAccessoryState();

        LocalPetInventoryDialogHandler dialogs=
            new LocalPetInventoryDialogHandler(
                player.bank(),
                player.miniPets(),
                player.petState(),
                npcs,
                player.movement(),
                accessory
            );

        LocalPetCompatibilityCommandHandler handler=
            new LocalPetCompatibilityCommandHandler(
                accessory,
                npcs,
                player.movement(),
                dialogs
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}));

        LocalPetCompatibilityCommandHandler.Outcome status=
            handler.handle(
                new String[]{"petaccessory"},
                writer);

        if(status==null||
           !status.logText.contains(
               "V5128_PET_ACCESSORY active=NONE")||
           status.saveReason!=null||
           status.dialogResult!=null){
            throw new AssertionError(
                "status="+
                (status==null?null:status.logText));
        }

        accessory.setActiveItem(20542);

        LocalPetCompatibilityCommandHandler.Outcome off=
            handler.handle(
                new String[]{"petaccessory","off"},
                writer);

        if(off==null||
           !"PET_ACCESSORY_DEV_OFF".equals(
               off.saveReason)||
           !off.logText.contains(
               "V5128_PET_ACCESSORY active=NONE")||
           accessory.activeItem()!=0){
            throw new AssertionError(
                "off="+
                (off==null?null:off.logText)+
                " active="+accessory.activeItem());
        }

        LocalPetCompatibilityCommandHandler.Outcome color=
            handler.handle(
                new String[]{"petswitchcolor","24016"},
                writer);

        if(color==null||
           color.dialogResult==null||
           !color.dialogResult.logText.contains(
               "V5128_SCOOBY_SWITCH_COLOR")||
           !color.dialogResult.logText.contains(
               "REJECTED_NO_VARIANT_IN_INVENTORY")){
            throw new AssertionError(
                "color="+
                (color==null||color.dialogResult==null
                    ?null
                    :color.dialogResult.logText));
        }

        if(handler.handle(
            new String[]{"petstatus"},
            writer)!=null){
            throw new AssertionError(
                "unrelated command consumed");
        }

        System.out.println(
            "LOCAL_PET_COMPATIBILITY_COMMAND_HANDLER_PASS accessoryStatus=true accessoryOff=true saveSignal=true colorCompatBoundary=true unrelatedRejected=true");
    }
}
