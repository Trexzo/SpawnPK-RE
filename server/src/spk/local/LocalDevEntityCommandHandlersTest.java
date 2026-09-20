package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class LocalDevEntityCommandHandlersTest {
    public static void main(String[] args)throws Exception{
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        WorldPlayer player=new WorldPlayer();
        NpcRegistry npcs=new NpcRegistry(dev);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        LocalDevPlayerCommandHandler playerHandler=
            new LocalDevPlayerCommandHandler(
                new PlayerPresentationService(dev),
                player.equipment(),
                player.playerState());

        int before=wire.size();
        List<String> anim=playerHandler.handle(
            new String[]{"devplayer","anim","808"},
            "opensrc",
            writer);

        assertOneContains(
            anim,
            "V591_DEV_PLAYER_ANIM anim=808",
            "player anim");

        if(wire.size()<=before)
            throw new AssertionError(
                "player anim emitted no packet");

        List<String> rejected=playerHandler.handle(
            new String[]{"devplayer","anim","70000"},
            "opensrc",
            writer);

        assertOneContains(
            rejected,
            "V591_DEV_PLAYER_ANIM result=REJECTED_RANGE",
            "player range");

        List<String> info=playerHandler.handle(
            new String[]{"devplayer","info"},
            "opensrc",
            writer);

        assertOneContains(
            info,
            "V592_",
            "player info");

        LocalDevNpcCommandHandler npcHandler=
            new LocalDevNpcCommandHandler(
                npcs,player.movement());

        List<String> spawn=npcHandler.handle(
            new String[]{"devnpc","spawn","7605","1","0"},
            writer);

        assertOneContains(
            spawn,
            "V592_DEV_NPC_SPAWN_OK",
            "npc spawn");

        int scene=-1;
        for(NpcEntity npc:npcs.snapshot()){
            if(npc.definitionId==7605){
                scene=npc.sceneIndex;
                break;
            }
        }

        if(scene<0)
            throw new AssertionError(
                "dev NPC not present after spawn");

        List<String> npcInfo=npcHandler.handle(
            new String[]{"devnpc","info",Integer.toString(scene)},
            writer);

        assertOneContains(
            npcInfo,
            "V592_DEV_NPC_INFO scene="+scene,
            "npc info");

        if(playerHandler.handle(
            new String[]{"devnpc","info"},
            "opensrc",
            writer)!=null)
            throw new AssertionError(
                "devplayer handler consumed devnpc");

        if(npcHandler.handle(
            new String[]{"devplayer","info"},
            writer)!=null)
            throw new AssertionError(
                "devnpc handler consumed devplayer");

        System.out.println(
            "LOCAL_DEV_ENTITY_COMMAND_HANDLERS_PASS playerPresentation=true npcDevLifecycle=true commandBoundaries=true");
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
