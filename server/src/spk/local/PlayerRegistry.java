package spk.local;

import java.util.*;

/** Shared membership only.  No sockets, ISAAC state or view indices live here. */
final class PlayerRegistry {
    private final LinkedHashMap<EntityId,WorldPlayer> byId=new LinkedHashMap<>();
    private final HashMap<String,EntityId> byName=new HashMap<>();

    synchronized long register(WorldPlayer player,String username){
        if(player==null)throw new NullPointerException("player");
        String key=canonical(username);
        if(key.isEmpty())
            throw new IllegalArgumentException(
                "username"
            );
        EntityId existing=byName.get(key);
        if(existing!=null)throw new IllegalStateException("DUPLICATE_LOGIN username="+username+" existing="+existing);
        long generation=player.markRegistered(username);
        byId.put(player.id(),player);
        byName.put(key,player.id());
        return generation;
    }

    synchronized boolean unregister(WorldPlayer player){
        if(player==null)return false;
        WorldPlayer present=byId.remove(player.id());
        if(present==null)return false;
        String name=present.username();
        if(name!=null)byName.remove(canonical(name),present.id());
        present.markUnregistered();
        return true;
    }

    synchronized WorldPlayer byId(EntityId id){return byId.get(id);}
    synchronized WorldPlayer byName(String username){EntityId id=byName.get(canonical(username));return id==null?null:byId.get(id);}
    synchronized int size(){return byId.size();}
    synchronized List<WorldPlayer> snapshot(){return Collections.unmodifiableList(new ArrayList<>(byId.values()));}

    private static String canonical(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
}