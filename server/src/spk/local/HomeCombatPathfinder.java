package spk.local;

import java.util.*;

/**
 * HOME interaction route planner shaped after the exact current client's tile
 * route finder.  The client performs an eight-neighbour breadth-first search:
 * every cardinal/diagonal tile step has the same path depth and a diagonal is
 * accepted only when both orthogonal legs are collision-clear.  R2.10's
 * cardinal-only route and the early R2.11 weighted A* both produced visibly
 * different approach geometry, so this keeps the server-side interaction route
 * aligned with the client's own movement model as closely as our recovered HOME
 * collision overlay permits.
 */
final class HomeCombatPathfinder {
    private static final Set<Long> BLOCKED_TILES=buildBlockedTiles();
    private static final Set<String> BLOCKED_EDGES=buildBlockedEdges();

    /* Exact-current client expansion order from the recovered route-finder:
       west, east, south, north, southwest, southeast, northwest, northeast.
       Coordinate labels are conventional here; the important authority is the
       field order / eight-neighbour structure and equal BFS depth. */
    private static final int[][] CLIENT_DIR_ORDER={
        {-1,0},{1,0},{0,-1},{0,1},
        {-1,-1},{1,-1},{-1,1},{1,1}
    };

    private HomeCombatPathfinder(){}

    static List<int[]> route(int startX,int startY,int targetX,int targetY,int range){
        if(range<1)range=1;
        if(inRange(startX,startY,targetX,targetY,range))return Collections.emptyList();

        final int minX=MovementState.REGION_BASE_X,minY=MovementState.REGION_BASE_Y;
        final int maxX=minX+MovementState.REGION_SIZE-1,maxY=minY+MovementState.REGION_SIZE-1;
        final int capacity=MovementState.REGION_SIZE*MovementState.REGION_SIZE;
        int[] qx=new int[capacity],qy=new int[capacity];
        int head=0,tail=0;
        long start=key(startX,startY);
        HashMap<Long,Long> parent=new HashMap<>();
        parent.put(start,Long.MIN_VALUE);
        qx[tail]=startX;qy[tail]=startY;tail++;
        long found=Long.MIN_VALUE;

        while(head<tail){
            int x=qx[head],y=qy[head];head++;
            if(inRange(x,y,targetX,targetY,range) && !(x==targetX&&y==targetY)){
                found=key(x,y);break;
            }
            for(int[] d:CLIENT_DIR_ORDER){
                int nx=x+d[0],ny=y+d[1];
                if(nx<minX||nx>maxX||ny<minY||ny>maxY)continue;
                if(nx==targetX&&ny==targetY)continue; // interaction target tile itself is not a melee destination
                long nk=key(nx,ny);
                if(parent.containsKey(nk))continue;
                if(!canStep(x,y,nx,ny))continue;
                parent.put(nk,key(x,y));
                if(tail>=capacity)return null; // fail closed; should be impossible in a 104x104 region
                qx[tail]=nx;qy[tail]=ny;tail++;
            }
        }
        if(found==Long.MIN_VALUE)return null;
        ArrayList<int[]> rev=new ArrayList<>();
        long k=found;
        while(k!=start){
            rev.add(new int[]{x(k),y(k)});
            Long p=parent.get(k);
            if(p==null||p.longValue()==Long.MIN_VALUE)break;
            k=p.longValue();
        }
        Collections.reverse(rev);
        return rev;
    }

    static boolean canStep(int x0,int y0,int x1,int y1){
        int dx=x1-x0,dy=y1-y0;
        if(Math.abs(dx)>1||Math.abs(dy)>1||(dx==0&&dy==0))return false;
        if(blockedTile(x1,y1))return false;
        if(dx==0||dy==0)return !blockedEdge(x0,y0,x1,y1);

        /* Exact-client route finder checks destination plus both cardinal legs
           for a diagonal.  Our collision source is normalized into blocked tile
           and blocked edge sets, so enforce the same no-corner-cut invariant. */
        int ax=x0+dx,ay=y0;
        int bx=x0,by=y0+dy;
        if(blockedTile(ax,ay)||blockedTile(bx,by))return false;
        if(blockedEdge(x0,y0,ax,ay)||blockedEdge(x0,y0,bx,by))return false;
        if(blockedEdge(ax,ay,x1,y1)||blockedEdge(bx,by,x1,y1))return false;
        return true;
    }

    static boolean blockedTile(int x,int y){return BLOCKED_TILES.contains(key(x,y));}
    static boolean blockedEdge(int x0,int y0,int x1,int y1){return BLOCKED_EDGES.contains(edgeKey(x0,y0,x1,y1));}
    static int blockedTileCount(){return BLOCKED_TILES.size();}
    static int blockedEdgeCount(){return BLOCKED_EDGES.size();}

    private static boolean inRange(int x,int y,int tx,int ty,int range){
        if(range==1)return Math.abs(tx-x)+Math.abs(ty-y)==1;
        return LocalSession.chebyshev(x,y,tx,ty)<=range && !(x==tx&&y==ty);
    }

    private static Set<Long> buildBlockedTiles(){
        HashSet<Long> out=new HashSet<>();
        for(HomeCollisionOverlayRepository.Entry e:HomeCollisionOverlayRepository.all()){
            if(!e.isAdd()||!Boolean.TRUE.equals(e.movementClip))continue;
            if(!"RECTANGLE".equals(e.collisionClass))continue;
            for(int dx=0;dx<e.effectiveWidth;dx++)for(int dy=0;dy<e.effectiveHeight;dy++)out.add(key(e.worldX+dx,e.worldY+dy));
        }
        return Collections.unmodifiableSet(out);
    }

    private static Set<String> buildBlockedEdges(){
        HashSet<String> out=new HashSet<>();
        for(HomeCollisionOverlayRepository.Entry e:HomeCollisionOverlayRepository.all()){
            if(!e.isAdd()||!Boolean.TRUE.equals(e.movementClip)||!"WALL".equals(e.collisionClass)||e.shape!=0)continue;
            int x=e.worldX,y=e.worldY,nx=x,ny=y;
            switch(e.rotation&3){case 0:nx=x-1;break;case 1:ny=y+1;break;case 2:nx=x+1;break;case 3:ny=y-1;break;default:break;}
            out.add(edgeKey(x,y,nx,ny));out.add(edgeKey(nx,ny,x,y));
        }
        return Collections.unmodifiableSet(out);
    }

    private static long key(int x,int y){return (((long)x)<<32)^(y&0xffffffffL);}
    private static int x(long k){return (int)(k>>32);}
    private static int y(long k){return (int)k;}
    private static String edgeKey(int x0,int y0,int x1,int y1){return x0+","+y0+">"+x1+","+y1;}
}
