package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class CombatFixtureContentCommandActionOwnershipTest {
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
                    "combatfixture"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "combatfixture binding="+binding
            );

            world.registerPlayer(
                player,
                "combatfixture-content-owner"
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
                    "::combatfixture",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .COMBAT_FIXTURE_ACTION_PREFIX+
                    ":0",
                "default damage"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::combatfixture 37",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .COMBAT_FIXTURE_ACTION_PREFIX+
                    ":37",
                "explicit damage"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::combatfixture nope",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .COMBAT_FIXTURE_ACTION_PREFIX+
                    ":0",
                "invalid damage compatibility"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::combatfixture -1 ignored",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .COMBAT_FIXTURE_ACTION_PREFIX+
                    ":-1",
                "extra args ignored"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "combatfixture content policy emitted wire bytes="+
                policyWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);
            CombatEngine combat=
                new CombatEngine(dev);
            LocalPetRuntimeCommandHandler petRuntime=
                new LocalPetRuntimeCommandHandler(
                    player.petState(),
                    player.petEffects(),
                    npcs,
                    player.movement()
                );
            LocalCombatCommandHandler combatHandler=
                new LocalCombatCommandHandler(
                    combat,
                    npcs,
                    petRuntime
                );

            LocalPetCompatibilityCommandHandler
                petCompatibility=
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
                    petCompatibility,
                    combatHandler
                );

            ByteArrayOutputStream effectWire=
                new ByteArrayOutputStream();
            ServerPacketWriter effectPackets=
                writer(
                    effectWire
                );

            LocalContentCommandActionExecutor.Outcome noTarget=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .COMBAT_FIXTURE_ACTION_PREFIX+
                    ":37",
                    "::combatfixture 37",
                    "combatfixture-content-owner",
                    effectPackets
                );

            assertLines(
                noTarget,
                "V59_COMBAT_FIXTURE command=::combatfixture 37 "+
                    "result=REJECTED_NO_SELECTED_TARGET",
                "no target"
            );

            require(
                effectWire.size()==0,
                "no-target fixture emitted wire"
            );

            LocalContentCommandActionExecutor.Outcome outOfRange=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .COMBAT_FIXTURE_ACTION_PREFIX+
                    ":999",
                    "::combatfixture 999",
                    "combatfixture-content-owner",
                    effectPackets
                );

            assertLines(
                outOfRange,
                "V59_COMBAT_FIXTURE command=::combatfixture 999 "+
                    "result=REJECTED_DAMAGE_RANGE expected=0..255",
                "out of range"
            );

            int beforeMalformed=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome malformed=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .COMBAT_FIXTURE_ACTION_PREFIX+
                    ":37:extra",
                    "::combatfixture 37 extra",
                    "combatfixture-content-owner",
                    effectPackets
                );

            require(
                malformed!=null&&
                malformed.logLines==null&&
                malformed.dialogResult==null&&
                malformed.contentResult!=null&&
                malformed.contentResult.logText()
                    .contains(
                        "REJECTED_UNSUPPORTED"),
                "malformed fixture action="+
                malformed
            );

            require(
                effectWire.size()==beforeMalformed,
                "malformed combatfixture action emitted wire"
            );

            runtimeBoundary();

            System.out.println(
                "COMBAT_FIXTURE_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "defaultDamage=true "+
                "invalidDamageCompatibility=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "internalLogLines=true "+
                "noTargetParity=true "+
                "rangeParity=true "+
                "malformedFailClosed=true "+
                "legacyParser=false "+
                "devhitRuntimeOwned=true "+
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

    private static void assertLines(
        LocalContentCommandActionExecutor.Outcome outcome,
        String expected,
        String label
    ){
        require(
            outcome!=null&&
            outcome.contentResult==null&&
            outcome.dialogResult==null&&
            outcome.logLines!=null,
            label+
            " outcome shape"
        );

        List<String> lines=
            outcome.logLines;

        require(
            lines.size()==1&&
            expected.equals(
                lines.get(0)),
            label+
            " lines="+lines
        );
    }

    private static void runtimeBoundary(){
        for(Method method:
                ContentResult.class
                    .getDeclaredMethods())
            require(
                !method.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    )
                    .contains(
                        "line"),
                "log-line outcome leaked into public ContentResult"
            );

        try{
            String combatSource=
                new String(
                    java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get(
                            "server/src/spk/local/LocalCombatCommandHandler.java"
                        )
                    ),
                    java.nio.charset.StandardCharsets.UTF_8
                );

            require(
                !combatSource.contains(
                    "equalsIgnoreCase(\"combatfixture\")"),
                "legacy combatfixture parser remains"
            );

            require(
                !combatSource.contains(
                    "equalsIgnoreCase(\"devhit\")")&&
                combatSource.contains(
                    "List<String> fixture(")&&
                combatSource.contains(
                    "String devHitInfo("),
                "combat runtime effect boundary changed unexpectedly"
            );

            String dispatcherSource=
                new String(
                    java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get(
                            "server/src/spk/local/LocalCommandDispatcher.java"
                        )
                    ),
                    java.nio.charset.StandardCharsets.UTF_8
                );

            require(
                dispatcherSource.contains(
                    "action.logLines")&&
                !dispatcherSource.contains(
                    "combatCommands.handle("),
                "dispatcher raw combat fallback remains"
            );
        }catch(java.io.IOException error){
            throw new AssertionError(
                "combatfixture source audit failed",
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
                "combatfixture content command failed "+
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
                new int[]{261,262,263,264}
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

    private CombatFixtureContentCommandActionOwnershipTest(){}
}
