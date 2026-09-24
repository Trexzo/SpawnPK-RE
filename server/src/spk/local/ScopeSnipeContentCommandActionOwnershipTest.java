package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class ScopeSnipeContentCommandActionOwnershipTest {
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
                    "scopesnipe"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "scopesnipe binding="+binding
            );

            world.registerPlayer(
                player,
                "scopesnipe-content-owner"
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
                    "::scopesnipe",
                    policyPackets
                );

            assertAction(
                normal,
                LocalLabCoreContentModule
                    .PET_SCOPE_SNIPE_ACTION,
                "normal"
            );

            ContentResult trailing=
                dispatch(
                    world,
                    player,
                    registry,
                    "::scopesnipe ignored",
                    policyPackets
                );

            assertAction(
                trailing,
                LocalLabCoreContentModule
                    .PET_SCOPE_SNIPE_ACTION,
                "trailing compatibility"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "scopesnipe content policy emitted wire bytes="+
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

            LocalContentCommandActionExecutor.Outcome rejected=
                executor.executeOutcome(
                    normal.actionKey(),
                    "::scopesnipe",
                    "opensrc",
                    effectPackets
                );

            effectPackets.flush();

            require(
                rejected!=null&&
                rejected.logLines!=null&&
                rejected.logLines.size()==1&&
                rejected.logLines.get(0)
                    .contains(
                        "REJECTED_NO_ACTIVE_SCOPESIGHT"),
                "inactive scopesnipe="+
                rejected
            );

            require(
                effectWire.size()==0,
                "inactive scopesnipe emitted wire"
            );

            PetDefinitionRepository.Def definition=
                PetDefinitionRepository.get(
                    ScopesightPetProfile.ITEM_ID
                );

            require(
                definition!=null&&
                definition.itemId==
                    ScopesightPetProfile.ITEM_ID&&
                definition.npcId==
                    ScopesightPetProfile.NPC_ID,
                "scopesight definition="+
                definition
            );

            player.petState()
                .activate(
                    definition
                );

            String spawn=
                npcs.spawnPet(
                    definition,
                    player.movement(),
                    effectPackets
                );

            require(
                spawn.contains(
                    "PET_SPAWN_OK item="+
                    ScopesightPetProfile.ITEM_ID+
                    " npc="+
                    ScopesightPetProfile.NPC_ID),
                "scopesight spawn="+spawn
            );

            effectPackets.flush();
            effectWire.reset();

            LocalContentCommandActionExecutor.Outcome success=
                executor.executeOutcome(
                    normal.actionKey(),
                    "::scopesnipe",
                    "opensrc",
                    effectPackets
                );

            effectPackets.flush();

            require(
                success!=null&&
                success.logLines!=null&&
                success.logLines.size()==1&&
                success.logLines.get(0)
                    .contains(
                        "PET_FORCE_TEXT_OK item="+
                        ScopesightPetProfile.ITEM_ID+
                        " npc="+
                        ScopesightPetProfile.NPC_ID+
                        " text="+
                        ScopesightPetProfile
                            .NATIVE_TRIGGER_TEXT)&&
                success.logLines.get(0)
                    .contains(
                        "nativeClientTrigger=true"),
                "active scopesnipe="+
                success
            );

            require(
                effectWire.size()>0,
                "active scopesnipe emitted no wire"
            );

            requireWireText(
                effectWire.toByteArray(),
                ScopesightPetProfile
                    .NATIVE_TRIGGER_TEXT+
                    "\n"
            );

            int beforeMalformed=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome malformed=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .PET_SCOPE_SNIPE_ACTION+
                    ":extra",
                    "::scopesnipe",
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
                "malformed scopesnipe="+
                malformed
            );

            require(
                effectWire.size()==beforeMalformed,
                "malformed scopesnipe emitted wire"
            );

            require(
                petRuntime.handle(
                    new String[]{"scopesnipe"},
                    effectPackets
                )==null,
                "raw scopesnipe route remains"
            );

            System.out.println(
                "SCOPE_SNIPE_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "inactiveFailClosed=true "+
                "activeScopesight=true "+
                "nativeTriggerRuntimeOnly=true "+
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

    private static void requireWireText(
        byte[] wire,
        String value
    ){
        byte[] needle=
            value.getBytes(
                StandardCharsets.ISO_8859_1
            );

        outer:
        for(int i=0;
                i+needle.length<=wire.length;
                i++){
            for(int j=0;
                    j<needle.length;
                    j++)
                if(wire[i+j]!=needle[j])
                    continue outer;

            return;
        }

        throw new AssertionError(
            "missing wire text "+
            value+
            " bytes="+
            wire.length
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
                "scopesnipe content command failed "+
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

    private ScopeSnipeContentCommandActionOwnershipTest(){}
}
