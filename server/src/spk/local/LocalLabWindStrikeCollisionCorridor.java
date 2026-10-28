package spk.local;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * P5.24 conservative, source-only CUSTOM_LOCALLAB projectile corridor.
 *
 * Uses current static collision masks and recorded HOME projectile overlay.
 * This is NOT recovered original SpawnPK LOS, animations, NPC footprints,
 * dynamic blockers, projectiles, spell mechanics, or native client proof.
 *
 * A tile on the ray lacking a backed static region is never assumed clear.
 * Grid-corner contact requires both orthogonal corridors clear to avoid
 * tunnelling through a projectile-blocking wall.
 */
final class LocalLabWindStrikeCollisionCorridor {
    private static final int BLOCK_PROJECTILE_TILE=131072;
    private static final int BLOCK_TERRAIN=2097152;
    private static final int WEST_PROJECTILE=65536;
    private static final int EAST_PROJECTILE=4096;
    private static final int NORTH_PROJECTILE=1024;
    private static final int SOUTH_PROJECTILE=16384;
    private static final int PROJECTILE_FLAGS=131072|512|1024|2048|
        4096|8192|16384|32768|65536;
    private static final Map<Long,Integer> HOME_OVERLAY=
        loadHomeProjectileOverlay();

    interface MaskSource {
        boolean hasEvidence(int x,int y,int plane);
        int mask(int x,int y,int plane);
    }

    private static final MaskSource SOURCE=new MaskSource(){
        public boolean hasEvidence(int x,int y,int plane){
            if(plane<0||plane>3)return false;
            int regionId=((x>>6)<<8)|(y>>6);
            return WorldCollisionAuthority.hasRegion(regionId);
        }

        public int mask(int x,int y,int plane){
            int mask=WorldCollisionAuthority.maskAt(x,y,plane);
            if(plane==0)
                mask|=HOME_OVERLAY.getOrDefault(key(x,y),0);
            return mask;
        }
    };

    private LocalLabWindStrikeCollisionCorridor(){}

    static String rejection(MovementState caster,WorldNpc target){
        Objects.requireNonNull(caster,"caster");
        Objects.requireNonNull(target,"target");

        // All callers already hold player mutation + exact NPC registry
        // mutation ownership and have passed P5.23 geometry.
        boolean clear=clearRay(
            SOURCE,caster.x(),caster.y(),target.x(),target.y(),
            caster.plane());
        return clear?null:"REJECTED_TARGET_COLLISION_CORRIDOR_UNVERIFIED";
    }

    /**
     * Deterministic bounded supercover grid traversal, test-injectable.
     * A true result is only a source-mask-clear corridor, not a promise
     * of authoritative original-server line-of-sight.
     */
    static boolean clearRay(
        MaskSource masks,
        int sx,int sy,int tx,int ty,int plane
    ){
        Objects.requireNonNull(masks,"masks");
        if(plane<0||plane>3)return false;
        long deltaX=(long)tx-sx,deltaY=(long)ty-sy;
        long ax=Math.abs(deltaX),ay=Math.abs(deltaY);
        if(Math.max(ax,ay)>
                LocalLabWindStrikeTargetGeometry.MAX_CHEBYSHEV_TILES)
            return false;

        int x=sx,y=sy;
        if(!available(masks,x,y,plane))return false;
        if(ax==0&&ay==0)return true;

        int dx=Long.signum(deltaX),dy=Long.signum(deltaY);
        long steppedX=0,steppedY=0;
        while(steppedX<ax||steppedY<ay){
            // Compare times to the next X and Y grid boundaries
            // using integers, avoiding floating-point corner drift.
            long nextX=(2*steppedX+1)*ay;
            long nextY=(2*steppedY+1)*ax;
            if(steppedX==ax||nextX>nextY){
                if(!cardinal(masks,x,y,plane,x,y+dy))return false;
                y+=dy;
                steppedY++;
            }else if(steppedY==ay||nextX<nextY){
                if(!cardinal(masks,x,y,plane,x+dx,y))return false;
                x+=dx;
                steppedX++;
            }else{
                // An exact grid corner touches both orthogonal tiles.
                // Require both complete two-step routes; this is a
                // conservative no-corner-cut LocalLab policy.
                int nx=x+dx,ny=y+dy;
                if(!cardinal(masks,x,y,plane,nx,y)||
                   !cardinal(masks,x,y,plane,x,ny)||
                   !cardinal(masks,nx,y,plane,nx,ny)||
                   !cardinal(masks,x,ny,plane,nx,ny))
                    return false;
                x=nx;y=ny;steppedX++;steppedY++;
            }
        }
        return x==tx&&y==ty;
    }

