package spk.local;

import java.util.*;

/**
 * Viewer-local mapping between canonical world NPC identity and packet-65 scene
 * index. Scene indexes are presentation handles, never global entity IDs.
 */
final class NpcViewIndexMap {
    static final int MAX_SCENE_INDEX=16382;

    private final LinkedHashMap<EntityId,Integer> byEntity=
        new LinkedHashMap<>();
    private final HashMap<Integer,EntityId> byScene=
        new HashMap<>();

    synchronized int bind(
        EntityId entityId,
        int sceneIndex
    ){
        if(entityId==null)
            throw new NullPointerException("entityId");

        if(sceneIndex<0||
           sceneIndex>MAX_SCENE_INDEX)
            throw new IllegalArgumentException(
                "sceneIndex="+sceneIndex
            );

        Integer existingScene=
            byEntity.get(entityId);

        if(existingScene!=null){
            if(existingScene.intValue()!=sceneIndex)
                throw new IllegalStateException(
                    "entity already mapped entity="+
                    entityId+
                    " scene="+existingScene+
                    " requested="+sceneIndex
                );
            return existingScene.intValue();
        }

        EntityId existingEntity=
            byScene.get(sceneIndex);

        if(existingEntity!=null&&
           !existingEntity.equals(entityId))
            throw new IllegalStateException(
                "scene already mapped scene="+
                sceneIndex+
                " entity="+existingEntity+
                " requested="+entityId
            );

        byEntity.put(entityId,sceneIndex);
        byScene.put(sceneIndex,entityId);
        return sceneIndex;
    }

    synchronized Integer sceneIndex(
        EntityId entityId
    ){
        return byEntity.get(entityId);
    }

    synchronized EntityId entityId(
        int sceneIndex
    ){
        return byScene.get(sceneIndex);
    }

    synchronized boolean unbind(
        EntityId entityId
    ){
        Integer scene=byEntity.remove(entityId);
        if(scene==null)return false;
        byScene.remove(scene,entityId);
        return true;
    }

    synchronized void clear(){
        byEntity.clear();
        byScene.clear();
    }

    synchronized int size(){
        return byEntity.size();
    }

    synchronized Map<EntityId,Integer> snapshot(){
        return Collections.unmodifiableMap(
            new LinkedHashMap<>(byEntity)
        );
    }
}
