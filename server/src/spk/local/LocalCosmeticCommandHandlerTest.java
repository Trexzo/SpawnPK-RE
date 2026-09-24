package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalCosmeticCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        PlayerPresentationService presentation=new PlayerPresentationService(dev);
        LocalCosmeticCommandHandler h=new LocalCosmeticCommandHandler(
            player.bank(),player.equipment(),player.playerState(),presentation);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(
            wire,
            new IsaacCipher(new int[]{1,2,3,4})
        );

        LocalCosmeticCommandHandler.Result info=
            h.info();
        if(info==null||!info.logText.startsWith("V5124_COSMETIC_INFO"))
            throw new AssertionError("info effect");
        if(info.saveReason!=null)
            throw new AssertionError("info must not save");

        LocalCosmeticCommandHandler.Result none=
            h.remove("opensrc",w);
        if(none==null||!none.logText.contains("COSMETIC_NONE_ACTIVE"))
            throw new AssertionError("empty remove effect");
        if(none.saveReason!=null)
            throw new AssertionError("empty remove must not save");

        // Native icon root 10556 is exact-current cosmetic-family authority.
        player.playerState().cosmetic().set(10556);
        player.playerState().syncEquipmentPresentation(player.equipment());
        if(player.playerState().nativeIconItemId()!=10556)
            throw new AssertionError("precondition native bs");

        int before=wire.size();
        LocalCosmeticCommandHandler.Result off=
            h.remove("opensrc",w);
        if(off==null||!off.logText.contains("COSMETIC_UNEQUIP_OK item=10556"))
            throw new AssertionError("remove effect="+(off==null?"null":off.logText));
        if(!"COSMETIC_OFF".equals(off.saveReason))
            throw new AssertionError("remove save reason="+off.saveReason);
        if(player.playerState().cosmetic().active())
            throw new AssertionError("cosmetic state still active");
        if(player.playerState().nativeIconItemId()!=-1)
            throw new AssertionError("native bs not cleared");
        if(player.bank().inventoryCount(10556)!=1)
            throw new AssertionError("cosmetic not returned to inventory");
        if(wire.size()<=before)
            throw new AssertionError("successful remove emitted no presentation packets");

        LocalCosmeticCommandHandler.Result help=
            h.help();
        if(help==null||
           !help.logText.startsWith("V511_COSMETIC_HELP")||
           help.saveReason!=null)
            throw new AssertionError("help effect");

        for(java.lang.reflect.Method method:
                LocalCosmeticCommandHandler.class.getDeclaredMethods())
            if("handle".equals(method.getName()))
                throw new AssertionError(
                    "legacy cosmetic command parser still exists"
                );

        System.out.println(
            "LOCAL_COSMETIC_COMMAND_HANDLER_PASS "+
            "infoEffect=true "+
            "removeEffect=true "+
            "helpEffect=true "+
            "legacyParser=false "+
            "inventoryReturn=true "+
            "appearanceRefresh=true "+
            "persistenceSignal=true"
        );
    }
}