    private static boolean cardinal(
        MaskSource masks,int x0,int y0,int plane,int x1,int y1
    ){
        if(!available(masks,x0,y0,plane)||
           !available(masks,x1,y1,plane))
            return false;

        int a=masks.mask(x0,y0,plane),b=masks.mask(x1,y1,plane);
        if(x1==x0-1&&y1==y0)
            return (a&WEST_PROJECTILE)==0&&
                (b&EAST_PROJECTILE)==0;
        if(x1==x0+1&&y1==y0)
            return (a&EAST_PROJECTILE)==0&&
                (b&WEST_PROJECTILE)==0;
        if(x1==x0&&y1==y0+1)
            return (a&NORTH_PROJECTILE)==0&&
                (b&SOUTH_PROJECTILE)==0;
        if(x1==x0&&y1==y0-1)
            return (a&SOUTH_PROJECTILE)==0&&
                (b&NORTH_PROJECTILE)==0;
        return false;
    }

    private static boolean available(
        MaskSource masks,int x,int y,int plane
    ){
        if(!masks.hasEvidence(x,y,plane))return false;
        int value=masks.mask(x,y,plane);
        return (value&(BLOCK_PROJECTILE_TILE|BLOCK_TERRAIN))==0;
    }

    private static Map<Long,Integer> loadHomeProjectileOverlay(){
        HashMap<Long,Integer> masks=new HashMap<>();
        for(HomeCollisionOverlayRepository.Entry entry:
                HomeCollisionOverlayRepository.all()){
            if(!entry.isAdd())continue;
            // Uncertain projectile clipping is blocked conservatively.
            if(Boolean.FALSE.equals(entry.projectileClip))continue;

            java.util.List<ClientCollisionDeltaCodec.Delta> deltas;
            if("RECTANGLE".equals(entry.collisionClass)){
                deltas=ClientCollisionDeltaCodec.rectangle(
                    ClientCollisionDeltaCodec.Kind.ADD,
                    entry.worldX,entry.worldY,
                    entry.sizeX,entry.sizeY,entry.rotation,true);
            }else if("WALL".equals(entry.collisionClass)&&
                     entry.shape==0){
                deltas=ClientCollisionDeltaCodec.straightWall(
                    ClientCollisionDeltaCodec.Kind.ADD,
                    entry.worldX,entry.worldY,entry.rotation,true);
            }else{
                // Unknown geometry on an added obstacle is not proof
                // of a clear ray. Block its source tile at minimum.
                masks.merge(
                    key(entry.worldX,entry.worldY),
                    BLOCK_PROJECTILE_TILE,(a,b)->a|b);
                continue;
            }
            for(ClientCollisionDeltaCodec.Delta delta:deltas){
                int projectile=delta.mask&PROJECTILE_FLAGS;
                if(projectile!=0)
                    masks.merge(
                        key(delta.worldX,delta.worldY),
                        projectile,(a,b)->a|b);
            }
        }
        return Collections.unmodifiableMap(masks);
    }

    private static long key(int x,int y){
        return ((long)x<<32)^(y&0xffffffffL);
    }
}
