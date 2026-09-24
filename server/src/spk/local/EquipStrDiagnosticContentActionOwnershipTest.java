package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class EquipStrDiagnosticContentActionOwnershipTest {
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
                    "equipstr"
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
                "equipstr binding="+binding
            );

            world.registerPlayer(
                player,
                "equipstr-content-owner"
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
                    "::equipstr",
                    policyPackets
                ),
                LocalDiagnosticContentModule
                    .EQUIPSTR_ACTION_PREFIX+
                    ":-1",
                "missing item"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::equipstr nope",
                    policyPackets
                ),
                LocalDiagnosticContentModule
                    .EQUIPSTR_ACTION_PREFIX+
                    ":-1",
                "invalid item"
            );

            ContentResult explicit=
                dispatch(
                    world,
                    player,
                    registry,
                    "::equipstr 4151 ignored",
                    policyPackets
                );

            assertAction(
                explicit,
                LocalDiagnosticContentModule
                    .EQUIPSTR_ACTION_PREFIX+
                    ":4151",
                "explicit item"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "equipstr content policy emitted wire bytes="+
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

            LocalContentCommandActionExecutor.Outcome
                unknown=
                    executor.executeOutcome(
                        LocalDiagnosticContentModule
                            .EQUIPSTR_ACTION_PREFIX+
                        ":-1",
                        "::equipstr",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            require(
                unknown!=null&&
                unknown.dialogResult==null&&
                unknown.logLines==null&&
                unknown.contentResult!=null&&
                unknown.contentResult
                    .saveReason()==null&&
                unknown.contentResult
                    .logText()
                    .contains(
                        "V5181_EQUIPSTR_FAIL_CLOSED item=-1 known=false")&&
                unknown.contentResult
                    .logText()
                    .contains(
                        "resetHover=true numeric14=UNRESOLVED_SERVER_AUTHORITY"),
                "unknown equipstr outcome="+
                unknown
            );

            require(
                effectWire.size()>0,
                "equipstr runtime emitted no hover-reset wire"
            );

            int beforeExplicit=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome
                known=
                    executor.executeOutcome(
                        explicit.actionKey(),
                        "::equipstr 4151 ignored",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            require(
                known!=null&&
                known.contentResult!=null&&
                known.contentResult
                    .logText()
                    .contains(
                        "V5181_EQUIPSTR_FAIL_CLOSED item=4151")&&
                known.contentResult
                    .logText()
                    .contains(
                        "numeric14=UNRESOLVED_SERVER_AUTHORITY"),
                "explicit equipstr outcome="+
                known
            );

            require(
                effectWire.size()>beforeExplicit,
                "explicit equipstr emitted no hover-reset wire"
            );

            int beforeMalformed=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome
                malformed=
                    executor.executeOutcome(
                        LocalDiagnosticContentModule
                            .EQUIPSTR_ACTION_PREFIX+
                        ":4151:extra",
                        "::equipstr 4151 extra",
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
                "malformed equipstr action="+
                malformed
            );

            require(
                effectWire.size()==beforeMalformed,
                "malformed equipstr action emitted wire"
            );

            require(
                !diagnostics.handle(
                    new String[]{
                        "equipstr",
                        "4151"
                    },
                    effectPackets,
                    "[equipstr-test] ",
                    "opensrc",
                    "localtest",
                    true,
                    null
                ),
                "legacy diagnostic handler still claims equipstr"
            );

            System.out.println(
                "EQUIPSTR_DIAGNOSTIC_CONTENT_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticItem=true "+
                "invalidCompatibility=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "runtimeHoverReset=true "+
                "numeric14FailClosed=true "+
                "malformedFailClosed=true "+
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
                "equipstr content command failed "+
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
                    291,
                    292,
                    293,
                    294
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

    private EquipStrDiagnosticContentActionOwnershipTest(){}
}
