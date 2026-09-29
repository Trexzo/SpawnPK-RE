package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class PetDamageContentCommandActionOwnershipTest {
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
                    "behemothhit"
                ),
                "behemothhit"
            );
            assertBinding(
                registry.commandBinding(
                    "petdamage"
                ),
                "petdamage"
            );

            world.registerPlayer(
                player,
                "petdamage-content-owner"
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
                    "::behemothhit 74",
                    policyPackets
                );
            assertAction(
                normal,
                LocalLabCoreContentModule
                    .PET_DAMAGE_ACTION_PREFIX+
                ":74",
                "behemothhit"
            );

            ContentResult alias=
                dispatch(
                    world,
                    player,
                    registry,
                    "::petdamage 1 ignored",
                    policyPackets
                );
            assertAction(
                alias,
                LocalLabCoreContentModule
                    .PET_DAMAGE_ACTION_PREFIX+
                ":1",
                "petdamage alias/trailing compatibility"
            );

            ContentResult invalid=
                dispatch(
                    world,
                    player,
                    registry,
                    "::petdamage nope",
                    policyPackets
                );
            assertAction(
                invalid,
                LocalLabCoreContentModule
                    .PET_DAMAGE_ACTION_PREFIX+
                ":0",
                "invalid integer compatibility"
            );

            ContentResult missing=
                dispatch(
                    world,
                    player,
                    registry,
                    "::behemothhit",
                    policyPackets
                );
            assertAction(
                missing,
                LocalLabCoreContentModule
                    .PET_DAMAGE_ACTION_PREFIX+
                ":0",
                "missing integer compatibility"
            );

            policyPackets.flush();
            require(
                policyWire.size()==0,
                "pet damage content policy emitted wire bytes="+
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
                nonpositive=
                    executor.executeOutcome(
                        invalid.actionKey(),
                        "::petdamage nope",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();
            requireSingleLine(
                nonpositive,
                "source=MANUAL_BEHEMOTH_HIT result=IGNORED_NONPOSITIVE damage=0",
                "nonpositive before eligibility"
            );
            require(
                effectWire.size()==0,
                "nonpositive damage emitted wire"
            );

            LocalContentCommandActionExecutor.Outcome
                noActive=
                    executor.executeOutcome(
                        normal.actionKey(),
                        "::behemothhit 74",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();
            requireSingleLine(
                noActive,
                "source=MANUAL_BEHEMOTH_HIT damage=74 result=NO_ACTIVE_CHARGE_PET",
                "positive no-active"
            );
            require(
                effectWire.size()==0,
                "no-active damage emitted wire"
            );

            PetDefinitionRepository.Def definition=
                PetDefinitionRepository.get(
                    24019
                );

            require(
                definition!=null&&
                PetPresentationProfile.isChargePet(
                    definition.itemId,
                    definition.npcId
                )&&
                PetPresentationProfile.damagePerCharge(
                    definition.itemId,
                    definition.npcId
                )==75,
                "certified 75-damage charge pet="+
                definition
            );

            String spawn=
                npcs.spawnPet(
                    definition,
                    player.movement(),
                    effectPackets
                );

            require(
                spawn.startsWith(
                    "PET_SPAWN_OK"),
                "charge-pet spawn="+
                spawn
            );

            player.petState()
                .activate(
                    definition
                );
            player.petEffects()
                .onPetChanged(
                    definition.itemId,
                    definition.npcId
                );

            effectPackets.flush();
            effectWire.reset();

            LocalContentCommandActionExecutor.Outcome
                accumulated=
                    executor.executeOutcome(
                        normal.actionKey(),
                        "::behemothhit 74",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();
            requireSingleLine(
                accumulated,
                "source=MANUAL_BEHEMOTH_HIT damage=74 chargeChanged=false presentation=UNCHANGED",
                "non-threshold accumulation"
            );
            require(
                player.petEffects()
                    .accumulatedDamage()==74&&
                player.petEffects()
                    .charge()==0&&
                effectWire.size()==0,
                "non-threshold damage changed presentation/state unexpectedly"
            );

            LocalContentCommandActionExecutor.Outcome
                threshold=
                    executor.executeOutcome(
                        alias.actionKey(),
                        "::petdamage 1 ignored",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();
            requireSingleLine(
                threshold,
                "source=MANUAL_BEHEMOTH_HIT damage=1 chargeChanged=true",
                "threshold crossing"
            );
            require(
                threshold.logLines.get(0)
                    .contains(
                        "modifiersRecordedOnly=true combatM2FormulaStillFixture=true")&&
                player.petEffects()
                    .accumulatedDamage()==75&&
                player.petEffects()
                    .charge()==1&&
                effectWire.size()>0,
                "threshold damage did not update native charge presentation"
            );

            int beforeMalformedWire=
                effectWire.size();
            int beforeMalformedDamage=
                player.petEffects()
                    .accumulatedDamage();
            int beforeMalformedCharge=
                player.petEffects()
                    .charge();

            LocalContentCommandActionExecutor.Outcome
                malformed=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PET_DAMAGE_ACTION_PREFIX+
                        ":1:extra",
                        "::petdamage 1",
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
                "malformed semantic damage action="+
                malformed
            );
            require(
                effectWire.size()==beforeMalformedWire&&
                player.petEffects()
                    .accumulatedDamage()==
                        beforeMalformedDamage&&
                player.petEffects()
                    .charge()==
                        beforeMalformedCharge,
                "malformed semantic damage action mutated state/wire"
            );

            require(
                petRuntime.handle(
                    new String[]{
                        "behemothhit",
                        "75"
                    },
                    effectPackets
                )==null,
                "raw behemothhit route remains"
            );
            require(
                petRuntime.handle(
                    new String[]{
                        "petdamage",
                        "75"
                    },
                    effectPackets
                )==null,
                "raw petdamage route remains"
            );

            List<String> petTestDamage=
                petRuntime.handle(
                    new String[]{
                        "pettest",
                        "damage",
                        "1"
                    },
                    effectPackets
                );

            require(
                petTestDamage!=null&&
                petTestDamage.size()==1&&
                petTestDamage.get(0)
                    .contains(
                        "source=PETTEST_DAMAGE"),
                "pettest damage workbench route lost="+
                petTestDamage
            );

            publicApiBoundary();

            System.out.println(
                "PET_DAMAGE_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "aliases=true "+
                "semanticAction=true "+
                "invalidCompatibility=true "+
                "missingCompatibility=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "nonpositiveBeforeEligibility=true "+
                "noActivePositive=true "+
                "nonThresholdNoWire=true "+
                "thresholdNativeState=true "+
                "sourceLabel=true "+
                "modifiersRecordedOnly=true "+
                "malformedFailClosed=true "+
                "legacyRoutes=false "+
                "pettestDamageRuntimeOwned=true "+
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

    private static void publicApiBoundary(){
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
                    "petdamage")&&
                !name.contains(
                    "behemothhit"),
                "pet damage runtime leaked to public ContentPlayer method="+
                method.getName()
            );
        }
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo binding,
        String command
    ){
        require(
            binding!=null&&
            "locallab-core".equals(
                binding.moduleId)&&
            binding.priority==100&&
            binding.provenance==
                ContentProvenance.CUSTOM_LOCALLAB,
            command+
            " binding="+
            binding
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
                "petdamage content command failed "+
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
                    371,
                    372,
                    373,
                    374
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

    private PetDamageContentCommandActionOwnershipTest(){}
}
