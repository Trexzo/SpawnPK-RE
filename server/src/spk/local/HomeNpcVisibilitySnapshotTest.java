package spk.local;

import java.util.*;

public final class HomeNpcVisibilitySnapshotTest {
    public static void main(String[] args){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();

        WorldHomeNpcService home=
            new WorldHomeNpcService(
                registry
            );

        HomeNpcSpawnRepository.Spawn spawn=
            HomeNpcSpawnRepository
                .defaultReplay()
                .get(0);

        WorldNpc canonical=
            home.canonicalForOrdinal(
                spawn.ordinal
            );

        if(canonical==null)
            throw new AssertionError(
                "canonical HOME NPC missing"
            );

        Tile before=
            canonical.tile();

        int playerX=before.x;
        int playerY=before.y+16;

        WorldHomeNpcService.VisibleNpc captured=
            byCanonical(
                home.visibleCanonical(
                    playerX,
                    playerY
                ),
                canonical.id
            );

        if(captured==null)
            throw new AssertionError(
                "boundary HOME NPC not visible"
            );

        if(captured.x!=before.x||
           captured.y!=before.y||
           captured.plane!=before.plane||
           captured.definitionId!=
               canonical.definitionId||
           !captured.canonicalId.equals(
               canonical.id))
            throw new AssertionError(
                "visibility snapshot mismatch"
            );

        int capturedDx=
            captured.x-playerX;
        int capturedDy=
            captured.y-playerY;

        if(capturedDx!=0||
           capturedDy!=-16)
            throw new AssertionError(
                "fixture not on signed5 boundary dx="+
                capturedDx+
                " dy="+capturedDy
            );

        registry.move(
            canonical.id,
            before.x,
            before.y-1,
            before.plane
        );

        Tile after=
            canonical.tile();

        if(after.y-playerY!=-17)
            throw new AssertionError(
                "canonical NPC did not move outside signed5 range"
            );

        if(captured.x!=before.x||
           captured.y!=before.y||
           captured.plane!=before.plane)
            throw new AssertionError(
                "captured visibility coordinates changed after canonical move"
            );

        int sceneIndex=
            HomeNpcRuntimePlan
                .sceneIndexForOrdinal(
                    spawn.ordinal
                );

        NpcEntity projection=
            captured.project(
                sceneIndex
            );

        if(projection.x!=before.x||
           projection.y!=before.y||
           projection.definitionId!=
               canonical.definitionId||
           !canonical.id.equals(
               projection.canonicalId()))
            throw new AssertionError(
                "snapshot projection reread canonical state "+
                projection
            );

        byte[] encoded=
            NpcSyncEncoder.initial(
                Collections.singletonList(
                    projection
                ),
                playerX,
                playerY
            );

        if(encoded.length==0)
            throw new AssertionError(
                "snapshot projection produced empty packet65 payload"
            );

        if(byCanonical(
                home.visibleCanonical(
                    playerX,
                    playerY
                ),
                canonical.id
            )!=null)
            throw new AssertionError(
                "fresh visibility query retained out-of-range canonical NPC"
            );

        System.out.println(
            "HOME_NPC_VISIBILITY_SNAPSHOT_PASS "+
            "boundaryDy=-16 "+
            "canonicalMovedDy=-17 "+
            "snapshotStable=true "+
            "packet65Representable=true "+
            "freshVisibilityExcluded=true "+
            "canonicalId="+canonical.id
        );
    }

    private static WorldHomeNpcService.VisibleNpc byCanonical(
        List<WorldHomeNpcService.VisibleNpc> visible,
        EntityId id
    ){
        for(WorldHomeNpcService.VisibleNpc npc:
                visible)
            if(id.equals(
                    npc.canonicalId))
                return npc;

        return null;
    }

    private HomeNpcVisibilitySnapshotTest(){}
}
