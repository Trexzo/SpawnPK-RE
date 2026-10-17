package spk.local;

final class SceneCoordinateContext {
    static final class Snapshot {
        final int chunkBaseLocalX;
        final int chunkBaseLocalY;

        Snapshot(
            int chunkBaseLocalX,
            int chunkBaseLocalY
        ){
            this.chunkBaseLocalX=chunkBaseLocalX;
            this.chunkBaseLocalY=chunkBaseLocalY;
        }
    }

    final int regionBaseWorldX,regionBaseWorldY,plane;
    private int chunkBaseLocalX=-1,chunkBaseLocalY=-1;
    SceneCoordinateContext(int x,int y,int plane){regionBaseWorldX=x;regionBaseWorldY=y;this.plane=plane;}
    int localX(int worldX){int v=worldX-regionBaseWorldX;requireLocal(v,"x");return v;}
    int localY(int worldY){int v=worldY-regionBaseWorldY;requireLocal(v,"y");return v;}
    int chunkXFor(int worldX){return SceneObjectPacketCodec.chunkBase(localX(worldX));}
    int chunkYFor(int worldY){return SceneObjectPacketCodec.chunkBase(localY(worldY));}
    boolean isCurrent(int cx,int cy){return chunkBaseLocalX==cx&&chunkBaseLocalY==cy;}
    void setCurrent(int cx,int cy){chunkBaseLocalX=cx;chunkBaseLocalY=cy;}
    void invalidate(){chunkBaseLocalX=chunkBaseLocalY=-1;}
    int currentChunkX(){return chunkBaseLocalX;}
    int currentChunkY(){return chunkBaseLocalY;}
    Snapshot snapshot(){
        return new Snapshot(
            chunkBaseLocalX,
            chunkBaseLocalY
        );
    }
    void restore(Snapshot snapshot){
        if(snapshot==null)
            throw new NullPointerException("snapshot");
        chunkBaseLocalX=snapshot.chunkBaseLocalX;
        chunkBaseLocalY=snapshot.chunkBaseLocalY;
    }
    private static void requireLocal(int v,String axis){if(v<0||v>=104)throw new IllegalArgumentException("outside loaded scene "+axis+" local="+v);}
}
