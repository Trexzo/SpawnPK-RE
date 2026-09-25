package spk.local;

import java.util.concurrent.atomic.AtomicBoolean;
import spk.content.api.*;
import spk.plugin.api.*;

public final class PluginCallbackLeaseIsolationTest {
    private static final int ITEM_ON_PLAYER=924242;

    public static void main(String[] args)
        throws Exception{
        assertLeaseWrapperCoverage();

        World world=
            World.isolatedForTest(25L);
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayerAndStart(
                player,
                "plugin-lease-player"
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        LeasePlugin plugin=
            new LeasePlugin();

        plugin.nested=
            ()->world.content()
                .dispatchCommand(
                    player,
                    "leaseinner",
                    writer
                );

        PluginHandle handle=
            world.plugins()
                .enable(plugin);

        try{
            world.submitAndWait(
                player,
                generation,
                ()->{
                    ContentResult outer=
                        world.content()
                            .dispatchCommand(
                                player,
                                "leaseouter",
                                writer
                            );

                    if(outer==null||
                       !"LEASE_OUTER".equals(
                            outer.logText()))
                        throw new AssertionError(
                            "outer dispatch"
                        );
                },
                5_000L
            );

            if(!plugin.sameCallbackPlayer||
               !plugin.sameCallbackPresentation||
               !plugin.sameCallbackDialogue||
               !plugin.workerRejected||
               !plugin.innerSameCallback||
               !plugin.outerSurvivedNested||
               !plugin.innerRejectedAfterNested)
                throw new AssertionError(
                    "outer/nested lease semantics failed"
                );

            assertRejected(
                ()->plugin.outerContext.player(),
                "outer context player after return"
            );
            assertRejected(
                ()->plugin.outerContext.presentation(),
                "outer context presentation after return"
            );
            assertRejected(
                ()->plugin.outerPlayer.runEnergy(),
                "outer player after return"
            );
            assertRejected(
                ()->plugin.outerPresentation.runEnergy(55),
                "outer presentation after return"
            );
            assertRejected(
                ()->plugin.outerDialogue.close(),
                "outer dialogue after return"
            );
            assertRejected(
                ()->plugin.innerPlayer.runEnergy(),
                "inner player after return"
            );

            world.submitAndWait(
                player,
                generation,
                ()->{
                    ContentResult later=
                        world.content()
                            .dispatchCommand(
                                player,
                                "leaselater",
                                writer
                            );

                    if(later==null||
                       !"LEASE_LATER".equals(
                            later.logText()))
                        throw new AssertionError(
                            "later dispatch"
                        );
                },
                5_000L
            );

            if(!plugin.priorRejectedOnLaterWorldCallback||
               !plugin.laterFreshWorks)
                throw new AssertionError(
                    "prior/fresh later lease semantics failed"
                );

            world.submitAndWait(
                player,
                generation,
                ()->{
                    ContentActionResult action=
                        world.content()
                            .dispatchAction(
                                player,
                                "lease.action"
                            );

                    if(action==null||
                       !action.allowed())
                        throw new AssertionError(
                            "action dispatch"
                        );

                    ContentDialogueTransition dialogue=
                        world.content()
                            .dispatchDialogue(
                                player,
                                "lease.dialogue",
                                "root",
                                ContentDialogueIntent
                                    .continueIntent()
                            );

                    if(dialogue==null||
                       dialogue.kind()!=
                            ContentDialogueTransition
                                .Kind.STAY)
                        throw new AssertionError(
                            "dialogue dispatch"
                        );

                    ContentInteractionResult item=
                        world.content()
                            .dispatchItemOnPlayer(
                                ITEM_ON_PLAYER,
                                player
                            );

                    if(item==null||
                       !"LEASE_ITEM_PLAYER".equals(
                            item.outcome()))
                        throw new AssertionError(
                            "item-on-player dispatch"
                        );
                },
                5_000L
            );

            if(!plugin.actionSameCallback||
               !plugin.dialogueSameCallback||
               !plugin.itemPlayerSameCallback)
                throw new AssertionError(
                    "live context same-callback access failed"
                );

            assertRejected(
                ()->plugin.actionContext.player(),
                "action context after return"
            );
            assertRejected(
                ()->plugin.actionPlayer.runEnergy(),
                "action player after return"
            );
            assertRejected(
                ()->plugin.dialogueContext.player(),
                "dialogue context after return"
            );
            assertRejected(
                ()->plugin.dialoguePlayer.runEnergy(),
                "dialogue player after return"
            );
            assertRejected(
                ()->plugin.itemPlayerContext.target(),
                "item-on-player context after return"
            );
            assertRejected(
                ()->plugin.itemPlayerTarget.runEnergy(),
                "item-on-player target after return"
            );

            if(!world.plugins().disable(
                    "lease.probe"))
                throw new AssertionError(
                    "disable returned false"
                );

            if(handle.enabled())
                throw new AssertionError(
                    "lease plugin handle stayed enabled"
                );

            assertScopeReleasedWorld(
                handle
            );

            System.out.println(
                "PLUGIN_CALLBACK_LEASE_ISOLATION_PASS "+
                "liveContexts=4 "+
                "commandPlayer=true "+
                "commandPresentation=true "+
                "dialogueSubFacade=true "+
                "actionPlayer=true "+
                "dialoguePlayer=true "+
                "itemOnPlayerTarget=true "+
                "sameCallback=true "+
                "offThreadRejected=true "+
                "nestedIndependent=true "+
                "outerSurvivesNested=true "+
                "innerClosesAfterNested=true "+
                "staleAfterReturnRejected=true "+
                "priorLeaseRejectedOnLaterWorldCallback=true "+
                "freshLaterLease=true "+
                "scopeDisableFence=true "+
                "terminalScopeReleasesWorld=true "+
                "terminalScopeReleasesWorldOpen=true "+
                "facadeCoverageComplete=true "+
                "publicApiExpanded=false"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation))
                world.unregisterPlayer(
                    player,
                    generation
                );

            if(!world.closed())
                world.close();
        }
    }

