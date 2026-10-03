package spk.local;

import java.util.*;
import java.util.concurrent.atomic.*;
import spk.content.api.*;
import spk.event.DomainEventBus;
import spk.plugin.api.*;

public final class PluginKernelLifecycleTest {
    private static final String COMMAND=
        "pluginkernelprobe";
    private static final int NPC=910001;
    private static final int OBJECT=910002;
    private static final int ITEM=910003;

    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(25L);

        installBaseContent(world.content());

        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayerAndStart(
                player,
                "plugin-kernel-player"
            );

        PluginManager manager=
            world.plugins();

        int listenerBaseline=
            world.domainEvents()
                .listenerCount();

        ProbePlugin plugin=
            new ProbePlugin(
                "kernel.probe",
                Collections.<String>emptyList()
            );
        plugin.worldThreadProbe=
            ()->world.pulse()
                .inExecutionContext();

        PluginHandle first=
            manager.enable(plugin);

        if(!first.enabled()||
           manager.plugin("kernel.probe")!=first)
            throw new AssertionError(
                "plugin enable handle not live"
            );

        if(plugin.enableCount.get()!=1)
            throw new AssertionError(
                "plugin enable callback count"
            );

        assertPluginBindings(
            world.content()
        );

        assertScopedRegistrarClosed(plugin);
        assertScopedEnableFacadesReleased(plugin);

        AtomicReference<String> command=
            new AtomicReference<>();
        AtomicReference<String> object=
            new AtomicReference<>();
        AtomicReference<String> item=
            new AtomicReference<>();
        AtomicReference<ContentNpcService> npc=
            new AtomicReference<>();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        world.submitAndWait(
            player,
            generation,
            ()->{
                ContentResult commandResult=
                    world.content()
                        .dispatchCommand(
                            player,
                            COMMAND,
                            writer
                        );

                command.set(
                    commandResult==null
                        ?null
                        :commandResult.logText()
                );

                ContentInteractionResult objectResult=
                    world.content()
                        .dispatchObjectOption(
                            OBJECT,
                            1,
                            3200,
                            3200
                        );
                object.set(
                    objectResult==null
                        ?null
                        :objectResult.outcome()
                );

                ContentInteractionResult itemResult=
                    world.content()
                        .dispatchItemOption(
                            ITEM,
                            1
                        );
                item.set(
                    itemResult==null
                        ?null
                        :itemResult.outcome()
                );

                ContentNpcOptionResult npcResult=
                    world.content()
                        .dispatchNpcOption(
                            NPC,
                            1,
                            3200,
                            3200
                        );
                npc.set(
                    npcResult==null
                        ?null
                        :npcResult.service()
                );

                world.domainEvents()
                    .publish(
                        new ProbeEvent()
                    );
            },
            5_000L
        );

        equals(
            "PLUGIN_COMMAND",
            command.get(),
            "plugin command dispatch"
        );
        equals(
            "PLUGIN_OBJECT",
            object.get(),
            "plugin object dispatch"
        );
        equals(
            "PLUGIN_ITEM",
            item.get(),
            "plugin item dispatch"
        );

        if(npc.get()!=ContentNpcService.TALK)
            throw new AssertionError(
                "plugin npc dispatch actual="+
                npc.get()
            );

        if(plugin.eventCount.get()!=1||
           !plugin.eventOnWorldThread.get())
            throw new AssertionError(
                "domain event callback did not run on World execution context"
            );

        ProbePlugin duplicate=
            new ProbePlugin(
                "kernel.probe",
                Collections.<String>emptyList()
            );

        expectFailure(
            ()->manager.enable(duplicate),
            "duplicate plugin id"
        );

        if(duplicate.enableCount.get()!=0)
            throw new AssertionError(
                "duplicate plugin executed enable callback"
            );

        ProbePlugin incompatible=
            new ProbePlugin(
                new PluginManifest(
                    "kernel.incompatible",
                    "1.0.0",
                    PluginApiVersion.CURRENT+1,
                    Collections.<String>emptyList()
                )
            );

        expectFailure(
            ()->manager.enable(incompatible),
            "incompatible API version"
        );

        if(incompatible.enableCount.get()!=0)
            throw new AssertionError(
                "incompatible plugin executed enable callback"
            );

        if(!manager.disable(
                "kernel.probe"))
            throw new AssertionError(
                "plugin disable returned false"
            );

        if(first.enabled()||
           plugin.disableCount.get()!=1)
            throw new AssertionError(
                "plugin disable lifecycle mismatch"
            );

        if(plugin.commandRegistration==null||
           plugin.commandRegistration.active())
            throw new AssertionError(
                "plugin content registration handle stayed active after disable"
            );

        if(world.domainEvents()
                .listenerCount()!=listenerBaseline)
            throw new AssertionError(
                "event subscription leaked after disable"
            );

        assertBaseBindingsRestored(
            world.content()
        );

        PluginHandle second=
            manager.enable(plugin);

        if(!second.enabled()||
           plugin.enableCount.get()!=2)
            throw new AssertionError(
                "same plugin id did not re-enable"
            );

        manager.disable(
            "kernel.probe"
        );

        FailingPlugin failing=
            new FailingPlugin();

        int listenersBeforeFailure=
            world.domainEvents()
                .listenerCount();

        expectFailure(
            ()->manager.enable(failing),
            "failing plugin"
        );

        if(failing.enableCount.get()!=1||
           failing.disableCount.get()!=1)
            throw new AssertionError(
                "failed-enable compensation callbacks mismatch"
            );

        if(manager.plugin(
                "kernel.fail")==null){
            // Expected: failed plugin is not retained.
        }else{
            throw new AssertionError(
                "failed plugin retained in manager"
            );
        }

        if(world.content()
                .commandBinding(
                    "pluginfailprobe"
                )!=null)
            throw new AssertionError(
                "failed plugin content leaked"
            );

        if(world.domainEvents()
                .listenerCount()!=
                    listenersBeforeFailure)
            throw new AssertionError(
                "failed plugin event subscription leaked"
            );

        ArrayList<String> order=
            new ArrayList<>();

        OrderingPlugin c=
            new OrderingPlugin(
                "dep.c",
                Collections.<String>emptyList(),
                order
            );
        OrderingPlugin b=
            new OrderingPlugin(
                "dep.b",
                Collections.singletonList(
                    "dep.a"
                ),
                order
            );
        OrderingPlugin a=
            new OrderingPlugin(
                "dep.a",
                Collections.<String>emptyList(),
                order
            );

        manager.enableAll(
            Arrays.asList(c,b,a)
        );

        equals(
            Arrays.asList(
                "dep.a",
                "dep.b",
                "dep.c"
            ),
            order,
            "deterministic dependency order"
        );

        expectRuntimeFailure(
            ()->manager.disable("dep.a"),
            "dependent disable guard"
        );

        manager.disable("dep.c");
        manager.disable("dep.b");
        manager.disable("dep.a");

        ArrayList<String> manifestSnapshotOrder=
            new ArrayList<>();

        SnapshotManifestPlugin manifestA=
            new SnapshotManifestPlugin(
                new PluginManifest(
                    "manifest.snapshot.a",
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    Collections.<String>emptyList()
                ),
                new PluginManifest(
                    "manifest.changed.a",
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    Collections.singletonList(
                        "manifest.never"
                    )
                ),
                manifestSnapshotOrder
            );
        SnapshotManifestPlugin manifestB=
            new SnapshotManifestPlugin(
                new PluginManifest(
                    "manifest.snapshot.b",
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    Collections.singletonList(
                        "manifest.snapshot.a"
                    )
                ),
                new PluginManifest(
                    "manifest.changed.b",
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    Collections.singletonList(
                        "manifest.never"
                    )
                ),
                manifestSnapshotOrder
            );

        manager.enableAll(
            Arrays.<Plugin>asList(
                manifestB,
                manifestA
            )
        );

        if(manifestA.manifestCalls.get()!=1||
           manifestB.manifestCalls.get()!=1)
            throw new AssertionError(
                "batch plugin manifest was not snapshotted exactly once a="+
                manifestA.manifestCalls.get()+
                " b="+
                manifestB.manifestCalls.get()
            );

        equals(
            Arrays.asList(
                "manifest.snapshot.a",
                "manifest.snapshot.b"
            ),
            manifestSnapshotOrder,
            "manifest snapshot dependency order"
        );

        if(manager.plugin(
                "manifest.snapshot.a"
            )==null||
           manager.plugin(
                "manifest.snapshot.b"
            )==null||
           manager.plugin(
                "manifest.changed.a"
            )!=null||
           manager.plugin(
                "manifest.changed.b"
            )!=null)
            throw new AssertionError(
                "batch enable did not retain snapshotted manifest identity"
            );

        manager.disable(
            "manifest.snapshot.b"
        );
        manager.disable(
            "manifest.snapshot.a"
        );

        ArrayList<String> batchOrder=
            new ArrayList<>();

        BatchPlugin batchA=
            new BatchPlugin(
                "batch.a",
                Collections.<String>emptyList(),
                false,
                batchOrder
            );
        BatchPlugin batchB=
            new BatchPlugin(
                "batch.b",
                Collections.singletonList(
                    "batch.a"
                ),
                true,
                batchOrder
            );

