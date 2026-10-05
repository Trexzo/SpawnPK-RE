package spk.local;

final class GroundItem {
    final long id;
    final int itemId;
    int amount;
    final Tile tile;
    String owner;
    final long spawnedTick;
    final boolean devOwned;
    GroundItem(long id,int itemId,int amount,Tile tile,String owner,long spawnedTick,boolean devOwned){
        if(itemId<0||amount<=0||tile==null)throw new IllegalArgumentException();
        this.id=id;this.itemId=itemId;this.amount=amount;this.tile=tile;this.owner=owner;this.spawnedTick=spawnedTick;this.devOwned=devOwned;
    }
    @Override public String toString(){return "GroundItem{"+id+",item="+itemId+",amount="+amount+",tile="+tile+",dev="+devOwned+"}";}
}
