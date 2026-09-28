package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.ContentProvenance;
import spk.content.api.ContentResult;
import spk.content.builtin.LocalLabCoreContentModule;

public final class PetSpecializedProcContentCommandActionOwnershipTest {
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

            assertBinding(
                registry.commandBinding(
                    "evilwolperproc"
                ),
                "evilwolperproc"
            );
            assertBinding(
                registry.commandBinding(
                    "temporossproc"
                ),
                "temporossproc"
            );

            world.registerPlayer(
                player,
                "pet-specialized-proc-owner"
            );
            world.start();

            ByteArrayOutputStream policyWire=
                new ByteArrayOutputStream();
            ServerPacketWriter policyPackets=
                writer(
                    policyWire
                );

            ContentResult evilMissing=
                dispatch(
                    world,
                    player,
                    registry,
                    "::evilwolperproc",
                    policyPackets
                );
            assertAction(
                evilMissing,
                LocalLabCoreContentModule
                    .PET_EVIL_WOLPER_PROC_ACTION_PREFIX+
                ":1",
                "evil missing default"
            );

            ContentResult evilInvalid=
                dispatch(
                    world,
                    player,
                    registry,
                    "::evilwolperproc nope",
                    policyPackets
                );
            assertAction(
                evilInvalid,
                LocalLabCoreContentModule
                    .PET_EVIL_WOLPER_PROC_ACTION_PREFIX+
                ":1",
                "evil invalid default"
            );

            ContentResult evilRange=
                dispatch(
                    world,
                    player,
                    registry,
                    "::evilwolperproc 9 ignored",
                    policyPackets
                );
            assertAction(
                evilRange,
                LocalLabCoreContentModule
                    .PET_EVIL_WOLPER_PROC_ACTION_PREFIX+
                ":1",
                "evil legacy range fallback"
            );

            ContentResult evilThree=
                dispatch(
                    world,
                    player,
                    registry,
                    "::evilwolperproc 3 ignored trailing",
                    policyPackets
                );
            assertAction(
                evilThree,
                LocalLabCoreContentModule
                    .PET_EVIL_WOLPER_PROC_ACTION_PREFIX+
                ":3",
                "evil state/trailing compatibility"
            );

            ContentResult temporossMissing=
                dispatch(
                    world,
                    player,
                    registry,
                    "::temporossproc",
                    policyPackets
                );
            assertAction(
                temporossMissing,
                LocalLabCoreContentModule
                    .PET_TEMPOROSS_PROC_ACTION_PREFIX+
                ":1",
                "tempoross missing default"
            );

            ContentResult temporossInvalid=
                dispatch(
                    world,
                    player,
                    registry,
                    "::temporossproc nope",
                    policyPackets
                );
            assertAction(
                temporossInvalid,
                LocalLabCoreContentModule
                    .PET_TEMPOROSS_PROC_ACTION_PREFIX+
                ":1",
                "tempoross invalid default"
            );

            ContentResult temporossUnclamped=
                dispatch(
                    world,
                    player,
                    registry,
                    "::temporossproc 9 ignored",
                    policyPackets
                );
            assertAction(
                temporossUnclamped,
                LocalLabCoreContentModule
                    .PET_TEMPOROSS_PROC_ACTION_PREFIX+
                ":9",
                "tempoross no extra clamp"
            );

