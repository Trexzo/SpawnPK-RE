package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalSessionBootstrapPublisherTest {
    private static final class Bridge
        implements LocalSessionBootstrapPublisher.SessionBridge
    {
        String saveReason;

        @Override public void saveAccount(
            String tag,
            String reason
        ){
            saveReason=reason;
        }
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);
            HomeWorldRuntimePlan homeWorld=
                new HomeWorldRuntimePlan();
            Bridge bridge=new Bridge();

            LocalSessionBootstrapPublisher publisher=
                new LocalSessionBootstrapPublisher(
                    world,
                    player.equipment(),
                    player.movement(),
                    player.playerState(),
                    player.prayers(),
                    player.magic(),
                    player.bank(),
                    homeWorld,
                    npcs,
                    player.petState(),
                    new PetAccessoryState(),
                    player.miniPets(),
                    true,
                    bridge
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(new int[]{1,2,3,4})
                );

            SceneUpdatePublisher scene=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            publisher.publish(
                writer,
                scene,
                "opensrc",
                true,
                "[bootstrap-test] "
            );

            if(wire.size()<=0)
                throw new AssertionError(
                    "bootstrap emitted no packets"
                );

            if(!"BOOTSTRAP".equals(bridge.saveReason))
                throw new AssertionError(
                    "bootstrap save boundary changed: "+
                    bridge.saveReason
                );

            if(npcs.visibleCount()<=0)
                throw new AssertionError(
                    "HOME NPC bootstrap did not execute"
                );

            System.out.println(
                "LOCAL_SESSION_BOOTSTRAP_PUBLISHER_PASS "+
                "wireBytes="+wire.size()+
                " homeNpcVisible="+npcs.visibleCount()+
                " saveReason="+bridge.saveReason
            );
        }finally{
            world.close();
        }
    }
}
