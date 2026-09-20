package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalDevWorldCommandHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            MovementState movement=player.movement();
            LocalDevWorldCommandHandler h=new LocalDevWorldCommandHandler(world,movement);

            ByteArrayOutputStream wire=new ByteArrayOutputStream();
            ServerPacketWriter w=new ServerPacketWriter(wire,new IsaacCipher(new int[]{1,2,3,4}));
            SceneUpdatePublisher scene=new SceneUpdatePublisher(
                w,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0));

            if(!h.handle(new String[]{"devworld","info"},scene,"opensrc",10L,"[devworld-test] "))
                throw new AssertionError("info not handled");

            int before=wire.size();
            if(!h.handle(new String[]{"devworld","ground","4151","1"},scene,"opensrc",10L,"[devworld-test] "))
                throw new AssertionError("ground not handled");
            GroundItem g=world.groundItems().findOwned(4151,movement.x(),movement.y(),0,"opensrc");
            if(g==null||g.amount!=1)throw new AssertionError("ground item not mutated through world registry");
            if(wire.size()<=before)throw new AssertionError("ground spawn emitted no scene packet");

            before=wire.size();
            if(!h.handle(new String[]{"devworld","groundclear"},scene,"opensrc",11L,"[devworld-test] "))
                throw new AssertionError("groundclear not handled");
            if(world.groundItems().findOwned(4151,movement.x(),movement.y(),0,"opensrc")!=null)
                throw new AssertionError("groundclear did not remove dev-owned item");
            if(wire.size()<=before)throw new AssertionError("groundclear emitted no scene packet");

            if(!h.handle(new String[]{"devworld","unknown"},scene,"opensrc",12L,"[devworld-test] "))
                throw new AssertionError("unknown devworld subcommand must emit help and remain handled");

            if(h.handle(new String[]{"item","4151"},scene,"opensrc",12L,"[devworld-test] "))
                throw new AssertionError("non-devworld command must remain outside handler");

            System.out.println("LOCAL_DEV_WORLD_COMMAND_HANDLER_PASS worldRegistryMutation=true scenePublication=true unrelatedRejected=true");
        }finally{
            world.close();
        }
    }
}
