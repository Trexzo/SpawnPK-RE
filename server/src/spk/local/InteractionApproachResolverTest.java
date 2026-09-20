package spk.local;

import java.io.*;
import java.util.*;

public final class InteractionApproachResolverTest {
    public static void main(String[] args)throws Exception{
        testBankObjectServerApproach();
        testMovingBankNpcReroute();

        System.out.println(
            "INTERACTION_APPROACH_RESOLVER_PASS "+
            "objectServerRoute=true "+
            "blockedTileAvoided=true "+
            "movingNpcRerouted=true"
        );
    }

    private static void testBankObjectServerApproach()
        throws Exception{
        MovementState movement=at(3083,3495);
        BankState bank=new BankState();
        LocalBankObjectInteractionHandler handler=
            new LocalBankObjectInteractionHandler(
                bank,
                movement
            );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=writer(out);

        ObjectInteraction bankObject=
            new ObjectInteraction(
                132,
                BankState.BANK_OBJECT_ID,
                3086,
                3495
            );

        String result=
            handler.handle(
                bankObject,
                writer
            );

        if(!result.contains(
                "action=DEFERRED_UNTIL_ADJACENT")||
           !result.contains(
                "status=ROUTE_QUEUED"))
            throw new AssertionError(
                "bank object did not queue server route: "+
                result
            );

        if(movement.queued()==0)
            throw new AssertionError(
                "bank object approach queue empty"
            );

        int px=movement.x();
        int py=movement.y();

        while(movement.queued()>0){
            MovementState.Tick tick=
                movement.advance();

            if(tick==null)
                throw new AssertionError(
                    "queued object route produced no movement tick"
                );

            if(HomeCombatPathfinder.blockedTile(
                    movement.x(),
                    movement.y()))
                throw new AssertionError(
                    "object route entered known blocked HOME tile "+
                    movement.x()+","+movement.y()
                );

            if(!HomeCombatPathfinder.canStep(
                    px,
                    py,
                    movement.x(),
                    movement.y()))
                throw new AssertionError(
                    "object route used illegal step "+
                    px+","+py+"->"+
                    movement.x()+","+movement.y()
                );

            px=movement.x();
            py=movement.y();
        }

        String arrival=
            handler.tick(
                System.currentTimeMillis(),
                writer
            );

        if(arrival==null||
           !arrival.contains(
                "OPENED_AFTER_AUTHORITATIVE_ARRIVAL"))
            throw new AssertionError(
                "bank object did not open after server route: "+
                arrival
            );

        if(!bank.isOpen())
            throw new AssertionError(
                "bank object arrival did not open bank"
            );
    }

    private static void testMovingBankNpcReroute()
        throws Exception{
        MovementState movement=at(3090,3490);
        BankState bank=new BankState();
        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        ByteArrayOutputStream out=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=writer(out);

        NpcEntity banker=
            npcs.spawnMirroredNpc(
                7605,
                3094,
                3490,
                null,
                movement,
                writer
            );

        LocalRoutedNpcInteractionHandler handler=
            new LocalRoutedNpcInteractionHandler(
                npcs,
                bank,
                movement
            );

        String result=
            handler.handle(
                new NpcAction(
                    17,
                    banker.sceneIndex
                ),
                banker,
                writer
            );

        if(!result.contains(
                "action=DEFERRED_UNTIL_ADJACENT")||
           movement.queued()==0)
            throw new AssertionError(
                "banker did not queue initial server route: "+
                result
            );

        // Move the live target after route selection. The queued route remains
        // valid for its original target, then tick() must reroute to this new
        // live position rather than cancelling the interaction.
        banker.x=3098;
        banker.y=3492;

        while(movement.queued()>0)
            movement.advance();

        String reroute=
            handler.tick(
                System.currentTimeMillis(),
                writer
            );

        if(reroute==null||
           !reroute.contains(
                "SERVER_REROUTED_MOVING_TARGET"))
            throw new AssertionError(
                "moving banker was not rerouted: "+
                reroute
            );

        if(movement.queued()==0)
            throw new AssertionError(
                "moving banker reroute queued no path"
            );

        int safety=64;
        String arrival=null;

        while(safety-->0){
            if(movement.queued()>0)
                movement.advance();

            arrival=
                handler.tick(
                    System.currentTimeMillis(),
                    writer
                );

            if(bank.isOpen())
                break;
        }

        if(!bank.isOpen())
            throw new AssertionError(
                "moving banker never opened bank last="+
                arrival
            );

        if(arrival==null||
           !arrival.contains(
                "OPENED_AFTER_AUTHORITATIVE_ARRIVAL"))
            throw new AssertionError(
                "moving banker arrival reason changed: "+
                arrival
            );
    }

    private static MovementState at(int x,int y){
        SortedMap<String,String> values=
            PersistenceSchemaTestSupport.values(
                "movement.worldX",
                Integer.toString(x),
                "movement.worldY",
                Integer.toString(y),
                "movement.plane",
                "0"
            );

        MovementState movement=
            new MovementState();
        PersistenceSchemaTestSupport.restoreMovement(
            movement,
            values
        );

        if(movement.x()!=x||
           movement.y()!=y)
            throw new AssertionError(
                "movement fixture rejected "+
                x+","+y
            );

        return movement;
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[]{1,2,3,4}
            )
        );
    }
}
