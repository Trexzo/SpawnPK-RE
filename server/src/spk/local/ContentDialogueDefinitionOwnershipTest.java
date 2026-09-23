package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.LocalLabCoreContentModule;
import spk.content.builtin.MakeoverMageDialogueContent;

public final class ContentDialogueDefinitionOwnershipTest {
    public static void main(String[] args)throws Exception{
        definitionValidation();

        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            ContentRegistry registry=
                world.content();

            assertBuiltInDefinition(
                registry,
                "initial built-in"
            );

            AtomicReference<ContentRegistration>
                transitionOnly=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "dialogue-transition-only",
                    registrar->
                        transitionOnly.set(
                            registrar.dialogue(
                                MakeoverMageDialogueContent
                                    .DIALOGUE_KEY,
                                200,
                                context->
                                    ContentDialogueTransition
                                        .stay()
                            )
                        )
                )
            );

            require(
                "dialogue-transition-only".equals(
                    registry.dialogueBinding(
                        MakeoverMageDialogueContent
                            .DIALOGUE_KEY
                    ).moduleId),
                "transition-only override not selected"
            );

            assertBuiltInDefinition(
                registry,
                "transition-only topology inheritance"
            );

            require(
                transitionOnly.get()!=null&&
                transitionOnly.get().unregister(),
                "transition-only unregister"
            );

            AtomicReference<ContentRegistration>
                compatibleOverride=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "dialogue-topology-compatible",
                    registrar->
                        compatibleOverride.set(
                            registrar.dialogue(
                                MakeoverMageDialogueContent
                                    .DIALOGUE_KEY,
                                300,
                                new CompatibleMakeoverHandler()
                            )
                        )
                )
            );

            assertDefinitionBinding(
                registry,
                "dialogue-topology-compatible",
                300,
                "compatible topology override"
            );

            boolean mismatchRejected=false;
            try{
                registry.installCustom(
                    module(
                        "dialogue-definition-mismatch",
                        registrar->
                            registrar.dialogue(
                                MakeoverMageDialogueContent
                                    .DIALOGUE_KEY,
                                400,
                                new MismatchedDefinitionHandler()
                            )
                    )
                );
            }catch(IllegalArgumentException expected){
                mismatchRejected=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "dialogue definition key mismatch"
                    );
            }

            require(
                mismatchRejected&&
                !registry.moduleInstalled(
                    "dialogue-definition-mismatch"
                ),
                "definition-key mismatch did not roll back"
            );

            world.registerPlayer(
                player,
                "dialogue-definition-owner"
            );
            world.start();

            LocalRoutedNpcInteractionHandler routed=
                routed(
                    world,
                    player
                );

            NpcEntity firstMage=
                adjacentMage(
                    player,
                    51
                );

            begin(
                world,
                player,
                routed,
                firstMage,
                new ByteArrayOutputStream()
            );
            continueDialogue(
                world,
                player,
                routed,
                new ByteArrayOutputStream()
            );

            DialogueSessionService.Snapshot compatible=
                routed.makeoverMage()
                    .semanticDialogueSnapshot();

            require(
                compatible.active&&
                MakeoverMageDialogueContent
                    .OPTIONS_NODE
                    .equals(
                        compatible.nodeKey)&&
                compatible.optionCount==2&&
                compatible.closeSupported&&
                compatible.revision==2L,
                "compatible topology not consumed by runtime"
            );

            cancel(
                world,
                player,
                routed
            );

            require(
                compatibleOverride.get()!=null&&
                compatibleOverride.get().unregister(),
                "compatible topology unregister"
            );

            assertBuiltInDefinition(
                registry,
                "restored built-in before incompatible probe"
            );

            AtomicReference<ContentRegistration>
                incompatibleOverride=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "dialogue-topology-incompatible",
                    registrar->
                        incompatibleOverride.set(
                            registrar.dialogue(
                                MakeoverMageDialogueContent
                                    .DIALOGUE_KEY,
                                300,
                                new OneOptionMakeoverHandler()
                            )
                        )
                )
            );

            ContentDialogueDefinition incompatible=
                registry.dialogueDefinition(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                );

            require(
                incompatible!=null&&
                incompatible.node(
                    MakeoverMageDialogueContent
                        .OPTIONS_NODE
                ).optionCount()==1,
                "incompatible topology not selected by registry"
            );

            ByteArrayOutputStream rejectedWire=
                new ByteArrayOutputStream();
            AtomicReference<Throwable> rejection=
                new AtomicReference<>();

            NpcEntity rejectedMage=
                adjacentMage(
                    player,
                    52
                );

            world.submitAndWait(
                player,
                ()->{
                    try{
                        routed.handle(
                            new NpcAction(
                                155,
                                rejectedMage.sceneIndex
                            ),
                            rejectedMage,
                            writer(rejectedWire),
                            "[dialogue-definition-test] "
                        );
                    }catch(Throwable failure){
                        rejection.set(failure);
                    }
                },
                5_000L
            );

            require(
                rejection.get() instanceof
                    IllegalStateException&&
                rejection.get().getMessage()!=null&&
                rejection.get().getMessage().contains(
                    "incompatible Make-over options topology"
                ),
                "incompatible topology did not fail closed "+
                rejection.get()
            );

            require(
                rejectedWire.size()==0,
                "incompatible topology emitted presentation bytes="+
                rejectedWire.size()
            );

            require(
                !routed.makeoverMage()
                    .semanticDialogueSnapshot()
                    .active,
                "incompatible topology left semantic dialogue active"
            );

            require(
                incompatibleOverride.get()!=null&&
                incompatibleOverride.get().unregister(),
                "incompatible topology unregister"
            );

            assertBuiltInDefinition(
                registry,
                "restored built-in after incompatible probe"
            );

            NpcEntity restoredMage=
                adjacentMage(
                    player,
                    53
                );

            begin(
                world,
                player,
                routed,
                restoredMage,
                new ByteArrayOutputStream()
            );
            continueDialogue(
                world,
                player,
                routed,
                new ByteArrayOutputStream()
            );

            DialogueSessionService.Snapshot restored=
                routed.makeoverMage()
                    .semanticDialogueSnapshot();

            require(
                restored.active&&
                restored.optionCount==2&&
                restored.closeSupported&&
                restored.revision==2L,
                "next session did not refresh restored topology"
            );

            System.out.println(
                "CONTENT_DIALOGUE_DEFINITION_OWNERSHIP_PASS "+
                "builtInTopology=true "+
                "transitionOnlyInherits=true "+
                "compatibleOverride=true "+
                "keyMismatchRollback=true "+
                "incompatibleOverrideFailsClosed=true "+
                "partialWire=false "+
                "runtimeRefresh=true "+
                "unregisterRestore=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static void definitionValidation(){
        expect(
            IllegalArgumentException.class,
            ()->new ContentDialogueNode(
                "node:intro",
                ContentDialogueNode
                    .InputMode.CONTINUE,
                1,
                false
            ),
            "CONTINUE option count"
        );

        expect(
            IllegalArgumentException.class,
            ()->new ContentDialogueNode(
                "node:intro",
                ContentDialogueNode
                    .InputMode.CONTINUE,
                0,
                true
            ),
            "CONTINUE close"
        );

        expect(
            IllegalArgumentException.class,
            ()->new ContentDialogueNode(
                "node:options",
                ContentDialogueNode
                    .InputMode.OPTIONS,
                0,
                false
            ),
            "OPTIONS minimum"
        );

        expect(
            IllegalArgumentException.class,
            ()->new ContentDialogueDefinition(
                "dialogue:test",
                "node:missing",
                Collections.singletonList(
                    new ContentDialogueNode(
                        "node:intro",
                        ContentDialogueNode
                            .InputMode.CONTINUE,
                        0,
                        false
                    )
                )
            ),
            "missing start node"
        );
    }

    private static void assertBuiltInDefinition(
        ContentRegistry registry,
        String phase
    ){
        assertDefinitionBinding(
            registry,
            "locallab-core",
            100,
            phase
        );

        ContentDialogueDefinition definition=
            registry.dialogueDefinition(
                MakeoverMageDialogueContent
                    .DIALOGUE_KEY
            );

        require(
            definition!=null&&
            MakeoverMageDialogueContent
                .DIALOGUE_KEY
                .equals(
                    definition.dialogueKey())&&
            MakeoverMageDialogueContent
                .INTRO_NODE
                .equals(
                    definition.startNodeKey())&&
            definition.nodes().size()==2,
            phase+" definition"
        );

        ContentDialogueNode intro=
            definition.node(
                MakeoverMageDialogueContent
                    .INTRO_NODE
            );
        ContentDialogueNode options=
            definition.node(
                MakeoverMageDialogueContent
                    .OPTIONS_NODE
            );

        require(
            intro!=null&&
            intro.inputMode()==
                ContentDialogueNode
                    .InputMode.CONTINUE&&
            intro.optionCount()==0&&
            !intro.closeSupported(),
            phase+" intro"
        );

        require(
            options!=null&&
            options.inputMode()==
                ContentDialogueNode
                    .InputMode.OPTIONS&&
            options.optionCount()==2&&
            options.closeSupported(),
            phase+" options"
        );
    }

    private static void assertDefinitionBinding(
        ContentRegistry registry,
        String module,
        int priority,
        String phase
    ){
        ContentRegistry.BindingInfo binding=
            registry.dialogueDefinitionBinding(
                MakeoverMageDialogueContent
                    .DIALOGUE_KEY
            );

        require(
            binding!=null&&
            module.equals(binding.moduleId)&&
            binding.priority==priority&&
            binding.provenance==
                ContentProvenance.CUSTOM_LOCALLAB,
            phase+" binding="+binding
        );
    }

    private static void begin(
        World world,
        WorldPlayer player,
        LocalRoutedNpcInteractionHandler routed,
        NpcEntity mage,
        ByteArrayOutputStream wire
    )throws Exception{
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    String residual=
                        routed.handle(
                            new NpcAction(
                                155,
                                mage.sceneIndex
                            ),
                            mage,
                            writer(wire),
                            "[dialogue-definition-test] "
                        );

                    if(residual!=null)
                        failure.set(
                            new AssertionError(
                                "begin residual="+
                                residual
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
                "begin failed",
                failure.get()
            );
    }

    private static void continueDialogue(
        World world,
        WorldPlayer player,
        LocalRoutedNpcInteractionHandler routed,
        ByteArrayOutputStream wire
    )throws Exception{
        AtomicReference<Throwable> failure=
            new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                try{
                    if(!routed.makeoverMage()
                            .handleContinue(
                                StandardDialoguePresentationAdapter
                                    .namedNpcContinueWidget(1),
                                writer(wire),
                                "[dialogue-definition-test] "
                            ))
                        failure.set(
                            new AssertionError(
                                "Continue not handled"
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
                "Continue failed",
                failure.get()
            );
    }

    private static void cancel(
        World world,
        WorldPlayer player,
        LocalRoutedNpcInteractionHandler routed
    )throws Exception{
        world.submitAndWait(
            player,
            ()->routed.makeoverMage().cancel(),
            5_000L
        );
    }

    private static ContentModule module(
        String id,
        ModuleBody body
    ){
        return new ContentModule(){
            @Override public String id(){
                return id;
            }

            @Override public void register(
                ContentRegistrar registrar
            ){
                body.register(registrar);
            }
        };
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

    private static NpcEntity adjacentMage(
        WorldPlayer player,
        int scene
    ){
        return new NpcEntity(
            scene,
            LocalLabCoreContentModule
                .MAKEOVER_MAGE_NPC,
            player.movement().x()+1,
            player.movement().y()
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{101,102,103,104}
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

    @FunctionalInterface
    private interface ModuleBody{
        void register(
            ContentRegistrar registrar
        );
    }

    private abstract static class BaseMakeoverHandler
        implements ContentDialogueHandler {

        @Override public ContentDialogueTransition handle(
            ContentDialogueContext context
        ){
            if(MakeoverMageDialogueContent
                    .INTRO_NODE
                    .equals(
                        context.nodeKey())&&
               context.intent().kind()==
                    ContentDialogueIntent
                        .Kind.CONTINUE)
                return ContentDialogueTransition.move(
                    MakeoverMageDialogueContent
                        .OPTIONS_NODE
                );

            if(MakeoverMageDialogueContent
                    .OPTIONS_NODE
                    .equals(
                        context.nodeKey())&&
               (context.intent().kind()==
                    ContentDialogueIntent
                        .Kind.OPTION||
                context.intent().kind()==
                    ContentDialogueIntent
                        .Kind.CLOSE))
                return ContentDialogueTransition.end();

            throw new IllegalStateException(
                "unsupported test Make-over transition"
            );
        }
    }

    private static final class CompatibleMakeoverHandler
        extends BaseMakeoverHandler {

        private final ContentDialogueDefinition definition=
            new ContentDialogueDefinition(
                MakeoverMageDialogueContent
                    .DIALOGUE_KEY,
                MakeoverMageDialogueContent
                    .INTRO_NODE,
                Arrays.asList(
                    new ContentDialogueNode(
                        MakeoverMageDialogueContent
                            .INTRO_NODE,
                        ContentDialogueNode
                            .InputMode.CONTINUE,
                        0,
                        false
                    ),
                    new ContentDialogueNode(
                        MakeoverMageDialogueContent
                            .OPTIONS_NODE,
                        ContentDialogueNode
                            .InputMode.OPTIONS,
                        2,
                        true
                    )
                )
            );

        @Override public ContentDialogueDefinition definition(){
            return definition;
        }
    }

    private static final class OneOptionMakeoverHandler
        extends BaseMakeoverHandler {

        private final ContentDialogueDefinition definition=
            new ContentDialogueDefinition(
                MakeoverMageDialogueContent
                    .DIALOGUE_KEY,
                MakeoverMageDialogueContent
                    .INTRO_NODE,
                Arrays.asList(
                    new ContentDialogueNode(
                        MakeoverMageDialogueContent
                            .INTRO_NODE,
                        ContentDialogueNode
                            .InputMode.CONTINUE,
                        0,
                        false
                    ),
                    new ContentDialogueNode(
                        MakeoverMageDialogueContent
                            .OPTIONS_NODE,
                        ContentDialogueNode
                            .InputMode.OPTIONS,
                        1,
                        false
                    )
                )
            );

        @Override public ContentDialogueDefinition definition(){
            return definition;
        }
    }

    private static final class MismatchedDefinitionHandler
        implements ContentDialogueHandler {

        @Override public ContentDialogueDefinition definition(){
            return new ContentDialogueDefinition(
                "dialogue:other",
                "node:start",
                Collections.singletonList(
                    new ContentDialogueNode(
                        "node:start",
                        ContentDialogueNode
                            .InputMode.CONTINUE,
                        0,
                        false
                    )
                )
            );
        }

        @Override public ContentDialogueTransition handle(
            ContentDialogueContext context
        ){
            return ContentDialogueTransition.stay();
        }
    }

    private ContentDialogueDefinitionOwnershipTest(){}
}
