package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;
import spk.content.builtin.MakeoverMageDialogueContent;
import spk.plugin.api.*;

public final class ContentSemanticActionOwnershipTest {
    public static void main(String[] args)throws Exception{
        actionResultContract();
        registryAndPluginLifecycle();
        makeoverDesignAuthorization();

        System.out.println(
            "CONTENT_SEMANTIC_ACTION_OWNERSHIP_PASS "+
            "registry=true "+
            "priorityOverride=true "+
            "pluginLifecycle=true "+
            "makeoverDesignGate=true "+
            "rawDesignPayloadExposed=false "+
            "denyNoMutation=true "+
            "denyNoSave=true "+
            "restoreApply=true"
        );
    }

    private static void actionResultContract(){
        ContentActionResult allow=
            ContentActionResult.allow();
        ContentActionResult deny=
            ContentActionResult.deny(
                "  Policy:Blocked  "
            );

        require(
            allow.allowed()&&
            allow.decision()==
                ContentActionResult.Decision.ALLOW&&
            allow.reasonKey()==null,
            "allow contract"
        );

        require(
            !deny.allowed()&&
            deny.decision()==
                ContentActionResult.Decision.DENY&&
            "policy:blocked".equals(
                deny.reasonKey()),
            "deny contract"
        );

        expect(
            IllegalArgumentException.class,
            ()->ContentActionResult.deny(
                "bad reason key"
            ),
            "unsafe deny reason"
        );
    }

    private static void registryAndPluginLifecycle()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            ContentRegistry registry=
                world.content();

            assertBinding(
                registry.actionBinding(
                    MakeoverMageDialogueContent
                        .ACTION_APPLY_CHARACTER_DESIGN
                ),
                "locallab-core",
                100,
                "built-in"
            );