            policyPackets.flush();
            require(
                policyWire.size()==0,
                "specialized proc content emitted wire bytes="+
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

            LocalContentCommandActionExecutor.Outcome
                noEvilPet=
                    executor.executeOutcome(
                        evilThree.actionKey(),
                        "::evilwolperproc 3",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();
            requireSingleLine(
                noEvilPet,
                "REJECTED_ACTIVE_PET_NOT_EVIL_WOLPER",
                "evil eligibility"
            );
            require(
                effectWire.size()==0,
                "rejected evil proc emitted wire"
            );

            PetDefinitionRepository.Def seed=
                PetDefinitionRepository.get(
                    24019
                );
            require(
                seed!=null,
                "missing seed pet definition"
            );

            String spawn=
                npcs.spawnPet(
                    seed,
                    player.movement(),
                    effectPackets
                );
            require(
                spawn.startsWith(
                    "PET_SPAWN_OK"
                ),
                "seed pet spawn="+
                spawn
            );
            player.petState().activate(
                seed
            );
            player.petEffects().onPetChanged(
                seed.itemId,
                seed.npcId
            );

            npcs.previewPetDefinition(
                6991,
                player.movement(),
                effectPackets
            );
            effectPackets.flush();
            effectWire.reset();

            LocalContentCommandActionExecutor.Outcome
                evilApplied=
                    executor.executeOutcome(
                        evilThree.actionKey(),
                        "::evilwolperproc 3 ignored",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();
            requireSingleLine(
                evilApplied,
                "V593_EVIL_WOLPER_PROC state=PET_NATIVE_STATE_OK",
                "evil runtime effect"
            );
            require(
                evilApplied.logLines.get(0)
                    .contains(
                        "state=3 visual=NATIVE_SPRITE_53"
                    )&&
                evilApplied.logLines.get(0)
                    .contains(
                        "ownerBoost=GFX1310_ONLY"
                    )&&
                effectWire.size()>0,
                "evil runtime effect ownership="+
                evilApplied.logLines
            );

            int evilWire=
                effectWire.size();
            LocalContentCommandActionExecutor.Outcome
                malformedEvil=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PET_EVIL_WOLPER_PROC_ACTION_PREFIX+
                        ":3:extra",
                        "::evilwolperproc 3",
                        "opensrc",
                        effectPackets
                    );
            effectPackets.flush();
            requireUnsupported(
                malformedEvil,
                "malformed evil action"
            );
            require(
                effectWire.size()==evilWire,
                "malformed evil action emitted wire"
            );

            LocalContentCommandActionExecutor.Outcome
                outOfRangeEvilSemantic=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PET_EVIL_WOLPER_PROC_ACTION_PREFIX+
                        ":9",
                        "::evilwolperproc 9",
                        "opensrc",
                        effectPackets
                    );
            effectPackets.flush();
            requireUnsupported(
                outOfRangeEvilSemantic,
                "out-of-range evil semantic action"
            );
            require(
                effectWire.size()==evilWire,
                "out-of-range evil semantic action emitted wire"
            );

            require(
                petRuntime.handle(
                    new String[]{
                        "evilwolperproc",
                        "3"
                    },
                    effectPackets
                )==null,
                "raw evilwolperproc route remains"
            );

            npcs.previewPetDefinition(
                8184,
                player.movement(),
                effectPackets
            );
            effectPackets.flush();
            effectWire.reset();

            LocalContentCommandActionExecutor.Outcome
                temporossTwo=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PET_TEMPOROSS_PROC_ACTION_PREFIX+
                        ":2",
                        "::temporossproc 2",
                        "opensrc",
                        effectPackets
                    );
            effectPackets.flush();
            requireSingleLine(
                temporossTwo,
                "V59_TEMPOROSS_PROC anim=PET_ANIMATION_OK",
                "tempoross runtime effect"
            );
            require(
                temporossTwo.logLines.get(0)
                    .contains(
                        "state=PET_NATIVE_STATE_OK"
                    )&&
                temporossTwo.logLines.get(0)
                    .contains(
                        "family=TEMPOROSS_DEBUFF state=2 visual=NATIVE_SPRITE_58"
                    )&&
                effectWire.size()>0,
                "tempoross runtime effect ownership="+
                temporossTwo.logLines
            );

            effectWire.reset();
            LocalContentCommandActionExecutor.Outcome
                temporossNine=
                    executor.executeOutcome(
                        temporossUnclamped.actionKey(),
                        "::temporossproc 9 ignored",
                        "opensrc",
                        effectPackets
                    );
            effectPackets.flush();
            requireSingleLine(
                temporossNine,
                "state=REJECTED_STATE_RANGE expected=0..3",
                "tempoross no clamp runtime path"
            );
            require(
                temporossNine.logLines.get(0)
                    .contains(
                        "anim=PET_ANIMATION_OK"
                    )&&
                effectWire.size()>0,
                "tempoross state 9 did not reach existing runtime path"
            );

