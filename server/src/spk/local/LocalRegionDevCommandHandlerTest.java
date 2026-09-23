package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalRegionDevCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        try{
            WorldPlayer player=new WorldPlayer();
            world.registerPlayer(player,"regiontest");

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);
            CombatEngine combat=new CombatEngine(dev);
            LocalPlayerInteractionHandler interactions=
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    player.movement(),
                    player.equipment());

            final boolean[] followCancelled={false};

            LocalRegionDevCommandHandler handler=
                new LocalRegionDevCommandHandler(
                    world,
                    player,
                    player.movement(),
                    interactions,
                    combat,
                    npcs,
                    player.petState(),
                    new HomeWorldRuntimePlan(),
                    ()->followCancelled[0]=true
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}));

            SceneUpdatePublisher original=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0));

            LocalRegionDevCommandHandler.Result missing=
                handler.handle(
                    new String[]{"regionload"},
                    "regiontest",
                    original,
                    writer);

            if(missing==null||
               !missing.logText.contains(
                   "V5160_REGION_LOAD result=REJECTED syntax=")||
               missing.saveReason!=null||
               missing.scenePublisher!=original){
                throw new AssertionError(
                    "missing syntax="+
                    (missing==null?null:missing.logText));
            }

            LocalRegionDevCommandHandler.Result home=
                handler.handle(
                    new String[]{"regionhome"},
                    "regiontest",
                    original,
                    writer);

            if(home==null||
               !home.logText.contains(
                   "V5160_REGION_HOME ALREADY_HOME")||
               home.saveReason!=null||
               home.scenePublisher!=original){
                throw new AssertionError(
                    "already-home="+
                    (home==null?null:home.logText));
            }

            int homeTeleportWireBefore=wire.size();
            LocalRegionDevCommandHandler.Result magicHome=
                handler.teleportHomeFromMagic(
                    "regiontest",
                    original,
                    writer
                );

            if(magicHome==null||
               !magicHome.logText.contains(
                   "V5160_MAGIC_HOME_TELEPORT OK")||
               !magicHome.logText.contains(
                   "regionReload=false")||
               !"MAGIC_HOME_TELEPORT".equals(
                   magicHome.saveReason)||
               magicHome.scenePublisher!=original||
               wire.size()<=homeTeleportWireBefore){
                throw new AssertionError(
                    "already-home magic teleport="+
                    (magicHome==null?null:magicHome.logText));
            }

            followCancelled[0]=false;
            player.movement().enterTransientRegion(
                3090,
                3535,
                0,
                3040,
                3480
            );

            LocalRegionDevCommandHandler.Result magicReturn=
                handler.teleportHomeFromMagic(
                    "regiontest",
                    original,
                    writer
                );

            if(magicReturn==null||
               !magicReturn.logText.contains(
                   "homeSceneReplay=DEFERRED_UNTIL_OPCODE121")||
               !magicReturn.logText.contains(
                   "lifecycleReason=MAGIC_HOME_TELEPORT")||
               !"MAGIC_HOME_TELEPORT".equals(
                   magicReturn.saveReason)||
               magicReturn.scenePublisher==original||
               player.movement().transientRegion()||
               player.movement().x()!=MovementState.INITIAL_X||
               player.movement().y()!=MovementState.INITIAL_Y||
               !followCancelled[0]){
                throw new AssertionError(
                    "transient magic home="+
                    (magicReturn==null?null:magicReturn.logText)+
                    " transient="+
                    player.movement().transientRegion()+
                    " followCancelled="+followCancelled[0]);
            }

            followCancelled[0]=false;

            LocalRegionDevCommandHandler.Result unknown=
                handler.handle(
                    new String[]{"regionload","-1"},
                    "regiontest",
                    original,
                    writer);

            if(unknown==null||
               !unknown.logText.contains(
                   "REJECTED_UNKNOWN_REGION id=-1")||
               followCancelled[0]){
                throw new AssertionError(
                    "unknown region="+
                    (unknown==null?null:unknown.logText)+
                    " followCancelled="+followCancelled[0]);
            }

            if(handler.handle(
                new String[]{"devworld","info"},
                "regiontest",
                original,
                writer)!=null){
                throw new AssertionError(
                    "unrelated command consumed");
            }

            System.out.println(
                "LOCAL_REGION_DEV_COMMAND_HANDLER_PASS syntaxGuard=true homeNoop=true magicHome=true magicTransientHome=true unknownRegionFailClosed=true followHookDeferred=true boundary=true");
        }finally{
            world.close();
        }
    }
}
