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
            "reenable=true "+
            "preEnableGuards=true "+
            "failureRollback=true "+
            "dependencyOrder=dep.a_dep.b_dep.c "+
            "batchRollback=true "+
            "dependencyCycleRejected=true "+
            "worldCloseClean=true"
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

        volatile ContentRegistrar retainedRegistrar;
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
            retainedRegistrar=
                context.content();

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
                context.events().subscribe(
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
