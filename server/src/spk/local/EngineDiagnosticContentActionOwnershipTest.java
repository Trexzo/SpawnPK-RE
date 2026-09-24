package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class EngineDiagnosticContentActionOwnershipTest {
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
                    "engine"
                );

            require(
                binding!=null&&
                LocalDiagnosticContentModule
                    .MODULE_ID
                    .equals(
                        binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "engine binding="+binding
            );

            world.registerPlayer(
                player,
                "engine-content-owner"
            );
            world.start();

            ByteArrayOutputStream policyWire=
                new ByteArrayOutputStream();

            ServerPacketWriter policyPackets=
                writer(
                    policyWire
                );

            ContentResult policy=
                dispatch(
                    world,
                    player,
                    registry,
                    "::engine ignored",
                    policyPackets
                );

            policyPackets.flush();

            require(
                policy!=null&&
                policy.hasAction()&&
                LocalDiagnosticContentModule
                    .ENGINE_INFO_ACTION
                    .equals(
                        policy.actionKey())&&
                policy.saveReason()==null,
                "engine policy="+policy
            );

            require(
                policyWire.size()==0,
                "engine content policy emitted wire bytes="+
                policyWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(
                    dev
                );

            LocalDiagnosticCommandHandler diagnostics=
                new LocalDiagnosticCommandHandler(
                    world,
                    player.equipment(),
                    new NativeItemLibraryService()
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
                    diagnostics
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
                        3200,
                        3200,
                        0
                    )
                );

            String expected=
                diagnostics.engineSummary(
                    "opensrc",
                    "localtest",
                    true,
                    scene
                );

            LocalContentCommandActionExecutor.Outcome
                outcome=
                    executor.executeOutcome(
                        policy.actionKey(),
                        "::engine ignored",
                        "opensrc",
                        "localtest",
                        true,
                        scene,
                        effectPackets
                    );

            require(
                outcome!=null&&
                outcome.dialogResult==null&&
                outcome.logLines==null&&
                outcome.contentResult!=null&&
                expected.equals(
                    outcome.contentResult
                        .logText())&&
                outcome.contentResult
                    .saveReason()==null,
                "engine runtime outcome="+
                outcome
            );

            effectPackets.flush();

            require(
                effectWire.size()==0,
                "engine runtime effect emitted wire bytes="+
                effectWire.size()
            );

            require(
                !diagnostics.handle(
                    new String[]{
                        "engine"
                    },
                    effectPackets,
                    "[engine-test] ",
                    "opensrc",
                    "localtest",
                    true,
                    scene
                ),
                "legacy diagnostic handler still claims engine"
            );

            assertPublicBoundary();

            System.out.println(
                "ENGINE_DIAGNOSTIC_CONTENT_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "policyWireBytes=0 "+
                "sessionMetadataRuntimeOnly=true "+
                "textParity=true "+
                "runtimeWireBytes=0 "+
                "legacyRoute=false "+
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

    private static void assertPublicBoundary(){
        for(java.lang.reflect.Method method:
                ContentPlayer.class
                    .getMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    );

            require(
                !name.contains(
                    "loginalias")&&
                !name.contains(
                    "persistentaccount")&&
                !name.contains(
                    "scenepublisher"),
                "session metadata leaked to public ContentPlayer method="+
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
                "engine content command failed",
                failure.get()
            );

        return result.get();
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{
                    281,
                    282,
                    283,
                    284
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

    private EngineDiagnosticContentActionOwnershipTest(){}
}
