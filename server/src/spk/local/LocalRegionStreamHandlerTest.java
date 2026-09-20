package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalRegionStreamHandlerTest {
    private static final class Bridge
        implements LocalRegionStreamHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        int replacements;
        int petFollowResets;

        @Override public String username(){
            return "opensrc";
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }

        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){
            publisher=replacement;
            replacements++;
        }

        @Override public void resetPetFollowRuntime(){
            petFollowResets++;
        }
    }

    private static LocalRegionStreamHandler create(
        boolean enabled,
        World world,
        WorldPlayer player,
        NpcRegistry npcs,
        HomeWorldRuntimePlan homeWorld,
        CombatEngine combat,
        Bridge bridge
    ){
        MovementState movement=player.movement();

        return new LocalRegionStreamHandler(
            enabled,
            world,
            player,
            movement,
            homeWorld,
            npcs,
            new LocalPlayerInteractionHandler(
                world,
                player,
                movement,
                player.equipment()
            ),
            combat,
            bridge
        );
    }

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(new int[]{1,2,3,4})
            );

        World disabledWorld=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            disabledWorld.registerPlayer(player,"opensrc");

            MovementState movement=player.movement();
            movement.enterTransientRegion(
                MovementState.INITIAL_X,
                MovementState.INITIAL_Y,
                0,
                3040,
                3456
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            Bridge bridge=new Bridge();
            bridge.publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        3040,
                        3456,
                        0
                    )
                );

            LocalRegionStreamHandler h=
                create(
                    false,
                    disabledWorld,
                    player,
                    new NpcRegistry(dev),
                    new HomeWorldRuntimePlan(),
                    new CombatEngine(dev),
                    bridge
                );

            if(h.maybeStream(
                writer,
                "[region-stream-test] "
            )){
                throw new AssertionError(
                    "disabled region streaming must fail closed"
                );
            }

            if(!movement.transientRegion())
                throw new AssertionError(
                    "disabled streaming mutated transient state"
                );
        }finally{
            disabledWorld.close();
        }

        World enabledWorld=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            enabledWorld.registerPlayer(player,"opensrc");

            MovementState movement=player.movement();
            int beforeX=movement.x();
            int beforeY=movement.y();

            movement.enterTransientRegion(
                beforeX,
                beforeY,
                0,
                3040,
                3456
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            Bridge bridge=new Bridge();
            bridge.publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        3040,
                        3456,
                        0
                    )
                );

            LocalRegionStreamHandler h=
                create(
                    true,
                    enabledWorld,
                    player,
                    new NpcRegistry(dev),
                    new HomeWorldRuntimePlan(),
                    new CombatEngine(dev),
                    bridge
                );

            int wireBefore=wire.size();

            if(!h.maybeStream(
                writer,
                "[region-stream-test] "
            )){
                throw new AssertionError(
                    "HOME reattach path was not handled"
                );
            }

            if(!movement.inHomeWindow())
                throw new AssertionError(
                    "HOME window was not restored"
                );

            if(movement.x()!=beforeX||
               movement.y()!=beforeY){
                throw new AssertionError(
                    "HOME reattach changed authoritative world position"
                );
            }

            if(bridge.replacements!=1)
                throw new AssertionError(
                    "scene publisher replacement count changed: "+
                    bridge.replacements
                );

            if(bridge.petFollowResets!=1)
                throw new AssertionError(
                    "pet-follow runtime reset count changed: "+
                    bridge.petFollowResets
                );

            if(wire.size()<=wireBefore)
                throw new AssertionError(
                    "HOME reattach emitted no packets"
                );

            System.out.println(
                "LOCAL_REGION_STREAM_HANDLER_PASS "+
                "disabledFailClosed=true homeReattach=true "+
                "positionPreserved=true sceneReplaced=true"
            );
        }finally{
            enabledWorld.close();
        }
    }
}
