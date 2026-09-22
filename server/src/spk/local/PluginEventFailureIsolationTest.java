package spk.local;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginApiVersion;
import spk.plugin.api.PluginContext;
import spk.plugin.api.PluginManifest;
import spk.plugin.api.PluginManager;

public final class PluginEventFailureIsolationTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        PluginManager manager=world.plugins();

        AtomicInteger throwingCalls=new AtomicInteger();
        AtomicInteger healthyCalls=new AtomicInteger();

        Plugin throwing=plugin(
            "events.throwing",
            context->context.events().subscribe(
                TestEvent.class,
                DomainEventBus.Priority.HIGH,
                event->{
                    throwingCalls.incrementAndGet();
                    throw new AssertionError(
                        "intentional plugin listener failure"
                    );
                }
            )
        );

        Plugin healthy=plugin(
            "events.healthy",
            context->context.events().subscribe(
                TestEvent.class,
                DomainEventBus.Priority.NORMAL,
                event->healthyCalls.incrementAndGet()
            )
        );

        try{
            manager.enable(throwing);
            manager.enable(healthy);

            if(world.domainEvents().listenerCount()!=2)
                throw new AssertionError(
                    "expected two plugin-owned listeners"
                );

            AtomicReference<Throwable> escaped=
                new AtomicReference<>();

            world.events().schedule(
                1L,
                ()->{
                    try{
                        world.domainEvents().publish(
                            new TestEvent()
                        );
                    }catch(Throwable failure){
                        escaped.set(failure);
                    }
                }
            );

            world.observePulse(
                System.currentTimeMillis()
            );

            if(escaped.get()!=null)
                throw new AssertionError(
                    "plugin listener failure escaped publication",
                    escaped.get()
                );

            if(throwingCalls.get()!=1)
                throw new AssertionError(
                    "throwing plugin did not execute exactly once"
                );

            if(healthyCalls.get()!=1)
                throw new AssertionError(
                    "healthy plugin was suppressed by prior failure"
                );

            if(!manager.disable("events.throwing"))
                throw new AssertionError(
                    "throwing plugin did not disable"
                );

            if(world.domainEvents().listenerCount()!=1)
                throw new AssertionError(
                    "throwing plugin subscription not removed"
                );

            if(!manager.disable("events.healthy"))
                throw new AssertionError(
                    "healthy plugin did not disable"
                );

            if(world.domainEvents().listenerCount()!=0)
                throw new AssertionError(
                    "healthy plugin subscription not removed"
                );

            DomainEventBus ordinary=
                new DomainEventBus(()->true);

            ordinary.subscribe(
                TestEvent.class,
                DomainEventBus.Priority.NORMAL,
                event->{
                    throw new AssertionError(
                        "ordinary bus failure"
                    );
                }
            );

            boolean corePropagationUnchanged=false;

            try{
                ordinary.publish(
                    new TestEvent()
                );
            }catch(AssertionError expected){
                corePropagationUnchanged=
                    "ordinary bus failure".equals(
                        expected.getMessage()
                    );
            }

            if(!corePropagationUnchanged)
                throw new AssertionError(
                    "ordinary DomainEventBus failure semantics changed"
                );

            System.out.println(
                "PLUGIN_EVENT_FAILURE_ISOLATION_PASS "+
                "pluginErrorContained=true "+
                "healthyListenerContinued=true "+
                "wrappedSubscriptionsRemoved=true "+
                "coreBusPropagationUnchanged=true"
            );
        }finally{
            world.close();
        }
    }

    @FunctionalInterface
    private interface Installer {
        void install(PluginContext context)throws Exception;
    }

    private static Plugin plugin(
        String id,
        Installer installer
    ){
        return new Plugin(){
            private final PluginManifest manifest=
                new PluginManifest(
                    id,
                    "1.0.0",
                    PluginApiVersion.CURRENT,
                    Collections.<String>emptyList()
                );

            @Override public PluginManifest manifest(){
                return manifest;
            }

            @Override public void enable(
                PluginContext context
            )throws Exception{
                installer.install(context);
            }
        };
    }

    private static final class TestEvent
        implements DomainEventBus.Event {}

    private PluginEventFailureIsolationTest(){}
}
