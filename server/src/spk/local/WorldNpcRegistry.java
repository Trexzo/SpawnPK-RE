package spk.local;

import java.util.*;

/**
 * Canonical world-owned NPC lifecycle.
 *
 * Client scene indexes are intentionally absent. A viewer must map a WorldNpc
 * identity to its own packet-65 scene index separately.
 */
final class WorldNpcRegistry {
    private final LinkedHashMap<EntityId,WorldNpc> byId=
        new LinkedHashMap<>();

    synchronized WorldNpc spawn(
        int definitionId,
        int x,
        int y,
        int plane
    ){
        return spawn(
            definitionId,
            x,
            y,
            plane,
            null,
            -1
        );
    }

    synchronized WorldNpc spawnOwned(
        int definitionId,
        int x,
        int y,
        int plane,
        EntityId ownerId,
        int sourceItemId
    ){
        if(ownerId==null)
            throw new NullPointerException("ownerId");

        return spawn(
            definitionId,
            x,
            y,
            plane,
            ownerId,
            sourceItemId
        );
    }

    private WorldNpc spawn(
        int definitionId,
        int x,
        int y,
        int plane,
        EntityId ownerId,
        int sourceItemId
    ){
        WorldNpc npc=
            new WorldNpc(
                EntityId.next(),
                definitionId,
                x,
                y,
                plane,
                ownerId,
                sourceItemId
            );

        if(byId.containsKey(npc.id))
            throw new IllegalStateException(
                "duplicate world npc id "+npc.id
            );

        byId.put(npc.id,npc);
        return npc;
    }

    synchronized WorldNpc replaceOwned(
        WorldNpc expectedCurrent,
        int definitionId,
        int x,
        int y,
        int plane,
        EntityId ownerId,
        int sourceItemId
    ){
        if(expectedCurrent==null)
            throw new NullPointerException(
                "expectedCurrent"
            );
        if(ownerId==null)
            throw new NullPointerException(
                "ownerId"
            );

        WorldNpc registered=
            byId.get(expectedCurrent.id);

        if(registered!=expectedCurrent)
            throw new IllegalStateException(
                "world npc replacement owner changed "+
                expectedCurrent.id
            );

        if(!ownerId.equals(
                expectedCurrent.ownerId))
            throw new IllegalStateException(
                "world npc replacement owner mismatch "+
                expectedCurrent.id
            );

        WorldNpc next=
            new WorldNpc(
                EntityId.next(),
                definitionId,
                x,
                y,
                plane,
                ownerId,
                sourceItemId
            );

        if(byId.containsKey(next.id))
            throw new IllegalStateException(
                "duplicate world npc id "+next.id
            );

        byId.put(next.id,next);

        if(!byId.remove(
                expectedCurrent.id,
                expectedCurrent)){
            byId.remove(next.id,next);
            throw new IllegalStateException(
                "world npc replacement owner changed "+
                expectedCurrent.id
            );
        }

        return next;
    }

    interface OwnedNpcAction {
        void run() throws Exception;
    }

    interface SpawnedNpcAction {
        void run(WorldNpc npc) throws Exception;
    }

    synchronized boolean withCurrentMutationOwnershipIfCurrent(
        WorldNpc expectedCurrent,
        OwnedNpcAction action
    )throws Exception{
        if(expectedCurrent==null||action==null)
            throw new NullPointerException();

        WorldNpc registered=
            byId.get(expectedCurrent.id);

        if(registered!=expectedCurrent)
            return false;

        action.run();
        return true;
    }

    synchronized WorldNpc spawnWithMutationOwnership(
        int definitionId,
        int x,
        int y,
        int plane,
        SpawnedNpcAction action
    )throws Exception{
        if(action==null)
            throw new NullPointerException(
                "action"
            );

        WorldNpc npc=
            spawn(
                definitionId,
                x,
                y,
                plane,
                null,
                -1
            );

        try{
            action.run(npc);
            return npc;
        }catch(Exception failure){
            byId.remove(
                npc.id,
                npc
            );
            throw failure;
        }catch(Error failure){
            byId.remove(
                npc.id,
                npc
            );
            throw failure;
        }
    }

    synchronized WorldNpc byId(EntityId id){
        return byId.get(id);
    }

    synchronized WorldNpc move(
        EntityId id,
        int x,
        int y,
        int plane
    ){
        WorldNpc npc=byId.get(id);
        if(npc==null)return null;
        npc.moveTo(x,y,plane);
        return npc;
    }

    synchronized boolean remove(EntityId id){
        return byId.remove(id)!=null;
    }

    synchronized int size(){
        return byId.size();
    }

    synchronized List<WorldNpc> snapshot(){
        return Collections.unmodifiableList(
            new ArrayList<>(byId.values())
        );
    }

    synchronized List<WorldNpc> ownedBy(EntityId ownerId){
        if(ownerId==null)
            return Collections.emptyList();

        ArrayList<WorldNpc> out=new ArrayList<>();
        for(WorldNpc npc:byId.values())
            if(ownerId.equals(npc.ownerId))
                out.add(npc);

        return Collections.unmodifiableList(out);
    }
}