    private static void assertScopeReleasedWorld(
        PluginHandle handle
    )throws Exception{
        java.lang.reflect.Field callbacksField=
            handle.getClass()
                .getDeclaredField(
                    "callbacks"
                );
        callbacksField.setAccessible(true);
        Object callbacks=
            callbacksField.get(handle);

        java.lang.reflect.Field executionField=
            callbacks.getClass()
                .getDeclaredField(
                    "worldExecution"
                );
        executionField.setAccessible(true);

        if(executionField.get(callbacks)!=null)
            throw new AssertionError(
                "terminal callback scope retained World execution supplier"
            );

        java.lang.reflect.Field openField=
            callbacks.getClass()
                .getDeclaredField(
                    "worldOpen"
                );
        openField.setAccessible(true);

        if(openField.get(callbacks)!=null)
            throw new AssertionError(
                "terminal callback scope retained World open supplier"
            );
    }

    private static void assertLeaseWrapperCoverage()
        throws Exception{
        assertOverridesAll(
            ContentPlayer.class,
            "spk.local.PluginCallbackScope$LeasedPlayer"
        );
        assertOverridesAll(
            ContentPresentation.class,
            "spk.local.PluginCallbackScope$LeasedPresentation"
        );
        assertOverridesAll(
            ContentDialoguePresentation.class,
            "spk.local.PluginCallbackScope$LeasedDialoguePresentation"
        );
        assertOverridesAll(
            ContentCommandContext.class,
            "spk.local.PluginCallbackScope$LeasedCommandContext"
        );
        assertOverridesAll(
            ContentActionContext.class,
            "spk.local.PluginCallbackScope$LeasedActionContext"
        );
        assertOverridesAll(
            ContentDialogueContext.class,
            "spk.local.PluginCallbackScope$LeasedDialogueContext"
        );
        assertOverridesAll(
            ContentItemOnPlayerContext.class,
            "spk.local.PluginCallbackScope$LeasedItemOnPlayerContext"
        );
    }

    private static void assertOverridesAll(
        Class<?> api,
        String wrapperName
    )throws Exception{
        Class<?> wrapper=
            Class.forName(
                wrapperName,
                false,
                PluginCallbackLeaseIsolationTest.class
                    .getClassLoader()
            );

        for(java.lang.reflect.Method method:
                api.getMethods()){
            if(method.getDeclaringClass()==
                    Object.class)
                continue;

            try{
                wrapper.getDeclaredMethod(
                    method.getName(),
                    method.getParameterTypes()
                );
            }catch(NoSuchMethodException missing){
                throw new AssertionError(
                    "lease wrapper missing API method api="+
                    api.getName()+
                    " method="+
                    method,
                    missing
                );
            }
        }
    }

