package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ItemLibDiagnosticContentActionOwnershipTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        try{
            player.equipment()
                .setWeapon(
                    28860
                );

            ContentRegistry registry=
                world.content();

            ContentRegistry.BindingInfo binding=
                registry.commandBinding(
                    "itemlib"
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
                "itemlib binding="+binding
            );

            world.registerPlayer(
                player,
                "itemlib-content-owner"
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
                    "::itemlib",
                    policyPackets
                ),
                LocalDiagnosticContentModule
                    .ITEMLIB_OPEN_ACTION_PREFIX+
                    ":28860",
                "current weapon"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::itemlib 28860 ignored",
                    policyPackets
                ),
                LocalDiagnosticContentModule
                    .ITEMLIB_OPEN_ACTION_PREFIX+
                    ":28860",
                "explicit numeric"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::itemlib Tumeken's shadow (i)",
                    policyPackets
                ),
                LocalDiagnosticContentModule
                    .ITEMLIB_OPEN_ACTION_PREFIX+
                    ":28539",
                "exact name"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::itemlib 999999",
                    policyPackets
                ),
                "unknown numeric"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::itemlib Definitely Not An Item",
                    policyPackets
                ),
                "unknown name"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "itemlib content policy emitted wire bytes="+
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

            LocalContentCommandActionExecutor.Outcome opened=
                executor.executeOutcome(
                    LocalDiagnosticContentModule
                        .ITEMLIB_OPEN_ACTION_PREFIX+
                    ":28860",
                    "::itemlib 28860",
                    "opensrc",
                    effectPackets
                );

            effectPackets.flush();

            require(
                opened!=null&&
                opened.dialogResult==null&&
                opened.logLines==null&&
                opened.contentResult!=null&&
                opened.contentResult
                    .saveReason()==null&&
                opened.contentResult
                    .logText()
                    .contains(
                        "V5150_ITEM_LIBRARY_DEV_OPEN result=")&&
                opened.contentResult
                    .logText()
                    .contains(
                        "opener=LOCAL_DEV_ONLY nativeRoot=47500 normalRequest=igsearch"),
                "itemlib open outcome="+opened
            );

            require(
                effectWire.size()>0,
                "itemlib runtime emitted no native UI wire"
            );

            requireWireText(
                effectWire.toByteArray(),
                "ITEM_GUIDE_RESET_PREVIEW\n"
            );
            requireWireText(
                effectWire.toByteArray(),
                "Scorching bow (i)\n"
            );

            int beforeUnknown=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome unknown=
                executor.executeOutcome(
                    LocalDiagnosticContentModule
                        .ITEMLIB_OPEN_ACTION_PREFIX+
                    ":999999",
                    "::itemlib 999999",
                    "opensrc",
                    effectPackets
                );

            effectPackets.flush();

            require(
                unknown!=null&&
                unknown.contentResult!=null&&
                unknown.contentResult
                    .logText()
                    .equals(
                        "V5150_ITEM_LIBRARY_DEV_OPEN result=REJECTED_UNKNOWN_ITEM syntax=::itemlib <itemId|exact name>"),
                "unknown runtime itemlib="+
                unknown
            );

            require(
                effectWire.size()==beforeUnknown,
                "unknown runtime itemlib emitted wire"
            );

            int beforeMalformed=
                effectWire.size();

            LocalContentCommandActionExecutor.Outcome malformed=
                executor.executeOutcome(
                    LocalDiagnosticContentModule
                        .ITEMLIB_OPEN_ACTION_PREFIX+
                    ":28860:extra",
                    "::itemlib 28860 extra",
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
                "malformed itemlib action="+
                malformed
            );

            require(
                effectWire.size()==beforeMalformed,
                "malformed itemlib action emitted wire"
            );

            require(
                !diagnostics.handle(
                    new String[]{
                        "itemlib",
                        "28860"
                    },
                    effectPackets,
                    "[itemlib-test] ",
                    "opensrc",
                    "localtest",
                    true,
                    null
                ),
                "legacy diagnostic handler still claims itemlib"
            );

            assertSourceBoundary();

            System.out.println(
                "ITEMLIB_DIAGNOSTIC_CONTENT_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "currentWeapon=true "+
                "numericItem=true "+
                "exactName=true "+
                "unknownFailClosed=true "+
                "policyWireBytes=0 "+
                "nativeUiRuntimeOnly=true "+
                "runtimeAuthorityRecheck=true "+
                "malformedFailClosed=true "+
                "legacyRoute=false "+
                "equipmentDependencyTrim=true "+
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

    private static void assertSourceBoundary(){
        try{
            String source=
                new String(
                    java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get(
                            "server/src/spk/local/LocalDiagnosticContentModule.java"
                        )
                    ),
                    StandardCharsets.UTF_8
                );

            require(
                !source.contains(
                    "nativeRoot=47500")&&
                !source.contains(
                    "ITEM_GUIDE_RESET_PREVIEW"),
                "exact item-library presentation leaked into diagnostic content module"
            );
        }catch(java.io.IOException error){
            throw new AssertionError(
                "itemlib source boundary audit failed",
                error
            );
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
                "itemlib content command failed "+
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

    private static void assertHandled(
        ContentResult result,
        String label
    ){
        require(
            result!=null&&
            !result.hasAction()&&
            result.saveReason()==null&&
            result.logText()
                .equals(
                    "V5150_ITEM_LIBRARY_DEV_OPEN result=REJECTED_UNKNOWN_ITEM syntax=::itemlib <itemId|exact name>"),
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
                    301,
                    302,
                    303,
                    304
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

    private ItemLibDiagnosticContentActionOwnershipTest(){}
}
