package spk.local;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Exact-current static terrain/object movement collision imported from WORLD Authority R1. */
final class WorldCollisionAuthority {
    private static final int BLOCK_TILE=256|2097152;
    static final class RegionCollision {
        final int[] keys,masks;
        RegionCollision(int[] keys,int[] masks){this.keys=keys;this.masks=masks;}
        int mask(int plane,int lx,int ly){
            if(plane<0||plane>3||lx<0||lx>63||ly<0||ly>63)return BLOCK_TILE;
            int k=(plane<<12)|(lx<<6)|ly, i=Arrays.binarySearch(keys,k);return i>=0?masks[i]:0;
        }
        int entries(){return keys.length;}
    }
    private static final Map<Integer,RegionCollision> BY_REGION=load();
    static int regionCount(){return BY_REGION.size();}
    static int entryCount(){int n=0;for(RegionCollision r:BY_REGION.values())n+=r.entries();return n;}
    static boolean hasRegion(int rid){return BY_REGION.containsKey(rid);}
    static int maskAt(int x,int y,int plane){
        RegionCollision r=BY_REGION.get(((x>>6)<<8)|(y>>6));
        return r==null?BLOCK_TILE:r.mask(plane,x&63,y&63);
    }
    static boolean blockedTile(int x,int y,int plane){return (maskAt(x,y,plane)&BLOCK_TILE)!=0;}

    static boolean canStep(int x0,int y0,int plane,int x1,int y1){
        int dx=x1-x0,dy=y1-y0;
        if(Math.abs(dx)>1||Math.abs(dy)>1||(dx==0&&dy==0))return false;
        if(blockedTile(x0,y0,plane)||blockedTile(x1,y1,plane))return false;
        if(dx==0||dy==0)return cardinal(x0,y0,plane,x1,y1);
        int sm=maskAt(x0,y0,plane),dm=maskAt(x1,y1,plane);
        int sb,db;
        if(dx<0&&dy>0){sb=1;db=16;}
        else if(dx>0&&dy>0){sb=4;db=64;}
        else if(dx>0){sb=16;db=1;}
        else {sb=64;db=4;}
        if((sm&sb)!=0||(dm&db)!=0)return false;
        int ax=x0+dx,ay=y0,bx=x0,by=y0+dy;
        // Exact static collision plus conservative corner rule: both orthogonal
        // corridors must exist. This matches LocalLab's certified no-corner-cut policy.
        return cardinal(x0,y0,plane,ax,ay)&&cardinal(x0,y0,plane,bx,by)
            &&cardinal(ax,ay,plane,x1,y1)&&cardinal(bx,by,plane,x1,y1);
    }
    private static boolean cardinal(int x0,int y0,int p,int x1,int y1){
        if(blockedTile(x0,y0,p)||blockedTile(x1,y1,p))return false;
        int dx=x1-x0,dy=y1-y0,sm=maskAt(x0,y0,p),dm=maskAt(x1,y1,p);
        if(dx==-1&&dy==0)return (sm&128)==0&&(dm&8)==0;
        if(dx==1&&dy==0)return (sm&8)==0&&(dm&128)==0;
        if(dx==0&&dy==1)return (sm&2)==0&&(dm&32)==0;
        if(dx==0&&dy==-1)return (sm&32)==0&&(dm&2)==0;
        return false;
    }

    /** Deterministic safe local-dev landing near region center; not production arrival authority. */
    static Tile safeTile(int regionId,int plane){
        WorldRegionAuthorityRepository.Region r=WorldRegionAuthorityRepository.get(regionId);
        if(r==null||!hasRegion(regionId)||plane<0||plane>3)return null;
        int cx=r.x0+32,cy=r.y0+32;
        for(int rad=0;rad<32;rad++){
            for(int dx=-rad;dx<=rad;dx++)for(int dy=-rad;dy<=rad;dy++){
                if(Math.max(Math.abs(dx),Math.abs(dy))!=rad)continue;
                int x=cx+dx,y=cy+dy;if(x<r.x0||x>r.x1||y<r.y0||y>r.y1)continue;
                if(blockedTile(x,y,plane))continue;
                if(canStep(x,y,plane,x+1,y)||canStep(x,y,plane,x-1,y)||canStep(x,y,plane,x,y+1)||canStep(x,y,plane,x,y-1))return new Tile(x,y,plane);
            }
        }
        return null;
    }

    private static Map<Integer,RegionCollision> load(){
        LinkedHashMap<Integer,RegionCollision> out=new LinkedHashMap<>();
        try(InputStream raw=WorldCollisionAuthority.class.getResourceAsStream("/spk/local/data/world_collision_authority.bin.gz")){
            if(raw==null)throw new IllegalStateException("missing world_collision_authority.bin.gz");
            try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))){
                byte[] magic=new byte[8];in.readFully(magic);if(!"SPKCOLL1".equals(new String(magic,"ISO-8859-1")))throw new IOException("bad collision magic");
                int regions=in.readInt();
                for(int r=0;r<regions;r++){
                    int rid=in.readInt(),n=in.readInt();if(n<0||n>20000)throw new IOException("bad collision count rid="+rid+" n="+n);
                    int[] k=new int[n],m=new int[n];
                    for(int i=0;i<n;i++){k[i]=in.readUnsignedShort();m[i]=in.readInt();}
                    out.put(rid,new RegionCollision(k,m));
                }
                if(in.read()!=-1)throw new IOException("collision trailing bytes");
            }
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
        return Collections.unmodifiableMap(out);
    }
    private WorldCollisionAuthority(){}
}
