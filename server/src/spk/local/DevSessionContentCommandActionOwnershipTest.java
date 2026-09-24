package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class DevSessionContentCommandActionOwnershipTest {
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
                    "dev"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "dev binding="+binding
            );

            world.registerPlayer(
                player,
                "dev-session-content-owner"
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
                    "::dev",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_SESSION_INFO_ACTION,
                "default info"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::dev info",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_SESSION_INFO_ACTION,
                "explicit info"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::dev unexpected",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_SESSION_INFO_ACTION,
                "legacy unknown-info fallback"
            );

            ContentResult resetPolicy=
                dispatch(
                    world,
                    player,
                    registry,
                    "::dev reset",
                    policyPackets
                );

            assertAction(
                resetPolicy,
                LocalLabCoreContentModule
                    .DEV_SESSION_RESET_ACTION,
                "reset"
            );

            ContentResult panel=
                dispatch(
                    world,
                    player,
                    registry,
                    "::dev panel",
                    policyPackets
                );

            require(
                panel==null,
                "dev panel must fall through content route result="+
                panel
            );

            require(
                LocalCommandDispatcher
                    .isDevPanelRoute(
                        new String[]{
                            "dev",
                            "panel"
                        }
                    ),
                "dev panel dispatcher route lost"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "dev content policy emitted wire bytes="+
                policyWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(
                    dev
                );
            LocalDevSessionCommandHandler handler=
                new LocalDevSessionCommandHandler(
                    world,
                    dev,
                    npcs,
                    new PlayerPresentationService(
                        dev
                    ),
                    player.equipment(),
                    player.playerState(),
                    player.bank(),
                    player.petState(),
                    player.movement()
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
                    ),
                    null,
                    null,
                    handler
                );

            ByteArrayOutputStream effectWire=
                new ByteArrayOutputStream();
            ServerPacketWriter effectPackets=
                writer(
                    effectWire
                );
            SceneUpdatePublisher scene=
                new SceneUpdatePublisher(
                    effectPackets,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalContentCommandActionExecutor.Outcome info=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .DEV_SESSION_INFO_ACTION,
                    "::dev info",
                    "opensrc",
                    "localtest",
                    true,
                    scene,
                    effectPackets
                );

            require(
                info!=null&&
                info.dialogResult==null&&
                info.logLines==null&&
                info.contentResult!=null&&
                info.contentResult
                    .saveReason()==null&&
                info.contentResult
                    .logText()
                    .startsWith(
                        "V592_DEV_INFO "),
                "dev info outcome="+info
            );

            int beforeInfoFlush=
                effectWire.size();
            effectPackets.flush();

            require(
                effectWire.size()==beforeInfoFlush,
                "dev info emitted runtime wire"
            );

            LocalDevVisualOverrideStore.clear();
            LocalDevVisualOverrideStore.set(
                "alpha",
                "123"
            );

            LocalContentCommandActionExecutor.Outcome reset=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .DEV_SESSION_RESET_ACTION,
                    "::dev reset",
                    "opensrc",
                    "localtest",
                    true,
                    scene,
                    effectPackets
                );

            effectPackets.flush();

            require(
                reset!=null&&
                reset.dialogResult==null&&
                reset.logLines==null&&
                reset.contentResult!=null&&
                reset.contentResult
                    .saveReason()==null&&
                reset.contentResult
                    .logText()
                    .startsWith(
                        "V511_DEV_RESET ")&&
                reset.contentResult
                    .logText()
                    .contains(
                        "persisted=false"),
                "dev reset outcome="+reset
            );

            require(
                LocalDevVisualOverrideStore
                    .summary()
                    .contains(
                        "alpha=123"),
                "command reset unexpectedly cleared panel visual overrides"
            );

            runtimeBoundary();

            System.out.println(
                "DEV_SESSION_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "infoAction=true "+
                "resetAction=true "+
                "unknownInfoCompatibility=true "+
                "panelFallthrough=true "+
                "policyWireBytes=0 "+
                "runtimeInfo=true "+
                "runtimeReset=true "+
                "commandVisualOverrideParity=true "+
                "legacyParser=false "+
                "dispatcherFallback=false "+
                "publicApiExpanded=false"
            );
        }finally{
            LocalDevVisualOverrideStore.clear();

            if(player.registered())
                world.unregisterPlayer(
                    player
                );

            world.close();
        }
    }

    private static void runtimeBoundary(){
        for(Method method:
                LocalDevSessionCommandHandler.class
                    .getDeclaredMethods())
            require(
                !"handle".equals(
                    method.getName()),
                "raw dev session parser remains"
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
                    "devSessionCommands.handle("),
                "dispatcher raw dev-session fallback remains"
            );

            require(
                source.contains(
                    "isDevPanelRoute(p)")&&
                source.contains(
                    "bridge.openDevPanel("),
                "dev panel runtime route lost"
            );
        }catch(java.io.IOException error){
            throw new AssertionError(
                "dev session source audit failed",
                error
            );
        }

        for(Method method:
                ContentPlayer.class
                    .getMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    );

            require(
                !name.contains(
                    "devsession")&&
                !name.contains(
                    "devreset"),
                "dev session runtime leaked to public ContentPlayer method="+
                method.getName()
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
            result=
                new AtomicReference<>();
        AtomicReference<Throwable>
            failure=
                new AtomicReference<>();

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
                    failure.set(
                        error
                    );
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "dev session content command failed "+
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
                new int[]{
                    311,
                    312,
                    313,
                    314
                }
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

    private DevSessionContentCommandActionOwnershipTest(){}
}