        expectFailure(
            ()->manager.enableAll(
                Arrays.asList(
                    batchB,
                    batchA
                )
            ),
            "dependency batch rollback"
        );

        equals(
            Arrays.asList(
                "batch.a",
                "batch.b"
            ),
            batchOrder,
            "batch enable order before failure"
        );

        if(manager.plugin("batch.a")!=null||
           manager.plugin("batch.b")!=null||
           batchA.disableCount.get()!=1||
           batchB.disableCount.get()!=1)
            throw new AssertionError(
                "failed dependency batch did not roll back"
            );

        ArrayList<String> cycleOrder=
            new ArrayList<>();

        OrderingPlugin cycleA=
            new OrderingPlugin(
                "cycle.a",
                Collections.singletonList(
                    "cycle.b"
                ),
                cycleOrder
            );
        OrderingPlugin cycleB=
            new OrderingPlugin(
                "cycle.b",
                Collections.singletonList(
                    "cycle.a"
                ),
                cycleOrder
            );

        expectFailure(
            ()->manager.enableAll(
                Arrays.asList(
                    cycleB,
                    cycleA
                )
            ),
            "dependency cycle"
        );

        if(!cycleOrder.isEmpty())
            throw new AssertionError(
                "dependency cycle executed plugin code"
            );

        pluginSelfSuppressionRegression();
        pluginRuntimeCloseExactlyOnceRegression();
        pluginRuntimeOpeningFenceOwnedRegression();
        pluginCleanupUsesSnapshotLoaderRegression();

        CloseProbePlugin closeProbe=
            new CloseProbePlugin();

        PluginHandle closeHandle=
            manager.enable(closeProbe);

        if(!world.unregisterPlayer(
                player,
                generation
            ))
            throw new AssertionError(
                "player cleanup before World close failed"
            );

        world.close();

        if(closeHandle.enabled())
            throw new AssertionError(
                "World close left plugin enabled"
            );

        if(closeProbe.disableCount.get()!=1||
           !closeProbe.subscriptionActiveDuringDisable.get())
            throw new AssertionError(
                "plugin cleanup did not run before event subscription teardown"
            );

        if(world.domainEvents()
                .listenerCount()!=0)
            throw new AssertionError(
                "World close left domain-event listeners"
            );

        if(!manager.enabled().isEmpty())
            throw new AssertionError(
                "World close left plugin handles enabled"
            );

        expectFailure(
            ()->manager.enable(
                new OrderingPlugin(
                    "after.close",
                    Collections.<String>emptyList(),
                    new ArrayList<String>()
                )
            ),
            "post-close plugin enable"
        );

