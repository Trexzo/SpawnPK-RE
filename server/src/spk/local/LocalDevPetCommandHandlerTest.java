package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class LocalDevPetCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);
        MovementState movement=new MovementState();
        BankState bank=new BankState();

        LocalDevPetCommandHandler handler=
            new LocalDevPetCommandHandler(
                dev,npcs,movement,bank);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        List<String> info=handler.handle(
            new String[]{"devpet","info"},writer);

        assertOneContains(
            info,
            "V591_DEV_PET_INFO",
            "info");

        List<String> fx=handler.handle(
            new String[]{"devpet","fx","7"},writer);

        assertOneContains(
            fx,
            "V591_DEV_PET_FX_SET value=7",
            "fx");

        if(dev.petParticleSelector()==null||
           dev.petParticleSelector().intValue()!=7)
            throw new AssertionError(
                "particle selector not updated");

        List<String> badFx=handler.handle(
            new String[]{"devpet","fx","999"},writer);

        assertOneContains(
            badFx,
            "V591_DEV_PET_FX result=REJECTED expected=auto|next|prev|0..255",
            "bad fx");

        List<String> map=handler.handle(
            new String[]{"devpet","map","npc","22088","4020"},
            writer);

        assertOneContains(
            map,
            "V593_DEV_PET_MAP_NPC item=22088 npc=4020",
            "npc map");

        Integer mapped=dev.petNpcBindings().get(22088);
        if(mapped==null||mapped.intValue()!=4020)
            throw new AssertionError(
                "dev npc binding not retained: "+
                dev.petNpcBindings());

        List<String> follow=handler.handle(
            new String[]{"devpet","follow","freeze"},writer);

        assertOneContains(
            follow,
            "V591_DEV_PET_FOLLOW DEV_PET_FOLLOW_FROZEN",
            "follow freeze");

        if(!npcs.followFrozen())
            throw new AssertionError(
                "follow freeze state not applied");

        if(handler.handle(
            new String[]{"devnpc","info"},writer)!=null)
            throw new AssertionError(
                "unrelated command should remain unhandled");

        System.out.println(
            "LOCAL_DEV_PET_COMMAND_HANDLER_PASS info=true fx=true map=true follow=true unrelatedRejected=true");
    }

    private static void assertOneContains(
        List<String> lines,
        String expected,
        String label
    ){
        if(lines==null||
           lines.size()!=1||
           !lines.get(0).contains(expected)){
            throw new AssertionError(
                label+" lines="+lines+
                " expected="+expected);
        }
    }
}
