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

        WorldPlayer existingPlayer=
            byId.get(player.id());
        if(existingPlayer!=null&&
           existingPlayer!=player)
            throw new IllegalStateException(
                "DUPLICATE_ENTITY_ID entityId="+
                player.id()+
                " existing="+
                existingPlayer
            );

        long generation=player.markRegistered(username);
        byId.put(player.id(),player);
        byName.put(key,player.id());
        return generation;
    }

    synchronized boolean owns(
        WorldPlayer player,
        long expectedGeneration
    ){
        if(player==null)return false;
        WorldPlayer present=byId.get(player.id());
        return present==player &&
            player.accepts(expectedGeneration);
    }

    synchronized boolean unregister(
        WorldPlayer player,
        long expectedGeneration
    ){
        if(!owns(player,expectedGeneration))
            return false;

        WorldPlayer present=byId.remove(player.id());
        String name=present.username();
        if(name!=null)
            byName.remove(
                canonical(name),
                present.id()
            );
        present.markUnregistered();
        return true;
    }

    synchronized boolean unregister(WorldPlayer player){
        if(player==null)return false;
        WorldPlayer present=byId.get(player.id());
        if(present!=player)return false;
        return unregister(
            player,
            present.generation()
        );
    }

    synchronized WorldPlayer byId(EntityId id){return byId.get(id);}
    synchronized WorldPlayer byName(String username){EntityId id=byName.get(canonical(username));return id==null?null:byId.get(id);}
    synchronized int size(){return byId.size();}
    synchronized List<WorldPlayer> snapshot(){return Collections.unmodifiableList(new ArrayList<>(byId.values()));}

    private static String canonical(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
}