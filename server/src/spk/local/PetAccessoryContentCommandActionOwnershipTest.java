package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class PetAccessoryContentCommandActionOwnershipTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        try{
            ContentRegistry registry=
                world.content();

            ContentRegistry.BindingInfo binding=
                registry.commandBinding(
                    "petaccessory"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "petaccessory binding="+binding
            );

            world.registerPlayer(
                player,
                "petaccessory-content-owner"
            );
            world.start();

            ByteArrayOutputStream policyWire=
                new ByteArrayOutputStream();
            ServerPacketWriter policyPackets=
                writer(
                    policyWire
                );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::petaccessory",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .PET_ACCESSORY_STATUS_ACTION,
                "default status"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::petaccessory status",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .PET_ACCESSORY_STATUS_ACTION,
                "status"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::petaccessory unexpected",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .PET_ACCESSORY_STATUS_ACTION,
                "legacy unknown-status fallback"
            );

            for(String alias:
                    new String[]{
                        "off",
                        "none",
                        "disable"
                    })
                assertAction(
                    dispatch(
                        world,
                        player,
                        registry,
                        "::petaccessory "+alias,
                        policyPackets
                    ),
                    LocalLabCoreContentModule
                        .PET_ACCESSORY_OFF_ACTION,
                    alias
                );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "petaccessory content policy emitted wire bytes="+
                policyWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);

            LocalPetInventoryDialogHandler dialogs=
                new LocalPetInventoryDialogHandler(
                    player.bank(),
                    player.miniPets(),
                    player.petState(),
                    npcs,
                    player.movement(),
                    player.petAccessoryState()
                );

            LocalPetCompatibilityCommandHandler
                compatibility=
                    new LocalPetCompatibilityCommandHandler(
                        player.petAccessoryState(),
                        npcs,
                        player.movement(),
                        dialogs
                    );

            LocalContentCommandActionExecutor executor=
                new LocalContentCommandActionExecutor(
                    new LocalCosmeticCommandHandler(
                        player.bank(),
                        player.equipment(),
                        player.playerState(),
                        new PlayerPresentationService(
                            dev
                        )
                    ),
                    new LocalCompColorsCommandHandler(
                        player.playerState(),
                        player.equipment(),
                        new PlayerPresentationService(
                            dev
                        )
                    ),
                    new LocalMiniPetCommandHandler(
                        player.miniPets(),
                        player.petState(),
                        npcs,
                        player.movement()
                    ),
                    compatibility
                );

            ByteArrayOutputStream effectWire=
                new ByteArrayOutputStream();
            ServerPacketWriter effectPackets=
                writer(
                    effectWire
                );

            ContentResult emptyStatus=
                executor.execute(
                    LocalLabCoreContentModule
                        .PET_ACCESSORY_STATUS_ACTION,
                    "::petaccessory status",
                    "petaccessory-content-owner",
                    effectPackets
                );

            require(
                emptyStatus!=null&&
                emptyStatus.saveReason()==null&&
                emptyStatus.logText().equals(
                    "V5128_PET_ACCESSORY active=NONE "+
                    "visualSelectorMapping=UNRESOLVED_FAIL_CLOSED"
                ),
                "empty accessory status="+
                emptyStatus
            );

            player.petAccessoryState()
                .setActiveItem(
                    20542
                );

            ContentResult activeStatus=
                executor.execute(
                    LocalLabCoreContentModule
                        .PET_ACCESSORY_STATUS_ACTION,
                    "::petaccessory status",
                    "petaccessory-content-owner",
                    effectPackets
                );

            require(
                activeStatus!=null&&
                activeStatus.saveReason()==null&&
                activeStatus.logText().contains(
                    "active=20542/White pet accessory"),
                "active accessory status="+
                activeStatus
            );

            ContentResult off=
                executor.execute(
                    LocalLabCoreContentModule
                        .PET_ACCESSORY_OFF_ACTION,
                    "::petaccessory off",
                    "petaccessory-content-owner",
                    effectPackets
                );

            require(
                off!=null&&
                "PET_ACCESSORY_DEV_OFF".equals(
                    off.saveReason())&&
                off.logText().contains(
                    "V5128_PET_ACCESSORY active=NONE")&&
                player.petAccessoryState()
                    .activeItem()==0,
                "accessory off="+off
            );

            int beforeUnknown=
                effectWire.size();

            ContentResult unknown=
                executor.execute(
                    "locallab.petaccessory.activate:20542",
                    "::petaccessory activate 20542",
                    "petaccessory-content-owner",
                    effectPackets
                );

            require(
                unknown!=null&&
                unknown.saveReason()==null&&
                unknown.logText().contains(
                    "REJECTED_UNSUPPORTED"),
                "unsupported accessory action="+
                unknown
            );

            require(
                effectWire.size()==beforeUnknown&&
                player.petAccessoryState()
                    .activeItem()==0,
                "unsupported accessory action mutated runtime"
            );

            LocalPetInventoryDialogHandler.Result
                color=
                    compatibility.switchColor(
                        24016,
                        effectPackets
                    );

            require(
                color!=null&&
                color.logText.contains(
                    "V5128_SCOOBY_SWITCH_COLOR"),
                "petswitchcolor runtime effect boundary lost"
            );

            runtimeBoundary();

            System.out.println(
                "PET_ACCESSORY_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "aliases=true "+
                "legacyUnknownStatus=true "+
                "policyWireBytes=0 "+
                "statusRuntime=true "+
                "offRuntime=true "+
                "offSave=true "+
                "unsupportedFailClosed=true "+
                "legacyAccessoryParser=false "+
                "petSwitchColorRuntimeOwned=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static void runtimeBoundary(){
        try{
            String source=
                new String(
                    java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get(
                            "server/src/spk/local/LocalPetCompatibilityCommandHandler.java"
                        )
                    ),
                    java.nio.charset.StandardCharsets.UTF_8
                );

            require(
                !source.contains(
                    "equalsIgnoreCase(\"petaccessory\")"),
                "legacy petaccessory parser remains in runtime handler"
            );

            require(
                !source.contains(
                    "equalsIgnoreCase(\"petswitchcolor\")"),
                "petswitchcolor raw runtime parser remains"
            );

            require(
                source.contains(
                    "switchColor("),
                "petswitchcolor runtime effect missing"
            );
        }catch(java.io.IOException error){
            throw new AssertionError(
                "pet compatibility source audit failed",
                error
            );
        }
    }

    private static ContentResult dispatch(
        World world,
        WorldPlayer player,
        ContentRegistry registry,
        String command,
        ServerPacketWriter packets
    )throws Exception{
        AtomicReference<ContentResult>
            result=new AtomicReference<>();
        AtomicReference<Throwable>
            failure=new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    result.set(
                        registry.dispatchCommand(
                            player,
                            command,
                            packets
                        )
                    );
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "petaccessory content command failed "+
                command,
                failure.get()
            );

        return result.get();
    }

    private static void assertAction(
        ContentResult result,
        String actionKey,
        String label
    ){
        require(
            result!=null&&
            result.hasAction()&&
            actionKey.equals(
                result.actionKey())&&
            result.saveReason()==null,
            label+
            " action result="+
            result
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{231,232,233,234}
            )
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private PetAccessoryContentCommandActionOwnershipTest(){}
}
