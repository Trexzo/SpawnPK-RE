package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class CosmeticContentCommandActionOwnershipTest {
    public static void main(String[] args)throws Exception{
        contentResultActionContract();

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
                    "cosmetic"
                )
            );

            world.registerPlayer(
                player,
                "cosmetic-content-owner"
            );
            world.start();

            ByteArrayOutputStream dispatchWire=
                new ByteArrayOutputStream();
            ServerPacketWriter dispatchPackets=
                writer(
                    dispatchWire
                );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::cosmetic",
                    dispatchPackets
                ),
                LocalLabCoreContentModule
                    .COSMETIC_INFO_ACTION,
                "default info"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::cosmetic status",
                    dispatchPackets
                ),
                LocalLabCoreContentModule
                    .COSMETIC_INFO_ACTION,
                "status alias"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::cosmetic off",
                    dispatchPackets
                ),
                LocalLabCoreContentModule
                    .COSMETIC_REMOVE_ACTION,
                "off alias"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::cosmetic remove",
                    dispatchPackets
                ),
                LocalLabCoreContentModule
                    .COSMETIC_REMOVE_ACTION,
                "remove alias"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::cosmetic unexpected",
                    dispatchPackets
                ),
                LocalLabCoreContentModule
                    .COSMETIC_HELP_ACTION,
                "help fallback"
            );

            dispatchPackets.flush();

            require(
                dispatchWire.size()==0,
                "content cosmetic policy emitted wire bytes="+
                dispatchWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            LocalCosmeticCommandHandler cosmetics=
                new LocalCosmeticCommandHandler(
                    player.bank(),
                    player.equipment(),
                    player.playerState(),
                    new PlayerPresentationService(
                        dev
                    )
                );
            LocalCompColorsCommandHandler compColors=
                new LocalCompColorsCommandHandler(
                    player.playerState(),
                    player.equipment(),
                    new PlayerPresentationService(
                        dev
                    )
                );
            LocalContentCommandActionExecutor executor=
                new LocalContentCommandActionExecutor(
                    cosmetics,
                    compColors,
                    new LocalMiniPetCommandHandler(
                        player.miniPets(),
                        player.petState(),
                        new NpcRegistry(
                            dev
                        ),
                        player.movement()
                    ),
                    new LocalPetCompatibilityCommandHandler(
                        player.petAccessoryState(),
                        new NpcRegistry(
                            dev
                        ),
                        player.movement(),
                        new LocalPetInventoryDialogHandler(
                            player.bank(),
                            player.miniPets(),
                            player.petState(),
                            new NpcRegistry(
                                dev
                            ),
                            player.movement(),
                            player.petAccessoryState()
                        )
                    )
                );

            ByteArrayOutputStream effectWire=
                new ByteArrayOutputStream();
            ServerPacketWriter effectPackets=
                writer(
                    effectWire
                );

            ContentResult info=
                executor.execute(
                    LocalLabCoreContentModule
                        .COSMETIC_INFO_ACTION,
                    "::cosmetic",
                    "cosmetic-content-owner",
                    effectPackets
                );

            require(
                info!=null&&
                !info.hasAction()&&
                info.saveReason()==null&&
                info.logText().startsWith(
                    "V5124_COSMETIC_INFO"),
                "cosmetic info effect="+
                info
            );

            int afterInfo=
                effectWire.size();

            ContentResult unknown=
                executor.execute(
                    "locallab.cosmetic.unknown",
                    "::cosmetic",
                    "cosmetic-content-owner",
                    effectPackets
                );

            require(
                unknown!=null&&
                !unknown.hasAction()&&
                unknown.saveReason()==null&&
                unknown.logText().equals(
                    "CONTENT_COMMAND_ACTION key="+
                    "locallab.cosmetic.unknown "+
                    "result=REJECTED_UNSUPPORTED"),
                "unknown action result="+
                unknown
            );

            require(
                effectWire.size()==afterInfo,
                "unknown action emitted wire bytes"
            );

            ContentResult emptyRemove=
                executor.execute(
                    LocalLabCoreContentModule
                        .COSMETIC_REMOVE_ACTION,
                    "::cosmetic",
                    "cosmetic-content-owner",
                    effectPackets
                );

            require(
                emptyRemove!=null&&
                emptyRemove.saveReason()==null&&
                emptyRemove.logText().contains(
                    "COSMETIC_NONE_ACTIVE"),
                "empty cosmetic remove="+
                emptyRemove
            );

            player.playerState()
                .cosmetic()
                .set(10556);
            player.playerState()
                .syncEquipmentPresentation(
                    player.equipment()
                );

            require(
                player.playerState()
                    .nativeIconItemId()==10556,
                "cosmetic active precondition"
            );

            int beforeRemove=
                effectWire.size();

            ContentResult removed=
                executor.execute(
                    LocalLabCoreContentModule
                        .COSMETIC_REMOVE_ACTION,
                    "::cosmetic",
                    "cosmetic-content-owner",
                    effectPackets
                );

            effectPackets.flush();

            require(
                removed!=null&&
                "COSMETIC_OFF".equals(
                    removed.saveReason())&&
                removed.logText().contains(
                    "COSMETIC_UNEQUIP_OK item=10556"),
                "successful cosmetic remove="+
                removed
            );

            require(
                !player.playerState()
                    .cosmetic()
                    .active()&&
                player.playerState()
                    .nativeIconItemId()==-1&&
                player.bank()
                    .inventoryCount(10556)==1,
                "successful cosmetic effect state mismatch"
            );

            require(
                effectWire.size()>beforeRemove,
                "successful cosmetic remove emitted no wire"
            );

            ContentResult help=
                executor.execute(
                    LocalLabCoreContentModule
                        .COSMETIC_HELP_ACTION,
                    "::cosmetic",
                    "cosmetic-content-owner",
                    effectPackets
                );

            require(
                help!=null&&
                help.saveReason()==null&&
                help.logText().startsWith(
                    "V511_COSMETIC_HELP"),
                "cosmetic help effect="+
                help
            );

            runtimeBoundary();

            System.out.println(
                "COSMETIC_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "aliases=true "+
                "policyWireBytes=0 "+
                "allowlist=true "+
                "unknownFailClosed=true "+
                "removeState=true "+
                "removeWire=true "+
                "saveReason=true "+
                "legacyParser=false "+
                "dispatcherExecutor=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(
                    player
                );
            world.close();
        }
    }

    private static void contentResultActionContract(){
        ContentResult handled=
            ContentResult.handled(
                "ok",
                "SAVE"
            );

        require(
            !handled.hasAction()&&
            handled.actionKey()==null&&
            "ok".equals(
                handled.logText())&&
            "SAVE".equals(
                handled.saveReason()),
            "handled ContentResult compatibility"
        );

        ContentResult action=
            ContentResult.action(
                "  LocalLab.Cosmetic.Info  "
            );

        require(
            action.hasAction()&&
            "locallab.cosmetic.info".equals(
                action.actionKey())&&
            "".equals(
                action.logText())&&
            action.saveReason()==null,
            "action key normalization="+
            action
        );

        boolean rejected=false;

        try{
            ContentResult.action(
                "bad key"
            );
        }catch(IllegalArgumentException expected){
            rejected=true;
        }

        require(
            rejected,
            "invalid semantic command action accepted"
        );
    }

    private static void runtimeBoundary(){
        boolean executorField=false;

        for(Field field:
                LocalCommandDispatcher.class
                    .getDeclaredFields())
            if(field.getType()==
                    LocalContentCommandActionExecutor.class)
                executorField=true;

        require(
            executorField,
            "dispatcher lacks semantic command action executor"
        );

        for(Method method:
                LocalCosmeticCommandHandler.class
                    .getDeclaredMethods())
            require(
                !"handle".equals(
                    method.getName()),
                "legacy cosmetic parser remains in runtime handler"
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
                "cosmetic content command failed "+
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

    private static void assertBinding(
        ContentRegistry.BindingInfo binding
    ){
        require(
            binding!=null&&
            "locallab-core".equals(
                binding.moduleId)&&
            binding.priority==100&&
            binding.provenance==
                ContentProvenance.CUSTOM_LOCALLAB,
            "cosmetic binding="+
            binding
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{201,202,203,204}
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

    private CosmeticContentCommandActionOwnershipTest(){}
}
