package spk.local;

import java.io.*;

public final class WorldPetNpcOwnershipTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer owner=new WorldPlayer();
            world.registerPlayer(owner,"pet-owner");

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(
                    dev,
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

            PetDefinitionRepository.Def mainDef=
                PetDefinitionRepository.get(22519);
            if(mainDef==null)
                throw new AssertionError(
                    "missing main pet definition 22519"
                );

            String spawn=
                npcs.spawnPet(
                    mainDef,
                    movement,
                    writer
                );
            if(!spawn.startsWith("PET_SPAWN_OK"))
                throw new AssertionError(spawn);

            NpcEntity localMain=npcs.pet();
            WorldNpc canonicalMain=
                world.petNpcs().main(owner.id());

            if(localMain==null||canonicalMain==null)
                throw new AssertionError(
                    "main pet not present in both view and World"
                );

            if(canonicalMain.definitionId!=localMain.definitionId||
               canonicalMain.sourceItemId!=mainDef.itemId||
               canonicalMain.x()!=localMain.x||
               canonicalMain.y()!=localMain.y||
               !owner.id().equals(canonicalMain.ownerId))
                throw new AssertionError(
                    "canonical main pet does not match owner presentation"
                );

            EntityId originalMainId=canonicalMain.id;

            PetState state=owner.petState();
            state.activate(mainDef);

            MiniPetService miniService=
                owner.miniPets();

            String configured=
                miniService.configure(
                    23629,
                    state,
                    npcs,
                    movement,
                    writer
                );

            if(!configured.startsWith("MINIPET_CONFIGURED"))
                throw new AssertionError(configured);

            NpcEntity localMini=npcs.miniPet();
            WorldNpc canonicalMini=
                world.petNpcs().mini(owner.id());

            if(localMini==null||canonicalMini==null)
                throw new AssertionError(
                    "mini pet not present in both view and World"
                );

            if(canonicalMini.definitionId!=localMini.definitionId||
               canonicalMini.sourceItemId!=23629||
               canonicalMini.x()!=localMini.x||
               canonicalMini.y()!=localMini.y||
               !owner.id().equals(canonicalMini.ownerId))
                throw new AssertionError(
                    "canonical mini pet does not match owner presentation"
                );

            if(canonicalMini.id.equals(canonicalMain.id))
                throw new AssertionError(
                    "main and mini share canonical identity"
                );

            movement.enterTransientRegion(
                3090,
                3495,
                0,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

            String snapped=
                npcs.devSnapToOwner(
                    movement,
                    writer
                );

            if(!snapped.startsWith("PET_TELEPORT_TO_OWNER"))
                throw new AssertionError(snapped);

            canonicalMain=
                world.petNpcs().main(owner.id());

            if(canonicalMain==null||
               !canonicalMain.id.equals(originalMainId))
                throw new AssertionError(
                    "main pet canonical identity changed on movement"
                );

            if(canonicalMain.x()!=npcs.pet().x||
               canonicalMain.y()!=npcs.pet().y)
                throw new AssertionError(
                    "canonical main position drifted from owner view"
                );

            String miniRemoved=npcs.removeMiniPet(writer);
            if(!miniRemoved.startsWith("MINIPET_DESPAWN_OK"))
                throw new AssertionError(miniRemoved);

            if(world.petNpcs().mini(owner.id())!=null||
               world.petNpcs().main(owner.id())==null)
                throw new AssertionError(
                    "mini removal disturbed canonical main ownership"
                );

            miniService.onMainPetSpawn(
                state,
                npcs,
                movement,
                writer
            );

            if(world.petNpcs().mini(owner.id())==null)
                throw new AssertionError(
                    "configured mini was not restored canonically"
                );

            String removed=npcs.removePet(writer);
            if(!removed.startsWith("PET_DESPAWN_OK"))
                throw new AssertionError(removed);

            if(world.petNpcs().main(owner.id())!=null||
               world.petNpcs().mini(owner.id())!=null)
                throw new AssertionError(
                    "pet removal left canonical owned actors"
                );

            npcs.spawnPet(mainDef,movement,writer);
            if(world.petNpcs().main(owner.id())==null)
                throw new AssertionError(
                    "main pet did not respawn canonically"
                );

            world.unregisterPlayer(owner);

            if(world.petNpcs().ownerActorCount(owner.id())!=0)
                throw new AssertionError(
                    "player unregister leaked canonical pet actors"
                );

            System.out.println(
                "WORLD_PET_NPC_OWNERSHIP_PASS "+
                "owner="+owner.id()+
                " mainIdentityStableOnMove=true"+
                " miniOwned=true"+
                " scenePresentationUnchanged="+NpcRegistry.PET_INDEX+
                " disconnectCleanup=true"
            );
        }finally{
            world.close();
        }
    }
}
