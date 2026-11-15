package spk.local;

import java.util.*;

/**
 * Canonical world ownership for a player's active main pet and configured
 * mini-pet actor.
 *
 * Packet-65 scene indexes remain viewer-local presentation state in NpcRegistry.
 */
final class WorldPetNpcService {
    private static final class OwnerActors {
        EntityId mainId;
        EntityId miniId;
    }

    private final WorldNpcRegistry registry;
    /**
     * R75: opt-in test-only owner-mutation gate. Live pet state and
     * unregister cleanup are unchanged unless a fixture installs it.
     */
    @FunctionalInterface
    interface OfflinePetMutationAdmission {
        boolean permits(EntityId ownerId);
    }

    private volatile OfflinePetMutationAdmission offlinePetMutationAdmission;

    synchronized void installOfflinePetMutationAdmissionForTest(
        OfflinePetMutationAdmission gate
    ){
        if(offlinePetMutationAdmission!=null)
            throw new IllegalStateException(
                "R75_PET_GATE_DENIED already installed"
            );
        offlinePetMutationAdmission=Objects.requireNonNull(
            gate,"gate"
        );
    }

    private void requireOfflinePetMutationAdmission(EntityId ownerId){
        OfflinePetMutationAdmission gate=offlinePetMutationAdmission;
        if(gate!=null && !gate.permits(ownerId))
            throw new IllegalStateException(
                "R75_PET_QUIESCED owner="+ownerId
            );
    }

    private final HashMap<EntityId,OwnerActors> byOwner=
        new HashMap<>();

    WorldPetNpcService(WorldNpcRegistry registry){
        this.registry=Objects.requireNonNull(
            registry,
            "registry"
        );
    }

    synchronized WorldNpc ensureMain(
        EntityId ownerId,
        int definitionId,
        int sourceItemId,
        int x,
        int y,
        int plane
    ){
        if(ownerId==null)
            throw new NullPointerException("ownerId");

        requireOfflinePetMutationAdmission(ownerId);
        WorldNpc.validateSpawnParameters(
            definitionId,
            plane,
            sourceItemId
        );

        OwnerActors actors=
            byOwner.get(ownerId);

        WorldNpc current=
            actors==null||actors.mainId==null
                ?null
                :registry.byId(actors.mainId);

        if(current!=null&&
           current.definitionId==definitionId&&
           current.sourceItemId==sourceItemId){
            current.moveTo(x,y,plane);
            return current;
        }

        WorldNpc next=
            current==null
                ?registry.spawnOwned(
                    definitionId,
                    x,
                    y,
                    plane,
                    ownerId,
                    sourceItemId
                )
                :registry.replaceOwned(
                    current,
                    definitionId,
                    x,
                    y,
                    plane,
                    ownerId,
                    sourceItemId
                );

        if(actors==null){
            actors=new OwnerActors();
            byOwner.put(ownerId,actors);
        }

        actors.mainId=next.id;
        return next;
    }

    synchronized WorldNpc ensureMini(
        EntityId ownerId,
        int definitionId,
        int sourceItemId,
        int x,
        int y,
        int plane
    ){
        if(ownerId==null)
            throw new NullPointerException("ownerId");

        requireOfflinePetMutationAdmission(ownerId);
        WorldNpc.validateSpawnParameters(
            definitionId,
            plane,
            sourceItemId
        );

        OwnerActors actors=
            byOwner.get(ownerId);

        WorldNpc current=
            actors==null||actors.miniId==null
                ?null
                :registry.byId(actors.miniId);

        if(current!=null&&
           current.definitionId==definitionId&&
           current.sourceItemId==sourceItemId){
            current.moveTo(x,y,plane);
            return current;
        }

        WorldNpc next=
            current==null
                ?registry.spawnOwned(
                    definitionId,
                    x,
                    y,
                    plane,
                    ownerId,
                    sourceItemId
                )
                :registry.replaceOwned(
                    current,
                    definitionId,
                    x,
                    y,
                    plane,
                    ownerId,
                    sourceItemId
                );

        if(actors==null){
            actors=new OwnerActors();
            byOwner.put(ownerId,actors);
        }

        actors.miniId=next.id;
        return next;
    }

    synchronized WorldNpc moveMain(
        EntityId ownerId,
        int x,
        int y,
        int plane
    ){
        requireOfflinePetMutationAdmission(ownerId);
        WorldNpc main=main(ownerId);
        if(main==null)return null;
        main.moveTo(x,y,plane);
        return main;
    }

    synchronized WorldNpc moveMini(
        EntityId ownerId,
        int x,
        int y,
        int plane
    ){
        requireOfflinePetMutationAdmission(ownerId);
        WorldNpc mini=mini(ownerId);
        if(mini==null)return null;
        mini.moveTo(x,y,plane);
        return mini;
    }

    synchronized boolean removeMini(
        EntityId ownerId
    ){
        requireOfflinePetMutationAdmission(ownerId);
        OwnerActors actors=byOwner.get(ownerId);
        if(actors==null||actors.miniId==null)
            return false;

        EntityId id=actors.miniId;
        actors.miniId=null;
        boolean removed=registry.remove(id);

        if(actors.mainId==null)
            byOwner.remove(ownerId);

        return removed;
    }

    synchronized boolean removeMainAndMini(
        EntityId ownerId
    ){
        OwnerActors actors=byOwner.remove(ownerId);
        if(actors==null)return false;

        boolean removed=false;
        if(actors.miniId!=null)
            removed|=registry.remove(actors.miniId);
        if(actors.mainId!=null)
            removed|=registry.remove(actors.mainId);

        return removed;
    }

    synchronized WorldNpc main(EntityId ownerId){
        OwnerActors actors=byOwner.get(ownerId);
        return actors==null||actors.mainId==null
            ?null
            :registry.byId(actors.mainId);
    }

    synchronized WorldNpc mini(EntityId ownerId){
        OwnerActors actors=byOwner.get(ownerId);
        return actors==null||actors.miniId==null
            ?null
            :registry.byId(actors.miniId);
    }

    synchronized int ownerActorCount(
        EntityId ownerId
    ){
        int count=0;
        if(main(ownerId)!=null)count++;
        if(mini(ownerId)!=null)count++;
        return count;
    }
}