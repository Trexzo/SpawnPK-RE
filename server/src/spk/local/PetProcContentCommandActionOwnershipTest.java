package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class PetProcContentCommandActionOwnershipTest {
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
                    "petproc"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "petproc binding="+binding
            );

            world.registerPlayer(
                player,
                "petproc-content-owner"
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
                    "::petproc",
                    policyPackets
                );

            assertAction(
                normal,
                LocalLabCoreContentModule
                    .PET_PROC_ACTION,
                "normal"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::petproc ignored trailing",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .PET_PROC_ACTION,
                "trailing compatibility"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "petproc content policy emitted wire bytes="+
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

            LocalContentCommandActionExecutor.Outcome inactive=
                executor.executeOutcome(
                    normal.actionKey(),
                    "::petproc",
                    "opensrc",
                    effectPackets
                );

            effectPackets.flush();

            require(
                inactive!=null&&
                inactive.logLines!=null&&
                inactive.logLines.size()==1&&
                inactive.logLines.get(0)
                    .contains(
                        "V511_PET_PROC_FIXTURE playerAnim=NONE playerGfx=1310 scopesight=NOT_SCOPESIGHT")&&
                inactive.logLines.get(0)
                    .contains(
                        "semantics=PRODUCTION_NORMAL_PET_BOOST_PRESENTATION"),
                "inactive petproc="+
                inactive
            );

            require(
                effectWire.size()>0,
                "inactive petproc emitted no GFX wire"
            );

            require(
                !containsWireText(
                    effectWire.toByteArray(),
                    ScopesightPetProfile
                        .NATIVE_TRIGGER_TEXT+
                        "\n"
                ),
                "inactive petproc emitted SNIPE"
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

            LocalContentCommandActionExecutor.Outcome active=
                executor.executeOutcome(
                    normal.actionKey(),
                    "::petproc",
                    "opensrc",
                    effectPackets
                );

            effectPackets.flush();

            require(
                active!=null&&
                active.logLines!=null&&
                active.logLines.size()==1&&
                active.logLines.get(0)
                    .contains(
                        "V511_PET_PROC_FIXTURE playerAnim=NONE playerGfx=1310 scopesight=PET_FORCE_TEXT_OK item="+
                        ScopesightPetProfile.ITEM_ID+
                        " npc="+
                        ScopesightPetProfile.NPC_ID+
                        " text="+
                        ScopesightPetProfile
                            .NATIVE_TRIGGER_TEXT)&&
                active.logLines.get(0)
                    .contains(
                        "semantics=PRODUCTION_NORMAL_PET_BOOST_PRESENTATION"),
                "active petproc="+
                active
            );

            require(
                effectWire.size()>0,
                "active petproc emitted no wire"
            );

            require(
                containsWireText(
                    effectWire.toByteArray(),
                    ScopesightPetProfile
                        .NATIVE_TRIGGER_TEXT+
                        "\n"
                ),
                "active petproc missing SNIPE wire"
            );

            int beforeMalformed=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome malformed=
                executor.executeOutcome(
                    LocalLabCoreContentModule
                        .PET_PROC_ACTION+
                    ":extra",
                    "::petproc",
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
                "malformed petproc="+
                malformed
            );

            require(
                effectWire.size()==beforeMalformed,
                "malformed petproc emitted wire"
            );

            require(
                petRuntime.handle(
                    new String[]{"petproc"},
                    effectPackets
                )==null,
                "raw petproc route remains"
            );

            System.out.println(
                "PET_PROC_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "playerGfxRuntimeOnly=true "+
                "inactiveNoSnipe=true "+
                "activeScopesightSnipe=true "+
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

    private static boolean containsWireText(
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

            return true;
        }

        return false;
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
                "petproc content command failed "+
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
                    341,
                    342,
                    343,
                    344
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

    private PetProcContentCommandActionOwnershipTest(){}
}