            boolean equalConflict=false;
            try{
                registry.installCustom(
                    module(
                        "action-equal-conflict",
                        100,
                        context->
                            ContentActionResult.allow(),
                        null
                    )
                );
            }catch(IllegalStateException expected){
                equalConflict=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "content binding conflict"
                    );
            }

            require(
                equalConflict,
                "equal-priority action conflict accepted"
            );

            boolean offThreadRejected=false;
            try{
                registry.dispatchAction(
                    player,
                    MakeoverMageDialogueContent
                        .ACTION_APPLY_CHARACTER_DESIGN
                );
            }catch(IllegalStateException expected){
                offThreadRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "World execution context"
                    );
            }

            require(
                offThreadRejected,
                "off-thread action dispatch accepted"
            );

            AtomicReference<ContentRegistration>
                override=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "action-explicit-override",
                    200,
                    context->
                        ContentActionResult.deny(
                            "test:blocked"
                        ),
                    override
                )
            );

            assertBinding(
                registry.actionBinding(
                    MakeoverMageDialogueContent
                        .ACTION_APPLY_CHARACTER_DESIGN
                ),
                "action-explicit-override",
                200,
                "explicit override"
            );

            world.registerPlayer(
                player,
                "semantic-action-owner"
            );
            world.start();

            AtomicReference<ContentActionResult>
                result=new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->result.set(
                    registry.dispatchAction(
                        player,
                        MakeoverMageDialogueContent
                            .ACTION_APPLY_CHARACTER_DESIGN
                    )
                ),
                5_000L
            );

            require(
                result.get()!=null&&
                !result.get().allowed()&&
                "test:blocked".equals(
                    result.get().reasonKey()),
                "explicit action override result"
            );

            require(
                override.get()!=null&&
                override.get().active()&&
                override.get().unregister()&&
                !override.get().active(),
                "explicit action unregister"
            );

            assertBinding(
                registry.actionBinding(
                    MakeoverMageDialogueContent
                        .ACTION_APPLY_CHARACTER_DESIGN
                ),
                "locallab-core",
                100,
                "explicit fallback"
            );

            ActionOverridePlugin plugin=
                new ActionOverridePlugin();

            PluginHandle handle=
                world.plugins().enable(
                    plugin
                );

            require(
                handle.enabled(),
                "action plugin not enabled"
            );

            assertBinding(
                registry.actionBinding(
                    MakeoverMageDialogueContent
                        .ACTION_APPLY_CHARACTER_DESIGN
                ),
                "plugin:semantic.action.override",
                300,
                "plugin override"
            );

            result.set(null);
            world.submitAndWait(
                player,
                ()->result.set(
                    registry.dispatchAction(
                        player,
                        MakeoverMageDialogueContent
                            .ACTION_APPLY_CHARACTER_DESIGN
                    )
                ),
                5_000L
            );

            require(
                result.get()!=null&&
                !result.get().allowed()&&
                "plugin:blocked".equals(
                    result.get().reasonKey()),
                "plugin action result"
            );

            require(
                world.plugins().disable(
                    "semantic.action.override"
                )&&
                !handle.enabled(),
                "action plugin disable"
            );

            assertBinding(
                registry.actionBinding(
                    MakeoverMageDialogueContent
                        .ACTION_APPLY_CHARACTER_DESIGN
                ),
                "locallab-core",
                100,
                "plugin fallback"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void makeoverDesignAuthorization()
        throws Exception
    {
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            AtomicReference<ContentRegistration>
                deny=
                    new AtomicReference<>();

            world.content().installCustom(
                module(
                    "makeover-design-deny",
                    250,
                    context->
                        ContentActionResult.deny(
                            "makeover:test-denied"
                        ),
                    deny
                )
            );

            world.registerPlayer(
                player,
                "makeover-action-owner"
            );
            world.start();

            LocalRoutedNpcInteractionHandler routed=
                routed(
                    world,
                    player
                );

            PlayerState state=
                player.playerState();
            int beforeGender=
                state.characterGender();
            int[] beforeKits=
                state.characterKits();
            int[] beforeColours=
                state.characterColours();

            openDesigner(
                world,
                player,
                routed,
                71
            );

            CharacterDesignRequest request=
                validFemaleDesign();

            ByteArrayOutputStream deniedWire=
                new ByteArrayOutputStream();
            AtomicReference<LocalMakeoverMageHandler.Result>
                denied=
                    new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        ServerPacketWriter packets=
                            writer(deniedWire);

                        denied.set(
                            routed.makeoverMage()
                                .handleDesign(
                                    request,
                                    packets,
                                    "[semantic-action-test] "
                                )
                        );
                        packets.flush();
                    }catch(Exception failure){
                        throw new RuntimeException(
                            failure
                        );
                    }
                },
                5_000L
            );

            LocalMakeoverMageHandler.Result deniedResult=
                denied.get();

            require(
                deniedResult!=null&&
                deniedResult.handled&&
                deniedResult.saveReason==null&&
                deniedResult.logText!=null&&
                deniedResult.logText.contains(
                    "CONTENT_ACTION_DENIED")&&
                deniedResult.logText.contains(
                    "makeover:test-denied"),
                "denied design result"
            );

            assertAppearance(
                state,
                beforeGender,
                beforeKits,
                beforeColours,
                "denied"
            );

            require(
                !routed.makeoverMage()
                    .designActive()&&
                deniedWire.size()>0,
                "denied design stage not closed"
            );

            require(
                deny.get()!=null&&
                deny.get().unregister(),
                "design deny unregister"
            );

            assertBinding(
                world.content()
                    .actionBinding(
                        MakeoverMageDialogueContent
                            .ACTION_APPLY_CHARACTER_DESIGN
                    ),
                "locallab-core",
                100,
                "design built-in restore"
            );

            openDesigner(
                world,
                player,
                routed,
                72
            );

            ByteArrayOutputStream acceptedWire=
                new ByteArrayOutputStream();
            AtomicReference<LocalMakeoverMageHandler.Result>
                accepted=
                    new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        ServerPacketWriter packets=
                            writer(acceptedWire);

                        accepted.set(
                            routed.makeoverMage()
                                .handleDesign(
                                    request,
                                    packets,
                                    "[semantic-action-test] "
                                )
                        );
                        packets.flush();
                    }catch(Exception failure){
                        throw new RuntimeException(
                            failure
                        );
                    }
                },
                5_000L
            );

            LocalMakeoverMageHandler.Result acceptedResult=
                accepted.get();

            require(
                acceptedResult!=null&&
                acceptedResult.handled&&
                "CHARACTER_DESIGN".equals(
                    acceptedResult.saveReason)&&
                acceptedResult.logText!=null&&
                acceptedResult.logText.contains(
                    "appearanceStateApplied=true"),
                "accepted design result"
            );

            assertAppearance(
                state,
                CharacterDesignProfile.FEMALE,
                new int[]{45,-1,56,61,67,70,79},
                new int[]{11,15,14,5,23},
                "accepted"
            );

            require(
                !routed.makeoverMage()
                    .designActive()&&
                acceptedWire.size()>0,
                "accepted design stage not closed"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static ContentModule module(
        String id,
        int priority,
        ContentActionHandler handler,
        AtomicReference<ContentRegistration>
            registration
    ){
        return new ContentModule(){
            @Override public String id(){
                return id;
            }

            @Override public void register(
                ContentRegistrar registrar
            ){
                ContentRegistration handle=
                    registrar.action(
                        MakeoverMageDialogueContent
                            .ACTION_APPLY_CHARACTER_DESIGN,
                        priority,
                        handler
                    );

                if(registration!=null)
                    registration.set(handle);
            }
        };
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo binding,
        String moduleId,
        int priority,
        String phase
    ){
        require(
            binding!=null&&
            moduleId.equals(
                binding.moduleId)&&
            binding.priority==priority&&
            binding.provenance==
                ContentProvenance.CUSTOM_LOCALLAB,
            phase+" binding="+binding
        );
    }

    private static LocalRoutedNpcInteractionHandler
        routed(
            World world,
            WorldPlayer player
        ){
        return new LocalRoutedNpcInteractionHandler(
            new NpcRegistry(),
            player.bank(),
            player.movement(),
            world.content(),
            player,
            player.equipment()
        );
    }

    private static void openDesigner(
        World world,
        WorldPlayer player,
        LocalRoutedNpcInteractionHandler routed,
        int scene
    )throws Exception{
        NpcEntity mage=
            new NpcEntity(
                scene,
                LocalLabCoreContentModule
                    .MAKEOVER_MAGE_NPC,
                player.movement().x()+1,
                player.movement().y()
            );

        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    ByteArrayOutputStream wire=
                        new ByteArrayOutputStream();
                    ServerPacketWriter packets=
                        writer(wire);

                    String residual=
                        routed.handle(
                            new NpcAction(
                                155,
                                mage.sceneIndex
                            ),
                            mage,
                            packets,
                            "[semantic-action-test] "
                        );

                    if(residual!=null)
                        throw new AssertionError(
                            "open residual="+
                            residual
                        );

                    if(!routed.makeoverMage()
                            .handleContinue(
                                StandardDialoguePresentationAdapter
                                    .namedNpcContinueWidget(1),
                                packets,
                                "[semantic-action-test] "))
                        throw new AssertionError(
                            "Continue not handled"
                        );

                    if(!routed.makeoverMage()
                            .handleOption(
                                1,
                                packets,
                                "[semantic-action-test] "))
                        throw new AssertionError(
                            "option 1 not handled"
                        );

                    packets.flush();
                }catch(Throwable error){
                    failure.set(error);
                }
            },
            5_000L
        );

        if(failure.get()!=null)
            throw new AssertionError(
                "open designer failed",
                failure.get()
            );

        require(
            routed.makeoverMage()
                .designActive(),
            "designer not active"
        );
    }

    private static CharacterDesignRequest
        validFemaleDesign()
    {
        return new CharacterDesignRequest(
            CharacterDesignProfile.FEMALE,
            new int[]{45,-1,56,61,67,70,79},
            new int[]{11,15,14,5,23}
        );
    }

    private static void assertAppearance(
        PlayerState state,
        int gender,
        int[] kits,
        int[] colours,
        String phase
    ){
        require(
            state.characterGender()==gender,
            phase+" gender="+
            state.characterGender()
        );
        require(
            Arrays.equals(
                state.characterKits(),
                kits
            ),
            phase+" kits="+
            Arrays.toString(
                state.characterKits()
            )
        );
        require(
            Arrays.equals(
                state.characterColours(),
                colours
            ),
            phase+" colours="+
            Arrays.toString(
                state.characterColours()
            )
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{121,122,123,124}
            )
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private static final class ActionOverridePlugin
        implements Plugin {

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "semantic.action.override",
                "1.0.0",
                PluginApiVersion.CURRENT,
                Collections.<String>emptyList()
            );
        }

        @Override public void enable(
            PluginContext context
        ){
            context.content().action(
                MakeoverMageDialogueContent
                    .ACTION_APPLY_CHARACTER_DESIGN,
                300,
                action->
                    ContentActionResult.deny(
                        "plugin:blocked"
                    )
            );
        }
    }

    private ContentSemanticActionOwnershipTest(){}
}
