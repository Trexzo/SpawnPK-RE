package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

public final class LocalPetCompatibilityCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);
        PetAccessoryState accessory=
            player.petAccessoryState();

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
            handler.accessoryStatus();

        if(status==null||
           !status.logText.contains(
               "V5128_PET_ACCESSORY active=NONE")||
           status.saveReason!=null||
           status.dialogResult!=null){
            throw new AssertionError(
                "status="+
                (status==null
                    ?null
                    :status.logText));
        }

        accessory.setActiveItem(20542);

        LocalPetCompatibilityCommandHandler.Outcome off=
            handler.accessoryOff(
                writer
            );

        if(off==null||
           !"PET_ACCESSORY_DEV_OFF".equals(
               off.saveReason)||
           !off.logText.contains(
               "V5128_PET_ACCESSORY active=NONE")||
           accessory.activeItem()!=0){
            throw new AssertionError(
                "off="+
                (off==null
                    ?null
                    :off.logText)+
                " active="+accessory.activeItem());
        }

        LocalPetInventoryDialogHandler.Result color=
            handler.switchColor(
                24016,
                writer
            );

        if(color==null||
           !color.logText.contains(
               "V5128_SCOOBY_SWITCH_COLOR")||
           !color.logText.contains(
               "REJECTED_NO_VARIANT_IN_INVENTORY")){
            throw new AssertionError(
                "color="+
                (color==null
                    ?null
                    :color.logText));
        }

        for(Method method:
                LocalPetCompatibilityCommandHandler.class
                    .getDeclaredMethods())
            if("handle".equals(
                    method.getName()))
                throw new AssertionError(
                    "raw pet compatibility command parser remains"
                );

        System.out.println(
            "LOCAL_PET_COMPATIBILITY_COMMAND_HANDLER_PASS "+
            "accessoryStatusEffect=true "+
            "accessoryOffEffect=true "+
            "saveSignal=true "+
            "colorCompatEffect=true "+
            "parserAbsent=true"
        );
    }
}
