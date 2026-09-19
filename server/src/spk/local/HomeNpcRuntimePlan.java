package spk.local;

import java.util.*;

/**
 * Merge-friendly WORLD -> MAINLINE NPC bootstrap plan.
 *
 * WORLD-R3 makes HOME scene indexes stable by canonical manifest ordinal rather
 * than assigning indexes based on whichever subset happens to be in signed5
 * range at that moment. MAINLINE still owns NpcRegistry/NpcSyncEncoder.
 */
final class HomeNpcRuntimePlan {
    static final int FIRST_RESERVED_WORLD_SCENE_INDEX = 100;

    static final class Plan {
        final List<NpcEntity> entities;
        final int playerX,playerY;
        Plan(List<NpcEntity> entities,int playerX,int playerY){this.entities=Collections.unmodifiableList(entities);this.playerX=playerX;this.playerY=playerY;}
        int size(){return entities.size();}
    }

    private HomeNpcRuntimePlan() {}

    static int sceneIndexForOrdinal(int ordinal){
        if(ordinal<1 || ordinal>HomeNpcSpawnRepository.count()) throw new IllegalArgumentException("HOME NPC ordinal "+ordinal);
        int scene=FIRST_RESERVED_WORLD_SCENE_INDEX+ordinal;
        if(scene>=16383) throw new IllegalStateException("NPC scene index exhausted");
        return scene;
    }

    static Plan nearbyInitial(int playerX,int playerY) {
        List<HomeNpcSpawnRepository.Spawn> src=HomeNpcSpawnRepository.nearbyDefaultReplay(playerX,playerY);
        ArrayList<NpcEntity> out=new ArrayList<>();
        for(HomeNpcSpawnRepository.Spawn s:src) out.add(new NpcEntity(sceneIndexForOrdinal(s.ordinal),s.npcDefinitionId,s.anchorX,s.anchorY));
        out.sort(Comparator.comparingInt(n->n.sceneIndex));
        return new Plan(out,playerX,playerY);
    }

    static HomeNpcWorldState newWorldState(){return new HomeNpcWorldState();}

    static List<HomeNpcSpawnRepository.Spawn> observedWanderers(){
        ArrayList<HomeNpcSpawnRepository.Spawn> out=new ArrayList<>();
        for(HomeNpcSpawnRepository.Spawn s:HomeNpcSpawnRepository.all()) if(s.localWanderObserved()&&s.defaultReplay) out.add(s);
        return Collections.unmodifiableList(out);
    }
}
