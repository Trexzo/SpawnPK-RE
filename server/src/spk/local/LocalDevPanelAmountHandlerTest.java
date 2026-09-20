package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalDevPanelAmountHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        try{
            WorldPlayer player=new WorldPlayer();
            world.registerPlayer(player,"paneltest");

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);
            CombatEngine combat=new CombatEngine(dev);
            DevControlCenter panel=new DevControlCenter();
            PlayerPresentationService presentation=
                new PlayerPresentationService(dev);

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

            final int[] keyClearCount={0};

            LocalDevPanelAmountHandler handler=
                new LocalDevPanelAmountHandler(
                    panel,
                    dev,
                    player.equipment(),
                    combat,
                    npcs,
                    player.movement(),
                    regions,
                    new NativeItemLibraryService(),
                    presentation,
                    player.playerState(),
                    player.prayers(),
                    renderer,
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

            panel.open(DevControlCenter.Page.COMBAT_ANIM);
            panel.prompt(
                DevControlCenter.PendingAmount.COMBAT_ANIM);

            LocalDevPanelAmountHandler.Outcome animation=
                handler.handle(
                    1234,
                    "paneltest",
                    scene,
                    writer);

            if(animation.pending!=
                    DevControlCenter.PendingAmount.COMBAT_ANIM||
               !animation.reopen||
               animation.saveReason!=null||
               animation.scenePublisher!=scene||
               !animation.resultText.contains(
                   "animationOverride=1234")||
               !dev.hasCombatAnimationOverride(
                   player.equipment().weapon())){
                throw new AssertionError(
                    "animation="+animation.resultText);
            }

            // Final prompt lifecycle remains session-owned.
            if(!panel.hasPending()){
                throw new AssertionError(
                    "handler prematurely finished prompt");
            }

            panel.finishPrompt();
            panel.prompt(
                DevControlCenter.PendingAmount.REGION_ID);

            LocalDevPanelAmountHandler.Outcome region=
                handler.handle(
                    -1,
                    "paneltest",
                    scene,
                    writer);

            if(region.reopen||
               region.saveReason!=null||
               region.scenePublisher!=scene||
               !region.resultText.contains(
                   "REJECTED_UNKNOWN_REGION id=-1")){
                throw new AssertionError(
                    "region="+region.resultText);
            }

            if(keyClearCount[0]!=0){
                throw new AssertionError(
                    "dialog-key callback fired outside item-library open");
            }

            System.out.println(
                "LOCAL_DEV_PANEL_AMOUNT_HANDLER_PASS combatAnim=true pendingLifecycleSessionOwned=true regionFailClosed=true sceneBoundary=true keyCallbackScoped=true");
        }finally{
            world.close();
        }
    }
}
