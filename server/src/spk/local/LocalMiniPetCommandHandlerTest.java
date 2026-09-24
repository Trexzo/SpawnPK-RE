package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalMiniPetCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);

        LocalMiniPetCommandHandler h=
            new LocalMiniPetCommandHandler(
                player.miniPets(),
                player.petState(),
                npcs,
                player.movement()
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter w=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        LocalMiniPetCommandHandler.Result status=
            h.status();

        if(status==null||
           !status.logText.startsWith(
               "V511_MINIPET_STATUS"))
            throw new AssertionError(
                "status effect"
            );

        if(status.saveReason!=null)
            throw new AssertionError(
                "status must not request save"
            );

        LocalMiniPetCommandHandler.Result set=
            h.configure(
                22088,
                w
            );

        if(set==null||
           !set.logText.contains(
               "MINIPET_CONFIGURED item=22088"))
            throw new AssertionError(
                "set effect="+
                (set==null
                    ?"null"
                    :set.logText)
            );

        if(!"MINIPET_SET_DEV".equals(
                set.saveReason))
            throw new AssertionError(
                "set save reason="+
                set.saveReason
            );

        if(player.petState()
                .miniItemId()!=22088)
            throw new AssertionError(
                "mini selection not delegated"
            );

        LocalMiniPetCommandHandler.Result off=
            h.off(
                w
            );

        if(off==null||
           !off.logText.contains(
               "MINIPET_DISABLED"))
            throw new AssertionError(
                "off effect"
            );

        if(!"MINIPET_OFF".equals(
                off.saveReason))
            throw new AssertionError(
                "off save reason="+
                off.saveReason
            );

        if(player.petState()
                .miniConfigured())
            throw new AssertionError(
                "mini selection not cleared"
            );

        LocalMiniPetCommandHandler.Result rejected=
            h.configure(
                999999,
                w
            );

        if(rejected==null||
           !rejected.logText.contains(
               "REJECTED_NOT_MINI_PET"))
            throw new AssertionError(
                "invalid mini-pet effect"
            );

        if(rejected.saveReason!=null)
            throw new AssertionError(
                "rejected set must not request save"
            );

        LocalMiniPetCommandHandler.Result help=
            h.help();

        if(help==null||
           !help.logText.contains(
               "nativeInventoryAction=Configure/C2S122"))
            throw new AssertionError(
                "help effect"
            );

        System.out.println(
            "LOCAL_MINIPET_COMMAND_HANDLER_PASS "+
            "configure=true "+
            "off=true "+
            "persistenceSignal=true "+
            "invalidNoSave=true "+
            "parserAbsent=true"
        );
    }
}
