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

            AtomicReference<ContentRegistration>
                topologyOverride=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "dialogue-topology-override",
                    registrar->
                        topologyOverride.set(
                            registrar.dialogue(
                                MakeoverMageDialogueContent
                                    .DIALOGUE_KEY,
                                300,
                                new OneOptionMakeoverHandler()
                            )
                        )
                )
            );

            ContentRegistry.BindingInfo topologyBinding=
                registry.dialogueDefinitionBinding(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                );

            require(
                topologyBinding!=null&&
                "dialogue-topology-override".equals(
                    topologyBinding.moduleId)&&
                topologyBinding.priority==300&&
                topologyBinding.provenance==
                    ContentProvenance.CUSTOM_LOCALLAB,
                "topology override binding "+
                topologyBinding
            );

            ContentDialogueDefinition overridden=
                registry.dialogueDefinition(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                );

            ContentDialogueNode overriddenOptions=
                overridden.node(
                    MakeoverMageDialogueContent
                        .OPTIONS_NODE
                );

            require(
                overriddenOptions!=null&&
                overriddenOptions.inputMode()==
                    ContentDialogueNode
                        .InputMode.OPTIONS&&
                overriddenOptions.optionCount()==1&&
                !overriddenOptions.closeSupported(),
                "one-option override definition"
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
            NpcEntity mage=
                adjacentMage(
                    player,
                    51
                );

            begin(
                world,
                player,
                routed,
                mage
            );

            continueDialogue(
                world,
                player,
                routed,
                new ByteArrayOutputStream()
            );

            DialogueSessionService.Snapshot customOptions=
                routed.makeoverMage()
                    .semanticDialogueSnapshot();

            require(
                customOptions.active&&
                MakeoverMageDialogueContent
                    .OPTIONS_NODE
                    .equals(
                        customOptions.nodeKey)&&
                customOptions.optionCount==1&&
                !customOptions.closeSupported&&
                customOptions.revision==2L,
                "runtime did not consume override topology"
            );

            ByteArrayOutputStream rejectedWire=
                new ByteArrayOutputStream();
            AtomicReference<Throwable> rejection=
                new AtomicReference<>();

            world.submitAndWait(
                player,
                ()->{
                    try{
                        routed.makeoverMage()
                            .handleOption(
                                2,
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
                rejection.get()!=null,
                "option 2 accepted by one-option topology"
            );
            require(
                rejectedWire.size()==0,
                "invalid topology input emitted bytes="+
                rejectedWire.size()
            );

            DialogueSessionService.Snapshot afterReject=
                routed.makeoverMage()
                    .semanticDialogueSnapshot();

            require(
                afterReject.active&&
                afterReject.revision==2L&&
                afterReject.optionCount==1,
                "invalid option mutated semantic session"
            );

            routed.makeoverMage().cancel();

            require(
                topologyOverride.get()!=null&&
                topologyOverride.get().unregister(),
                "topology override unregister"
            );
            require(
                transitionOnly.get()!=null&&
                transitionOnly.get().unregister(),
                "transition-only override unregister"
            );

            assertBuiltInDefinition(
                registry,
                "restored built-in"
            );

            NpcEntity secondMage=
                adjacentMage(
                    player,
                    52
                );

            begin(
                world,
                player,
                routed,
                secondMage
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
                "priorityTopologyOverride=true "+
                "keyMismatchRollback=true "+
                "runtimeRefresh=true "+
                "invalidOptionPartialWire=false "+
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
        ContentRegistry.BindingInfo binding=
            registry.dialogueDefinitionBinding(
                MakeoverMageDialogueContent
                    .DIALOGUE_KEY
            );

        ContentDialogueDefinition definition=
            registry.dialogueDefinition(
                MakeoverMageDialogueContent
                    .DIALOGUE_KEY
            );

        require(
            binding!=null&&
            "locallab-core".equals(
                binding.moduleId)&&
            binding.priority==100&&
            binding.provenance==
                ContentProvenance.CUSTOM_LOCALLAB,
            phase+" binding="+binding
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

    private static void begin(
        World world,
        WorldPlayer player,
        LocalRoutedNpcInteractionHandler routed,
        NpcEntity mage
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
                            writer(
                                new ByteArrayOutputStream()
                            ),
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

    private static final class OneOptionMakeoverHandler
        implements ContentDialogueHandler {

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
               context.intent().kind()==
                    ContentDialogueIntent
                        .Kind.OPTION)
                return ContentDialogueTransition.end();

            throw new IllegalStateException(
                "unsupported one-option Make-over transition"
            );
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
