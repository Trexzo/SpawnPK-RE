package spk.local;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import spk.content.api.*;
import spk.event.DomainEventBus;
import spk.plugin.api.*;

public final class PluginRuntimeFailureCascadeTest {
    private static final String COMMAND=
        "cascadefailure";

    public static void main(String[] args)throws Exception{
        eventCascade();
        taskFailure();
        contentFailureNoFallback();

        System.out.println(
            "PLUGIN_RUNTIME_FAILURE_CASCADE_PASS "+
            "transitiveCascade=true "+
            "reverseDependencyCleanup=true "+
            "unrelatedEventContinues=true "+
            "taskFailureTerminal=true "+
            "contentFailureTerminal=true "+
            "sameDispatchFallback=false "+
            "disableExactlyOnce=true "+
            "terminalDiagnosticsDetached=true "+
            "terminalHandlesReleaseFailureSink=true"
        );
    }

    private static void eventCascade()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        List<String> disableOrder=
            Collections.synchronizedList(
                new ArrayList<>()
            );
        AtomicInteger bCalls=
            new AtomicInteger();
        AtomicInteger cCalls=
            new AtomicInteger();
        AtomicInteger uCalls=
            new AtomicInteger();

        CascadePlugin a=
            new CascadePlugin(
                "cascade.a",
                Collections.<String>emptyList(),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            CascadeEvent.class,
                            DomainEventBus.Priority.HIGH,
                            event->{
                                throw new IllegalStateException(
                                    "cascade-a-event"
                                );
                            }
                        )
            );
        CascadePlugin b=
            new CascadePlugin(
                "cascade.b",
                Collections.singletonList(
                    "cascade.a"
                ),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            CascadeEvent.class,
                            DomainEventBus.Priority.NORMAL,
                            event->
                                bCalls.incrementAndGet()
                        )
            );
        CascadePlugin c=
            new CascadePlugin(
                "cascade.c",
                Collections.singletonList(
                    "cascade.b"
                ),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            CascadeEvent.class,
                            DomainEventBus.Priority.LOW,
                            event->
                                cCalls.incrementAndGet()
                        )
            );
        CascadePlugin u=
            new CascadePlugin(
                "cascade.u",
                Collections.<String>emptyList(),
                disableOrder,
                context->
                    context.events()
                        .subscribe(
                            CascadeEvent.class,
                            DomainEventBus.Priority.NORMAL,
                            event->
                                uCalls.incrementAndGet()
                        )
            );

        PluginHandle aHandle=null;

        try{
            List<PluginHandle> handles=
                manager.enableAll(
                    Arrays.<Plugin>asList(
                        c,
                        u,
                        b,
                        a
                    )
                );

            for(PluginHandle handle:handles)
                if("cascade.a".equals(
                        handle.manifest().id()))
                    aHandle=handle;

            world.events().schedule(
                1L,
                ()->{
                    try{
                        world.domainEvents()
                            .publish(
                                new CascadeEvent()
                            );
                    }catch(Exception failure){
                        throw new RuntimeException(
                            failure
                        );
                    }
                }
            );

            world.observePulse(
                System.currentTimeMillis()
            );

            require(
                manager.plugin("cascade.a")==null&&
                manager.plugin("cascade.b")==null&&
                manager.plugin("cascade.c")==null,
                "failed dependency cascade remained enabled"
            );
            require(
                manager.plugin("cascade.u")!=null,
                "unrelated plugin was terminalized"
            );
            require(
                bCalls.get()==0&&
                cCalls.get()==0,
                "dependant listener executed after provider failure"
            );
            require(
                uCalls.get()==1,
                "unrelated listener did not continue same publication"
            );
            require(
                disableOrder.equals(
                    Arrays.asList(
                        "cascade.c",
                        "cascade.b",
                        "cascade.a"
                    )
                ),
                "reverse dependency cleanup order "+
                disableOrder
            );
            require(
                a.disableCalls.get()==1&&
                b.disableCalls.get()==1&&
                c.disableCalls.get()==1,
                "cascade disable count"
            );
            require(
                aHandle!=null&&
                !aHandle.enabled(),
                "failed handle stayed enabled"
            );
            assertFailureSinkReleased(
                aHandle
            );

            List<String> diagnostics=
                manager.terminalDiagnostics();

            require(
                diagnostics.stream()
                    .anyMatch(
                        line->
                            line.contains(
                                "plugin=cascade.a"
                            )&&
                            line.contains(
                                "phase=CALLBACK:EVENT:"
                            )&&
                            line.contains(
                                "errorClass=java.lang.IllegalStateException"
                            )
                    ),
                "detached callback diagnostic missing "+
                diagnostics
            );

            for(Object diagnostic:diagnostics)
                require(
                    diagnostic instanceof String,
                    "terminal diagnostic retained non-string root"
                );

            require(
                !manager.disable(
                    "cascade.a"
                )&&
                a.disableCalls.get()==1,
                "post-failure explicit disable repeated cleanup"
            );

            require(
                manager.disable(
                    "cascade.u"
                ),
                "unrelated plugin did not remain normally disableable"
            );
        }finally{
            world.close();
        }
    }

    private static void taskFailure()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        AtomicInteger unrelated=
            new AtomicInteger();

        CascadePlugin failing=
            new CascadePlugin(
                "task.failure",
                Collections.<String>emptyList(),
                new ArrayList<>(),
                context->
                    context.scheduler().schedule(
                        1L,
                        ()->{
                            throw new IllegalStateException(
                                "task-runtime-failure"
                            );
                        }
                    )
            );

        CascadePlugin healthy=
            new CascadePlugin(
                "task.healthy",
                Collections.<String>emptyList(),
                new ArrayList<>(),
                context->
                    context.scheduler().schedule(
                        1L,
                        unrelated::incrementAndGet
                    )
            );

        try{
            manager.enable(failing);
            manager.enable(healthy);

            world.observePulse(
                System.currentTimeMillis()
            );

            require(
                manager.plugin(
                    "task.failure"
                )==null,
                "task failure plugin stayed enabled"
            );
            require(
                manager.plugin(
                    "task.healthy"
                )!=null&&
                unrelated.get()==1,
                "unrelated scheduled plugin did not continue"
            );
            require(
                failing.disableCalls.get()==1,
                "task failure disable count"
            );
            require(
                manager.terminalDiagnostics()
                    .stream()
                    .anyMatch(
                        line->
                            line.contains(
                                "plugin=task.failure"
                            )&&
                            line.contains(
                                "phase=CALLBACK:TASK"
                            )
                    ),
                "task failure diagnostic missing"
            );
        }finally{
            world.close();
        }
    }

    private static void contentFailureNoFallback()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPluginManager manager=
            (WorldPluginManager)
                world.plugins();
        AtomicInteger fallbackCalls=
            new AtomicInteger();

        world.content().installCustom(
            new ContentModule(){
                @Override public String id(){
                    return "cascade-base";
                }

                @Override public void register(
                    ContentRegistrar registrar
                ){
                    registrar.command(
                        COMMAND,
                        1,
                        context->{
                            fallbackCalls.incrementAndGet();
                            return ContentResult.handled(
                                "FALLBACK",
                                null
                            );
                        }
                    );
                }
            }
        );

        CascadePlugin failing=
            new CascadePlugin(
                "content.failure",
                Collections.<String>emptyList(),
                new ArrayList<>(),
                context->
                    context.content()
                        .command(
                            COMMAND,
                            100,
                            command->{
                                throw new IllegalStateException(
                                    "content-runtime-failure"
                                );
                            }
                        )
            );

        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayerAndStart(
                player,
                "content-failure-player"
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                new OutboundPacketQueue(),
                new IsaacCipher(
                    new int[]{1,2,3,4}
                )
            );

        try{
            manager.enable(failing);

            boolean failed=false;

            try{
                world.submitAndWait(
                    player,
                    generation,
                    ()->world.content()
                        .dispatchCommand(
                            player,
                            "::"+COMMAND,
                            writer
                        ),
                    5_000L
                );
            }catch(Exception expected){
                failed=true;
            }

            require(
                failed,
                "failing winner did not propagate failure"
            );
            require(
                fallbackCalls.get()==0,
                "same dispatch fell through to shadowed handler"
            );
            require(
                manager.plugin(
                    "content.failure"
                )==null&&
                failing.disableCalls.get()==1,
                "content failure did not terminalize owner"
            );
            require(
                manager.terminalDiagnostics()
                    .stream()
                    .anyMatch(
                        line->
                            line.contains(
                                "plugin=content.failure"
                            )&&
                            line.contains(
                                "phase=CALLBACK:CONTENT_COMMAND"
                            )
                    ),
                "content failure diagnostic missing"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation))
                world.unregisterPlayer(
                    player,
                    generation
                );
            world.close();
        }
    }

    private static void assertFailureSinkReleased(
        PluginHandle handle
    )throws Exception{
        java.lang.reflect.Field sink=
            handle.getClass()
                .getDeclaredField(
                    "failureSink"
                );
        sink.setAccessible(true);

        if(sink.get(handle)!=null)
            throw new AssertionError(
                "terminal PluginHandle retained runtime failure sink"
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
    private interface Installer {
        void install(
            PluginContext context
        )throws Exception;
    }

    private static final class CascadePlugin
        implements Plugin {

        private final PluginManifest manifest;
        private final List<String> disableOrder;
        private final Installer installer;
        final AtomicInteger disableCalls=
            new AtomicInteger();

        CascadePlugin(
            String id,
            List<String> dependencies,
            List<String> disableOrder,
            Installer installer
        ){
            manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    dependencies
                );
            this.disableOrder=disableOrder;
            this.installer=installer;
        }

        @Override public PluginManifest manifest(){
            return manifest;
        }

        @Override public void enable(
            PluginContext context
        )throws Exception{
            installer.install(
                context
            );
        }

        @Override public void disable(){
            disableCalls.incrementAndGet();
            disableOrder.add(
                manifest.id()
            );
        }
    }

    private static final class CascadeEvent
        implements DomainEventBus.Event {}

    private PluginRuntimeFailureCascadeTest(){}
}
