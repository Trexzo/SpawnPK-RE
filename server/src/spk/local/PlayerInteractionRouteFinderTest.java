package spk.local;

import java.io.*;
import java.util.*;

public final class PlayerInteractionRouteFinderTest {
    public static void main(String[] args)throws Exception{
        testInteractionRoute(2,"Follow");
        testInteractionRoute(1,"Attack");

        System.out.println(
            "PLAYER_INTERACTION_ROUTEFINDER_PASS "+
            "follow=true attack=true "+
            "knownBlockerAvoided=true "+
            "genericRouteFinder=true"
        );
    }

    private static void testInteractionRoute(
        int optionSlot,
        String label
    )throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer owner=new WorldPlayer();
        WorldPlayer target=new WorldPlayer();

        place(owner.movement(),3083,3495);
        place(target.movement(),3086,3495);

        world.registerPlayer(
            owner,
            "owner-"+label
        );
        world.registerPlayer(
            target,
            "target-"+label
        );

        ServerPacketWriter ownerWriter=writer();
        ServerPacketWriter targetWriter=writer();

        Player81WorldSync.Context ownerSync=
            Player81WorldSync.register(
                ownerWriter,
                world,
                owner,
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.Context targetSync=
            Player81WorldSync.register(
                targetWriter,
                world,
                target,
                new DevAuthorityWorkbench()
            );

        try{
            Player81WorldSync.transformForTest(
                ownerSync,
                BootstrapPackets.player81Idle()
            );
            Player81WorldSync.transformForTest(
                targetSync,
                BootstrapPackets.player81Idle()
            );

            int clientIndex=
                ownerSync.clientIndexFor(target);

            if(clientIndex<0)
                throw new AssertionError(
                    label+" target not visible"
                );

            if(optionSlot==1)
                owner.equipment().setWeapon(4151);

            LocalPlayerInteractionHandler handler=
                new LocalPlayerInteractionHandler(
                    world,
                    owner,
                    owner.movement(),
                    owner.equipment()
                );

            String request=
                handler.handleResolved(
                    new PlayerAction(
                        optionSlot==1?128:153,
                        optionSlot,
                        clientIndex,
                        label
                    ),
                    target,
                    ownerSync
                );

            if(request==null)
                throw new AssertionError(
                    label+" request missing"
                );

            String prep=
                handler.prepareTick(
                    1L,
                    ownerSync
                );

            if(prep!=null&&
               prep.contains("REJECT"))
                throw new AssertionError(
                    label+" route rejected "+
                    prep
                );

            if(owner.movement().queued()==0)
                throw new AssertionError(
                    label+" queued no server route"
                );

            int px=owner.movement().x();
            int py=owner.movement().y();
            boolean moved=false;

            while(owner.movement().queued()>0){
                MovementState.Tick tick=
                    owner.movement().advance();

                if(tick==null)
                    throw new AssertionError(
                        label+" route produced null tick"
                    );

                moved=true;

                if(owner.movement().x()==3084&&
                   owner.movement().y()==3495)
                    throw new AssertionError(
                        label+" route entered known blocker"
                    );

                if(!HomeCombatPathfinder.canStep(
                        px,
                        py,
                        owner.movement().x(),
                        owner.movement().y()))
                    throw new AssertionError(
                        label+" route used illegal HOME step "+
                        px+","+py+"->"+
                        owner.movement().x()+","+
                        owner.movement().y()
                    );

                px=owner.movement().x();
                py=owner.movement().y();
            }

            if(!moved)
                throw new AssertionError(
                    label+" fixture did not move"
                );

            int dx=Math.abs(
                target.movement().x()-
                owner.movement().x()
            );
            int dy=Math.abs(
                target.movement().y()-
                owner.movement().y()
            );

            if(dx+dy!=1)
                throw new AssertionError(
                    label+" did not finish cardinal-adjacent "+
                    "owner="+
                    owner.movement().x()+","+
                    owner.movement().y()+
                    " target="+
                    target.movement().x()+","+
                    target.movement().y()
                );
        }finally{
            Player81WorldSync.unregister(
                ownerWriter
            );
            Player81WorldSync.unregister(
                targetWriter
            );
            world.unregisterPlayer(owner);
            world.unregisterPlayer(target);
            world.close();
        }
    }

    private static void place(
        MovementState movement,
        int x,
        int y
    ){
        SortedMap<String,String> values=
            PersistenceSchemaTestSupport.values(
                "movement.worldX",
                Integer.toString(x),
                "movement.worldY",
                Integer.toString(y),
                "movement.plane",
                "0"
            );
        PersistenceSchemaTestSupport.restoreMovement(
            movement,
            values
        );

        if(movement.x()!=x||
           movement.y()!=y)
            throw new AssertionError(
                "fixture placement failed "+
                x+","+y
            );
    }

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(
                new int[]{1,2,3,4}
            )
        );
    }
}
