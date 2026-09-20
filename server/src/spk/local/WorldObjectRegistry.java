package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

final class WorldObjectRegistry {
    private final LinkedHashMap<Long,WorldObject> byId=new LinkedHashMap<>();
    private final AtomicLong ids=new AtomicLong();
    synchronized WorldObject put(int objectId,Tile tile,int shape,int rotation,boolean devOwned){
        if(objectId<0||tile==null||shape<0||shape>22||rotation<0||rotation>3)throw new IllegalArgumentException();
        removeAt(tile,shape); WorldObject o=new WorldObject(ids.incrementAndGet(),objectId,tile,shape,rotation,devOwned);byId.put(o.id,o);return o;
    }
    synchronized WorldObject findAt(Tile tile,int shape){for(WorldObject o:byId.values())if(o.shape==shape&&o.tile.equals(tile))return o;return null;}
    synchronized WorldObject removeAt(Tile tile,int shape){Iterator<WorldObject>it=byId.values().iterator();while(it.hasNext()){WorldObject o=it.next();if(o.shape==shape&&o.tile.equals(tile)){it.remove();return o;}}return null;}
    synchronized List<WorldObject> removeDevOwned(){List<WorldObject>x=new ArrayList<>();Iterator<WorldObject>it=byId.values().iterator();while(it.hasNext()){WorldObject o=it.next();if(o.devOwned){x.add(o);it.remove();}}return x;}
    synchronized List<WorldObject> snapshot(){return new ArrayList<>(byId.values());}
    synchronized int size(){return byId.size();}
}
