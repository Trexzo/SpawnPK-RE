package spk.local;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * P5.24 conservative source-only projectile corridor tests. Synthetic
 * collision masks make walls, corner-touch and missing map evidence
 * deterministic independently of real cache fixtures.
 */
public final class PlayableWindStrikeCollisionCorridorTest {
    private PlayableWindStrikeCollisionCorridorTest(){}

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(
            "P524_WIND_STRIKE_COLLISION_CORRIDOR "+why);
    }

    private static final class Masks
        implements LocalLabWindStrikeCollisionCorridor.MaskSource{
        private final Map<Long,Integer> masks=new HashMap<>();
        private final Set<Long> missing=new HashSet<>();
        private static long key(int x,int y){
            return ((long)x<<32)^(y&0xffffffffL);
        }
        void put(int x,int y,int flag){
            masks.merge(key(x,y),flag,(a,b)->a|b);
        }
        void omit(int x,int y){missing.add(key(x,y));}
        public boolean hasEvidence(int x,int y,int plane){
            return plane==0&&!missing.contains(key(x,y));
        }
        public int mask(int x,int y,int plane){
            return masks.getOrDefault(key(x,y),0);
        }
    }

    private static boolean ray(
        Masks masks,int x,int y,int tx,int ty
    ){
        return LocalLabWindStrikeCollisionCorridor.clearRay(
            masks,x,y,tx,ty,0);
    }

    public static void main(String[] args){
        require(args.length==0,"unexpected arguments");

        Masks clear=new Masks();
        require(ray(clear,0,0,8,0)&&
                ray(clear,0,0,0,-8)&&
                ray(clear,0,0,8,8)&&
                ray(clear,8,8,0,0)&&
                ray(clear,0,0,6,3)&&
                ray(clear,0,0,3,6),
                "known-clear straight and diagonal corridors rejected");
        require(!ray(clear,0,0,9,0),
                "unreviewed range was admitted");

        // Movement-only clipping is not automatically projectile clipping.
        Masks movementOnly=new Masks();
        movementOnly.put(1,0,256);
        require(ray(movementOnly,0,0,2,0),
                "movement-only object misread as projectile wall");

        Masks eastWall=new Masks();
        eastWall.put(0,0,4096);
        require(!ray(eastWall,0,0,1,0),
                "source-side east projectile wall ignored");
        Masks westWall=new Masks();
        westWall.put(1,0,65536);
        require(!ray(westWall,0,0,1,0),
                "destination-side west projectile wall ignored");
        Masks northWall=new Masks();
        northWall.put(0,0,1024);
        require(!ray(northWall,0,0,0,1),
                "source-side north projectile wall ignored");
        Masks southWall=new Masks();
        southWall.put(0,1,16384);
        require(!ray(southWall,0,0,0,1),
                "destination-side south projectile wall ignored");

        Masks object=new Masks();
        object.put(2,0,131072);
        require(!ray(object,0,0,4,0),
                "projectile-blocking object was crossed");
        Masks terrain=new Masks();
        terrain.put(2,0,2097152);
        require(!ray(terrain,0,0,4,0),
                "blocked static terrain was crossed");
        Masks missing=new Masks();
        missing.omit(2,0);
        require(!ray(missing,0,0,4,0),
                "missing region mask silently treated as clear");

        // A diagonal crossing of a perfect grid corner must not pass
        // between blocked orthogonal tiles even when (1,1) is clear.
        Masks corner=new Masks();
        corner.put(1,0,131072);
        require(!ray(corner,0,0,1,1),
                "projectile slipped between blocked diagonal corner");
        Masks diagonalWall=new Masks();
        diagonalWall.put(0,0,4096);
        require(!ray(diagonalWall,0,0,1,1),
                "diagonal skipped cardinal projectile wall");

        Masks unrelated=new Masks();
        unrelated.put(2,0,131072);
        require(ray(unrelated,0,1,4,1),
                "off-ray obstacle should not block clear corridor");

        require(!LocalLabWindStrikeCollisionCorridor.clearRay(
                clear,0,0,1,0,4),
                "missing plane evidence accepted");

        System.out.println(
            "P524_WIND_STRIKE_COLLISION_CORRIDOR_PASS"+
            " projectileEdges=true"+
            " projectileTiles=true"+
            " movementOnlyNotProjectile=true"+
            " missingRegionFailClosed=true"+
            " diagonalCornerCutDenied=true"+
            " canonicalRayBoundedToEight=true"+
            " originalSpellLOSAndNativeGuiUnverified=true");
    }
}
