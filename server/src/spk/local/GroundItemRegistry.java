package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

final class GroundItemRegistry {
    private final LinkedHashMap<Long,GroundItem> byId=new LinkedHashMap<>();
    private final AtomicLong ids=new AtomicLong();
    synchronized GroundItem add(int itemId,int amount,Tile tile,String owner,long tick,boolean devOwned){
        if(itemId<0||amount<=0||tile==null)throw new IllegalArgumentException();
        for(GroundItem g:byId.values()) if(g.itemId==itemId&&g.tile.equals(tile)&&Objects.equals(g.owner,owner)&&g.devOwned==devOwned){
            long sum=(long)g.amount+amount; if(sum>Integer.MAX_VALUE)throw new IllegalStateException("ground amount overflow"); g.amount=(int)sum; return g;
        }
        GroundItem g=new GroundItem(ids.incrementAndGet(),itemId,amount,tile,owner,tick,devOwned); byId.put(g.id,g); return g;
    }
    synchronized GroundItem find(int itemId,int x,int y,int plane){ for(GroundItem g:byId.values()) if(g.itemId==itemId&&g.tile.x==x&&g.tile.y==y&&g.tile.plane==plane)return g; return null; }
    synchronized GroundItem findOwned(int itemId,int x,int y,int plane,String owner){ for(GroundItem g:byId.values()) if(g.itemId==itemId&&g.tile.x==x&&g.tile.y==y&&g.tile.plane==plane&&Objects.equals(g.owner,owner))return g; return null; }
    synchronized boolean remove(long id){ return byId.remove(id)!=null; }
    synchronized List<GroundItem> snapshot(){ return new ArrayList<>(byId.values()); }
    synchronized List<GroundItem> removeDevOwned(){ List<GroundItem> out=new ArrayList<>(); Iterator<GroundItem>it=byId.values().iterator(); while(it.hasNext()){GroundItem g=it.next();if(g.devOwned){out.add(g);it.remove();}}return out; }
    synchronized int size(){return byId.size();}
}