package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class CompColorsContentCommandActionOwnershipTest {
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
                    "compcolors"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "compcolors binding="+binding
            );

            world.registerPlayer(
                player,
                "compcolors-content-owner"
            );
            world.start();

            ByteArrayOutputStream policyWire=
                new ByteArrayOutputStream();
            ServerPacketWriter policyPackets=
                writer(
                    policyWire
                );

            ContentResult valid=
                dispatch(
                    world,
                    player,
                    registry,
                    "::compcolors 1 2 3 4 5 6",
                    policyPackets
                );

            require(
                valid!=null&&
                valid.hasAction()&&
                (
                    LocalLabCoreContentModule
                        .COMP_COLORS_APPLY_ACTION_PREFIX+
                    ":1:2:3:4:5:6"
                ).equals(
                    valid.actionKey())&&
                valid.saveReason()==null,
                "valid action="+valid
            );

            ContentResult invalid=
                dispatch(
                    world,
                    player,
                    registry,
                    "::compcolors 1 2 3 4 5 99",
                    policyPackets
                );

            require(
                invalid!=null&&
                !invalid.hasAction()&&
                invalid.saveReason()==null&&
                invalid.logText().equals(
                    "V54_COMP_COLORS command="+
                    "::compcolors 1 2 3 4 5 99 "+
                    "result=REJECTED_SELECTOR_RANGE expected=0..19"
                ),
                "invalid result="+invalid
            );

            ContentResult wrongArity=
                dispatch(
                    world,
                    player,
                    registry,
                    "::compcolors 1 2",
                    policyPackets
                );

            require(
                wrongArity!=null&&
                !wrongArity.hasAction()&&
                wrongArity.saveReason()==null&&
                wrongArity.logText().contains(
                    "REJECTED_SELECTOR_RANGE"),
                "wrong arity="+wrongArity
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "content policy emitted wire bytes="+
                policyWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();

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
                        new NpcRegistry(
                            dev
                        ),
                        player.movement()
                    )
                );

            ByteArrayOutputStream effectWire=
                new ByteArrayOutputStream();
            ServerPacketWriter effectPackets=
                writer(
                    effectWire
                );

            ContentResult applied=
                executor.execute(
                    valid.actionKey(),
                    "::compcolors 1 2 3 4 5 6",
                    "compcolors-content-owner",
                    effectPackets
                );

            require(
                applied!=null&&
                !applied.hasAction()&&
                "COMP_COLORS".equals(
                    applied.saveReason())&&
                applied.logText().equals(
                    "V55_COMP_COLORS command="+
                    "::compcolors 1 2 3 4 5 6 "+
                    "result=APPLIED selectors=[1, 2, 3, 4, 5, 6] "+
                    "capeEquipped=false appearanceRefresh=false"
                ),
                "apply result="+applied
            );

            require(
                Arrays.equals(
                    new int[]{1,2,3,4,5,6},
                    player.playerState()
                        .compSelectors()
                ),
                "selectors not applied"
            );

            int beforeUnknown=
                effectWire.size();

            ContentResult malformed=
                executor.execute(
                    LocalLabCoreContentModule
                        .COMP_COLORS_APPLY_ACTION_PREFIX+
                    ":1:2:3:4:5:99",
                    "::compcolors 1 2 3 4 5 99",
                    "compcolors-content-owner",
                    effectPackets
                );

            require(
                malformed!=null&&
                malformed.saveReason()==null&&
                malformed.logText().equals(
                    "CONTENT_COMMAND_ACTION key="+
                    LocalLabCoreContentModule
                        .COMP_COLORS_APPLY_ACTION_PREFIX+
                    ":1:2:3:4:5:99 "+
                    "result=REJECTED_UNSUPPORTED"
                ),
                "malformed executor action="+
                malformed
            );

            require(
                effectWire.size()==beforeUnknown&&
                Arrays.equals(
                    new int[]{1,2,3,4,5,6},
                    player.playerState()
                        .compSelectors()
                ),
                "malformed action mutated runtime"
            );

            player.equipment()
                .set(
                    EquipmentSlot.CAPE,
                    23063
                );

            int beforeCape=
                effectWire.size();

            ContentResult capeApplied=
                executor.execute(
                    LocalLabCoreContentModule
                        .COMP_COLORS_APPLY_ACTION_PREFIX+
                    ":6:5:4:3:2:1",
                    "::compcolors 6 5 4 3 2 1",
                    "compcolors-content-owner",
                    effectPackets
                );

            effectPackets.flush();

            require(
                capeApplied!=null&&
                "COMP_COLORS".equals(
                    capeApplied.saveReason())&&
                capeApplied.logText().contains(
                    "capeEquipped=true appearanceRefresh=true"),
                "equipped cape result="+
                capeApplied
            );

            require(
                effectWire.size()>beforeCape,
                "equipped cape emitted no appearance wire"
            );

            runtimeBoundary();

            System.out.println(
                "COMP_COLORS_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "strictSelectors=true "+
                "invalidHandled=true "+
                "policyWireBytes=0 "+
                "allowlist=true "+
                "malformedFailClosed=true "+
                "stateMutation=true "+
                "saveReason=true "+
                "appearanceRefresh=true "+
                "legacyParser=false "+
                "dispatcherFallback=false"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static void runtimeBoundary(){
        for(Method method:
                LocalCompColorsCommandHandler.class
                    .getDeclaredMethods())
            require(
                !"handle".equals(
                    method.getName()),
                "legacy compcolors parser remains"
            );

        try{
            String source=
                new String(
                    java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get(
                            "server/src/spk/local/LocalCommandDispatcher.java"
                        )
                    ),
                    java.nio.charset.StandardCharsets.UTF_8
                );

            require(
                !source.contains(
                    "compColorsCommands.handle("),
                "dispatcher direct compcolors fallback remains"
            );
        }catch(java.io.IOException error){
            throw new AssertionError(
                "dispatcher source audit failed",
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
                "compcolors content command failed "+
                command,
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
                new int[]{211,212,213,214}
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

    private CompColorsContentCommandActionOwnershipTest(){}
}
