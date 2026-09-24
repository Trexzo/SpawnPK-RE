package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class DevHitContentCommandActionOwnershipTest {
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
                    "devhit"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "devhit binding="+binding
            );

            world.registerPlayer(
                player,
                "devhit-content-owner"
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
                    "::devhit",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_INFO_ACTION,
                "default info"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit info",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_INFO_ACTION,
                "explicit info"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit reset",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_RESET_ACTION,
                "reset"
            );

            for(String alias:
                    new String[]{
                        "auto",
                        "off",
                        "reset"
                    })
                assertAction(
                    dispatch(
                        world,
                        player,
                        registry,
                        "::devhit damage "+alias,
                        policyPackets
                    ),
                    LocalLabCoreContentModule
                        .DEV_HIT_DAMAGE_AUTO_ACTION,
                    "damage "+alias
                );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit damage 37",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_DAMAGE_ACTION_PREFIX+
                    ":37",
                "damage 37"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit damage nope",
                    policyPackets
                ),
                "V5128_DEVHIT_REJECTED damage=0..255|auto",
                "bad damage"
            );

            for(String alias:
                    new String[]{
                        "off",
                        "auto",
                        "reset"
                    })
                assertAction(
                    dispatch(
                        world,
                        player,
                        registry,
                        "::devhit sequence "+alias,
                        policyPackets
                    ),
                    LocalLabCoreContentModule
                        .DEV_HIT_SEQUENCE_OFF_ACTION,
                    "sequence "+alias
                );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit sequence 37,100",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_SEQUENCE_ACTION_PREFIX+
                    ":37:100",
                "sequence values"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit sequence 37",
                    policyPackets
                ),
                "V5128_DEVHIT_REJECTED sequence=comma-separated_2..16_values_0..255",
                "short sequence"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit sequence 37,nope",
                    policyPackets
                ),
                "V5128_DEVHIT_REJECTED sequence=example_37,100",
                "invalid sequence"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit sequence 37,999",
                    policyPackets
                ),
                "V5128_DEVHIT_REJECTED sequence=value_range_0..255",
                "range sequence"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit variant auto",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_VARIANT_AUTO_ACTION,
                "variant auto"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit variant manual",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_VARIANT_MANUAL_ACTION,
                "variant manual"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit variant nope",
                    policyPackets
                ),
                "V5128_DEVHIT_REJECTED variant=auto|manual",
                "variant reject"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit next",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_NEXT_ACTION,
                "next"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit prev",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_PREV_ACTION,
                "prev"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit type 4",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_TYPE_ACTION_PREFIX+
                    ":4",
                "type"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit type 999",
                    policyPackets
                ),
                "V5128_DEVHIT_REJECTED type=0..255",
                "type reject"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit styleicon 7",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_STYLE_ICON_ACTION_PREFIX+
                    ":7",
                "styleicon"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit styleicon -1",
                    policyPackets
                ),
                "V5128_DEVHIT_REJECTED styleicon=0..255",
                "styleicon reject"
            );

            assertAction(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit placement primary",
                    policyPackets
                ),
                LocalLabCoreContentModule
                    .DEV_HIT_PLACEMENT_PRIMARY_ACTION,
                "placement primary"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit placement secondary",
                    policyPackets
                ),
                "V5128_DEVHIT_REJECTED placement=secondary reason=current_NPC_sync_encoder_only_certifies_primary_singleHit_mask",
                "placement secondary"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit placement nope",
                    policyPackets
                ),
                "V5128_DEVHIT_REJECTED placement=primary|secondary",
                "placement reject"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit damage",
                    policyPackets
                ),
                "V5128_DEVHIT_HELP info | variant auto|manual | type <0..255> | next | prev | damage <0..255|auto> | sequence <a,b,...|off> | styleicon <0..255> | placement primary|secondary | reset",
                "missing damage help"
            );

            assertHandled(
                dispatch(
                    world,
                    player,
                    registry,
                    "::devhit unexpected",
                    policyPackets
                ),
                "V5128_DEVHIT_HELP info | variant auto|manual | type <0..255> | next | prev | damage <0..255|auto> | sequence <a,b,...|off> | styleicon <0..255> | placement primary|secondary | reset",
                "unknown help"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "devhit content policy emitted wire bytes="+
                policyWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);
            CombatEngine combat=
                new CombatEngine(dev);
            LocalPetRuntimeCommandHandler petRuntime=
                new LocalPetRuntimeCommandHandler(
                    player.petState(),
                    player.petEffects(),
                    npcs,
                    player.movement()
                );
            LocalCombatCommandHandler combatHandler=
                new LocalCombatCommandHandler(
                    combat,
                    player.equipment(),
                    player.combatStyles(),
                    npcs,
                    petRuntime
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
                    combatHandler
                );

            ByteArrayOutputStream effectWire=
                new ByteArrayOutputStream();
            ServerPacketWriter effectPackets=
                writer(
                    effectWire
                );

            ContentResult info=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_INFO_ACTION,
                    effectPackets
                );
            require(
                info.logText().contains(
                    "V5128_type=6")&&
                info.logText().contains(
                    "variantMode=auto(normal=1,max=6)")&&
                info.logText().contains(
                    "styleIcon=255"),
                "info effect="+info
            );

            ContentResult manual=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_VARIANT_MANUAL_ACTION,
                    effectPackets
                );
            require(
                manual.logText().contains(
                    "variantMode=manual"),
                "manual effect="+manual
            );

            ContentResult type=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_TYPE_ACTION_PREFIX+
                    ":4",
                    effectPackets
                );
            require(
                type.logText().contains(
                    "type=4")&&
                type.logText().contains(
                    "variantMode=manual"),
                "type effect="+type
            );

            ContentResult next=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_NEXT_ACTION,
                    effectPackets
                );
            require(
                next.logText().contains(
                    "type=5"),
                "next effect="+next
            );

            ContentResult prev=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_PREV_ACTION,
                    effectPackets
                );
            require(
                prev.logText().contains(
                    "type=4"),
                "prev effect="+prev
            );

            ContentResult damage=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_DAMAGE_ACTION_PREFIX+
                    ":37",
                    effectPackets
                );
            require(
                damage.logText().contains(
                    "damageMode=fixed:37"),
                "damage effect="+damage
            );

            ContentResult damageAuto=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_DAMAGE_AUTO_ACTION,
                    effectPackets
                );
            require(
                damageAuto.logText().contains(
                    "damageMode=legacy_context_fixture"),
                "damage auto="+damageAuto
            );

            ContentResult sequence=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_SEQUENCE_ACTION_PREFIX+
                    ":37:100",
                    effectPackets
                );
            require(
                sequence.logText().contains(
                    "damageMode=sequence[37, 100]@0"),
                "sequence effect="+sequence
            );

            ContentResult sequenceOff=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_SEQUENCE_OFF_ACTION,
                    effectPackets
                );
            require(
                sequenceOff.logText().contains(
                    "damageMode=legacy_context_fixture"),
                "sequence off="+sequenceOff
            );

            ContentResult style=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_STYLE_ICON_ACTION_PREFIX+
                    ":7",
                    effectPackets
                );
            require(
                style.logText().contains(
                    "styleIcon=7")&&
                style.logText().contains(
                    "transportNote=current_NPC_singleHit_mask_has_no_styleIcon_field"),
                "style icon effect="+style
            );

            ContentResult placement=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_PLACEMENT_PRIMARY_ACTION,
                    effectPackets
                );
            require(
                placement.logText().contains(
                    "placement=primary"),
                "placement effect="+placement
            );

            String beforeMalformed=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_INFO_ACTION,
                    effectPackets
                ).logText();

            ContentResult malformed=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_SEQUENCE_ACTION_PREFIX+
                    ":37",
                    effectPackets
                );

            require(
                malformed.logText().contains(
                    "REJECTED_UNSUPPORTED"),
                "malformed sequence action="+malformed
            );

            ContentResult malformedScalar=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_TYPE_ACTION_PREFIX+
                    ":999",
                    effectPackets
                );

            require(
                malformedScalar.logText().contains(
                    "REJECTED_UNSUPPORTED"),
                "malformed scalar action="+
                malformedScalar
            );

            String afterMalformed=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_INFO_ACTION,
                    effectPackets
                ).logText();

            require(
                beforeMalformed.equals(
                    afterMalformed),
                "malformed actions mutated devhit state"
            );

            ContentResult reset=
                execute(
                    executor,
                    LocalLabCoreContentModule
                        .DEV_HIT_RESET_ACTION,
                    effectPackets
                );

            require(
                reset.logText().contains(
                    "V5128_DEVHIT_RESET")&&
                reset.logText().contains(
                    "type=6")&&
                reset.logText().contains(
                    "variantMode=auto(normal=1,max=6)")&&
                reset.logText().contains(
                    "styleIcon=255")&&
                reset.logText().contains(
                    "damageMode=legacy_context_fixture"),
                "reset effect="+reset
            );

            effectPackets.flush();

            require(
                effectWire.size()==0,
                "devhit runtime effects emitted wire bytes="+
                effectWire.size()
            );

            runtimeBoundary();

            System.out.println(
                "DEV_HIT_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticActions=true "+
                "legacySyntax=true "+
                "legacyRejections=true "+
                "sequencePolicy=true "+
                "policyWireBytes=0 "+
                "typedRuntimeEffects=true "+
                "malformedFailClosed=true "+
                "runtimeWireBytes=0 "+
                "combatEngineParser=false "+
                "handlerParser=false "+
                "dispatcherFallback=false "+
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

    private static ContentResult execute(
        LocalContentCommandActionExecutor executor,
        String actionKey,
        ServerPacketWriter packets
    )throws Exception{
        return executor.execute(
            actionKey,
            "::devhit test",
            "devhit-content-owner",
            packets
        );
    }

    private static void runtimeBoundary(){
        for(Method method:
                CombatEngine.class
                    .getDeclaredMethods())
            require(
                !"devHitCommand".equals(
                    method.getName()),
                "CombatEngine raw devHitCommand parser remains"
            );

        for(Method method:
                LocalCombatCommandHandler.class
                    .getDeclaredMethods())
            require(
                !"handle".equals(
                    method.getName()),
                "LocalCombatCommandHandler raw parser remains"
            );

        for(Method method:
                ContentResult.class
                    .getDeclaredMethods())
            require(
                !method.getName()
                    .toLowerCase(
                        java.util.Locale.ROOT
                    )
                    .contains(
                        "devhit"),
                "devhit runtime surface leaked into public ContentResult"
            );

        try{
            String dispatcher=
                new String(
                    java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get(
                            "server/src/spk/local/LocalCommandDispatcher.java"
                        )
                    ),
                    java.nio.charset.StandardCharsets.UTF_8
                );

            require(
                !dispatcher.contains(
                    "combatCommands.handle("),
                "dispatcher raw combat fallback remains"
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
                "devhit content command failed "+
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
        String expected,
        String label
    ){
        require(
            result!=null&&
            !result.hasAction()&&
            result.saveReason()==null&&
            expected.equals(
                result.logText()),
            label+
            " handled result="+
            result
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{271,272,273,274}
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

    private DevHitContentCommandActionOwnershipTest(){}
}
