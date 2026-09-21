package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import spk.event.DomainEventBus;

public final class WorldDomainEventBusCloseOrderingTest {
    private static final class TestEvent
        implements DomainEventBus.Event {}

    public static void main(String[] args)
        throws Exception {

        World world=
            World.isolatedForTest(10L);
        WorldPlayer player=
            new WorldPlayer();

        CountDownLatch tickEntered=
            new CountDownLatch(1);
        CountDownLatch releaseTick=
            new CountDownLatch(1);
        CountDownLatch tickFinished=
            new CountDownLatch(1);
        CountDownLatch closeReturned=
            new CountDownLatch(1);

        AtomicBoolean firstTick=
            new AtomicBoolean();
        AtomicInteger delivered=
            new AtomicInteger();
        AtomicReference<Throwable> publishFailure=
            new AtomicReference<>();
        AtomicReference<Throwable> closeFailure=
            new AtomicReference<>();

        try{
            long generation=
                world.registerPlayer(
                    player,
                    "event-close-order"
                );

            DomainEventBus bus=
                world.domainEvents();

            DomainEventBus.Subscription subscription=
                bus.subscribe(
                    TestEvent.class,
                    DomainEventBus.Priority.NORMAL,
                    event->delivered.incrementAndGet()
                );

            world.attachTickTarget(
                new WorldTickTarget(){
                    @Override public EntityId ownerId(){
                        return player.id();
                    }

                    @Override public long ownerGeneration(){
                        return generation;
                    }

                    @Override public void onWorldTick(
                        long tick,
                        long nowMillis
                    ){
                        if(!firstTick.compareAndSet(
                                false,
                                true
                            ))
                            return;

                        tickEntered.countDown();

                        for(;;){
                            try{
                                releaseTick.await();
                                break;
                            }catch(InterruptedException ignored){
                                // WorldPulse.close() interrupts the pulse
                                // thread. Keep this callback in-flight until
                                // the test explicitly releases it.
                            }
                        }

                        try{
                            bus.publish(
                                new TestEvent()
                            );
                        }catch(Throwable error){
                            publishFailure.set(error);
                        }finally{
                            tickFinished.countDown();
                        }
                    }
                }
            );

            world.start();

            if(!tickEntered.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "tick callback did not enter"
                );

            Thread closer=
                new Thread(
                    ()->{
                        try{
                            world.close();
                        }catch(Throwable error){
                            closeFailure.set(error);
                        }finally{
                            closeReturned.countDown();
                        }
                    },
                    "world-domain-event-close-order"
                );

            closer.start();

            waitFor(
                world::closed,
                2_000L,
                "World close fence"
            );

            if(closeReturned.await(
                    100,
                    TimeUnit.MILLISECONDS))
                throw new AssertionError(
                    "World.close returned while tick callback was blocked"
                );

            releaseTick.countDown();

            if(!tickFinished.await(
                    2,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "tick callback did not finish"
                );

            if(!closeReturned.await(
                    2,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "World.close did not finish"
                );

            closer.join(1_000L);

            if(closer.isAlive())
                throw new AssertionError(
                    "close thread retained"
                );

            if(closeFailure.get()!=null)
                throw new AssertionError(
                    "World.close failed",
                    closeFailure.get()
                );

            if(publishFailure.get()!=null)
                throw new AssertionError(
                    "in-flight publication failed during close",
                    publishFailure.get()
                );

            if(delivered.get()!=1)
                throw new AssertionError(
                    "in-flight publication delivery="+
                    delivered.get()
                );

            if(subscription.active()||
               bus.listenerCount()!=0)
                throw new AssertionError(
                    "terminal bus state active="+
                    subscription.active()+
                    " listeners="+
                    bus.listenerCount()
                );

            System.out.println(
                "WORLD_DOMAIN_EVENT_BUS_CLOSE_ORDER_PASS "+
                "inFlightPublishDelivered=true "+
                "pulseQuiescedBeforeBusClose=true "+
                "terminalListenersCleared=true"
            );
        }finally{
            releaseTick.countDown();

            if(player.registered())
                world.unregisterPlayer(player);

            world.close();
        }
    }

    private static void waitFor(
        Check check,
        long timeoutMillis,
        String label
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(
                timeoutMillis
            );

        while(System.nanoTime()<deadline){
            if(check.ok())
                return;

            Thread.sleep(5L);
        }

        throw new AssertionError(
            label+" timeout"
        );
    }

    private interface Check {
        boolean ok();
    }

    private WorldDomainEventBusCloseOrderingTest(){}
}
