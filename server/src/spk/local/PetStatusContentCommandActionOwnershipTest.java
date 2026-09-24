package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class PetStatusContentCommandActionOwnershipTest {
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
                    "petstatus"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "petstatus binding="+
                binding
            );

            world.registerPlayer(
                player,
                "petstatus-content-owner"
            );
            world.start();

            ByteArrayOutputStream policyWire=
                new ByteArrayOutputStream();
            ServerPacketWriter policyPackets=
                writer(
                    policyWire
                );

            ContentResult plain=
                dispatch(
                    world,
                    player,
                    registry,
                    "::petstatus",
                    policyPackets
                );

            assertAction(
                plain,
                "plain"
            );

            ContentResult extra=
                dispatch(
                    world,
                    player,
                    registry,
                    "::petstatus ignored",
                    policyPackets
                );

            assertAction(
                extra,
                "extra args"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "petstatus content policy emitted wire bytes="+
                policyWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(
                    dev
                );

            LocalPetRuntimeCommandHandler petRuntime=
                new LocalPetRuntimeCommandHandler(
                    player.petState(),
                    player.petEffects(),
                    npcs,
                    player.movement()
                );

            List<String> expected=
                petRuntime.status();

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
                    null,
                    null,
                    petRuntime
                );

            ByteArrayOutputStream effectWire=
                new ByteArrayOutputStream();
            ServerPacketWriter effectPackets=
                writer(
                    effectWire
                );

            LocalContentCommandActionExecutor.Outcome
                outcome=
                    executor.executeOutcome(
                        plain.actionKey(),
                        "::petstatus",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            require(
                outcome!=null&&
                outcome.contentResult==null&&
                outcome.dialogResult==null&&
                outcome.logLines!=null&&
                outcome.logLines.equals(
                    expected),
                "petstatus runtime outcome="+
                outcome
            );

            require(
                effectWire.size()==0,
                "petstatus runtime projection emitted wire bytes="+
                effectWire.size()
            );

            LocalContentCommandActionExecutor.Outcome
                malformed=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PET_STATUS_ACTION+
                        ":extra",
                        "::petstatus extra",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            require(
                malformed!=null&&
                malformed.contentResult!=null&&
                malformed.contentResult
                    .logText()
                    .contains(
                        "REJECTED_UNSUPPORTED"),
                "malformed petstatus action="+
                malformed
            );

            require(
                effectWire.size()==0,
                "malformed petstatus action emitted wire"
            );

            require(
                petRuntime.handle(
                    new String[]{
                        "petstatus"
                    },
                    effectPackets
                )==null,
                "legacy petstatus route remains"
            );

            runtimeBoundary();

            System.out.println(
                "PET_STATUS_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "runtimeProjection=true "+
                "runtimeWireBytes=0 "+
                "malformedFailClosed=true "+
                "legacyRoute=false "+
                "contentBeforeRuntimeFallback=true "+
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

    private static void runtimeBoundary()
        throws Exception{
        String handler=
            source(
                "server/src/spk/local/LocalPetRuntimeCommandHandler.java"
            );

        require(
            !handler.contains(
                "equalsIgnoreCase(\"petstatus\")"),
            "raw petstatus branch remains"
        );

        String dispatcher=
            source(
                "server/src/spk/local/LocalCommandDispatcher.java"
            );

        int content=
            dispatcher.indexOf(
                "contentRegistry.dispatchCommand("
            );
        int fallback=
            dispatcher.indexOf(
                "petRuntimeCommands.handle("
            );

        require(
            content>=0&&
            fallback>content,
            "content must precede pet runtime fallback"
        );

        for(Method method:
                ContentPlayer.class
                    .getMethods())
            require(
                !method.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    )
                    .contains(
                        "petstatus"),
                "petstatus leaked to public ContentPlayer method="+
                method.getName()
            );
    }

    private static String source(
        String path
    )throws Exception{
        return new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(
                    path
                )
            ),
            java.nio.charset.StandardCharsets.UTF_8
        );
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
                "petstatus content command failed "+
                command,
                failure.get()
            );

        return result.get();
    }

    private static void assertAction(
        ContentResult result,
        String label
    ){
        require(
            result!=null&&
            result.hasAction()&&
            LocalLabCoreContentModule
                .PET_STATUS_ACTION
                .equals(
                    result.actionKey())&&
            result.saveReason()==null,
            label+
            " result="+
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
                    331,
                    332,
                    333,
                    334
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

    private PetStatusContentCommandActionOwnershipTest(){}
}
