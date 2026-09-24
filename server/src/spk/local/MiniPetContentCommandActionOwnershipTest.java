package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class MiniPetContentCommandActionOwnershipTest {
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
                    "minipet"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "minipet binding="+binding
            );

            world.registerPlayer(
                player,
                "minipet-content-owner"
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
                    "::minipet",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .MINIPET_STATUS_ACTION,
                "default status"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::minipet info",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .MINIPET_STATUS_ACTION,
                "info alias"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::minipet off",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .MINIPET_OFF_ACTION,
                "off"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::minipet disable",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .MINIPET_OFF_ACTION,
                "disable alias"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::minipet set 22088",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .MINIPET_SET_ACTION_PREFIX+
                    ":22088",
                "set"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::minipet set nope",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .MINIPET_SET_ACTION_PREFIX+
                    ":-1",
                "invalid item parse"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::minipet set",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .MINIPET_HELP_ACTION,
                "missing set item help"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::minipet unexpected",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .MINIPET_HELP_ACTION,
                "help fallback"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "minipet content policy emitted wire bytes="+
                policyWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);

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
                    new LocalPetCompatibilityCommandHandler(
                        player.petAccessoryState(),
                        npcs,
                        player.movement(),
                        new LocalPetInventoryDialogHandler(
                            player.bank(),
                            player.miniPets(),
                            player.petState(),
                            npcs,
                            player.movement(),
                            player.petAccessoryState()
                        )
                    )
                );

            ByteArrayOutputStream effectWire=
                new ByteArrayOutputStream();
            ServerPacketWriter effectPackets=
                writer(
                    effectWire
                );

            ContentResult status=
                executor.execute(
                    LocalLabCoreContentModule
                        .MINIPET_STATUS_ACTION,
                    "::minipet status",
                    "minipet-content-owner",
                    effectPackets
                );

            require(
                status!=null&&
                status.saveReason()==null&&
                status.logText().startsWith(
                    "V511_MINIPET_STATUS"),
                "status result="+status
            );

            ContentResult configured=
                executor.execute(
                    LocalLabCoreContentModule
                        .MINIPET_SET_ACTION_PREFIX+
                    ":22088",
                    "::minipet set 22088",
                    "minipet-content-owner",
                    effectPackets
                );

            require(
                configured!=null&&
                "MINIPET_SET_DEV".equals(
                    configured.saveReason())&&
                configured.logText().contains(
                    "MINIPET_CONFIGURED item=22088")&&
                player.petState()
                    .miniItemId()==22088,
                "configure result="+configured
            );

            ContentResult rejected=
                executor.execute(
                    LocalLabCoreContentModule
                        .MINIPET_SET_ACTION_PREFIX+
                    ":999999",
                    "::minipet set 999999",
                    "minipet-content-owner",
                    effectPackets
                );

            require(
                rejected!=null&&
                rejected.saveReason()==null&&
                rejected.logText().contains(
                    "REJECTED_NOT_MINI_PET"),
                "rejected configure="+rejected
            );

            int beforeMalformed=
                effectWire.size();

            ContentResult malformed=
                executor.execute(
                    LocalLabCoreContentModule
                        .MINIPET_SET_ACTION_PREFIX+
                    ":22088:extra",
                    "::minipet set 22088 extra",
                    "minipet-content-owner",
                    effectPackets
                );

            require(
                malformed!=null&&
                malformed.saveReason()==null&&
                malformed.logText().contains(
                    "REJECTED_UNSUPPORTED"),
                "malformed set action="+malformed
            );

            require(
                effectWire.size()==beforeMalformed,
                "malformed minipet action emitted wire"
            );

            ContentResult disabled=
                executor.execute(
                    LocalLabCoreContentModule
                        .MINIPET_OFF_ACTION,
                    "::minipet off",
                    "minipet-content-owner",
                    effectPackets
                );

            require(
                disabled!=null&&
                "MINIPET_OFF".equals(
                    disabled.saveReason())&&
                disabled.logText().contains(
                    "MINIPET_DISABLED")&&
                !player.petState()
                    .miniConfigured(),
                "off result="+disabled
            );

            ContentResult help=
                executor.execute(
                    LocalLabCoreContentModule
                        .MINIPET_HELP_ACTION,
                    "::minipet help",
                    "minipet-content-owner",
                    effectPackets
                );

            require(
                help!=null&&
                help.saveReason()==null&&
                help.logText().contains(
                    "nativeInventoryAction=Configure/C2S122"),
                "help result="+help
            );

            runtimeBoundary();

            System.out.println(
                "MINIPET_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "aliases=true "+
                "setItemSemantic=true "+
                "policyWireBytes=0 "+
                "allowlist=true "+
                "malformedFailClosed=true "+
                "configureState=true "+
                "invalidNoSave=true "+
                "offSave=true "+
                "helpRuntimeOwned=true "+
                "legacyParser=false "+
                "dispatcherFallback=false"
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
                LocalMiniPetCommandHandler.class
                    .getDeclaredMethods())
            require(
                !"handle".equals(
                    method.getName()),
                "legacy minipet parser remains"
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
                !source.contains(
                    "miniPetCommands.handle("),
                "dispatcher direct minipet fallback remains"
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
                "minipet content command failed "+
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
                new int[]{221,222,223,224}
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

    private MiniPetContentCommandActionOwnershipTest(){}
}
