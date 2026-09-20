package spk.local;

import java.io.IOException;

/**
 * Session-local developer workbench info/reset coordinator.
 *
 * This owns only LocalLab developer state cleanup and presentation refresh.
 * Nothing here is persisted or promoted into recovered SpawnPK gameplay
 * authority.
 */
final class LocalDevSessionCommandHandler {
    private final World world;
    private final DevAuthorityWorkbench dev;
    private final NpcRegistry npcs;
    private final PlayerPresentationService playerPresentation;
    private final EquipmentState equipment;
    private final PlayerState playerState;
    private final BankState bank;
    private final PetState petState;
    private final MovementState movement;

    LocalDevSessionCommandHandler(
        World world,
        DevAuthorityWorkbench dev,
        NpcRegistry npcs,
        PlayerPresentationService playerPresentation,
        EquipmentState equipment,
        PlayerState playerState,
        BankState bank,
        PetState petState,
        MovementState movement
    ){
        this.world=java.util.Objects.requireNonNull(world,"world");
        this.dev=java.util.Objects.requireNonNull(dev,"dev");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.playerPresentation=java.util.Objects.requireNonNull(
            playerPresentation,"playerPresentation");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.playerState=java.util.Objects.requireNonNull(playerState,"playerState");
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.petState=java.util.Objects.requireNonNull(petState,"petState");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
    }

    String handle(
        String[] p,
        String username,
        SceneUpdatePublisher scenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        if(p==null||
           p.length<1||
           !p[0].equalsIgnoreCase("dev")){
            return null;
        }

        String sub=
            p.length>=2
                ?p[1].toLowerCase(java.util.Locale.ROOT)
                :"info";

        if(sub.equals("reset")){
            return "V511_DEV_RESET "+
                reset(
                    username,
                    scenePublisher,
                    writer,
                    false
                );
        }

        return "V592_DEV_INFO "+dev.summary();
    }

    String resetForPanel(
        String username,
        SceneUpdatePublisher scenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        return reset(
            username,
            scenePublisher,
            writer,
            true
        );
    }

    private String reset(
        String username,
        SceneUpdatePublisher scenePublisher,
        ServerPacketWriter writer,
        boolean clearVisualOverrides
    )throws IOException{
        boolean hadMorph=
            dev.playerNpcTransformId()!=null;

        String npcResult=
            npcs.devRemoveAllNpcs(writer);
        String worldResult=
            resetDevWorld(scenePublisher);

        dev.resetAll();

        if(clearVisualOverrides){
            LocalDevVisualOverrideStore.clear();
        }

        if(hadMorph){
            playerPresentation.refresh(
                username,
                equipment,
                playerState,
                writer);
        }

        String petReset="none";
        if(npcs.pet()!=null&&petState.active()){
            petReset=npcs.previewPetDefinition(
                petState.npcId(),
                movement,
                writer);
        }

        String inventory=
            bank.restoreDevInventoryPreview(writer);

        return "workbench="+dev.summary()+
            " inventory="+inventory+
            " pet="+petReset+
            " devNpcs="+npcResult+
            " devWorld="+worldResult+
            " playerRefresh="+hadMorph+
            " persisted=false";
    }

    private String resetDevWorld(
        SceneUpdatePublisher scenePublisher
    )throws IOException{
        int ground=0;
        int objects=0;

        for(GroundItem item:world.groundItems().removeDevOwned()){
            try{
                scenePublisher.groundRemove(item);
                ground++;
            }catch(IllegalArgumentException ignored){}
        }

        for(WorldObject object:world.objects().removeDevOwned()){
            try{
                scenePublisher.objectRemove(
                    object.tile,
                    object.shape,
                    object.rotation);
                objects++;
            }catch(IllegalArgumentException ignored){}
        }

        return "DEV_WORLD_RESET ground="+ground+
            " objects="+objects;
    }
}
