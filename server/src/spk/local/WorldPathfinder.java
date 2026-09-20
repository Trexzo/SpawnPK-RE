package spk.local;

import java.util.*;

/** Generic cache-region static pathfinder. HOME remains delegated to its certified pathfinder. */
final class WorldPathfinder {
    static List<int[]> route(int sx,int sy,int plane,int tx,int ty,int stopRange){
        if(MovementState.insideLoadedRegion(sx,sy)&&MovementState.insideLoadedRegion(tx,ty)&&plane==0)
            return HomeCombatPathfinder.route(sx,sy,tx,ty,stopRange);
        ArrayList<int[]> empty=new ArrayList<>();
        if(stopRange<0||plane<0||plane>3)return empty;
        if(distance(sx,sy,tx,ty)<=stopRange)return empty;
        final int radius=104;
        int minX=sx-radius,maxX=sx+radius,minY=sy-radius,maxY=sy+radius;
        ArrayDeque<Long> q=new ArrayDeque<>();HashMap<Long,Long> prev=new HashMap<>();
        long start=key(sx,sy);q.add(start);prev.put(start,Long.MIN_VALUE);long found=Long.MIN_VALUE;
        final int[] DX={-1,0,1,-1,1,-1,0,1},DY={1,1,1,0,0,-1,-1,-1};
        while(!q.isEmpty()&&prev.size()<50000){
            long cur=q.removeFirst();int x=x(cur),y=y(cur);
            if(distance(x,y,tx,ty)<=stopRange){found=cur;break;}
            for(int i=0;i<8;i++){
                int nx=x+DX[i],ny=y+DY[i];if(nx<minX||nx>maxX||ny<minY||ny>maxY)continue;
                long nk=key(nx,ny);if(prev.containsKey(nk))continue;
                if(!CollisionStepAuthority.canStep(
                        CollisionStepAuthority.Policy.WORLD_STATIC,
                        x,y,plane,nx,ny))continue;
                prev.put(nk,cur);q.addLast(nk);
            }
        }
        if(found==Long.MIN_VALUE)return empty;
        ArrayList<int[]> rev=new ArrayList<>();for(long k=found;k!=start;k=prev.get(k))rev.add(new int[]{x(k),y(k)});
        Collections.reverse(rev);return rev;
    }
    private static int distance(int a,int b,int c,int d){return Math.max(Math.abs(a-c),Math.abs(b-d));}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);} private static int x(long k){return (int)(k>>32);}private static int y(long k){return (int)k;}
    private WorldPathfinder(){}
}
