package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class PetBoostContentCommandActionOwnershipTest {
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
                    "petboost"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "petboost binding="+binding
            );

            world.registerPlayer(
                player,
                "petboost-content-owner"
            );
            world.start();

            ByteArrayOutputStream policyWire=
                new ByteArrayOutputStream();
            ServerPacketWriter policyPackets=
                writer(
                    policyWire
                );

            ContentResult normal=
                dispatch(
                    world,
                    player,
                    registry,
                    "::petboost",
                    policyPackets
                );

            assertAction(
                normal,
                LocalLabCoreContentModule
                    .PET_BOOST_ACTION,
                "normal"
            );

            ContentResult trailing=
                dispatch(
                    world,
                    player,
                    registry,
                    "::petboost ignored trailing",
                    policyPackets
                );

            assertAction(
                trailing,
                LocalLabCoreContentModule
                    .PET_BOOST_ACTION,
                "trailing compatibility"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "petboost content policy emitted wire bytes="+
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

            List<String> direct=
                petRuntime.boost(
                    effectPackets
                );

            require(
                direct!=null&&
                direct.size()==1&&
                direct.get(0).contains(
                    "V593_PET_BOOST_FIXTURE")&&
                direct.get(0).contains(
                    "gfx=1310")&&
                direct.get(0).contains(
                    "productionNormalPetEvidence=LIVE_COMPONENT_ISOLATION"),
                "direct petboost="+direct
            );

            effectPackets.flush();

            require(
                effectWire.size()>0,
                "direct petboost emitted no wire"
            );

            effectWire.reset();

            LocalContentCommandActionExecutor.Outcome
                outcome=
                    executor.executeOutcome(
                        normal.actionKey(),
                        "::petboost",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            require(
                outcome!=null&&
                outcome.contentResult==null&&
                outcome.dialogResult==null&&
                outcome.logLines!=null&&
                outcome.logLines.size()==1&&
                outcome.logLines.get(0)
                    .equals(
                        direct.get(0)),
                "petboost outcome="+outcome
            );

            require(
                effectWire.size()>0,
                "semantic petboost emitted no runtime packet"
            );

            int beforeMalformed=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome
                malformed=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PET_BOOST_ACTION+
                        ":extra",
                        "::petboost",
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
                "malformed petboost="+malformed
            );

            require(
                effectWire.size()==beforeMalformed,
                "malformed petboost emitted packet"
            );

            require(
                petRuntime.handle(
                    new String[]{"petboost"},
                    effectPackets
                )==null,
                "raw petboost route remains"
            );

            System.out.println(
                "PET_BOOST_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "runtimePresentation=true "+
                "runtimeWireBytes=true "+
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
                "petboost content command failed "+
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
                    321,
                    322,
                    323,
                    324
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

    private PetBoostContentCommandActionOwnershipTest(){}
}
