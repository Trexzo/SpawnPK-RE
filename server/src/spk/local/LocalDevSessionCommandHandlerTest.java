package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalDevSessionCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        try{
            WorldPlayer player=new WorldPlayer();
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);

            LocalDevSessionCommandHandler handler=
                new LocalDevSessionCommandHandler(
                    world,
                    dev,
                    npcs,
                    new PlayerPresentationService(dev),
                    player.equipment(),
                    player.playerState(),
                    player.bank(),
                    player.petState(),
                    player.movement());

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}));

            SceneUpdatePublisher scene=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0));

            String info=handler.handle(
                new String[]{"dev"},
                "opensrc",
                scene,
                writer);

            if(info==null||
               !info.startsWith("V592_DEV_INFO ")){
                throw new AssertionError(
                    "info="+info);
            }

            LocalDevVisualOverrideStore.clear();
            LocalDevVisualOverrideStore.set(
                "alpha","123");

            String reset=handler.handle(
                new String[]{"dev","reset"},
                "opensrc",
                scene,
                writer);

            if(reset==null||
               !reset.startsWith("V511_DEV_RESET ")||
               !reset.contains(
                   "devWorld=DEV_WORLD_RESET ground=0 objects=0")||
               !reset.contains("persisted=false")){
                throw new AssertionError(
                    "reset="+reset);
            }

            if(!LocalDevVisualOverrideStore.summary()
                .contains("alpha=123")){
                throw new AssertionError(
                    "command reset unexpectedly cleared visual overrides: "+
                    LocalDevVisualOverrideStore.summary());
            }

            String panelReset=handler.resetForPanel(
                "opensrc",
                scene,
                writer);

            if(!panelReset.contains("persisted=false")){
                throw new AssertionError(
                    "panel reset="+panelReset);
            }

            if(LocalDevVisualOverrideStore.summary()
                .contains("alpha=123")){
                throw new AssertionError(
                    "panel reset did not clear visual overrides: "+
                    LocalDevVisualOverrideStore.summary());
            }

            if(handler.handle(
                new String[]{"devpet","info"},
                "opensrc",
                scene,
                writer)!=null){
                throw new AssertionError(
                    "unrelated command consumed");
            }

            System.out.println(
                "LOCAL_DEV_SESSION_COMMAND_HANDLER_PASS info=true reset=true commandVisualOverrideParity=true panelVisualClear=true boundary=true");
        }finally{
            LocalDevVisualOverrideStore.clear();
            world.close();
        }
    }
}
