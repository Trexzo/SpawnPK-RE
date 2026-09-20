package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class LocalDevToolCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        WorldPlayer player=new WorldPlayer();

        LocalDevToolCommandHandler handler=
            new LocalDevToolCommandHandler(
                dev,
                player.bank(),
                player.equipment());

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        List<String> traceOn=handler.handle(
            new String[]{"devtrace","on"},
            writer);

        assertContains(
            traceOn,
            "V592_DEV_TRACE",
            "trace on");

        if(!dev.trace().enabled())
            throw new AssertionError(
                "trace was not enabled");

        List<String> asset=handler.handle(
            new String[]{"devasset","item","4151"},
            writer);

        assertContains(
            asset,
            "V592_",
            "asset item");

        List<String> itemHelp=handler.handle(
            new String[]{"devitem","help"},
            writer);

        assertContains(
            itemHelp,
            "V591_DEV_ITEM_HELP",
            "item help");

        List<String> combatInfo=handler.handle(
            new String[]{"devcombat","info"},
            writer);

        assertContains(
            combatInfo,
            "V591_DEV_COMBAT_INFO",
            "combat info");

        List<String> badAnim=handler.handle(
            new String[]{"devcombat","anim","70000"},
            writer);

        assertContains(
            badAnim,
            "V591_DEV_COMBAT_ANIM result=REJECTED animation -1..65535",
            "combat animation rejection");

        if(handler.handle(
            new String[]{"devnpc","info"},
            writer)!=null)
            throw new AssertionError(
                "unrelated dev command was consumed");

        System.out.println(
            "LOCAL_DEV_TOOL_COMMAND_HANDLER_PASS trace=true asset=true itemPreviewRoute=true combat=true boundary=true");
    }

    private static void assertContains(
        List<String> lines,
        String expected,
        String label
    ){
        if(lines==null||lines.isEmpty()){
            throw new AssertionError(
                label+" returned no lines");
        }

        for(String line:lines){
            if(line.contains(expected))return;
        }

        throw new AssertionError(
            label+" lines="+lines+
            " expected="+expected);
    }
}
