package spk.local;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;
import spk.content.builtin.MakeoverMageDialogueContent;
import spk.plugin.api.*;

public final class ContentDialogueTransitionOwnershipTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(20L);
        WorldPlayer player=
            new WorldPlayer();

        try{
            ContentRegistry registry=
                world.content();

            assertBinding(
                registry.dialogueBinding(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                ),
                "locallab-core",
                100,
                ContentProvenance.CUSTOM_LOCALLAB,
                "built-in dialogue"
            );

            boolean conflict=false;
            try{
                registry.installCustom(
                    module(
                        "dialogue-equal-conflict",
                        100,
                        context->
                            ContentDialogueTransition.stay(),
                        null
                    )
                );
            }catch(IllegalStateException expected){
                conflict=
                    expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "content binding conflict"
                    );
            }

            require(
                conflict,
                "equal-priority dialogue conflict accepted"
            );

            boolean offThreadRejected=false;
            try{
                registry.dispatchDialogue(
                    player,
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY,
                    MakeoverMageDialogueContent
                        .INTRO_NODE,
                    ContentDialogueIntent
                        .continueIntent()
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
                "off-thread dialogue dispatch accepted"
            );

            world.registerPlayer(
                player,
                "dialogue-content-owner"
            );
            world.start();

            ContentDialogueTransition builtIn=
                dispatch(
                    world,
                    player,
                    registry,
                    MakeoverMageDialogueContent
                        .INTRO_NODE,
                    ContentDialogueIntent
                        .continueIntent()
                );

            require(
                builtIn.kind()==
                    ContentDialogueTransition.Kind.MOVE&&
                MakeoverMageDialogueContent
                    .OPTIONS_NODE
                    .equals(
                        builtIn.nextNodeKey()
                    ),
                "built-in transition"
            );

            AtomicReference<ContentRegistration>
                overrideHandle=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "dialogue-explicit-override",
                    200,
                    context->
                        ContentDialogueTransition.stay(),
                    overrideHandle
                )
            );

            assertBinding(
                registry.dialogueBinding(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                ),
                "dialogue-explicit-override",
                200,
                ContentProvenance.CUSTOM_LOCALLAB,
                "explicit override"
            );

            ContentDialogueTransition overridden=
                dispatch(
                    world,
                    player,
                    registry,
                    MakeoverMageDialogueContent
                        .INTRO_NODE,
                    ContentDialogueIntent
                        .continueIntent()
                );

            require(
                overridden.kind()==
                    ContentDialogueTransition.Kind.STAY,
                "explicit override transition"
            );

            require(
                overrideHandle.get()!=null&&
                overrideHandle.get().active()&&
                overrideHandle.get().unregister()&&
                !overrideHandle.get().active(),
                "explicit dialogue unregister"
            );

            assertBinding(
                registry.dialogueBinding(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                ),
                "locallab-core",
                100,
                ContentProvenance.CUSTOM_LOCALLAB,
                "fallback after explicit unregister"
            );

            DialogueOverridePlugin plugin=
                new DialogueOverridePlugin();

            PluginHandle pluginHandle=
                world.plugins().enable(plugin);

            require(
                pluginHandle.enabled(),
                "dialogue plugin not enabled"
            );

            assertBinding(
                registry.dialogueBinding(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                ),
                "plugin:dialogue.transition.override",
                300,
                ContentProvenance.CUSTOM_LOCALLAB,
                "plugin override"
            );

            ContentDialogueTransition pluginResult=
                dispatch(
                    world,
                    player,
                    registry,
                    MakeoverMageDialogueContent
                        .OPTIONS_NODE,
                    ContentDialogueIntent.option(1)
                );

            require(
                pluginResult.kind()==
                    ContentDialogueTransition.Kind.STAY,
                "plugin override transition"
            );

            require(
                world.plugins().disable(
                    "dialogue.transition.override"
                ),
                "dialogue plugin disable"
            );

            require(
                !pluginHandle.enabled(),
                "disabled dialogue plugin handle still live"
            );

            assertBinding(
                registry.dialogueBinding(
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY
                ),
                "locallab-core",
                100,
                ContentProvenance.CUSTOM_LOCALLAB,
                "built-in restored after plugin disable"
            );

            ContentDialogueTransition restored=
                dispatch(
                    world,
                    player,
                    registry,
                    MakeoverMageDialogueContent
                        .OPTIONS_NODE,
                    ContentDialogueIntent.closeIntent()
                );

            require(
                restored.kind()==
                    ContentDialogueTransition.Kind.END,
                "restored built-in close transition"
            );

            System.out.println(
                "CONTENT_DIALOGUE_TRANSITION_OWNERSHIP_PASS "+
                "builtIn=true "+
                "priorityOverride=true "+
                "equalPriorityConflict=true "+
                "offThreadRejected=true "+
                "explicitUnregisterRestore=true "+
                "pluginOverride=true "+
                "pluginDisableRestore=true "+
                "rawTransportExposed=false"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static ContentDialogueTransition dispatch(
        World world,
        WorldPlayer player,
        ContentRegistry registry,
        String nodeKey,
        ContentDialogueIntent intent
    )throws Exception{
        AtomicReference<ContentDialogueTransition>
            result=
                new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->result.set(
                registry.dispatchDialogue(
                    player,
                    MakeoverMageDialogueContent
                        .DIALOGUE_KEY,
                    nodeKey,
                    intent
                )
            ),
            5_000L
        );

        return result.get();
    }

    private static ContentModule module(
        String id,
        int priority,
        ContentDialogueHandler handler,
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
                    registrar.dialogue(
                        MakeoverMageDialogueContent
                            .DIALOGUE_KEY,
                        priority,
                        handler
                    );

                if(registration!=null)
                    registration.set(handle);
            }
        };
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo info,
        String module,
        int priority,
        ContentProvenance provenance,
        String label
    ){
        require(
            info!=null&&
            module.equals(info.moduleId)&&
            info.priority==priority&&
            info.provenance==provenance,
            label+" actual="+info
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private static final class DialogueOverridePlugin
        implements Plugin {

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "dialogue.transition.override",
                "1.0.0",
                PluginApiVersion.CURRENT,
                Collections.<String>emptyList()
            );
        }

        @Override public void enable(
            PluginContext context
        ){
            context.content().dialogue(
                MakeoverMageDialogueContent
                    .DIALOGUE_KEY,
                300,
                dialogue->
                    ContentDialogueTransition.stay()
            );
        }
    }

    private ContentDialogueTransitionOwnershipTest(){}
}
