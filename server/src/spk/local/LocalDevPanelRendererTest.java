package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalDevPanelRendererTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);
        DevControlCenter panel=new DevControlCenter();

        LocalDevPanelRenderer renderer=
            new LocalDevPanelRenderer(
                panel,
                player.equipment(),
                player.combatStyles(),
                dev,
                new CombatEngine(dev),
                npcs,
                player.petState(),
                player.magic(),
                player.prayers(),
                player.movement(),
                new PlayerPresentationService(dev)
            );

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{1,2,3,4}));

        if(renderer.render(writer)){
            throw new AssertionError(
                "closed panel rendered");
        }

        if(wire.size()!=0){
            throw new AssertionError(
                "closed panel emitted packets");
        }

        panel.open(DevControlCenter.Page.MAIN);
        int before=wire.size();

        if(!renderer.render(writer)){
            throw new AssertionError(
                "MAIN panel not rendered");
        }

        if(wire.size()<=before){
            throw new AssertionError(
                "MAIN panel emitted no packets");
        }

        panel.setPage(DevControlCenter.Page.WORLD);
        before=wire.size();

        if(!renderer.render(writer)){
            throw new AssertionError(
                "WORLD panel not rendered");
        }

        if(wire.size()<=before){
            throw new AssertionError(
                "WORLD panel emitted no packets");
        }

        panel.setPage(
            DevControlCenter.Page.RESEARCH_PET);
        before=wire.size();

        if(!renderer.render(writer)){
            throw new AssertionError(
                "RESEARCH_PET panel not rendered");
        }

        if(wire.size()<=before){
            throw new AssertionError(
                "RESEARCH_PET panel emitted no packets");
        }

        if(panel.page()!=
           DevControlCenter.Page.RESEARCH_PET){
            throw new AssertionError(
                "renderer mutated panel navigation");
        }

        System.out.println(
            "LOCAL_DEV_PANEL_RENDERER_PASS closedNoop=true main=true world=true researchPet=true navigationReadOnly=true");
    }
}
