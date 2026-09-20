package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalDevPanelWidgetHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        try{
            WorldPlayer player=new WorldPlayer();
            world.registerPlayer(player,"widgettest");

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);
            CombatEngine combat=new CombatEngine(dev);
            DevControlCenter panel=new DevControlCenter();
            PlayerPresentationService presentation=
                new PlayerPresentationService(dev);
            VoidglassPetState voidglass=
                new VoidglassPetState();

            LocalPlayerInteractionHandler interactions=
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    player.movement(),
                    player.equipment());

            LocalRegionDevCommandHandler regions=
                new LocalRegionDevCommandHandler(
                    world,
                    player,
                    player.movement(),
                    interactions,
                    combat,
                    npcs,
                    player.petState(),
                    new HomeWorldRuntimePlan(),
                    ()->{}
                );

            LocalVoidglassCommandHandler voidglassCommands=
                new LocalVoidglassCommandHandler(
                    player.bank(),
                    player.petState(),
                    npcs,
                    player.movement(),
                    dev,
                    voidglass
                );

            LocalDevSessionCommandHandler devSession=
                new LocalDevSessionCommandHandler(
                    world,
                    dev,
                    npcs,
                    presentation,
                    player.equipment(),
                    player.playerState(),
                    player.bank(),
                    player.petState(),
                    player.movement()
                );

            LocalDevPanelRenderer renderer=
                new LocalDevPanelRenderer(
                    panel,
                    player.equipment(),
                    player.combatStyles(),
                    dev,
                    combat,
                    npcs,
                    player.petState(),
                    player.magic(),
                    player.prayers(),
                    player.movement(),
                    presentation
                );

            final int[] promptCount={0};
            final DevControlCenter.PendingAmount[] prompted={
                DevControlCenter.PendingAmount.NONE
            };
            final int[] keyClearCount={0};

            LocalDevPanelWidgetHandler handler=
                new LocalDevPanelWidgetHandler(
                    panel,
                    player.equipment(),
                    player.combatStyles(),
                    dev,
                    combat,
                    npcs,
                    player.movement(),
                    voidglass,
                    voidglassCommands,
                    player.prayers(),
                    player.magic(),
                    regions,
                    player.bank(),
                    presentation,
                    player.playerState(),
                    devSession,
                    renderer,
                    (pending,writer)->{
                        promptCount[0]++;
                        prompted[0]=pending;
                        panel.prompt(pending);
                    },
                    ()->keyClearCount[0]++
                );

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

            if(handler.handle(
                2482,
                "widgettest",
                scene,
                writer)!=null){
                throw new AssertionError(
                    "closed panel consumed widget");
            }

            panel.open(DevControlCenter.Page.MAIN);

            LocalDevPanelWidgetHandler.Outcome navigation=
                handler.handle(
                    2482,
                    "widgettest",
                    scene,
                    writer);

            if(navigation==null||
               !navigation.renderAfter||
               navigation.choice!=0||
               panel.page()!=DevControlCenter.Page.COMBAT||
               navigation.resultText==null||
               !navigation.resultText.isEmpty()){
                throw new AssertionError(
                    "MAIN navigation failed");
            }

            panel.setPage(
                DevControlCenter.Page.COMBAT_HIT);

            LocalDevPanelWidgetHandler.Outcome prompt=
                handler.handle(
                    2482,
                    "widgettest",
                    scene,
                    writer);

            if(prompt==null||
               prompt.renderAfter||
               promptCount[0]!=1||
               prompted[0]!=
                   DevControlCenter.PendingAmount.HIT_DAMAGE||
               !panel.hasPending()){
                throw new AssertionError(
                    "HIT_DAMAGE prompt boundary failed");
            }

            panel.finishPrompt();
            panel.setPage(
                DevControlCenter.Page.RESET_CONFIRM);

            LocalDevPanelWidgetHandler.Outcome close=
                handler.handle(
                    2484,
                    "widgettest",
                    scene,
                    writer);

            if(close==null||
               close.renderAfter||
               close.directLogText==null||
               !close.directLogText.contains(
                   "RESET_PAGE_CLOSE")||
               panel.isOpen()||
               keyClearCount[0]!=1){
                throw new AssertionError(
                    "RESET close boundary failed");
            }

            panel.open(DevControlCenter.Page.MAIN);
            LocalDevPanelWidgetHandler.Outcome windowClose=
                handler.handle(
                    54195,
                    "widgettest",
                    scene,
                    writer);

            if(windowClose==null||
               !windowClose.directLogText.contains(
                   "widget=54195")||
               panel.isOpen()||
               keyClearCount[0]!=2){
                throw new AssertionError(
                    "window close boundary failed");
            }

            System.out.println(
                "LOCAL_DEV_PANEL_WIDGET_HANDLER_PASS navigation=true promptBoundary=true resetClose=true windowClose=true sessionEdgesExplicit=true");
        }finally{
            world.close();
        }
    }
}