    private static void assertRejected(
        ThrowingAction action,
        String phase
    )throws Exception{
        boolean rejected=false;

        try{
            action.run();
        }catch(IllegalStateException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                phase+
                " did not reject stale/off-thread facade"
            );
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface NestedDispatch {
        ContentResult run() throws Exception;
    }

    private static final class LeasePlugin
        implements Plugin {

        private final PluginManifest manifest=
            new PluginManifest(
                "lease.probe",
                "1.0.0"
            );

        NestedDispatch nested;

        ContentCommandContext outerContext;
        ContentPlayer outerPlayer;
        ContentPresentation outerPresentation;
        ContentDialoguePresentation outerDialogue;
        ContentPlayer innerPlayer;

        ContentActionContext actionContext;
        ContentPlayer actionPlayer;
        ContentDialogueContext dialogueContext;
        ContentPlayer dialoguePlayer;
        ContentItemOnPlayerContext itemPlayerContext;
        ContentPlayer itemPlayerTarget;

        volatile boolean sameCallbackPlayer;
        volatile boolean sameCallbackPresentation;
        volatile boolean sameCallbackDialogue;
        volatile boolean workerRejected;
        volatile boolean innerSameCallback;
        volatile boolean outerSurvivedNested;
        volatile boolean innerRejectedAfterNested;
        volatile boolean priorRejectedOnLaterWorldCallback;
        volatile boolean laterFreshWorks;
        volatile boolean actionSameCallback;
        volatile boolean dialogueSameCallback;
        volatile boolean itemPlayerSameCallback;

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        ){
            context.content().command(
                "leaseouter",
                100,
                command->{
                    outerContext=command;
                    outerPlayer=command.player();
                    outerPresentation=
                        command.presentation();
                    outerDialogue=
                        outerPresentation.dialogue();

                    sameCallbackPlayer=
                        outerPlayer.runEnergy()>=0;

                    outerPresentation.runEnergy(
                        outerPlayer.runEnergy()
                    );
                    sameCallbackPresentation=true;

                    outerDialogue.close();
                    sameCallbackDialogue=true;

                    AtomicBoolean rejected=
                        new AtomicBoolean();
                    Thread worker=
                        new Thread(
                            ()->{
                                try{
                                    outerPlayer.runEnergy();
                                }catch(
                                    IllegalStateException expected
                                ){
                                    rejected.set(true);
                                }
                            },
                            "plugin-lease-worker"
                        );

                    worker.start();

                    try{
                        worker.join(5_000L);
                    }catch(InterruptedException e){
                        Thread.currentThread()
                            .interrupt();
                        throw new RuntimeException(e);
                    }

                    if(worker.isAlive())
                        throw new AssertionError(
                            "lease worker did not finish"
                        );

                    workerRejected=rejected.get();

                    ContentResult inner=
                        nested.run();

                    if(inner==null||
                       !"LEASE_INNER".equals(
                            inner.logText()))
                        throw new AssertionError(
                            "inner dispatch"
                        );

                    outerSurvivedNested=
                        outerPlayer.runEnergy()>=0;

                    try{
                        innerPlayer.runEnergy();
                    }catch(
                        IllegalStateException expected
                    ){
                        innerRejectedAfterNested=true;
                    }

                    return ContentResult.handled(
                        "LEASE_OUTER",
                        null
                    );
                }
            );

            context.content().command(
                "leaseinner",
                100,
                command->{
                    innerPlayer=command.player();
                    innerSameCallback=
                        innerPlayer.runEnergy()>=0;

                    return ContentResult.handled(
                        "LEASE_INNER",
                        null
                    );
                }
            );

            context.content().command(
                "leaselater",
                100,
                command->{
                    try{
                        outerPlayer.runEnergy();
                    }catch(
                        IllegalStateException expected
                    ){
                        priorRejectedOnLaterWorldCallback=
                            true;
                    }

                    laterFreshWorks=
                        command.player().runEnergy()>=0;

                    return ContentResult.handled(
                        "LEASE_LATER",
                        null
                    );
                }
            );

            context.content().action(
                "lease.action",
                100,
                action->{
                    actionContext=action;
                    actionPlayer=action.player();
                    actionSameCallback=
                        actionPlayer.runEnergy()>=0;

                    return ContentActionResult.allow();
                }
            );

            context.content().dialogue(
                "lease.dialogue",
                100,
                dialogue->{
                    dialogueContext=dialogue;
                    dialoguePlayer=
                        dialogue.player();
                    dialogueSameCallback=
                        dialoguePlayer.runEnergy()>=0;

                    return ContentDialogueTransition.stay();
                }
            );

            context.content().itemOnPlayer(
                ITEM_ON_PLAYER,
                100,
                interaction->{
                    itemPlayerContext=interaction;
                    itemPlayerTarget=
                        interaction.target();
                    itemPlayerSameCallback=
                        itemPlayerTarget.runEnergy()>=0;

                    return ContentInteractionResult.handled(
                        "LEASE_ITEM_PLAYER"
                    );
                }
            );
        }
    }

    private PluginCallbackLeaseIsolationTest(){}
}
