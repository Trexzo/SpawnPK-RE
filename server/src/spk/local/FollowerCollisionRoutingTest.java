package spk.local;

import java.io.*;
import java.util.*;

public final class FollowerCollisionRoutingTest {
    public static void main(String[] args)throws Exception{
        testOpenSpaceParity();
        testBlockedCardinalDetour();
        testMainPetCatchupAroundBlocker();
        testMiniPetCatchupAroundBlocker();

        System.out.println(
            "FOLLOWER_COLLISION_ROUTING_PASS "+
            "openSpaceParity=true "+
            "blockedDetour=true "+
            "mainPet=true "+
            "miniPet=true"
        );
    }

    private static void testOpenSpaceParity(){
        MovementState movement=at(3090,3490);

        int east=
            FollowerStepResolver.nextDirection(
                3090,3490,
                3093,3490,
                1,
                movement
            );

        if(east!=4)
            throw new AssertionError(
                "open X-first parity changed dir="+east
            );

        int north=
            FollowerStepResolver.nextDirection(
                3090,3490,
                3090,3493,
                1,
                movement
            );

        if(north!=1)
            throw new AssertionError(
                "open Y parity changed dir="+north
            );
    }

    private static void testBlockedCardinalDetour(){
        MovementState movement=at(3085,3495);

        int x=3083;
        int y=3495;

        int first=
            FollowerStepResolver.nextDirection(
                x,y,
                movement.x(),
                movement.y(),
                1,
                movement
            );

        if(first==4)
            throw new AssertionError(
                "follower selected blocked east step into 3084,3495"
            );

        int guard=32;
        while(LocalSession.chebyshev(
                x,y,
                movement.x(),
                movement.y())>1&&
              guard-->0){
            int dir=
                FollowerStepResolver.nextDirection(
                    x,y,
                    movement.x(),
                    movement.y(),
                    1,
                    movement
                );

            if(dir<0)
                throw new AssertionError(
                    "no detour direction from "+
                    x+","+y
                );

            int[] next=step(x,y,dir);

            if(!CollisionStepAuthority.canStep(
                    CollisionStepAuthority.Policy.HOME_RECOVERED_STATIC,
                    x,y,0,next[0],next[1]))
                throw new AssertionError(
                    "resolver produced blocked step "+
                    x+","+y+"->"+
                    next[0]+","+next[1]
                );

            x=next[0];
            y=next[1];
        }

        if(LocalSession.chebyshev(
                x,y,
                movement.x(),
                movement.y())>1)
            throw new AssertionError(
                "cardinal detour failed to reach adjacency final="+
                x+","+y
            );
    }

    private static void testMainPetCatchupAroundBlocker()
        throws Exception{
        MovementState movement=at(3085,3495);
        NpcRegistry npcs=new NpcRegistry();
        PetState state=new PetState();
        ServerPacketWriter writer=writer();

        npcs.bootstrap(writer,movement,state);

        PetDefinitionRepository.Def def=
            PetDefinitionRepository.get(22519);

        if(def==null)
            throw new AssertionError(
                "main pet fixture missing"
            );

        String spawned=
            npcs.spawnPet(
                def,
                movement,
                writer
            );

        if(!spawned.startsWith("PET_SPAWN_OK"))
            throw new AssertionError(spawned);

        int settle=16;
        while(npcs.hasQueuedFollow()&&settle-->0)
            npcs.tickFollow(
                movement,
                writer
            );

        NpcEntity pet=npcs.pet();
        pet.x=3083;
        pet.y=3495;

        int guard=24;
        while(LocalSession.chebyshev(
                pet.x,pet.y,
                movement.x(),movement.y())>1&&
              guard-->0){
            int oldX=pet.x;
            int oldY=pet.y;

            String log=
                npcs.tickFollow(
                    movement,
                    writer
                );

            if(log==null)
                throw new AssertionError(
                    "main pet stalled at "+
                    oldX+","+oldY
                );

            if(pet.x==oldX&&pet.y==oldY)
                throw new AssertionError(
                    "main pet produced no movement: "+
                    log
                );

            if(!legalCardinalProgress(
                    oldX,oldY,
                    pet.x,pet.y))
                throw new AssertionError(
                    "main pet crossed blocked geometry "+
                    oldX+","+oldY+"->"+
                    pet.x+","+pet.y+
                    " log="+log
                );
        }

        if(LocalSession.chebyshev(
                pet.x,pet.y,
                movement.x(),movement.y())>1)
            throw new AssertionError(
                "main pet did not catch up around blocker"
            );

        if(pet.x==3084&&pet.y==3495)
            throw new AssertionError(
                "main pet occupied known blocked tile"
            );
    }

