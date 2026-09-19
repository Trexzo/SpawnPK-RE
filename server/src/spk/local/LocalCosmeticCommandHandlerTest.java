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
        ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));

        LocalCosmeticCommandHandler.Result info=
            h.handle(new String[]{"cosmetic","info"},"opensrc",w);
        if(info==null||!info.logText.startsWith("V5124_COSMETIC_INFO"))
            throw new AssertionError("info route");
        if(info.saveReason!=null)throw new AssertionError("info must not save");

        LocalCosmeticCommandHandler.Result none=
            h.handle(new String[]{"cosmetic","off"},"opensrc",w);
        if(none==null||!none.logText.contains("COSMETIC_NONE_ACTIVE"))
            throw new AssertionError("empty off route");
        if(none.saveReason!=null)throw new AssertionError("empty off must not save");

        // Native icon root 10556 is exact-current cosmetic-family authority.
        player.playerState().cosmetic().set(10556);
        player.playerState().syncEquipmentPresentation(player.equipment());
        if(player.playerState().nativeIconItemId()!=10556)
            throw new AssertionError("precondition native bs");

        int before=wire.size();
        LocalCosmeticCommandHandler.Result off=
            h.handle(new String[]{"cosmetic","remove"},"opensrc",w);
        if(off==null||!off.logText.contains("COSMETIC_UNEQUIP_OK item=10556"))
            throw new AssertionError("remove route="+(off==null?"null":off.logText));
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

        if(h.handle(new String[]{"item","10556"},"opensrc",w)!=null)
            throw new AssertionError("unrelated command must remain outside cosmetic handler");

        System.out.println("LOCAL_COSMETIC_COMMAND_HANDLER_PASS info=true remove=true inventoryReturn=true appearanceRefresh=true persistenceSignal=true");
    }
}
