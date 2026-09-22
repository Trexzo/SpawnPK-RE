package spk.local;

import java.io.ByteArrayOutputStream;

public final class TransientRegionPetViewTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);

        try{
            WorldPlayer player=new WorldPlayer();
            world.registerPlayer(player,"opensrc");

            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(27340);

            if(def==null||def.npcId!=8124)
                throw new AssertionError(
                    "expected runtime-proven pet 27340->8124"
                );

            player.petState().activate(def);

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();

            NpcRegistry npcs=
                new NpcRegistry(
                    dev,
                    world.petNpcs(),
                    player.id()
                );

            HomeWorldRuntimePlan home=
                new HomeWorldRuntimePlan(
                    world.homeNpcs()
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();

            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{91,92,93,94}
                    )
                );

            MovementState movement=
                player.movement();

            npcs.bootstrapHome(
                writer,
                movement,
                player.petState(),
                home
            );

            int homeVisible=npcs.visibleCount();
            NpcEntity originalPet=npcs.pet();

            if(homeVisible<=1||originalPet==null)
                throw new AssertionError(
                    "HOME fixture missing NPCs/pet visible="+
                    homeVisible
                );

            int removed=
                npcs.detachRegionViewPreservingFollowers(
                    writer
                );

            if(removed<=0||
               npcs.visibleCount()!=1||
               npcs.pet()!=originalPet)
                throw new AssertionError(
                    "transient detach mismatch removed="+
                    removed+
                    " visible="+
                    npcs.visibleCount()+
                    " samePet="+
                    (npcs.pet()==originalPet)
                );

            movement.rebaseLoadedWindow(
                3040,
                3480,
                true
            );

            String despawn=
                npcs.removePet(writer);

            if(!despawn.startsWith("PET_DESPAWN_OK")||
               npcs.visibleCount()!=0)
                throw new AssertionError(
                    "transient pet remove retained stale NPC view "+
                    despawn+
                    " visible="+
                    npcs.visibleCount()
                );

            player.petState().clear();

            String spawn=
                npcs.spawnPet(
                    def,
                    movement,
                    writer
                );

            if(!spawn.startsWith("PET_SPAWN_OK")||
               npcs.visibleCount()!=1||
               npcs.pet()==null)
                throw new AssertionError(
                    "transient pet replacement failed "+
                    spawn+
                    " visible="+
                    npcs.visibleCount()
                );

            player.petState().activate(def);

            movement.restoreHomeWindowAtCurrentPosition();

            int homeAdded=
                npcs.reattachHomeView(
                    writer,
                    movement,
                    home
                );

            if(homeAdded<=0||
               npcs.visibleCount()<=1||
               npcs.pet()==null)
                throw new AssertionError(
                    "HOME reattach mismatch added="+
                    homeAdded+
                    " visible="+
                    npcs.visibleCount()
                );

            System.out.println(
                "TRANSIENT_REGION_PET_VIEW_PASS "+
                "homeRemoved="+removed+
                " followerPreserved=true "+
                "transientReplaceSafe=true "+
                "homeAdded="+homeAdded+
                " staleHomeRetains=false"
            );
        }finally{
            world.close();
        }
    }

    private TransientRegionPetViewTest(){}
}
