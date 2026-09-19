package spk.local;

final class WorldObject {
    final long id;
    final int objectId,shape,rotation;
    final Tile tile;
    final boolean devOwned;
    WorldObject(long id,int objectId,Tile tile,int shape,int rotation,boolean devOwned){
        if(objectId<0||tile==null||shape<0||shape>22||rotation<0||rotation>3)throw new IllegalArgumentException();
        this.id=id;this.objectId=objectId;this.tile=tile;this.shape=shape;this.rotation=rotation;this.devOwned=devOwned;
    }
    @Override public String toString(){return "WorldObject{"+id+",object="+objectId+",tile="+tile+",shape="+shape+",rot="+rotation+",dev="+devOwned+"}";}
}
