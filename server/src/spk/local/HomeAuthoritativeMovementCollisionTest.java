package spk.local;

import java.util.*;

public final class HomeAuthoritativeMovementCollisionTest {
    public static void main(String[] args){
        testKnownBlockedClientPath();
        testOpenClientPath();
        testDiagonalCornerClip();
        testRunCannotCrossBlockedBoundary();

        System.out.println(
            "HOME_AUTHORITATIVE_MOVEMENT_COLLISION_PASS "+
            "blockedPath=true openPath=true "+
            "diagonalCorner=true runBoundary=true"
        );
    }

    private static void testKnownBlockedClientPath(){
        MovementState movement=at(3083,3495);

        MovementRequest request=
            new MovementRequest(
                164,
                false,
                new int[]{3084},
                new int[]{3495},
                new byte[0]
            );

        String result=movement.accept(request);

        if(!result.startsWith(
                "REJECT_STATIC_COLLISION"))
            throw new AssertionError(
                "known HOME blocker was bypassed: "+
                result
            );

        if(movement.queued()!=0)
            throw new AssertionError(
                "rejected blocked route queued steps"
            );
    }

    private static void testOpenClientPath(){
        int sx=3090,sy=3490;
        int tx=3091,ty=3490;

        if(!HomeCombatPathfinder.canStep(
                sx,sy,tx,ty))
            throw new AssertionError(
                "chosen open fixture is not open"
            );

        MovementState movement=at(sx,sy);

        String result=
            movement.accept(
                new MovementRequest(
                    164,
                    false,
                    new int[]{tx},
                    new int[]{ty},
                    new byte[0]
                )
            );

        if(!result.startsWith("ACCEPTED"))
            throw new AssertionError(
                "open HOME step rejected: "+
                result
            );

        if(movement.queued()!=1)
            throw new AssertionError(
                "open HOME step queue changed: "+
                movement.queued()
            );
    }

    private static void testDiagonalCornerClip(){
        int[] fixture=findBlockedOpenDiagonal();

        MovementState movement=
            at(fixture[0],fixture[1]);

        String result=
            movement.accept(
                new MovementRequest(
                    164,
                    false,
                    new int[]{fixture[2]},
                    new int[]{fixture[3]},
                    new byte[0]
                )
            );

        if(!result.startsWith(
                "REJECT_STATIC_COLLISION"))
            throw new AssertionError(
                "diagonal corner clipping was accepted fixture="+
                Arrays.toString(fixture)+
                " result="+result
            );

        if(movement.queued()!=0)
            throw new AssertionError(
                "corner-clipped route queued steps"
            );
    }

    private static void testRunCannotCrossBlockedBoundary(){
        MovementState movement=at(3083,3495);

        MovementRequest request=
            new MovementRequest(
                248,
                true,
                new int[]{3085},
                new int[]{3495},
                new byte[0]
            );

        String result=movement.accept(request);

        if(!result.startsWith(
                "REJECT_STATIC_COLLISION"))
            throw new AssertionError(
                "run request crossed blocked HOME boundary: "+
                result
            );

        if(movement.queued()!=0)
            throw new AssertionError(
                "rejected run route retained partial queue"
            );
    }

    private static int[] findBlockedOpenDiagonal(){
        int minX=MovementState.REGION_BASE_X+1;
        int minY=MovementState.REGION_BASE_Y+1;
        int maxX=
            MovementState.REGION_BASE_X+
            MovementState.REGION_SIZE-2;
        int maxY=
            MovementState.REGION_BASE_Y+
            MovementState.REGION_SIZE-2;

        int[][] diagonal={
            {-1,-1},{1,-1},{-1,1},{1,1}
        };

        for(int x=minX;x<=maxX;x++){
            for(int y=minY;y<=maxY;y++){
                if(HomeCombatPathfinder.blockedTile(x,y))
                    continue;

                for(int[] d:diagonal){
                    int nx=x+d[0];
                    int ny=y+d[1];

                    if(HomeCombatPathfinder.blockedTile(
                            nx,ny))
                        continue;

                    if(!HomeCombatPathfinder.canStep(
                            x,y,nx,ny))
                        return new int[]{
                            x,y,nx,ny
                        };
                }
            }
        }

        throw new AssertionError(
            "no open-destination diagonal corner fixture found"
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
           movement.y()!=y||
           movement.plane()!=0)
            throw new AssertionError(
                "fixture position rejected "+
                x+","+y
            );

        return movement;
    }
}
