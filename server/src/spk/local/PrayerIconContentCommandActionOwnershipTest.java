package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;

public final class PrayerIconContentCommandActionOwnershipTest {
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
                    "prayericon"
                );

            require(
                binding!=null&&
                "locallab-core".equals(
                    binding.moduleId)&&
                binding.priority==100&&
                binding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "prayericon binding="+
                binding
            );

            world.registerPlayer(
                player,
                "prayericon-content-owner"
            );
            world.start();

            ByteArrayOutputStream policyWire=
                new ByteArrayOutputStream();
            ServerPacketWriter policyPackets=
                writer(
                    policyWire
                );

            ContentResult missing=
                dispatch(
                    world,
                    player,
                    registry,
                    "::prayericon",
                    policyPackets
                );

            require(
                missing==null,
                "missing prayericon argument must preserve fallthrough result="+
                missing
            );

            assertRejected(
                dispatch(
                    world,
                    player,
                    registry,
                    "::prayericon nope",
                    policyPackets
                ),
                "invalid token"
            );

            assertRejected(
                dispatch(
                    world,
                    player,
                    registry,
                    "::prayericon 21",
                    policyPackets
                ),
                "high range"
            );

            assertRejected(
                dispatch(
                    world,
                    player,
                    registry,
                    "::prayericon -2",
                    policyPackets
                ),
                "low range"
            );

            ContentResult appliedPolicy=
                dispatch(
                    world,
                    player,
                    registry,
                    "::prayericon 3 ignored",
                    policyPackets
                );

            assertAction(
                appliedPolicy,
                LocalLabCoreContentModule
                    .PRAYER_ICON_ACTION_PREFIX+
                    ":3",
                "valid icon"
            );

            ContentResult clearPolicy=
                dispatch(
                    world,
                    player,
                    registry,
                    "::prayericon -1",
                    policyPackets
                );

            assertAction(
                clearPolicy,
                LocalLabCoreContentModule
                    .PRAYER_ICON_ACTION_PREFIX+
                    ":-1",
                "clear icon"
            );

            policyPackets.flush();

            require(
                policyWire.size()==0,
                "prayericon content policy emitted wire bytes="+
                policyWire.size()
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(
                    dev
                );

            LocalPrayerMagicCommandHandler prayerMagic=
                new LocalPrayerMagicCommandHandler(
                    player.prayers()
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
                    prayerMagic
                );

            ByteArrayOutputStream effectWire=
                new ByteArrayOutputStream();
            ServerPacketWriter effectPackets=
                writer(
                    effectWire
                );

            LocalContentCommandActionExecutor.Outcome
                applied=
                    executor.executeOutcome(
                        appliedPolicy.actionKey(),
                        "::prayericon 3 ignored",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            require(
                applied!=null&&
                applied.dialogResult==null&&
                applied.logLines==null&&
                applied.contentResult!=null&&
                applied.contentResult
                    .saveReason()==null&&
                applied.contentResult
                    .logText()
                    .equals(
                        "V510_PRAYER_ICON result=LOCAL_PRAYER_HEADICON_FIXTURE value=3 semanticMapping=UNASSIGNED_R25"),
                "prayericon runtime outcome="+
                applied
            );

            require(
                player.prayers()
                    .manualHeadIcon()==3,
                "prayericon runtime state="+
                player.prayers()
                    .manualHeadIcon()
            );

            require(
                effectWire.size()>0,
                "prayericon runtime emitted no packet"
            );

            int beforeMalformed=
                effectWire.size();
            int beforeState=
                player.prayers()
                    .manualHeadIcon();

            LocalContentCommandActionExecutor.Outcome
                malformed=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PRAYER_ICON_ACTION_PREFIX+
                        ":3:extra",
                        "::prayericon 3 extra",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            requireUnsupported(
                malformed,
                "malformed action"
            );

            require(
                effectWire.size()==beforeMalformed&&
                player.prayers()
                    .manualHeadIcon()==beforeState,
                "malformed prayericon mutated runtime"
            );

            LocalContentCommandActionExecutor.Outcome
                outOfRange=
                    executor.executeOutcome(
                        LocalLabCoreContentModule
                            .PRAYER_ICON_ACTION_PREFIX+
                        ":21",
                        "::prayericon 21",
                        "opensrc",
                        effectPackets
                    );

            effectPackets.flush();

            requireUnsupported(
                outOfRange,
                "out-of-range action"
            );

            require(
                effectWire.size()==beforeMalformed&&
                player.prayers()
                    .manualHeadIcon()==beforeState,
                "out-of-range prayericon mutated runtime"
            );

            runtimeBoundary();

            System.out.println(
                "PRAYER_ICON_CONTENT_COMMAND_ACTION_OWNERSHIP_PASS "+
                "binding=true "+
                "semanticAction=true "+
                "missingFallthrough=true "+
                "rangePolicy=true "+
                "extraArgsCompatibility=true "+
                "policyWireBytes=0 "+
                "runtimeEffect=true "+
                "malformedFailClosed=true "+
                "outOfRangeFailClosed=true "+
                "legacyParser=false "+
                "dispatcherFallback=false "+
                "magicDependencyTrim=true "+
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

    private static void runtimeBoundary()
        throws Exception{
        for(Method method:
                LocalPrayerMagicCommandHandler.class
                    .getDeclaredMethods())
            require(
                !"handle".equals(
                    method.getName()),
                "raw prayer/magic parser remains"
            );

        String dispatcher=
            source(
                "server/src/spk/local/LocalCommandDispatcher.java"
            );

        require(
            !dispatcher.contains(
                "prayerMagicCommands.handle("),
            "dispatcher prayer/magic fallback remains"
        );

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
                    "prayericon")&&
                !name.contains(
                    "headicon"),
                "prayer icon runtime leaked to public ContentPlayer method="+
                method.getName()
            );
        }
    }

    private static String source(
        String path
    )throws Exception{
        return new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(
                    path
                )
            ),
            java.nio.charset.StandardCharsets.UTF_8
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
                "prayericon content command failed "+
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

    private static void assertRejected(
        ContentResult result,
        String label
    ){
        require(
            result!=null&&
            !result.hasAction()&&
            result.saveReason()==null&&
            "V510_PRAYER_ICON result=REJECTED_HEADICON_RANGE expected=-1..20"
                .equals(
                    result.logText()),
            label+
            " result="+
            result
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
                    "REJECTED_UNSUPPORTED"),
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

    private PrayerIconContentCommandActionOwnershipTest(){}
}
