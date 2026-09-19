package spk.local;

import java.util.*;

/**
 * Exact sparse collision-mask delta model for the current client paths used by the HOME overlay.
 * Recovered statically from pinned client classes rs.x (object placement) and rs.f (collision map).
 *
 * Supported here because V9.06 HOME dynamic additions use only:
 *   - GAME_OBJECT shape 10 rectangles
 *   - BOUNDARY_WALL shape 0 straight walls
 *
 * Object definition field aj gates collision. Field ab selects the additional projectile/impenetrable masks.
 */
final class ClientCollisionDeltaCodec {
    enum Kind { ADD, REMOVE }
    static final class Delta {
        final Kind kind; final int worldX,worldY,mask;
        Delta(Kind k,int x,int y,int m){kind=k;worldX=x;worldY=y;mask=m;}
        @Override public String toString(){return kind+"@"+worldX+","+worldY+" mask=0x"+Integer.toHexString(mask);}
    }
    private ClientCollisionDeltaCodec(){}

    static List<Delta> rectangle(Kind kind,int worldX,int worldY,int sizeX,int sizeY,int rotation,boolean projectileClip){
        if(rotation<0||rotation>3)throw new IllegalArgumentException("rotation");
        int w=sizeX,h=sizeY; if(rotation==1||rotation==3){int t=w;w=h;h=t;}
        int mask=256 + (projectileClip?131072:0); // exact rs.f rectangle mask: 0x100 [+0x20000]
        ArrayList<Delta> out=new ArrayList<>();
        for(int x=worldX;x<worldX+w;x++)for(int y=worldY;y<worldY+h;y++)out.add(new Delta(kind,x,y,mask));
        return out;
    }

    static List<Delta> straightWall(Kind kind,int worldX,int worldY,int rotation,boolean projectileClip){
        if(rotation<0||rotation>3)throw new IllegalArgumentException("rotation");
        ArrayList<Delta> out=new ArrayList<>(4);
        // Exact rs.f shape=0 low movement masks and mirrored neighbor masks.
        switch(rotation){
            case 0: add(out,kind,worldX,worldY,128); add(out,kind,worldX-1,worldY,8); break;
            case 1: add(out,kind,worldX,worldY,2);   add(out,kind,worldX,worldY+1,32); break;
            case 2: add(out,kind,worldX,worldY,8);   add(out,kind,worldX+1,worldY,128); break;
            case 3: add(out,kind,worldX,worldY,32);  add(out,kind,worldX,worldY-1,2); break;
        }
        if(projectileClip){
            switch(rotation){
                case 0: add(out,kind,worldX,worldY,65536); add(out,kind,worldX-1,worldY,4096); break;
                case 1: add(out,kind,worldX,worldY,1024);  add(out,kind,worldX,worldY+1,16384); break;
                case 2: add(out,kind,worldX,worldY,4096);  add(out,kind,worldX+1,worldY,65536); break;
                case 3: add(out,kind,worldX,worldY,16384); add(out,kind,worldX,worldY-1,1024); break;
            }
        }
        return out;
    }
    private static void add(List<Delta> o,Kind k,int x,int y,int m){o.add(new Delta(k,x,y,m));}
}
