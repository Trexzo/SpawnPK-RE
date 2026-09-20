package spk.local;

import java.util.List;

public final class WorldNpcRegistryTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer owner=new WorldPlayer();
            long generation=
                world.registerPlayer(owner,"npc-owner");

            if(generation<=0L)
                throw new AssertionError(
                    "player generation not assigned"
                );

            WorldNpc shared=
                world.npcs().spawn(
                    1488,
                    3081,
                    3491,
                    0
                );

            WorldNpc pet=
                world.npcs().spawnOwned(
                    8330,
                    3080,
                    3491,
                    0,
                    owner.id(),
                    13045
                );

            if(shared.id.equals(owner.id())||
               pet.id.equals(owner.id())||
               shared.id.equals(pet.id()))
                throw new AssertionError(
                    "canonical entity IDs collided"
                );

            if(world.npcs().byId(shared.id)!=shared)
                throw new AssertionError(
                    "registry did not preserve canonical instance"
                );

            if(world.npcs().byId(pet.id)!=pet)
                throw new AssertionError(
                    "owned NPC canonical instance missing"
                );

            List<WorldNpc> owned=
                world.npcs().ownedBy(owner.id());

            if(owned.size()!=1||owned.get(0)!=pet)
                throw new AssertionError(
                    "owner metadata lookup changed"
                );

            WorldNpc moved=
                world.npcs().move(
                    shared.id,
                    3082,
                    3492,
                    0
                );

            if(moved!=shared)
                throw new AssertionError(
                    "move replaced canonical NPC identity"
                );

            if(shared.x()!=3082||
               shared.y()!=3492||
               shared.plane()!=0)
                throw new AssertionError(
                    "canonical NPC move state not applied"
                );

            if(world.npcs().snapshot().size()!=2)
                throw new AssertionError(
                    "unexpected canonical NPC count"
                );

            if(!world.npcs().remove(shared.id))
                throw new AssertionError(
                    "canonical NPC removal failed"
                );

            if(world.npcs().byId(shared.id)!=null)
                throw new AssertionError(
                    "removed NPC still addressable"
                );

            if(world.players().byId(owner.id())!=owner)
                throw new AssertionError(
                    "NPC lifecycle disturbed player ownership"
                );

            if(world.npcs().size()!=1)
                throw new AssertionError(
                    "owned NPC should remain after unrelated removal"
                );

            System.out.println(
                "WORLD_NPC_REGISTRY_PASS "+
                "playerId="+owner.id()+
                " petId="+pet.id+
                " canonical=true sceneIndexFree=true"
            );
        }finally{
            world.close();
        }
    }
}
