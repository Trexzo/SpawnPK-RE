package spk.local;

import java.util.*;

public final class CanonicalNpcViewerLifecycleAcceptanceTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer firstViewer=new WorldPlayer();
            WorldPlayer secondViewer=new WorldPlayer();

            world.registerPlayer(firstViewer,"opensrc");
            world.registerPlayer(secondViewer,"src");

            HomeNpcSpawnRepository.Spawn spawn=
                HomeNpcSpawnRepository.defaultReplay().get(0);

            WorldNpc canonical=
                world.homeNpcs().canonicalForOrdinal(
                    spawn.ordinal
                );

            if(canonical==null)
                throw new AssertionError(
                    "canonical HOME NPC missing"
                );

            HomeWorldRuntimePlan nearView=
                new HomeWorldRuntimePlan(
                    world.homeNpcs()
                );
            HomeWorldRuntimePlan farView=
                new HomeWorldRuntimePlan(
                    world.homeNpcs()
                );

            List<NpcEntity> near=
                nearView.bootstrapNpcs(
                    canonical.x(),
                    canonical.y()
                );

            NpcEntity nearProjection=
                byCanonical(
                    near,
                    canonical.id
                );

            if(nearProjection==null)
                throw new AssertionError(
                    "near viewer did not project canonical NPC"
                );

            int farX=canonical.x()+64;
            int farY=canonical.y()+64;

            List<NpcEntity> far=
                farView.bootstrapNpcs(
                    farX,
                    farY
                );

            if(byCanonical(far,canonical.id)!=null)
                throw new AssertionError(
                    "far viewer projected out-of-range canonical NPC"
                );

            int canonicalCount=
                world.npcs().size();

            if(!world.unregisterPlayer(firstViewer))
                throw new AssertionError(
                    "first viewer unregister failed"
                );

            if(world.players().byId(firstViewer.id())!=null)
                throw new AssertionError(
                    "first viewer remained registered"
                );

            if(world.npcs().byId(canonical.id)!=canonical)
                throw new AssertionError(
                    "disconnect deleted/replaced shared canonical NPC"
                );

            if(world.npcs().size()!=canonicalCount)
                throw new AssertionError(
                    "disconnect changed canonical NPC population"
                );

            List<NpcEntity> secondNear=
                farView.bootstrapNpcs(
                    canonical.x(),
                    canonical.y()
                );

            NpcEntity secondProjection=
                byCanonical(
                    secondNear,
                    canonical.id
                );

            if(secondProjection==null)
                throw new AssertionError(
                    "remaining viewer cannot project canonical NPC after peer disconnect"
                );

            if(secondProjection==nearProjection)
                throw new AssertionError(
                    "viewer-local projections unexpectedly share object identity"
                );

            if(!canonical.id.equals(
                    secondProjection.canonicalId()))
                throw new AssertionError(
                    "remaining viewer projection lost canonical identity"
                );

            Integer firstScene=
                nearView.sceneIndexForCanonical(
                    canonical.id
                );
            Integer secondScene=
                farView.sceneIndexForCanonical(
                    canonical.id
                );

            if(firstScene==null||
               secondScene==null)
                throw new AssertionError(
                    "viewer-local scene mappings missing"
                );

            int expectedScene=
                HomeNpcRuntimePlan.sceneIndexForOrdinal(
                    spawn.ordinal
                );

            if(firstScene.intValue()!=expectedScene||
               secondScene.intValue()!=expectedScene)
                throw new AssertionError(
                    "HOME scene-index compatibility changed"
                );

            if(!world.unregisterPlayer(secondViewer))
                throw new AssertionError(
                    "second viewer unregister failed"
                );

            if(world.npcs().byId(canonical.id)!=canonical)
                throw new AssertionError(
                    "last viewer disconnect deleted shared HOME NPC"
                );

            System.out.println(
                "CANONICAL_NPC_VIEWER_LIFECYCLE_ACCEPTANCE_PASS "+
                "entityId="+canonical.id+
                " scene="+expectedScene+
                " disconnectSafe=true "+
                " spatialFilter=true "+
                " viewerProjectionLocal=true"
            );
        }finally{
            world.close();
        }
    }

    private static NpcEntity byCanonical(
        List<NpcEntity> projections,
        EntityId id
    ){
        for(NpcEntity npc:projections)
            if(id.equals(npc.canonicalId()))
                return npc;
        return null;
    }
}
