package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class PetChargeContentCommandActionOwnershipTest {
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
                    "behemothcharge"
                ),
                "behemothcharge"
            );

            assertBinding(
                registry.commandBinding(
                    "petcharge"
                ),
                "petcharge"
            );

            world.registerPlayer(
                player,
                "petcharge-content-owner"
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
                    "::behemothcharge 2",
                    policyPackets
                );

            assertAction(
                normal,
                LocalLabCoreContentModule
                    .PET_CHARGE_ACTION_PREFIX+
                ":2",
                "behemothcharge"
            );

            ContentResult alias=
                dispatch(
                    world,
                    player,
                    registry,
                    "::petcharge 3 ignored",
                    policyPackets
                );

            assertAction(
                alias,
                LocalLabCoreContentModule
                    .PET_CHARGE_ACTION_PREFIX+
                ":3",
                "petcharge alias/trailing compatibility"
            );

            ContentResult invalid=
                dispatch(
                    world,
                    player,
                    registry,
                    "::petcharge nope",
                    policyPackets
                );

            assertAction(
                invalid,
                LocalLabCoreContentModule
                    .PET_CHARGE_ACTION_PREFIX+
                ":-1",
                "invalid integer compatibility"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "petcharge content policy emitted wire bytes="+
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
                noActiveInvalid=
                    executor.executeOutcome(
                        invalid.actionKey(),
                        "::petcharge nope",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            requireSingleLine(
                noActiveInvalid,
                "REJECTED_ACTIVE_PET_NOT_CHARGE_FAMILY",
                "eligibility-before-range"
            );

            require(
                effectWire.size()==0,
                "no-active invalid charge emitted wire"
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
                ),
                "certified charge-pet definition="+
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
                charged=
                    executor.executeOutcome(
                        normal.actionKey(),
                        "::behemothcharge 2",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            requireSingleLine(
                charged,
                "V59_BEHEMOTH_CHARGE result=PET_NATIVE_STATE_OK",
                "runtime charge"
            );

            require(
                charged.logLines.get(0)
                    .contains(
                        "charge=2")&&
                player.petEffects()
                    .charge()==2,
                "charge state/log mismatch lines="+
                charged.logLines+
                " state="+
                player.petEffects().charge()
            );

            require(
                effectWire.size()>0,
                "runtime charge emitted no native-state packet"
            );

            int beforeRange=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome
                outOfRange=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PET_CHARGE_ACTION_PREFIX+
                        ":4",
                        "::petcharge 4",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            requireSingleLine(
                outOfRange,
                "REJECTED_RANGE expected=0..3",
                "active range"
            );

            require(
                effectWire.size()==beforeRange&&
                player.petEffects()
                    .charge()==2,
                "out-of-range charge mutated wire/state"
            );

            int beforeMalformed=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome
                malformed=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PET_CHARGE_ACTION_PREFIX+
                        ":2:extra",
                        "::petcharge 2",
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
                "malformed charge="+
                malformed
            );

            require(
                effectWire.size()==beforeMalformed&&
                player.petEffects()
                    .charge()==2,
                "malformed charge mutated wire/state"
            );

            require(
                petRuntime.handle(
                    new String[]{
                        "behemothcharge",
                        "1"
                    },
                    effectPackets
                )==null,
                "raw behemothcharge route remains"
            );

            require(
                petRuntime.handle(
                    new String[]{
                        "petcharge",
                        "1"
                    },
                    effectPackets
                )==null,
                "raw petcharge route remains"
            );

            publicApiBoundary();

            System.out.println(
                "PET_CHARGE_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "aliases=true "+
                "semanticAction=true "+
                "invalidCompatibility=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "eligibilityBeforeRange=true "+
                "runtimeCharge=true "+
                "nativeStateRuntimeOnly=true "+
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
                    "petcharge")&&
                !name.contains(
                    "behemothcharge"),
                "pet charge runtime leaked to public ContentPlayer method="+
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
                "petcharge content command failed "+
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
                    361,
                    362,
                    363,
                    364
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

    private PetChargeContentCommandActionOwnershipTest(){}
}
