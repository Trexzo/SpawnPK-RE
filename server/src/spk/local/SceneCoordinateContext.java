package spk.local;

final class SceneCoordinateContext {
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
    private static void requireLocal(int v,String axis){if(v<0||v>=104)throw new IllegalArgumentException("outside loaded scene "+axis+" local="+v);}
}