    private static void testMiniPetCatchupAroundBlocker()
        throws Exception{
        MovementState movement=at(3086,3495);
        NpcRegistry npcs=new NpcRegistry();
        PetState state=new PetState();
        ServerPacketWriter writer=writer();

        PetDefinitionRepository.Def main=
            PetDefinitionRepository.get(22519);

        if(main==null)
            throw new AssertionError(
                "main pet fixture missing"
            );

        npcs.spawnPet(
            main,
            movement,
            writer
        );

        int settle=16;
        while(npcs.hasQueuedFollow()&&settle-->0)
            npcs.tickFollow(
                movement,
                writer
            );

        NpcEntity pet=npcs.pet();
        pet.x=3085;
        pet.y=3495;

        MiniPetDefinitionRepository.Def mini=
            MiniPetDefinitionRepository.get(23629);

        if(mini==null)
            throw new AssertionError(
                "mini pet fixture missing"
            );

        String configured=
            npcs.spawnOrReplaceMiniPet(
                mini,
                movement,
                writer
            );

        if(!configured.startsWith(
                "MINIPET_SPAWN_OK"))
            throw new AssertionError(
                configured
            );

        NpcEntity miniPet=npcs.miniPet();
        miniPet.x=3083;
        miniPet.y=3495;

        int guard=24;
        while(LocalSession.chebyshev(
                miniPet.x,miniPet.y,
                pet.x,pet.y)>1&&
              guard-->0){
            int oldX=miniPet.x;
            int oldY=miniPet.y;

            String log=
                npcs.tickFollow(
                    movement,
                    writer
                );

            if(log==null)
                throw new AssertionError(
                    "mini pet stalled at "+
                    oldX+","+oldY
                );

            if(miniPet.x==oldX&&
               miniPet.y==oldY)
                throw new AssertionError(
                    "mini pet produced no movement: "+
                    log
                );

            if(!legalCardinalProgress(
                    oldX,oldY,
                    miniPet.x,miniPet.y))
                throw new AssertionError(
                    "mini pet crossed blocked geometry "+
                    oldX+","+oldY+"->"+
                    miniPet.x+","+miniPet.y+
                    " log="+log
                );
        }

        if(LocalSession.chebyshev(
                miniPet.x,miniPet.y,
                pet.x,pet.y)>1)
            throw new AssertionError(
                "mini pet did not catch up around blocker"
            );

        if(miniPet.x==3084&&
           miniPet.y==3495)
            throw new AssertionError(
                "mini pet occupied known blocked tile"
            );
    }

    private static boolean legalCardinalProgress(
        int fromX,
        int fromY,
        int toX,
        int toY
    ){
        int x=fromX;
        int y=fromY;

        while(x!=toX||y!=toY){
            int dx=Integer.compare(toX,x);
            int dy=Integer.compare(toY,y);

            // Follower packet presentation is cardinal, so a two-tile pulse
            // can only be validated as two cardinal components.
            int nx=x;
            int ny=y;

            if(dx!=0&&dy==0){
                nx+=dx;
            }else if(dx==0&&dy!=0){
                ny+=dy;
            }else{
                return false;
            }

            if(!CollisionStepAuthority.canStep(
                    CollisionStepAuthority.Policy.HOME_RECOVERED_STATIC,
                    x,y,0,nx,ny))
                return false;

            x=nx;
            y=ny;
        }

        return true;
    }

    private static int[] step(
        int x,
        int y,
        int direction
    ){
        switch(direction){
            case 0:return new int[]{x-1,y+1};
            case 1:return new int[]{x,y+1};
            case 2:return new int[]{x+1,y+1};
            case 3:return new int[]{x-1,y};
            case 4:return new int[]{x+1,y};
            case 5:return new int[]{x-1,y-1};
            case 6:return new int[]{x,y-1};
            case 7:return new int[]{x+1,y-1};
            default:throw new IllegalArgumentException(
                "direction="+direction
            );
        }
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

    private static ServerPacketWriter writer(){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(
                new int[]{7,5,3,1}
            )
        );
    }
}