            int temporossWire=
                effectWire.size();
            LocalContentCommandActionExecutor.Outcome
                malformedTempoross=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PET_TEMPOROSS_PROC_ACTION_PREFIX+
                        ":2:extra",
                        "::temporossproc 2",
                        "opensrc",
                        effectPackets
                    );
            effectPackets.flush();
            requireUnsupported(
                malformedTempoross,
                "malformed tempoross action"
            );
            require(
                effectWire.size()==temporossWire,
                "malformed tempoross action emitted wire"
            );

            require(
                petRuntime.handle(
                    new String[]{
                        "temporossproc",
                        "2"
                    },
                    effectPackets
                )==null,
                "raw temporossproc route remains"
            );

            List<String> petTestTempoross=
                petRuntime.handle(
                    new String[]{
                        "pettest",
                        "tempoross",
                        "1"
                    },
                    effectPackets
                );
            require(
                petTestTempoross!=null&&
                petTestTempoross.size()==1&&
                petTestTempoross.get(0)
                    .contains(
                        "V59_PET_TEST tempoross"
                    ),
                "pettest tempoross route lost="+
                petTestTempoross
            );

            List<String> petTestAll=
                petRuntime.handle(
                    new String[]{
                        "pettestall"
                    },
                    effectPackets
                );
            require(
                petTestAll!=null&&
                petTestAll.size()==1&&
                petTestAll.get(0)
                    .contains(
                        "V59_PET_TEST_ALL_ARMED"
                    ),
                "pettestall route lost="+
                petTestAll
            );

            List<String> petNpc=
                petRuntime.handle(
                    new String[]{
                        "petnpc",
                        "8184"
                    },
                    effectPackets
                );
            require(
                petNpc!=null&&
                petNpc.size()==1&&
                petNpc.get(0)
                    .contains(
                        "V59_PET_NPC_PREVIEW"
                    ),
                "petnpc route lost="+
                petNpc
            );

            System.out.println(
                "PET_SPECIALIZED_PROC_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "aliases=true "+
                "customProvenance=true "+
                "evilDefault=true "+
                "evilRangeFallback=true "+
                "evilTrailingArgs=true "+
                "temporossDefault=true "+
                "temporossUnclamped=true "+
                "policyWireBytes=0 "+
                "runtimeEligibility=true "+
                "evilNativeState=true "+
                "evilOwnerGfx=true "+
                "temporossAnimation=true "+
                "temporossNativeState=true "+
                "malformedFailClosed=true "+
                "legacyRoutes=false "+
                "workbenchRoutesRetained=true "+
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

    private static void assertBinding(
        ContentRegistry.BindingInfo binding,
        String command
    ){
        require(
            binding!=null&&
            binding.provenance==
                ContentProvenance.CUSTOM_LOCALLAB,
            command+
            " binding="+
            binding
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
                "specialized proc content command failed "+
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

    private static void requireSingleLine(
        LocalContentCommandActionExecutor.Outcome outcome,
        String text,
        String label
    ){
        require(
            outcome!=null&&
            outcome.contentResult==null&&
            outcome.dialogResult==null&&
            outcome.logLines!=null&&
            outcome.logLines.size()==1&&
            outcome.logLines.get(0)
                .contains(
                    text),
            label+
            " outcome="+
            outcome
        );
    }

    private static void requireUnsupported(
        LocalContentCommandActionExecutor.Outcome outcome,
        String label
    ){
        require(
            outcome!=null&&
            outcome.contentResult!=null&&
            outcome.contentResult
                .logText()
                .contains(
                    "REJECTED_UNSUPPORTED"
                ),
            label+
            " outcome="+
            outcome
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{
                    381,
                    382,
                    383,
                    384
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

    private PetSpecializedProcContentCommandActionOwnershipTest(){}
}
