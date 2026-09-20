package spk.local;

/**
 * Canonical world-owned NPC state.
 *
 * This deliberately has no client scene index. Scene/index allocation belongs
 * to a viewer-specific presentation layer and must not become global identity.
 */
final class WorldNpc {
    final EntityId id;
    final int definitionId;
    final EntityId ownerId;
    final int sourceItemId;
    private int x;
    private int y;
    private int plane;

    WorldNpc(
        EntityId id,
        int definitionId,
        int x,
        int y,
        int plane,
        EntityId ownerId,
        int sourceItemId
    ){
        if(id==null)throw new NullPointerException("id");
        if(definitionId<0||definitionId>=16384)
            throw new IllegalArgumentException(
                "definitionId="+definitionId
            );
        if(plane<0||plane>3)
            throw new IllegalArgumentException(
                "plane="+plane
            );
        if(sourceItemId< -1)
            throw new IllegalArgumentException(
                "sourceItemId="+sourceItemId
            );

        this.id=id;
        this.definitionId=definitionId;
        this.x=x;
        this.y=y;
        this.plane=plane;
        this.ownerId=ownerId;
        this.sourceItemId=sourceItemId;
    }

    synchronized int x(){return x;}
    synchronized int y(){return y;}
    synchronized int plane(){return plane;}
    synchronized Tile tile(){return new Tile(x,y,plane);}

    synchronized void moveTo(
        int x,
        int y,
        int plane
    ){
        if(plane<0||plane>3)
            throw new IllegalArgumentException(
                "plane="+plane
            );
        this.x=x;
        this.y=y;
        this.plane=plane;
    }

    boolean owned(){
        return ownerId!=null;
    }

    @Override public synchronized String toString(){
        return "WorldNpc{id="+id+
            ",def="+definitionId+
            ",xy="+x+","+y+","+plane+
            ",owner="+ownerId+
            ",sourceItem="+sourceItemId+
            "}";
    }
}
