package spk.local;

import java.util.concurrent.atomic.AtomicInteger;
import spk.event.DomainEventBus;

/** Proves the shared domain-event bus follows the real WorldPulse execution fence. */
public final class DomainEventBusWorldContextTest {
    private static final class TickEvent
        implements DomainEventBus.Event {}

    public static void main(String[] args) throws Exception {
        World world = World.isolatedForTest(
            GameClock.TICK_MILLIS
        );
        AtomicInteger hits = new AtomicInteger();

        DomainEventBus.Subscription subscription =
            world.domainEvents().subscribe(
                TickEvent.class,
                DomainEventBus.Priority.NORMAL,
                event -> hits.incrementAndGet()
            );

        try {
            world.domainEvents().publish(
                new TickEvent()
            );
            throw new AssertionError(
                "off-world publication was accepted"
            );
        } catch (IllegalStateException expected) {
            // Expected: this test thread is not inside WorldPulse yet.
        }

        world.events().schedule(
            1L,
            () -> {
                try {
                    world.domainEvents().publish(
                        new TickEvent()
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        );

        world.pulse().pulseOnce(
            System.currentTimeMillis()
        );

        if (hits.get() != 1) {
            throw new AssertionError(
                "world publication hits=" + hits.get()
            );
        }

        subscription.close();
        if (subscription.active()) {
            throw new AssertionError(
                "subscription leaked after close"
            );
        }

        world.close();

        System.out.println(
            "DOMAIN_EVENT_BUS_WORLD_CONTEXT_PASS " +
            "offWorldRejected=true worldPulseAccepted=true"
        );
    }
}
