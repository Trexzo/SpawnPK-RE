package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class PetSwitchColorContentCommandActionOwnershipTest {
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
                    "petswitchcolor"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "petswitchcolor binding="+binding
            );

            world.registerPlayer(
                player,
                "petswitchcolor-content-owner"
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
                    "::petswitchcolor",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .PET_SWITCH_COLOR_ACTION_PREFIX+
                    ":-1",
                "default request"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::petswitchcolor 24016",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .PET_SWITCH_COLOR_ACTION_PREFIX+
                    ":24016",
                "explicit request"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::petswitchcolor nope",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .PET_SWITCH_COLOR_ACTION_PREFIX+
                    ":-1",
                "invalid request"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::petswitchcolor 24017 ignored",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .PET_SWITCH_COLOR_ACTION_PREFIX+
                    ":24017",
                "legacy extra args ignored"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "petswitchcolor content policy emitted wire bytes="+
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

            LocalContentCommandActionExecutor.Outcome rejected=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .PET_SWITCH_COLOR_ACTION_PREFIX+
                    ":24016",
                    "::petswitchcolor 24016",
                    "petswitchcolor-content-owner",
                    effectPackets
                );

            require(
                rejected!=null&&
                rejected.contentResult==null&&
                rejected.dialogResult!=null&&
                rejected.dialogResult.logText.contains(
                    "REJECTED_NO_VARIANT_IN_INVENTORY"),
                "rejected dialog outcome="+
                rejected
            );

            int beforeMalformed=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome malformed=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .PET_SWITCH_COLOR_ACTION_PREFIX+
                    ":24016:extra",
                    "::petswitchcolor 24016 extra",
                    "petswitchcolor-content-owner",
                    effectPackets
                );

            require(
                malformed!=null&&
                malformed.dialogResult==null&&
                malformed.contentResult!=null&&
                malformed.contentResult.logText()
                    .contains(
                        "REJECTED_UNSUPPORTED"),
                "malformed action outcome="+
                malformed
            );

            require(
                effectWire.size()==beforeMalformed,
                "malformed petswitchcolor action emitted wire"
            );

            player.bank()
                .spawnItem(
                    24016,
                    1,
                    effectPackets
                );

            int beforeOpen=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome opened=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .PET_SWITCH_COLOR_ACTION_PREFIX+
                    ":24016",
                    "::petswitchcolor 24016",
                    "petswitchcolor-content-owner",
                    effectPackets
                );

            effectPackets.flush();

            require(
                opened!=null&&
                opened.contentResult==null&&
                opened.dialogResult!=null&&
                opened.dialogResult.keyAction==
                    LocalPetInventoryDialogHandler
                        .KeyAction.PUBLISH_2482_2485&&
                opened.dialogResult.logText.contains(
                    "result=DIALOG_OPEN current=24016"),
                "open dialog outcome="+
                opened
            );

            require(
                effectWire.size()>beforeOpen,
                "petswitchcolor dialog emitted no wire"
            );

            runtimeBoundary();

            System.out.println(
                "PET_SWITCH_COLOR_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "defaultRequest=true "+
                "invalidRequest=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "internalDialogOutcome=true "+
                "rejectedDialogResult=true "+
                "malformedFailClosed=true "+
                "dialogOpen=true "+
                "dialogWire=true "+
                "legacyParser=false "+
                "dispatcherBridge=true "+
                "publicApiExpanded=false"
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
        for(Method method:
                LocalPetCompatibilityCommandHandler.class
                    .getDeclaredMethods())
            require(
                !"handle".equals(
                    method.getName()),
                "raw pet compatibility parser remains"
            );

        for(Method method:
                ContentResult.class
                    .getDeclaredMethods())
            require(
                !method.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    )
                    .contains(
                        "dialog"),
                "dialog outcome leaked into public ContentResult"
            );

        try{
            String source=
                new String(
                    java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get(
                            "server/src/spk/local/LocalCommandDispatcher.java"
                        )
                    ),
                    java.nio.charset.StandardCharsets.UTF_8
                );

            require(
                source.contains(
                    "contentCommandActions.executeOutcome(")&&
                source.contains(
                    "bridge.applyPetDialog("),
                "dispatcher lacks semantic dialog action bridge"
            );

            require(
                !source.contains(
                    "petCompatibilityCommands.handle("),
                "dispatcher raw pet compatibility fallback remains"
            );
        }catch(java.io.IOException error){
            throw new AssertionError(
                "dispatcher source audit failed",
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
                "petswitchcolor content command failed "+
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
                new int[]{251,252,253,254}
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

    private PetSwitchColorContentCommandActionOwnershipTest(){}
}
