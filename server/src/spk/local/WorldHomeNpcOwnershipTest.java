package spk.local;

import java.util.*;

public final class WorldHomeNpcOwnershipTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            HomeWorldRuntimePlan first=
                new HomeWorldRuntimePlan(
                    world.homeNpcs()
                );
            HomeWorldRuntimePlan second=
                new HomeWorldRuntimePlan(
                    world.homeNpcs()
                );

            HomeNpcSpawnRepository.Spawn wanderer=
                chooseWanderer();

            int playerX=wanderer.anchorX;
            int playerY=wanderer.anchorY;
            int sceneIndex=
                HomeNpcRuntimePlan.sceneIndexForOrdinal(
                    wanderer.ordinal
                );

            List<NpcEntity> firstBootstrap=
                first.bootstrapNpcs(
                    playerX,
                    playerY
                );

            int canonicalCountAfterFirst=
                world.npcs().size();

            List<NpcEntity> secondBootstrap=
                second.bootstrapNpcs(
                    playerX,
                    playerY
                );

            int expectedCanonical=
                HomeNpcSpawnRepository.defaultReplay().size();

            if(canonicalCountAfterFirst!=expectedCanonical)
                throw new AssertionError(
                    "first HOME projection did not initialize one canonical population: "+
                    canonicalCountAfterFirst+
                    " expected="+expectedCanonical
                );

            if(world.npcs().size()!=canonicalCountAfterFirst)
                throw new AssertionError(
                    "second viewer duplicated canonical HOME NPCs"
                );

            WorldNpc canonical=
                world.homeNpcs().canonicalForOrdinal(
                    wanderer.ordinal
                );

            if(canonical==null)
                throw new AssertionError(
                    "canonical wanderer missing"
                );

            NpcEntity firstView=
                byScene(firstBootstrap,sceneIndex);
            NpcEntity secondView=
                byScene(secondBootstrap,sceneIndex);

            if(firstView==null||secondView==null)
                throw new AssertionError(
                    "wanderer was not visible to both viewers"
                );

            if(firstView==secondView)
                throw new AssertionError(
                    "viewer packet projections must remain viewer-local objects"
                );

            if(!canonical.id.equals(firstView.canonicalId())||
               !canonical.id.equals(secondView.canonicalId()))
                throw new AssertionError(
                    "HOME projections lost canonical identity"
                );

            if(firstView.x!=canonical.x()||
               firstView.y!=canonical.y()||
               secondView.x!=canonical.x()||
               secondView.y!=canonical.y())
                throw new AssertionError(
                    "viewer projections do not reflect canonical HOME position"
                );

            if(!Integer.valueOf(sceneIndex).equals(
                    first.sceneIndexForCanonical(canonical.id))||
               !Integer.valueOf(sceneIndex).equals(
                    second.sceneIndexForCanonical(canonical.id)))
                throw new AssertionError(
                    "viewer-local canonical ID mapping did not preserve HOME scene index"
                );

            int cadence=
                HomeNpcWanderRepository.cadenceTicks(
                    wanderer.ordinal
                );

            if(cadence<=0||
               cadence==Integer.MAX_VALUE)
                throw new AssertionError(
                    "test wanderer has no finite cadence"
                );

            HomeWorldRuntimePlan.NpcDelta firstTick=
                first.tick(
                    cadence,
                    playerX,
                    playerY
                );

            int afterFirstX=canonical.x();
            int afterFirstY=canonical.y();

            Integer firstDirection=
                firstTick.walkDirection(
                    sceneIndex
                );

            if(firstDirection==null)
                throw new AssertionError(
                    "canonical HOME wander did not publish first viewer movement"
                );

            HomeWorldRuntimePlan.NpcDelta secondTick=
                second.tick(
                    cadence,
                    playerX,
                    playerY
                );

            Integer secondDirection=
                secondTick.walkDirection(
                    sceneIndex
                );

            if(secondDirection==null||
               !secondDirection.equals(firstDirection))
                throw new AssertionError(
                    "second viewer did not project the same canonical movement"
                );

            if(canonical.x()!=afterFirstX||
               canonical.y()!=afterFirstY)
                throw new AssertionError(
                    "same World tick advanced canonical HOME NPC twice"
                );

            if(world.npcs().byId(canonical.id)!=canonical)
                throw new AssertionError(
                    "HOME movement replaced canonical NPC identity"
                );

            if(first.trackedWorldNpcCount()!=expectedCanonical||
               second.trackedWorldNpcCount()!=expectedCanonical)
                throw new AssertionError(
                    "viewer plans disagree on canonical HOME population count"
                );

            System.out.println(
                "WORLD_HOME_NPC_OWNERSHIP_PASS "+
                "canonicalCount="+expectedCanonical+
                " ordinal="+wanderer.ordinal+
                " scene="+sceneIndex+
                " entityId="+canonical.id+
                " moveDir="+firstDirection+
                " position="+canonical.x()+","+canonical.y()+
                " oneAdvancePerWorldTick=true"
            );
        }finally{
            world.close();
        }
    }

    private static HomeNpcSpawnRepository.Spawn chooseWanderer(){
        for(HomeNpcSpawnRepository.Spawn spawn:
            HomeNpcRuntimePlan.observedWanderers()){
            if(!HomeNpcWanderRepository.outgoing(
                spawn.ordinal,
                spawn.anchorX,
                spawn.anchorY
            ).isEmpty())
                return spawn;
        }

        throw new AssertionError(
            "no observed HOME wanderer with anchor edge"
        );
    }

    private static NpcEntity byScene(
        List<NpcEntity> entities,
        int sceneIndex
    ){
        for(NpcEntity npc:entities)
            if(npc.sceneIndex==sceneIndex)
                return npc;
        return null;
    }
}
