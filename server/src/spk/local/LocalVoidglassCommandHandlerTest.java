package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalVoidglassCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        VoidglassR3CustomContent.ensureRuntimePetMapping();

        WorldPlayer player=new WorldPlayer();
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);
        VoidglassPetState r1=new VoidglassPetState();

        LocalVoidglassCommandHandler handler=
            new LocalVoidglassCommandHandler(
                player.bank(),
                player.petState(),
                npcs,
                player.movement(),
                dev,
                r1);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        LocalVoidglassCommandHandler.Outcome give=
            handler.handle(
                new String[]{"voidglass3","give"},
                writer);

        if(give==null||
           !"CUSTOM_VOIDGLASS_R3_GIVE".equals(
               give.saveReason)||
           !give.text.contains(
               "CUSTOM_PET_R3_VOIDGLASS VOIDGLASS_R3_GIVE"))
            throw new AssertionError(
                "R3 give="+
                (give==null?null:give.text));

        if(player.bank().inventoryCount(
            VoidglassR3CustomContent.ITEM_ID)!=1)
            throw new AssertionError(
                "R3 item not added to inventory");

        LocalVoidglassCommandHandler.Outcome status=
            handler.handle(
                new String[]{"voidglass3","status"},
                writer);

        if(status==null||
           !status.text.contains(
               "CUSTOM_PET_R3_VOIDGLASS"))
            throw new AssertionError(
                "R3 status="+
                (status==null?null:status.text));

        LocalVoidglassCommandHandler.Outcome r1On=
            handler.handle(
                new String[]{"voidglass","on"},
                writer);

        if(r1On==null||
           !r1On.text.contains(
               "REJECTED_NEED_BASE_VASA"))
            throw new AssertionError(
                "R1 authority guard="+
                (r1On==null?null:r1On.text));

        LocalVoidglassCommandHandler.Outcome rawGive=
            handler.giveR3(writer);

        if(rawGive.text.startsWith(
            "CUSTOM_PET_R3_VOIDGLASS"))
            throw new AssertionError(
                "raw control-center result gained command prefix");

        if(handler.handle(
            new String[]{"pettest","status"},
            writer)!=null)
            throw new AssertionError(
                "unrelated command consumed");

        System.out.println(
            "LOCAL_VOIDGLASS_COMMAND_HANDLER_PASS r3Give=true saveSignal=true r3Status=true r1Guard=true uiHelperBoundary=true");
    }
}
