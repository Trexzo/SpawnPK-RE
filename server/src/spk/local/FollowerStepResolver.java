package spk.local;

import java.util.*;

/**
 * Collision-aware cardinal step selector for pet/minipet followers.
 *
 * Presentation remains packet-65 cardinal WALK/RUN. This resolver only chooses
 * the next legal cardinal component and never changes follower cadence/budgets.
 */
final class FollowerStepResolver {
    static int nextDirection(
        int fromX,
        int fromY,
        int targetX,
        int targetY,
        int stopRange,
        MovementState movement
    ){
        if(movement==null)
            throw new NullPointerException("movement");
        if(stopRange<0)
            throw new IllegalArgumentException("stopRange="+stopRange);

        if(distance(fromX,fromY,targetX,targetY)<=stopRange)
            return -1;

        CollisionStepAuthority.Policy policy=
            movement.transientRegion()
                ?CollisionStepAuthority.Policy.WORLD_STATIC
                :CollisionStepAuthority.Policy.HOME_RECOVERED_STATIC;

        // Preserve the recovered open-space follower rule: X first, then Y.
        int sx=Integer.compare(targetX,fromX);
        if(sx!=0){
            int nx=fromX+sx;
            int ny=fromY;
            if(legal(
                    policy,
                    movement.plane(),
                    fromX,fromY,nx,ny))
                return MovementState.direction(
                    fromX,fromY,nx,ny
                );
        }

        int sy=Integer.compare(targetY,fromY);
        if(sy!=0){
            int nx=fromX;
            int ny=fromY+sy;
            if(legal(
                    policy,
                    movement.plane(),
                    fromX,fromY,nx,ny))
                return MovementState.direction(
                    fromX,fromY,nx,ny
                );
        }

        // Direct cardinal progress is blocked. Find a cardinal-only detour so
        // packet presentation remains the established follower WALK/RUN form.
        int[] step=
            firstRouteStep(
                fromX,
                fromY,
                targetX,
                targetY,
                stopRange,
                movement,
                policy
            );

        if(step==null)return -1;

        return MovementState.direction(
            fromX,
            fromY,
            step[0],
            step[1]
        );
    }

    private static int[] firstRouteStep(
        int startX,
        int startY,
        int targetX,
        int targetY,
        int stopRange,
        MovementState movement,
        CollisionStepAuthority.Policy policy
    ){
        final int radius=32;
        final int minX=startX-radius;
        final int maxX=startX+radius;
        final int minY=startY-radius;
        final int maxY=startY+radius;

        ArrayDeque<Long> queue=new ArrayDeque<>();
        HashMap<Long,Long> previous=new HashMap<>();

        long start=key(startX,startY);
        queue.add(start);
        previous.put(start,Long.MIN_VALUE);

        long found=Long.MIN_VALUE;

        while(!queue.isEmpty()&&
              previous.size()<8192){
            long current=queue.removeFirst();
            int x=x(current);
            int y=y(current);

            if(distance(x,y,targetX,targetY)<=stopRange){
                found=current;
                break;
            }

            int sx=Integer.compare(targetX,x);
            int sy=Integer.compare(targetY,y);

            // Deterministic preference preserves old X-first/Y-second behavior
            // when multiple cardinal detours are equally short.
            int[][] directions;
            if(sx!=0&&sy==0){
                directions=new int[][]{
                    {sx,0},
                    {0,1},
                    {0,-1},
                    {-sx,0}
                };
            }else if(sx==0&&sy!=0){
                directions=new int[][]{
                    {0,sy},
                    {1,0},
                    {-1,0},
                    {0,-sy}
                };
            }else{
                directions=new int[][]{
                    {sx,0},
                    {0,sy},
                    {-sx,0},
                    {0,-sy}
                };
            }

            for(int[] direction:directions){
                int dx=direction[0];
                int dy=direction[1];

                if(dx==0&&dy==0)continue;

                int nx=x+dx;
                int ny=y+dy;

                if(nx<minX||nx>maxX||
                   ny<minY||ny>maxY)
                    continue;

                if(!movement.insideCurrentLoadedRegion(
                        nx,
                        ny))
                    continue;

                long next=key(nx,ny);
                if(previous.containsKey(next))
                    continue;

                if(!legal(
                        policy,
                        movement.plane(),
                        x,y,nx,ny))
                    continue;

                previous.put(next,current);
                queue.addLast(next);
            }
        }

        if(found==Long.MIN_VALUE||
           found==start)
            return null;

        long cursor=found;
        long parent=previous.get(cursor);

        while(parent!=start&&
              parent!=Long.MIN_VALUE){
            cursor=parent;
            parent=previous.get(cursor);
        }

        return new int[]{x(cursor),y(cursor)};
    }

    private static boolean legal(
        CollisionStepAuthority.Policy policy,
        int plane,
        int x0,
        int y0,
        int x1,
        int y1
    ){
        return CollisionStepAuthority.canStep(
            policy,
            x0,y0,plane,x1,y1
        );
    }

    private static int distance(
        int x0,
        int y0,
        int x1,
        int y1
    ){
        return Math.max(
            Math.abs(x1-x0),
            Math.abs(y1-y0)
        );
    }

    private static long key(int x,int y){
        return ((long)x<<32)^(y&0xffffffffL);
    }

    private static int x(long key){
        return (int)(key>>32);
    }

    private static int y(long key){
        return (int)key;
    }

    private FollowerStepResolver(){}
}
