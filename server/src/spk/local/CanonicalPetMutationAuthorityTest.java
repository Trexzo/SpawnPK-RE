package spk.local;

import java.io.*;

public final class CanonicalPetMutationAuthorityTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer owner=new WorldPlayer();
            world.registerPlayer(owner,"pet-authority");

            NpcRegistry npcs=
                new NpcRegistry(
                    new DevAuthorityWorkbench(),
                    world.petNpcs(),
                    owner.id()
                );

            MovementState movement=owner.movement();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(new int[]{1,2,3,4})
                );

            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(22519);
            if(def==null)
                throw new AssertionError(
                    "missing pet 22519"
                );

            String spawn=
                npcs.spawnPet(
                    def,
                    movement,
                    writer
                );
            if(!spawn.startsWith("PET_SPAWN_OK"))
                throw new AssertionError(spawn);

            WorldNpc canonical=
                world.petNpcs().main(owner.id());
            NpcEntity projection=npcs.pet();

            if(canonical==null||projection==null)
                throw new AssertionError(
                    "pet missing after spawn"
                );

            EntityId id=canonical.id;
            int originX=canonical.x();
            int originY=canonical.y();

            // Spawn establishes the certified Drop-egress breadcrumb. Clear it as
            // an ordinary route replacement so this regression isolates canonical
            // catch-up authority rather than testing Drop presentation geometry.
            npcs.onOwnerRouteReplaced();

            // Move only the owner three tiles east. The pet's canonical state remains
            // at the original spawn tile and should be the source for catch-up.
            movement.enterTransientRegion(
                originX+3,
                originY,
                0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            // Deliberately corrupt only the viewer projection. A production follow
            // tick must refresh from World before deciding/mutating the route.
            projection.x=originX+20;
            projection.y=originY+20;

            if(canonical.x()!=originX||
               canonical.y()!=originY)
                throw new AssertionError(
                    "projection corruption leaked into canonical state"
                );

            String follow=
                npcs.tickFollow(
                    movement,
                    writer
                );

            if(follow==null)
                throw new AssertionError(
                    "canonical pet did not catch up"
                );

            WorldNpc after=
                world.petNpcs().main(owner.id());
            NpcEntity projectedAfter=npcs.pet();

            if(after==null||
               !id.equals(after.id))
                throw new AssertionError(
                    "canonical identity changed during movement"
                );

            // Existing policy runs two cardinal steps when distance > 2, so a
            // three-tile east gap should end one tile behind the owner.
            if(after.x()!=originX+2||
               after.y()!=originY)
                throw new AssertionError(
                    "follow used stale projection instead of canonical origin: "+
                    after.x()+","+after.y()+
                    " expected="+(originX+2)+","+originY
                );

            if(projectedAfter.x!=after.x()||
               projectedAfter.y!=after.y())
                throw new AssertionError(
                    "owner projection did not derive from canonical movement"
                );

            if(!after.id.equals(
                    projectedAfter.canonicalId()))
                throw new AssertionError(
                    "projection lost canonical identity"
                );

            // Corrupt again and prove an external read re-projects canonical state.
            projectedAfter.x=after.x()+9;
            projectedAfter.y=after.y()+9;

            NpcEntity refreshed=npcs.pet();

            if(refreshed.x!=after.x()||
               refreshed.y!=after.y())
                throw new AssertionError(
                    "pet getter did not refresh canonical coordinates"
                );

            System.out.println(
                "CANONICAL_PET_MUTATION_AUTHORITY_PASS "+
                "entity="+after.id+
                " canonicalOrigin="+originX+","+originY+
                " result="+after.x()+","+after.y()+
                " staleProjectionIgnored=true"+
                " worldFirstMovement=true"
            );
        }finally{
            world.close();
        }
    }
}