        System.out.println(
            "PLUGIN_KERNEL_LIFECYCLE_PASS "+
            "publicCapabilitiesOnly=true "+
            "customProvenance=true "+
            "contentShadowRestore=true "+
            "eventWorldThread=true "+
            "disableClean=true "+
            "enableFacadeRootsReleased=true "+
            "reenable=true "+
            "preEnableGuards=true "+
            "failureRollback=true "+
            "dependencyOrder=dep.a_dep.b_dep.c "+
            "manifestSnapshotOnce=true "+
            "batchRollback=true "+
            "pluginSelfSuppressionSafe=true "+
            "enableRollbackEvidencePreserved=true "+
            "worldCloseSnapshotLoader=true "+
            "pluginRuntimeCloseExactlyOnce=true "+
            "pluginRuntimeOpeningFenceOwned=true "+
            "pluginRuntimeCloseOwnerReleased=true "+
            "pluginCleanupUsesSnapshotLoader=true "+
            "pluginCleanupContinuesAfterLoaderFailure=true "+
            "dependencyCycleRejected=true "+
            "worldCloseClean=true"
        );
    }

    private static void pluginSelfSuppressionRegression()
        throws Exception{
        sameObjectEnableDisableRollback();
        sameObjectBatchRollback();
        enableRollbackEvidencePreserved();
        runtimeCloseSuppressionIdentity();
        suppressionHelperOrdering();
        worldCloseUsesSnapshotLoader();
    }

    private static void sameObjectEnableDisableRollback()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        RuntimeException shared=
            new IllegalStateException(
                "same-enable-disable"
            );
        SameFailurePlugin plugin=
            new SameFailurePlugin(
                "self.single",
                Collections.<String>emptyList(),
                shared,
                true
            );
        int baseline=
            world.domainEvents()
                .listenerCount();

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enable(
                        plugin
                    )
                );

            if(observed!=shared)
                throw new AssertionError(
                    "same enable/disable failure identity changed",
                    observed
                );

            if(shared.getSuppressed().length!=0)
                throw new AssertionError(
                    "same enable/disable failure self-suppressed"
                );

            if(plugin.disableCount.get()!=1)
                throw new AssertionError(
                    "same enable/disable compensation count="+
                    plugin.disableCount.get()
                );

            if(manager.plugin(
                    "self.single")!=null)
                throw new AssertionError(
                    "same-failure plugin retained after failed enable"
                );

            if(world.content()
                    .commandBinding(
                        "selfsingle"
                    )!=null)
                throw new AssertionError(
                    "same-failure plugin content survived rollback"
                );

            if(world.domainEvents()
                    .listenerCount()!=baseline)
                throw new AssertionError(
                    "same-failure plugin event survived rollback"
                );
        }finally{
            world.close();
        }
    }

    private static void sameObjectBatchRollback()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        RuntimeException shared=
            new IllegalStateException(
                "same-batch-rollback"
            );
        AtomicInteger firstDisable=
            new AtomicInteger();

        Plugin first=
            new Plugin(){
                @Override public PluginManifest manifest(){
                    return new PluginManifest(
                        "self.batch.a",
                        "1.0.0"
                    );
                }

                @Override public void enable(
                    PluginContext context
                ){
                    context.content()
                        .command(
                            "selfbatcha",
                            100,
                            command->
                                ContentResult.handled(
                                    "A",
                                    null
                                )
                        );
                }

                @Override public void disable(){
                    firstDisable.incrementAndGet();
                }
            };

        CleanupLoaderFailureRuntime middle=
            new CleanupLoaderFailureRuntime(
                "self.batch.b",
                Collections.singletonList(
                    "self.batch.a"
                ),
                shared
            );

        Plugin failing=
            new Plugin(){
                @Override public PluginManifest manifest(){
                    return new PluginManifest(
                        "self.batch.c",
                        "1.0.0",
                        PluginApiVersion.CURRENT,
                        Collections.singletonList(
                            "self.batch.b"
                        )
                    );
                }

                @Override public void enable(
                    PluginContext context
                ){
                    middle.failCleanupLoader=true;
                    throw shared;
                }

                @Override public void disable(){}
            };

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enableAll(
                        Arrays.<Plugin>asList(
                            failing,
                            middle,
                            first
                        )
                    )
                );

            if(observed!=shared)
                throw new AssertionError(
                    "same batch rollback failure identity changed",
                    observed
                );

            if(shared.getSuppressed().length!=0)
                throw new AssertionError(
                    "same batch rollback failure self-suppressed"
                );

            if(firstDisable.get()!=1)
                throw new AssertionError(
                    "batch rollback stopped after same-object cleanup failure"
                );

            if(middle.disableCount.get()!=1||
               middle.closeCount.get()!=1||
               middle.callbackLoaderCalls.get()!=1)
                throw new AssertionError(
                    "batch rollback did not use snapshotted cleanup loader "+
                    "queries="+middle.callbackLoaderCalls.get()+
                    " disable="+middle.disableCount.get()+
                    " close="+middle.closeCount.get()
                );

            if(manager.plugin(
                    "self.batch.a")!=null||
               manager.plugin(
                    "self.batch.b")!=null||
               manager.plugin(
                    "self.batch.c")!=null)
                throw new AssertionError(
                    "same-failure batch retained plugin"
                );

            if(world.content()
                    .commandBinding(
                        "selfbatcha"
                    )!=null)
                throw new AssertionError(
                    "same-failure batch retained prior content"
                );
        }finally{
            world.close();
        }
    }

    private static void enableRollbackEvidencePreserved()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        RuntimeException primary=
            new IllegalStateException(
                "enable-primary"
            );
        RuntimeException disableCleanup=
            new IllegalArgumentException(
                "disable-cleanup"
            );
        RuntimeException runtimeCleanup=
            new IllegalStateException(
                "runtime-close-cleanup"
            );
        DistinctCleanupRuntimePlugin plugin=
            new DistinctCleanupRuntimePlugin(
                primary,
                disableCleanup,
                runtimeCleanup
            );
        int baseline=
            world.domainEvents()
                .listenerCount();

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enable(
                        plugin
                    )
                );

            if(observed!=primary)
                throw new AssertionError(
                    "enable rollback exposed wrapper instead of original cause",
                    observed
                );

            Throwable[] suppressed=
                primary.getSuppressed();

            if(suppressed.length!=2||
               suppressed[0]!=disableCleanup||
               suppressed[1]!=runtimeCleanup)
                throw new AssertionError(
                    "enable rollback cleanup evidence order mismatch "+
                    Arrays.toString(
                        suppressed
                    )
                );

            for(Throwable cleanup:suppressed)
                if(cleanup==observed.getCause()||
                   cleanup.getClass()
                       .getName()
                       .contains(
                           "PluginEnableFailure"
                       ))
                    throw new AssertionError(
                        "internal PluginEnableFailure leaked through rollback evidence"
                    );

            if(plugin.disableCount.get()!=1||
               plugin.closeCount.get()!=1||
               manager.plugin(
                   "self.unwrap"
               )!=null||
               world.content()
                   .commandBinding(
                       "selfunwrap"
                   )!=null||
               world.domainEvents()
                   .listenerCount()!=
                       baseline)
                throw new AssertionError(
                    "enable rollback evidence path did not finish cleanup"
                );
        }finally{
            world.close();
        }
    }

    private static void worldCloseUsesSnapshotLoader()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        RuntimeException shared=
            new IllegalStateException(
                "world-close-shared"
            );
        AtomicInteger tailDisable=
            new AtomicInteger();

        Plugin tail=
            new Plugin(){
                @Override public PluginManifest manifest(){
                    return new PluginManifest(
                        "self.world.a",
                        "1.0.0"
                    );
                }

                @Override public void enable(
                    PluginContext context
                ){}

                @Override public void disable(){
                    tailDisable.incrementAndGet();
                }
            };

        CleanupLoaderFailureRuntime middle=
            new CleanupLoaderFailureRuntime(
                "self.world.b",
                shared
            );
        CleanupLoaderFailureRuntime first=
            new CleanupLoaderFailureRuntime(
                "self.world.c",
                shared
            );

        manager.enable(
            tail
        );
        manager.enable(
            middle
        );
        manager.enable(
            first
        );

        if(middle.callbackLoaderCalls.get()!=1||
           first.callbackLoaderCalls.get()!=1)
            throw new AssertionError(
                "runtime callback loader was not snapshotted exactly once"
            );

        middle.failCleanupLoader=true;
        first.failCleanupLoader=true;

        world.close();
        world.close();

        if(tailDisable.get()!=1||
           middle.disableCount.get()!=1||
           first.disableCount.get()!=1||
           middle.closeCount.get()!=1||
           first.closeCount.get()!=1)
            throw new AssertionError(
                "world-close snapshot-loader cleanup count mismatch"
            );

        if(middle.callbackLoaderCalls.get()!=1||
           first.callbackLoaderCalls.get()!=1)
            throw new AssertionError(
                "world-close re-queried hostile runtime callback loader"
            );

        if(!manager.enabled().isEmpty())
            throw new AssertionError(
                "world-close snapshot-loader cleanup retained plugin handles"
            );
    }

    private static void runtimeCloseSuppressionIdentity(){
        RuntimeException shared=
            new IllegalStateException(
                "same-runtime-close"
            );
        CloseFailureRuntime same=
            new CloseFailureRuntime(
                shared
            );

        Throwable cleanup=
            PluginRuntimeSupport
                .closePluginRuntime(
                    same,
                    shared
                );

        if(cleanup!=shared||
           same.closeCount.get()!=1||
           shared.getSuppressed().length!=0)
            throw new AssertionError(
                "runtime close same-object suppression mismatch"
            );

        RuntimeException primary=
            new IllegalStateException(
                "runtime-primary"
            );
        Error distinct=
            new AssertionError(
                "runtime-close-distinct"
            );
        CloseFailureRuntime different=
            new CloseFailureRuntime(
                distinct
            );

        Throwable distinctCleanup=
            PluginRuntimeSupport
                .closePluginRuntime(
                    different,
                    primary
                );

        if(distinctCleanup!=distinct||
           different.closeCount.get()!=1||
           primary.getSuppressed().length!=1||
           primary.getSuppressed()[0]!=distinct)
            throw new AssertionError(
                "runtime close distinct suppression ordering changed"
            );
    }

    private static void suppressionHelperOrdering(){
        RuntimeException primary=
            new IllegalStateException(
                "helper-primary"
            );
        Error cleanup=
            new AssertionError(
                "helper-cleanup"
            );

        PluginRuntimeSupport
            .suppressIfDistinct(
                primary,
                cleanup
            );

        if(primary.getSuppressed().length!=1||
           primary.getSuppressed()[0]!=cleanup)
            throw new AssertionError(
                "distinct helper suppression ordering changed"
            );

        PluginRuntimeSupport
            .suppressIfDistinct(
                primary,
                primary
            );

        if(primary.getSuppressed().length!=1)
            throw new AssertionError(
                "helper self-suppression changed suppressed list"
            );

        Error errorPrimary=
            new AssertionError(
                "helper-error-primary"
            );
        RuntimeException runtimeCleanup=
            new IllegalStateException(
                "helper-runtime-cleanup"
            );

        PluginRuntimeSupport
            .suppressIfDistinct(
                errorPrimary,
                runtimeCleanup
            );

        if(errorPrimary.getSuppressed().length!=1||
           errorPrimary.getSuppressed()[0]!=
                runtimeCleanup)
            throw new AssertionError(
                "Error-primary suppression ordering changed"
            );
    }

    private static Throwable captureFailure(
        ThrowingAction action
    ){
        try{
            action.run();
        }catch(Throwable failure){
            return failure;
        }

        throw new AssertionError(
            "expected failure did not occur"
        );
    }

    private static void pluginRuntimeCloseExactlyOnceRegression()
        throws Exception{
        singleFailedRuntimeClosesOnce();
        batchRuntimeRollbackClosesOnce();
        commitRuntimeFailureClosesOnce();
        snapshotFailureRuntimeClosesOnce();
        duplicateRequestedRuntimeClosesOnce();
        successfulRuntimeLifecycleClosesOnce();
    }

    private static void singleFailedRuntimeClosesOnce()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        RuntimeException primary=
            new IllegalStateException(
                "single-close-primary"
            );
        CountingPluginRuntime runtime=
            new CountingPluginRuntime(
                "close.single",
                Collections.<String>emptyList(),
                primary,
                null,
                null,
                true
            );
        int baseline=
            world.domainEvents()
                .listenerCount();

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enable(
                        runtime
                    )
                );

            if(observed!=primary)
                throw new AssertionError(
                    "single failed runtime primary changed",
                    observed
                );

            if(runtime.closeCount.get()!=1||
               runtime.disableCount.get()!=1)
                throw new AssertionError(
                    "single failed runtime cleanup count close="+
                    runtime.closeCount.get()+
                    " disable="+
                    runtime.disableCount.get()
                );

            if(manager.plugin(
                    "close.single")!=null||
               world.content()
                   .commandBinding(
                       "closesingle"
                   )!=null||
               world.domainEvents()
                   .listenerCount()!=
                       baseline)
                throw new AssertionError(
                    "single failed runtime retained lifecycle roots"
                );
        }finally{
            world.close();
        }

        World throwingWorld=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager throwingManager=
            (WorldPluginManager)
                throwingWorld.plugins();
        RuntimeException throwingPrimary=
            new IllegalStateException(
                "single-close-throw-primary"
            );
        RuntimeException closeFailure=
            new IllegalArgumentException(
                "single-close-cleanup"
            );
        CountingPluginRuntime throwingRuntime=
            new CountingPluginRuntime(
                "close.single.throw",
                Collections.<String>emptyList(),
                throwingPrimary,
                null,
                closeFailure,
                false
            );

        try{
            Throwable observed=
                captureFailure(
                    ()->throwingManager.enable(
                        throwingRuntime
                    )
                );

            if(observed!=throwingPrimary||
               throwingRuntime.closeCount.get()!=1||
               throwingPrimary.getSuppressed().length!=1||
               throwingPrimary.getSuppressed()[0]!=
                    closeFailure)
                throw new AssertionError(
                    "throwing runtime close was retried or reordered",
                    observed
                );
        }finally{
            throwingWorld.close();
        }
    }

    private static void batchRuntimeRollbackClosesOnce()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        RuntimeException primary=
            new IllegalStateException(
                "batch-close-primary"
            );

        CountingPluginRuntime a=
            new CountingPluginRuntime(
                "close.batch.a",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                true
            );
        CountingPluginRuntime b=
            new CountingPluginRuntime(
                "close.batch.b",
                Collections.singletonList(
                    "close.batch.a"
                ),
                null,
                null,
                null,
                true
            );
        CountingPluginRuntime c=
            new CountingPluginRuntime(
                "close.batch.c",
                Collections.singletonList(
                    "close.batch.b"
                ),
                primary,
                null,
                null,
                true
            );

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enableAll(
                        Arrays.<Plugin>asList(
                            c,
                            b,
                            a
                        )
                    )
                );

            if(observed!=primary)
                throw new AssertionError(
                    "batch runtime primary changed",
                    observed
                );

            for(CountingPluginRuntime runtime:
                    Arrays.asList(
                        a,
                        b,
                        c
                    ))
                if(runtime.closeCount.get()!=1||
                   runtime.disableCount.get()!=1)
                    throw new AssertionError(
                        "batch runtime cleanup not exactly once id="+
                        runtime.manifest().id()+
                        " close="+
                        runtime.closeCount.get()+
                        " disable="+
                        runtime.disableCount.get()
                    );

            if(!manager.enabled().isEmpty())
                throw new AssertionError(
                    "batch runtime rollback retained handles"
                );
        }finally{
            world.close();
        }
    }

    private static void commitRuntimeFailureClosesOnce()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        ArrayList<String> disableOrder=
            new ArrayList<>();
        RuntimeException closeFailure=
            new IllegalStateException(
                "commit-close-cleanup"
            );

        CommitFailureRuntime a=
            new CommitFailureRuntime(
                "close.commit.a",
                Collections.<String>emptyList(),
                world,
                true,
                false,
                closeFailure,
                disableOrder
            );
        CommitFailureRuntime b=
            new CommitFailureRuntime(
                "close.commit.b",
                Collections.singletonList(
                    "close.commit.a"
                ),
                world,
                false,
                true,
                null,
                disableOrder
            );

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enableAll(
                        Arrays.<Plugin>asList(
                            b,
                            a
                        )
                    )
                );

            if(!(observed instanceof
                    ArithmeticException)||
               observed.getMessage()==null||
               !observed.getMessage()
                   .contains(
                       "overflow"
                   ))
                throw new AssertionError(
                    "commitRuntime overflow did not remain primary",
                    observed
                );

            if(a.enableCount.get()!=1||
               b.enableCount.get()!=1)
                throw new AssertionError(
                    "commitRuntime failure occurred before both enables completed"
                );

            equals(
                Arrays.asList(
                    "close.commit.b",
                    "close.commit.a"
                ),
                disableOrder,
                "commitRuntime rollback order"
            );

            if(a.disableCount.get()!=1||
               b.disableCount.get()!=1||
               a.closeCount.get()!=1||
               b.closeCount.get()!=1)
                throw new AssertionError(
                    "commitRuntime rollback runtime cleanup count mismatch "+
                    "aDisable="+a.disableCount.get()+
                    " bDisable="+b.disableCount.get()+
                    " aClose="+a.closeCount.get()+
                    " bClose="+b.closeCount.get()
                );

            if(!manager.enabled().isEmpty())
                throw new AssertionError(
                    "commitRuntime failure retained enabled plugins"
                );

            if(manager.terminalDiagnostics()
                    .stream()
                    .noneMatch(
                        line->
                            line.contains(
                                "plugin=close.commit.a"
                            )&&
                            line.contains(
                                "CLASSLOADER_CLOSE"
                            )&&
                            line.contains(
                                closeFailure
                                    .getClass()
                                    .getName()
                            )
                    ))
                throw new AssertionError(
                    "commitRuntime runtime-close failure was not retained diagnostically"
                );

            assertManagerCleanupIdle(
                manager,
                "commitRuntime failure"
            );
        }finally{
            world.close();
        }
    }

    private static void snapshotFailureRuntimeClosesOnce()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        RuntimeException snapshotFailure=
            new IllegalStateException(
                "snapshot-callback-loader-failure"
            );
        CountingPluginRuntime runtime=
            new CountingPluginRuntime(
                "close.snapshot",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );
        runtime.callbackLoaderFailure=
            snapshotFailure;

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enable(
                        runtime
                    )
                );

            if(observed!=snapshotFailure||
               runtime.closeCount.get()!=1||
               runtime.enableCount.get()!=0||
               runtime.disableCount.get()!=0)
                throw new AssertionError(
                    "snapshot failure runtime ownership mismatch",
                    observed
                );
        }finally{
            world.close();
        }
    }

    private static void duplicateRequestedRuntimeClosesOnce()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        CountingPluginRuntime runtime=
            new CountingPluginRuntime(
                "close.duplicate",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );

        try{
            captureFailure(
                ()->manager.enableAll(
                    Arrays.<Plugin>asList(
                        runtime,
                        runtime
                    )
                )
            );

            if(runtime.closeCount.get()!=1)
                throw new AssertionError(
                    "duplicate requested runtime closed "+
                    runtime.closeCount.get()+
                    " times"
                );

            if(manager.plugin(
                    "close.duplicate")!=null)
                throw new AssertionError(
                    "duplicate requested runtime retained handle"
                );
        }finally{
            world.close();
        }

        World distinctWorld=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager distinctManager=
            (WorldPluginManager)
                distinctWorld.plugins();
        CountingPluginRuntime first=
            new CountingPluginRuntime(
                "close.duplicate.distinct",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );
        CountingPluginRuntime second=
            new CountingPluginRuntime(
                "close.duplicate.distinct",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );

        try{
            Throwable observed=
                captureFailure(
                    ()->distinctManager.enableAll(
                        Arrays.<Plugin>asList(
                            first,
                            second
                        )
                    )
                );

            if(!(observed instanceof
                    IllegalStateException)||
               first.closeCount.get()!=1||
               second.closeCount.get()!=1)
                throw new AssertionError(
                    "distinct duplicate-id runtimes did not receive separate close ownership",
                    observed
                );
        }finally{
            distinctWorld.close();
        }
    }

    private static void successfulRuntimeLifecycleClosesOnce()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        CountingPluginRuntime explicit=
            new CountingPluginRuntime(
                "close.success.explicit",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );

        try{
            PluginHandle handle=
                manager.enable(
                    explicit
                );

            if(!handle.enabled())
                throw new AssertionError(
                    "successful runtime did not enable"
                );

            if(!manager.disable(
                    "close.success.explicit"))
                throw new AssertionError(
                    "successful runtime explicit disable failed"
                );

            if(explicit.closeCount.get()!=1||
               explicit.disableCount.get()!=1)
                throw new AssertionError(
                    "successful explicit runtime cleanup count mismatch"
                );

            world.close();
            world.close();

            if(explicit.closeCount.get()!=1)
                throw new AssertionError(
                    "repeated World close reclosed explicit runtime"
                );
        }finally{
            if(!world.closed())
                world.close();
        }

        World terminalWorld=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager terminalManager=
            (WorldPluginManager)
                terminalWorld.plugins();
        CountingPluginRuntime terminal=
            new CountingPluginRuntime(
                "close.success.world",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );

        terminalManager.enable(
            terminal
        );
        terminalWorld.close();
        terminalWorld.close();

        if(terminal.closeCount.get()!=1||
           terminal.disableCount.get()!=1)
            throw new AssertionError(
                "successful World-close runtime cleanup count mismatch"
            );
    }

    private static void pluginRuntimeOpeningFenceOwnedRegression()
        throws Exception{
        closedManagerOpeningFenceOwnsFreshRuntime();
        worldOpenOpeningFenceOwnsFreshRuntime();
        openingFenceBatchOwnsRuntimeIdentityOnce();
        partialCollectionMaterializationOwnsObservedRuntimes();
        terminalizingRuntimeRemainsOwned();
    }

    private static void closedManagerOpeningFenceOwnsFreshRuntime()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            isolatedPluginManager(
                world,
                ()->true
            );

        manager.beginClose();

        CountingPluginRuntime runtime=
            new CountingPluginRuntime(
                "opening.closed.single",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enable(
                        runtime
                    )
                );

            assertOpeningFenceFailure(
                observed,
                "closed manager single"
            );

            if(runtime.enableCount.get()!=0||
               runtime.disableCount.get()!=0||
               runtime.closeCount.get()!=1)
                throw new AssertionError(
                    "closed-manager fresh runtime ownership mismatch "+
                    "enable="+runtime.enableCount.get()+
                    " disable="+runtime.disableCount.get()+
                    " close="+runtime.closeCount.get()
                );

            RuntimeException closeFailure=
                new IllegalArgumentException(
                    "opening-closed-close-failure"
                );
            CountingPluginRuntime throwing=
                new CountingPluginRuntime(
                    "opening.closed.throw",
                    Collections.<String>emptyList(),
                    null,
                    null,
                    closeFailure,
                    false
                );

            Throwable throwingObserved=
                captureFailure(
                    ()->manager.enable(
                        throwing
                    )
                );

            assertOpeningFenceFailure(
                throwingObserved,
                "closed manager throwing single"
            );

            if(throwing.closeCount.get()!=1||
               throwingObserved.getSuppressed().length!=1||
               throwingObserved.getSuppressed()[0]!=
                    closeFailure)
                throw new AssertionError(
                    "closed-manager runtime close failure was not suppressed exactly once",
                    throwingObserved
                );
        }finally{
            manager.closeResources();
            world.close();
        }
    }

    private static void worldOpenOpeningFenceOwnsFreshRuntime()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        AtomicBoolean worldOpen=
            new AtomicBoolean(
                false
            );
        WorldPluginManager manager=
            isolatedPluginManager(
                world,
                worldOpen::get
            );
        RuntimeException closeFailure=
            new IllegalStateException(
                "opening-world-open-close-failure"
            );
        CountingPluginRuntime runtime=
            new CountingPluginRuntime(
                "opening.worldopen.single",
                Collections.<String>emptyList(),
                null,
                null,
                closeFailure,
                false
            );

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enable(
                        runtime
                    )
                );

            assertOpeningFenceFailure(
                observed,
                "worldOpen false single"
            );

            if(runtime.enableCount.get()!=0||
               runtime.disableCount.get()!=0||
               runtime.closeCount.get()!=1||
               observed.getSuppressed().length!=1||
               observed.getSuppressed()[0]!=
                    closeFailure)
                throw new AssertionError(
                    "worldOpen-false runtime ownership/suppression mismatch",
                    observed
                );

            CountingPluginRuntime first=
                new CountingPluginRuntime(
                    "opening.worldopen.batch.a",
                    Collections.<String>emptyList(),
                    null,
                    null,
                    null,
                    false
                );
            CountingPluginRuntime second=
                new CountingPluginRuntime(
                    "opening.worldopen.batch.b",
                    Collections.<String>emptyList(),
                    null,
                    null,
                    null,
                    false
                );

            Throwable batchObserved=
                captureFailure(
                    ()->manager.enableAll(
                        Arrays.<Plugin>asList(
                            first,
                            first,
                            second
                        )
                    )
                );

            assertOpeningFenceFailure(
                batchObserved,
                "worldOpen false batch"
            );

            if(first.closeCount.get()!=1||
               second.closeCount.get()!=1||
               first.enableCount.get()!=0||
               second.enableCount.get()!=0)
                throw new AssertionError(
                    "worldOpen-false batch runtime identity ownership mismatch "+
                    "firstClose="+first.closeCount.get()+
                    " secondClose="+second.closeCount.get()
                );
        }finally{
            manager.beginClose();
            manager.closeResources();
            world.close();
        }
    }

    private static void openingFenceBatchOwnsRuntimeIdentityOnce()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            isolatedPluginManager(
                world,
                ()->true
            );

        manager.beginClose();

        CountingPluginRuntime first=
            new CountingPluginRuntime(
                "opening.closed.batch.a",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );
        CountingPluginRuntime second=
            new CountingPluginRuntime(
                "opening.closed.batch.b",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enableAll(
                        Arrays.<Plugin>asList(
                            first,
                            first,
                            second
                        )
                    )
                );

            assertOpeningFenceFailure(
                observed,
                "closed manager batch"
            );

            if(first.closeCount.get()!=1||
               second.closeCount.get()!=1||
               first.enableCount.get()!=0||
               second.enableCount.get()!=0)
                throw new AssertionError(
                    "closed-manager batch runtime identity ownership mismatch "+
                    "firstClose="+first.closeCount.get()+
                    " secondClose="+second.closeCount.get()
                );
        }finally{
            manager.closeResources();
            world.close();
        }
    }

    private static void partialCollectionMaterializationOwnsObservedRuntimes()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();

        RuntimeException primary=
            new IllegalStateException(
                "partial-collection-primary"
            );
        RuntimeException firstClose=
            new IllegalArgumentException(
                "partial-close-r1"
            );
        RuntimeException secondClose=
            new IllegalStateException(
                "partial-close-r2"
            );
        CountingPluginRuntime first=
            new CountingPluginRuntime(
                "opening.partial.r1",
                Collections.<String>emptyList(),
                null,
                null,
                firstClose,
                false
            );
        CountingPluginRuntime second=
            new CountingPluginRuntime(
                "opening.partial.r2",
                Collections.<String>emptyList(),
                null,
                null,
                secondClose,
                false
            );

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enableAll(
                        new ThrowingPluginCollection(
                            primary,
                            first,
                            first,
                            second
                        )
                    )
                );

            if(observed!=primary)
                throw new AssertionError(
                    "partial collection failure identity changed",
                    observed
                );

            if(first.closeCount.get()!=1||
               second.closeCount.get()!=1||
               first.enableCount.get()!=0||
               second.enableCount.get()!=0||
               first.disableCount.get()!=0||
               second.disableCount.get()!=0)
                throw new AssertionError(
                    "partial collection retirement count mismatch "+
                    "r1Close="+first.closeCount.get()+
                    " r2Close="+second.closeCount.get()
                );

            Throwable[] suppressed=
                primary.getSuppressed();

            if(suppressed.length!=2||
               suppressed[0]!=firstClose||
               suppressed[1]!=secondClose)
                throw new AssertionError(
                    "partial collection cleanup suppression order changed"
                );

            if(!manager.enabled().isEmpty()||
               manager.plugin(
                   "opening.partial.r1"
               )!=null||
               manager.plugin(
                   "opening.partial.r2"
               )!=null)
                throw new AssertionError(
                    "partial collection materialization created plugin handle"
                );
        }finally{
            world.close();
        }

        World ownedWorld=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager ownedManager=
            (WorldPluginManager)
                ownedWorld.plugins();
        CountingPluginRuntime owned=
            new CountingPluginRuntime(
                "opening.partial.owned",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );
        CountingPluginRuntime fresh=
            new CountingPluginRuntime(
                "opening.partial.fresh",
                Collections.<String>emptyList(),
                null,
                null,
                null,
                false
            );
        RuntimeException ownedPrimary=
            new IllegalStateException(
                "partial-owned-primary"
            );

        try{
            ownedManager.enable(
                owned
            );

            Throwable observed=
                captureFailure(
                    ()->ownedManager.enableAll(
                        new ThrowingPluginCollection(
                            ownedPrimary,
                            owned,
                            fresh,
                            owned
                        )
                    )
                );

            if(observed!=ownedPrimary)
                throw new AssertionError(
                    "partial owned collection failure identity changed",
                    observed
                );

            if(owned.closeCount.get()!=0||
               owned.enableCount.get()!=1||
               fresh.closeCount.get()!=1||
               fresh.enableCount.get()!=0)
                throw new AssertionError(
                    "partial collection disturbed already-owned runtime "+
                    "ownedClose="+owned.closeCount.get()+
                    " ownedEnable="+owned.enableCount.get()+
                    " freshClose="+fresh.closeCount.get()
                );

            PluginHandle handle=
                ownedManager.plugin(
                    "opening.partial.owned"
                );

            if(handle==null||
               !handle.enabled())
                throw new AssertionError(
                    "partial collection failure lost already-owned runtime"
                );

            if(!ownedManager.disable(
                    "opening.partial.owned"))
                throw new AssertionError(
                    "partial collection owned runtime cleanup failed"
                );

            if(owned.closeCount.get()!=1||
               owned.disableCount.get()!=1)
                throw new AssertionError(
                    "already-owned runtime did not retire exactly once through original owner"
                );
        }finally{
            ownedWorld.close();
        }
    }

    private static void terminalizingRuntimeRemainsOwned()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        BlockingOpeningFenceRuntime runtime=
            new BlockingOpeningFenceRuntime();
        AtomicReference<Throwable> cleanupFailure=
            new AtomicReference<>();

        PluginHandle retainedHandle=
            manager.enable(
                runtime
            );
        manager.beginClose();

        Thread cleanup=
            new Thread(
                ()->{
                    try{
                        manager.closeResources();
                    }catch(Throwable failure){
                        cleanupFailure.set(
                            failure
                        );
                    }
                },
                "plugin-opening-fence-cleanup"
            );

        cleanup.start();

        if(!runtime.disableEntered.await(
                5,
                java.util.concurrent.TimeUnit.SECONDS))
            throw new AssertionError(
                "terminal cleanup did not enter plugin disable"
            );

        assertRuntimeCloseOwnerIdentity(
            retainedHandle,
            runtime,
            "terminalizing cleanup"
        );

        try{
            Throwable observed=
                captureFailure(
                    ()->manager.enable(
                        runtime
                    )
                );

            assertOpeningFenceFailure(
                observed,
                "terminalizing owned runtime"
            );

            if(runtime.closeCount.get()!=0)
                throw new AssertionError(
                    "rejected re-enable closed still-owned terminalizing runtime"
                );
        }finally{
            runtime.releaseDisable.countDown();
        }

        cleanup.join(
            5_000L
        );

        if(cleanup.isAlive())
            throw new AssertionError(
                "terminal cleanup did not finish"
            );

        if(cleanupFailure.get()!=null)
            throw new AssertionError(
                "terminal cleanup failed",
                cleanupFailure.get()
            );

        if(runtime.enableCount.get()!=1||
           runtime.disableCount.get()!=1||
           runtime.closeCount.get()!=1)
            throw new AssertionError(
                "terminalizing runtime ownership was not exactly once "+
                "enable="+runtime.enableCount.get()+
                " disable="+runtime.disableCount.get()+
                " close="+runtime.closeCount.get()
            );

        assertRuntimeCloseOwnerIdentity(
            retainedHandle,
            null,
            "terminal cleanup complete"
        );

        world.close();
        world.close();

        if(runtime.closeCount.get()!=1)
            throw new AssertionError(
                "repeated World close reclosed terminalizing runtime"
            );
    }

    private static WorldPluginManager isolatedPluginManager(
        World world,
        java.util.function.BooleanSupplier worldOpen
    ){
        return new WorldPluginManager(
            world.content(),
            world.domainEvents(),
            world.clock(),
            world.events(),
            worldOpen,
            ()->true
        );
    }

    private static void assertOpeningFenceFailure(
        Throwable failure,
        String phase
    ){
        if(!(failure instanceof
                IllegalStateException)||
           failure.getMessage()==null||
           !failure.getMessage()
               .contains(
                   "plugin manager closed"
               ))
            throw new AssertionError(
                phase+
                " did not preserve opening-fence failure",
                failure
            );
    }

    private static void pluginCleanupUsesSnapshotLoaderRegression()
        throws Exception{
        World world=
            World.isolatedForTest(
                25L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        int listenerBaseline=
            world.domainEvents()
                .listenerCount();
        RuntimeException hostileFailure=
            new IllegalStateException(
                "post-enable-loader-failure"
            );
        SnapshotCleanupRuntime runtime=
            new SnapshotCleanupRuntime(
                hostileFailure
            );

        try{
            PluginHandle handle=
                manager.enable(
                    runtime
                );

            if(!handle.enabled()||
               runtime.callbackLoaderCalls.get()!=1)
                throw new AssertionError(
                    "snapshot cleanup runtime enable/loader count mismatch"
                );

            if(world.content()
                    .commandBinding(
                        "snapcleanup"
                    )==null)
                throw new AssertionError(
                    "snapshot cleanup content binding missing before disable"
                );

            if(world.domainEvents()
                    .listenerCount()!=
                        listenerBaseline+1)
                throw new AssertionError(
                    "snapshot cleanup event subscription missing before disable"
                );

            if(runtime.task==null||
               !runtime.task.active())
                throw new AssertionError(
                    "snapshot cleanup pending task missing before disable"
                );

            runtime.failLaterLoader=true;

            if(!manager.disable(
                    "cleanup.snapshot"))
                throw new AssertionError(
                    "snapshot cleanup explicit disable failed"
                );

            if(runtime.callbackLoaderCalls.get()!=1)
                throw new AssertionError(
                    "terminal cleanup re-queried hostile runtime callback loader"
                );

            if(runtime.disableCount.get()!=1||
               runtime.closeCount.get()!=1||
               !runtime.disableTcclCorrect)
                throw new AssertionError(
                    "snapshot cleanup disable/runtime retirement mismatch "+
                    "disable="+runtime.disableCount.get()+
                    " close="+runtime.closeCount.get()+
                    " tccl="+runtime.disableTcclCorrect
                );

            if(world.content()
                    .commandBinding(
                        "snapcleanup"
                    )!=null)
                throw new AssertionError(
                    "snapshot cleanup content binding survived terminal cleanup"
                );

            if(world.domainEvents()
                    .listenerCount()!=
                        listenerBaseline)
                throw new AssertionError(
                    "snapshot cleanup event subscription survived terminal cleanup"
                );

            if(runtime.task.active())
                throw new AssertionError(
                    "snapshot cleanup pending task survived terminal cleanup"
                );

            if(manager.plugin(
                    "cleanup.snapshot")!=null)
                throw new AssertionError(
                    "snapshot cleanup handle survived terminal cleanup"
                );

            java.lang.reflect.Field handleLoader=
                handle.getClass()
                    .getDeclaredField(
                        "callbackLoader"
                    );
            handleLoader.setAccessible(
                true
            );

            if(handleLoader.get(
                    handle)!=null)
                throw new AssertionError(
                    "retained disabled handle kept snapshotted callback loader"
                );

            assertRuntimeCloseOwnerIdentity(
                handle,
                null,
                "retained disabled handle"
            );

            assertManagerCleanupIdle(
                manager,
                "snapshot-loader cleanup"
            );

            world.close();
            world.close();

            if(runtime.callbackLoaderCalls.get()!=1||
               runtime.disableCount.get()!=1||
               runtime.closeCount.get()!=1)
                throw new AssertionError(
                    "repeated World close revisited snapshot cleanup runtime"
                );
        }finally{
            if(!world.closed())
                world.close();
        }
    }

    private static void assertRuntimeCloseOwnerIdentity(
        PluginHandle handle,
        Plugin expected,
        String phase
    )throws Exception{
        java.lang.reflect.Field ownerField=
            handle.getClass()
                .getDeclaredField(
                    "runtimeClose"
                );
        ownerField.setAccessible(
            true
        );
        Object owner=
            ownerField.get(
                handle
            );

        if(owner==null)
            throw new AssertionError(
                phase+
                " lost runtime-close owner token"
            );

        java.lang.reflect.Field pluginField=
            owner.getClass()
                .getDeclaredField(
                    "plugin"
                );
        pluginField.setAccessible(
            true
        );

        Object actual=
            pluginField.get(
                owner
            );

        if(actual!=expected)
            throw new AssertionError(
                phase+
                " runtime-close owner identity mismatch expected="+
                (expected==null
                    ?"<released>"
                    :expected.getClass()
                        .getName())+
                " actual="+
                (actual==null
                    ?"<released>"
                    :actual.getClass()
                        .getName())
            );
    }

    private static void assertManagerCleanupIdle(
        WorldPluginManager manager,
        String phase
    )throws Exception{
        java.lang.reflect.Field terminalizing=
            WorldPluginManager.class
                .getDeclaredField(
                    "terminalizing"
                );
        terminalizing.setAccessible(
            true
        );
        java.util.Map<?,?> terminal=
            (java.util.Map<?,?>)
                terminalizing.get(
                    manager
                );

        java.lang.reflect.Field active=
            WorldPluginManager.class
                .getDeclaredField(
                    "activeCleanups"
                );
        active.setAccessible(
            true
        );

        if(!terminal.isEmpty()||
           active.getInt(
               manager
           )!=0)
            throw new AssertionError(
                phase+
                " left terminal cleanup ownership live"
            );
    }

    private static void installBaseContent(
        ContentRegistry registry
    ){
        registry.installCustom(
            new ContentModule(){
                @Override public String id(){
                    return "plugin-kernel-base";
                }

                @Override public void register(
                    ContentRegistrar registrar
                ){
                    registrar.command(
                        COMMAND,
                        1,
                        context->
                            ContentResult.handled(
                                "BASE_COMMAND",
                                null
                            )
                    );
                    registrar.objectOption(
                        OBJECT,
                        1,
                        1,
                        context->
                            ContentInteractionResult.handled(
                                "BASE_OBJECT"
                            )
                    );
                    registrar.itemOption(
                        ITEM,
                        1,
                        1,
                        context->
                            ContentInteractionResult.handled(
                                "BASE_ITEM"
                            )
                    );
                    registrar.npcOption(
                        NPC,
                        1,
                        1,
                        context->
                            ContentNpcOptionResult.handled(
                                ContentNpcService.BANK
                            )
                    );
                }
            }
        );
    }

    private static void assertPluginBindings(
        ContentRegistry registry
    ){
        assertBinding(
            registry.commandBinding(COMMAND),
            "plugin:kernel.probe"
        );
        assertBinding(
            registry.objectOptionBinding(
                OBJECT,
                1
            ),
            "plugin:kernel.probe"
        );
        assertBinding(
            registry.itemOptionBinding(
                ITEM,
                1
            ),
            "plugin:kernel.probe"
        );
        assertBinding(
            registry.npcOptionBinding(
                NPC,
                1
            ),
            "plugin:kernel.probe"
        );
    }

    private static void assertBaseBindingsRestored(
        ContentRegistry registry
    ){
        assertBase(
            registry.commandBinding(COMMAND)
        );
        assertBase(
            registry.objectOptionBinding(
                OBJECT,
                1
            )
        );
        assertBase(
            registry.itemOptionBinding(
                ITEM,
                1
            )
        );
        assertBase(
            registry.npcOptionBinding(
                NPC,
                1
            )
        );
    }

    private static void assertBinding(
        ContentRegistry.BindingInfo info,
        String module
    ){
        if(info==null)
            throw new AssertionError(
                "plugin binding missing"
            );

        equals(
            module,
            info.moduleId,
            "plugin binding module"
        );

        if(info.provenance!=
                ContentProvenance.CUSTOM_LOCALLAB)
            throw new AssertionError(
                "plugin provenance widened: "+
                info.provenance
            );
    }

    private static void assertBase(
        ContentRegistry.BindingInfo info
    ){
        if(info==null)
            throw new AssertionError(
                "base binding not restored"
            );

        equals(
            "plugin-kernel-base",
            info.moduleId,
            "base binding module"
        );
    }

    private static void assertScopedRegistrarClosed(
        ProbePlugin plugin
    ){
        boolean rejected=false;

        try{
            plugin.retainedRegistrar.command(
                "latepluginbinding",
                1,
                context->
                    ContentResult.handled(
                        "LATE",
                        null
                    )
            );
        }catch(IllegalStateException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                "retained plugin registrar accepted late registration"
            );
    }

    private static void assertScopedEnableFacadesReleased(
        ProbePlugin plugin
    )throws Exception{
        expectRuntimeFailure(
            ()->plugin.retainedContext.content(),
            "retained plugin context content"
        );
        expectRuntimeFailure(
            ()->plugin.retainedContext.events(),
            "retained plugin context events"
        );
        expectRuntimeFailure(
            ()->plugin.retainedContext.scheduler(),
            "retained plugin context scheduler"
        );
        expectRuntimeFailure(
            ()->plugin.retainedEvents.subscribe(
                ProbeEvent.class,
                DomainEventBus.Priority.NORMAL,
                event->{}
            ),
            "retained plugin events"
        );

        assertStaticClass(
            plugin.retainedContext.getClass(),
            "PluginContext"
        );
        assertStaticClass(
            plugin.retainedEvents.getClass(),
            "PluginEvents"
        );

        assertNullFields(
            plugin.retainedContext,
            new String[]{
                "content",
                "events",
                "scheduler"
            },
            "PluginContext"
        );
        assertNullFields(
            plugin.retainedRegistrar,
            new String[]{
                "delegate",
                "context",
                "callbackLoader",
                "callbackScope"
            },
            "ContentRegistrar"
        );
        assertNullFields(
            plugin.retainedEvents,
            new String[]{
                "eventBus",
                "tracker",
                "context",
                "callbackLoader"
            },
            "PluginEvents"
        );
    }

    private static void assertStaticClass(
        Class<?> type,
        String phase
    ){
        if(!java.lang.reflect.Modifier.isStatic(
                type.getModifiers()))
            throw new AssertionError(
                phase+
                " retained implicit outer instance"
            );
    }

    private static void assertNullFields(
        Object target,
        String[] fields,
        String phase
    )throws Exception{
        for(String name:fields){
            java.lang.reflect.Field field=
                target.getClass()
                    .getDeclaredField(name);
            field.setAccessible(true);

            if(field.get(target)!=null)
                throw new AssertionError(
                    phase+
                    " retained field "+
                    name
                );
        }
    }

    private static void expectFailure(
        ThrowingAction action,
        String phase
    )throws Exception{
        boolean failed=false;

        try{
            action.run();
        }catch(Exception expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                phase+" did not fail"
            );
    }

    private static void expectRuntimeFailure(
        Runnable action,
        String phase
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(RuntimeException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                phase+" did not fail"
            );
    }

    private static void equals(
        Object expected,
        Object actual,
        String phase
    ){
        if(!Objects.equals(
                expected,
                actual))
            throw new AssertionError(
                phase+
                " expected="+expected+
                " actual="+actual
            );
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run()throws Exception;
    }

    private static final class ProbeEvent
        implements DomainEventBus.Event {}

    private static class ProbePlugin
        implements Plugin {

        final PluginManifest manifest;
        final AtomicInteger enableCount=
            new AtomicInteger();
        final AtomicInteger disableCount=
            new AtomicInteger();
        final AtomicInteger eventCount=
            new AtomicInteger();
        final AtomicBoolean eventOnWorldThread=
            new AtomicBoolean();

        volatile PluginContext retainedContext;
        volatile PluginEvents retainedEvents;
        volatile ContentRegistrar retainedRegistrar;
        volatile ContentRegistration commandRegistration;
        volatile java.util.function.BooleanSupplier
            worldThreadProbe;

        ProbePlugin(
            String id,
            List<String> dependencies
        ){
            this(
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    dependencies
                )
            );
        }

        ProbePlugin(
            PluginManifest manifest
        ){
            this.manifest=manifest;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        )throws Exception{
            enableCount.incrementAndGet();
            retainedContext=context;
            retainedRegistrar=
                context.content();
            retainedEvents=
                context.events();

            commandRegistration=
                retainedRegistrar.command(
                    COMMAND,
                    100,
                    command->
                        ContentResult.handled(
                            "PLUGIN_COMMAND",
                            null
                        )
                );
            retainedRegistrar.objectOption(
                OBJECT,
                1,
                100,
                object->
                    ContentInteractionResult.handled(
                        "PLUGIN_OBJECT"
                    )
            );
            retainedRegistrar.itemOption(
                ITEM,
                1,
                100,
                item->
                    ContentInteractionResult.handled(
                        "PLUGIN_ITEM"
                    )
            );
            retainedRegistrar.npcOption(
                NPC,
                1,
                100,
                npc->
                    ContentNpcOptionResult.handled(
                        ContentNpcService.TALK
                    )
            );

            DomainEventBus.Subscription subscription=
                retainedEvents.subscribe(
                    ProbeEvent.class,
                    DomainEventBus.Priority.NORMAL,
                    event->{
                        eventCount.incrementAndGet();
                        if(worldThreadProbe!=null)
                            eventOnWorldThread.set(
                                worldThreadProbe
                                    .getAsBoolean()
                            );
                    }
                );

            if(!subscription.active())
                throw new AssertionError(
                    "plugin event subscription inactive during enable"
                );
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
        }
    }

    private static final class FailingPlugin
        implements Plugin {

        final AtomicInteger enableCount=
            new AtomicInteger();
        final AtomicInteger disableCount=
            new AtomicInteger();

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "kernel.fail",
                "1.0.0"
            );
        }

        @Override public void enable(
            PluginContext context
        )throws Exception{
            enableCount.incrementAndGet();

            context.events().subscribe(
                ProbeEvent.class,
                DomainEventBus.Priority.NORMAL,
                event->{}
            );

            context.content().command(
                "pluginfailprobe",
                100,
                command->
                    ContentResult.handled(
                        "FAIL",
                        null
                    )
            );

            throw new Exception(
                "intentional plugin enable failure"
            );
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
        }
    }

    private static final class OrderingPlugin
        implements Plugin {

        private final PluginManifest manifest;
        private final List<String> order;

        OrderingPlugin(
            String id,
            List<String> dependencies,
            List<String> order
        ){
            manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    dependencies
                );
            this.order=order;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        ){
            order.add(
                manifest.id()
            );
        }
    }

    private static final class SnapshotManifestPlugin
        implements Plugin {

        private final PluginManifest firstManifest;
        private final PluginManifest laterManifest;
        private final List<String> order;
        final AtomicInteger manifestCalls=
            new AtomicInteger();

        SnapshotManifestPlugin(
            PluginManifest firstManifest,
            PluginManifest laterManifest,
            List<String> order
        ){
            this.firstManifest=
                firstManifest;
            this.laterManifest=
                laterManifest;
            this.order=order;
        }

        @Override public PluginManifest manifest(){
            return manifestCalls
                .incrementAndGet()==1
                    ?firstManifest
                    :laterManifest;
        }

        @Override public void enable(
            PluginContext context
        ){
            order.add(
                firstManifest.id()
            );
        }
    }

    private static final class BatchPlugin
        implements Plugin {

        private final PluginManifest manifest;
        private final boolean fail;
        private final List<String> order;
        final AtomicInteger disableCount=
            new AtomicInteger();

        BatchPlugin(
            String id,
            List<String> dependencies,
            boolean fail,
            List<String> order
        ){
            manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    dependencies
                );
            this.fail=fail;
            this.order=order;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        )throws Exception{
            order.add(
                manifest.id()
            );

            if(fail)
                throw new Exception(
                    "intentional batch failure"
                );
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
        }
    }

    private static final class SameFailurePlugin
        implements Plugin {
        private final PluginManifest manifest;
        private final RuntimeException failure;
        private final boolean registerContent;
        final AtomicInteger disableCount=
            new AtomicInteger();

        SameFailurePlugin(
            String id,
            List<String> dependencies,
            RuntimeException failure,
            boolean registerContent
        ){
            this.manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    dependencies
                );
            this.failure=
                failure;
            this.registerContent=
                registerContent;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        ){
            if(registerContent){
                String command=
                    manifest.id()
                        .replace(
                            ".",
                            ""
                        );

                context.content()
                    .command(
                        command,
                        100,
                        request->
                            ContentResult.handled(
                                "SELF",
                                null
                            )
                    );
                context.events()
                    .subscribe(
                        ProbeEvent.class,
                        DomainEventBus.Priority.NORMAL,
                        event->{}
                    );
            }

            throw failure;
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
            throw failure;
        }
    }

    private static final class DistinctCleanupRuntimePlugin
        implements PluginRuntime {
        private final RuntimeException primary;
        private final RuntimeException disableCleanup;
        private final RuntimeException runtimeCleanup;
        final AtomicInteger disableCount=
            new AtomicInteger();
        final AtomicInteger closeCount=
            new AtomicInteger();

        DistinctCleanupRuntimePlugin(
            RuntimeException primary,
            RuntimeException disableCleanup,
            RuntimeException runtimeCleanup
        ){
            this.primary=primary;
            this.disableCleanup=disableCleanup;
            this.runtimeCleanup=runtimeCleanup;
        }

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "self.unwrap",
                "1.0.0"
            );
        }

        @Override public void enable(
            PluginContext context
        ){
            context.content()
                .command(
                    "selfunwrap",
                    100,
                    request->
                        ContentResult.handled(
                            "UNWRAP",
                            null
                        )
                );
            context.events()
                .subscribe(
                    ProbeEvent.class,
                    DomainEventBus.Priority.NORMAL,
                    event->{}
                );
            throw primary;
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
            throw disableCleanup;
        }

        @Override public ClassLoader callbackClassLoader(){
            return getClass()
                .getClassLoader();
        }

        @Override public void close(){
            closeCount.incrementAndGet();
            throw runtimeCleanup;
        }
    }

    private static final class CleanupLoaderFailureRuntime
        implements PluginRuntime {
        private final PluginManifest manifest;
        private final RuntimeException failure;
        volatile boolean failCleanupLoader;
        final AtomicInteger disableCount=
            new AtomicInteger();
        final AtomicInteger closeCount=
            new AtomicInteger();
        final AtomicInteger callbackLoaderCalls=
            new AtomicInteger();

        CleanupLoaderFailureRuntime(
            String id,
            RuntimeException failure
        ){
            this(
                id,
                Collections.<String>emptyList(),
                failure
            );
        }

        CleanupLoaderFailureRuntime(
            String id,
            List<String> dependencies,
            RuntimeException failure
        ){
            manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    dependencies
                );
            this.failure=failure;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        ){}

        @Override public void disable(){
            disableCount.incrementAndGet();
        }

        @Override public ClassLoader callbackClassLoader(){
            callbackLoaderCalls.incrementAndGet();

            if(failCleanupLoader)
                throw failure;

            return getClass()
                .getClassLoader();
        }

        @Override public void close(){
            closeCount.incrementAndGet();
        }
    }

    private static final class SnapshotCleanupRuntime
        implements PluginRuntime {
        private final RuntimeException hostileFailure;
        volatile boolean failLaterLoader;
        volatile boolean disableTcclCorrect;
        PluginTask task;
        final AtomicInteger callbackLoaderCalls=
            new AtomicInteger();
        final AtomicInteger disableCount=
            new AtomicInteger();
        final AtomicInteger closeCount=
            new AtomicInteger();

        SnapshotCleanupRuntime(
            RuntimeException hostileFailure
        ){
            this.hostileFailure=
                hostileFailure;
        }

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "cleanup.snapshot",
                "1.0.0"
            );
        }

        @Override public void enable(
            PluginContext context
        ){
            context.content()
                .command(
                    "snapcleanup",
                    100,
                    request->
                        ContentResult.handled(
                            "SNAPSHOT_CLEANUP",
                            null
                        )
                );

            context.events()
                .subscribe(
                    ProbeEvent.class,
                    DomainEventBus.Priority.NORMAL,
                    event->{}
                );

            task=
                context.scheduler()
                    .schedule(
                        10L,
                        ()->{}
                    );
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
            disableTcclCorrect=
                Thread.currentThread()
                    .getContextClassLoader()==
                    getClass()
                        .getClassLoader();
        }

        @Override public ClassLoader callbackClassLoader(){
            callbackLoaderCalls.incrementAndGet();

            if(failLaterLoader)
                throw hostileFailure;

            return getClass()
                .getClassLoader();
        }

        @Override public void close(){
            closeCount.incrementAndGet();
        }
    }

    private static final class CloseFailureRuntime
        implements PluginRuntime {
        private final Throwable failure;
        final AtomicInteger closeCount=
            new AtomicInteger();

        CloseFailureRuntime(
            Throwable failure
        ){
            this.failure=failure;
        }

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "self.runtime",
                "1.0.0"
            );
        }

        @Override public void enable(
            PluginContext context
        ){}

        @Override public void disable(){}

        @Override public ClassLoader callbackClassLoader(){
            return getClass()
                .getClassLoader();
        }

        @Override public void close()
            throws Exception{
            closeCount.incrementAndGet();

            if(failure instanceof Exception)
                throw (Exception)failure;

            throw (Error)failure;
        }
    }

    private static final class CommitFailureRuntime
        implements PluginRuntime {
        private final PluginManifest manifest;
        private final World world;
        private final boolean scheduleOverflow;
        private final boolean advanceClock;
        private final RuntimeException closeFailure;
        private final List<String> disableOrder;
        final AtomicInteger enableCount=
            new AtomicInteger();
        final AtomicInteger disableCount=
            new AtomicInteger();
        final AtomicInteger closeCount=
            new AtomicInteger();

        CommitFailureRuntime(
            String id,
            List<String> dependencies,
            World world,
            boolean scheduleOverflow,
            boolean advanceClock,
            RuntimeException closeFailure,
            List<String> disableOrder
        ){
            manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    dependencies
                );
            this.world=world;
            this.scheduleOverflow=
                scheduleOverflow;
            this.advanceClock=
                advanceClock;
            this.closeFailure=
                closeFailure;
            this.disableOrder=
                disableOrder;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        ){
            enableCount.incrementAndGet();

            if(scheduleOverflow){
                long delay=
                    Math.subtractExact(
                        Long.MAX_VALUE,
                        world.clock()
                            .tick()
                    );

                context.scheduler()
                    .schedule(
                        delay,
                        ()->{}
                    );
            }

            if(advanceClock)
                world.clock()
                    .advance();
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
            disableOrder.add(
                manifest.id()
            );
        }

        @Override public ClassLoader callbackClassLoader(){
            return getClass()
                .getClassLoader();
        }

        @Override public void close(){
            closeCount.incrementAndGet();

            if(closeFailure!=null)
                throw closeFailure;
        }
    }

    private static final class ThrowingPluginCollection
        extends AbstractCollection<Plugin> {

        private final RuntimeException failure;
        private final Plugin[] values;

        ThrowingPluginCollection(
            RuntimeException failure,
            Plugin... values
        ){
            this.failure=failure;
            this.values=values;
        }

        @Override public Iterator<Plugin> iterator(){
            return new Iterator<Plugin>(){
                private int index;

                @Override public boolean hasNext(){
                    if(index<values.length)
                        return true;

                    throw failure;
                }

                @Override public Plugin next(){
                    if(index>=values.length)
                        throw new NoSuchElementException();

                    return values[
                        index++
                    ];
                }
            };
        }

        @Override public int size(){
            return values.length+1;
        }
    }

    private static final class BlockingOpeningFenceRuntime
        implements PluginRuntime {

        final AtomicInteger enableCount=
            new AtomicInteger();
        final AtomicInteger disableCount=
            new AtomicInteger();
        final AtomicInteger closeCount=
            new AtomicInteger();
        final java.util.concurrent.CountDownLatch
            disableEntered=
                new java.util.concurrent.CountDownLatch(
                    1
                );
        final java.util.concurrent.CountDownLatch
            releaseDisable=
                new java.util.concurrent.CountDownLatch(
                    1
                );

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "opening.terminalizing",
                "1.0.0"
            );
        }

        @Override public void enable(
            PluginContext context
        ){
            enableCount.incrementAndGet();
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
            disableEntered.countDown();

            boolean interrupted=false;

            while(true)
                try{
                    releaseDisable.await();
                    break;
                }catch(InterruptedException ignored){
                    interrupted=true;
                }

            if(interrupted)
                Thread.currentThread()
                    .interrupt();
        }

        @Override public ClassLoader callbackClassLoader(){
            return getClass()
                .getClassLoader();
        }

        @Override public void close(){
            closeCount.incrementAndGet();
        }
    }

    private static final class CountingPluginRuntime
        implements PluginRuntime {
        private final PluginManifest manifest;
        private final RuntimeException enableFailure;
        private final RuntimeException disableFailure;
        private final RuntimeException closeFailure;
        private final boolean registerContent;
        volatile RuntimeException callbackLoaderFailure;
        final AtomicInteger enableCount=
            new AtomicInteger();
        final AtomicInteger disableCount=
            new AtomicInteger();
        final AtomicInteger closeCount=
            new AtomicInteger();

        CountingPluginRuntime(
            String id,
            List<String> dependencies,
            RuntimeException enableFailure,
            RuntimeException disableFailure,
            RuntimeException closeFailure,
            boolean registerContent
        ){
            manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    dependencies
                );
            this.enableFailure=enableFailure;
            this.disableFailure=disableFailure;
            this.closeFailure=closeFailure;
            this.registerContent=registerContent;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        ){
            enableCount.incrementAndGet();

            if(registerContent){
                context.content()
                    .command(
                        manifest.id()
                            .replace(
                                ".",
                                ""
                            ),
                        100,
                        request->
                            ContentResult.handled(
                                "COUNTING_RUNTIME",
                                null
                            )
                    );
                context.events()
                    .subscribe(
                        ProbeEvent.class,
                        DomainEventBus.Priority.NORMAL,
                        event->{}
                    );
            }

            if(enableFailure!=null)
                throw enableFailure;
        }

        @Override public void disable(){
            disableCount.incrementAndGet();

            if(disableFailure!=null)
                throw disableFailure;
        }

        @Override public ClassLoader callbackClassLoader(){
            RuntimeException failure=
                callbackLoaderFailure;

            if(failure!=null)
                throw failure;

            return getClass()
                .getClassLoader();
        }

        @Override public void close(){
            closeCount.incrementAndGet();

            if(closeFailure!=null)
                throw closeFailure;
        }
    }

    private static final class CloseProbePlugin
        implements Plugin {

        final AtomicInteger disableCount=
            new AtomicInteger();
        final AtomicBoolean subscriptionActiveDuringDisable=
            new AtomicBoolean();
        volatile DomainEventBus.Subscription subscription;

        @Override public PluginManifest manifest(){
            return new PluginManifest(
                "kernel.close",
                "1.0.0"
            );
        }

        @Override public void enable(
            PluginContext context
        ){
            subscription=
                context.events().subscribe(
                    ProbeEvent.class,
                    DomainEventBus.Priority.NORMAL,
                    event->{}
                );
        }

        @Override public void disable(){
            disableCount.incrementAndGet();
            subscriptionActiveDuringDisable.set(
                subscription!=null&&
                subscription.active()
            );
        }
    }

    private PluginKernelLifecycleTest(){}
}
