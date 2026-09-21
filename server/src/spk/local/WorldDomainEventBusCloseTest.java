package spk.local;

import spk.event.DomainEventBus;

public final class WorldDomainEventBusCloseTest {
    private static final class TestEvent
        implements DomainEventBus.Event {}

    public static void main(String[] args)
        throws Exception {
        World world=
            World.isolatedForTest(
                GameClock.TICK_MILLIS
            );

        DomainEventBus bus=
            world.domainEvents();

        DomainEventBus.Subscription subscription=
            bus.subscribe(
                TestEvent.class,
                DomainEventBus.Priority.NORMAL,
                event -> {}
            );

        if(!subscription.active()||
           bus.listenerCount()!=1)
            throw new AssertionError(
                "pre-close listener state active="+
                subscription.active()+
                " listeners="+
                bus.listenerCount()
            );

        world.close();

        if(subscription.active())
            throw new AssertionError(
                "subscription remained active"
            );

        if(subscription.unsubscribe())
            throw new AssertionError(
                "unsubscribe after World close "+
                "was not idempotent"
            );

        if(bus.listenerCount()!=0)
            throw new AssertionError(
                "World close retained listeners="+
                bus.listenerCount()
            );

        boolean subscribeRejected=false;
        try{
            bus.subscribe(
                TestEvent.class,
                DomainEventBus.Priority.NORMAL,
                event -> {}
            );
        }catch(IllegalStateException expected){
            subscribeRejected=
                expected.getMessage().contains(
                    "domain event bus closed"
                );
        }

        if(!subscribeRejected)
            throw new AssertionError(
                "subscribe after World close accepted"
            );

        boolean publishRejected=false;
        try{
            bus.publish(new TestEvent());
        }catch(IllegalStateException expected){
            publishRejected=
                expected.getMessage().contains(
                    "domain event bus closed"
                );
        }

        if(!publishRejected)
            throw new AssertionError(
                "publish after World close accepted"
            );

        world.close();

        if(bus.listenerCount()!=0||
           subscription.active())
            throw new AssertionError(
                "repeated World close changed "+
                "terminal bus state"
            );

        System.out.println(
            "WORLD_DOMAIN_EVENT_BUS_CLOSE_PASS "+
            "listenersCleared=true "+
            "subscriptionInactive=true "+
            "subscribeRejected=true "+
            "publishRejected=true "+
            "repeatedClose=true"
        );
    }
}
