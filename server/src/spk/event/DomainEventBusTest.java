package spk.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class DomainEventBusTest {
    private static final class TestEvent implements DomainEventBus.Event {
        final List<String> trace = new ArrayList<>();
    }

    private static final class CancelEvent implements DomainEventBus.Cancellable {
        private boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicBoolean worldContext = new AtomicBoolean(true);
        DomainEventBus bus = new DomainEventBus(worldContext::get);

        TestEvent event = new TestEvent();
        bus.subscribe(
            DomainEventBus.Event.class,
            DomainEventBus.Priority.LOW,
            e -> event.trace.add("base-low")
        );
        bus.subscribe(
            TestEvent.class,
            DomainEventBus.Priority.NORMAL,
            e -> e.trace.add("normal-1")
        );
        bus.subscribe(
            TestEvent.class,
            DomainEventBus.Priority.HIGH,
            e -> e.trace.add("high")
        );
        bus.subscribe(
            TestEvent.class,
            DomainEventBus.Priority.NORMAL,
            e -> e.trace.add("normal-2")
        );

        bus.publish(event);
        assertTrace(
            event.trace,
            "high",
            "normal-1",
            "normal-2",
            "base-low"
        );

        AtomicInteger cancelledNormal = new AtomicInteger();
        AtomicInteger cancelledObserver = new AtomicInteger();
        bus.subscribe(
            CancelEvent.class,
            DomainEventBus.Priority.HIGH,
            e -> e.cancel()
        );
        bus.subscribe(
            CancelEvent.class,
            DomainEventBus.Priority.NORMAL,
            e -> cancelledNormal.incrementAndGet()
        );
        bus.subscribe(
            CancelEvent.class,
            DomainEventBus.Priority.LOW,
            true,
            e -> cancelledObserver.incrementAndGet()
        );

        CancelEvent cancelled = new CancelEvent();
        bus.publish(cancelled);
        if (!cancelled.isCancelled()) {
            throw new AssertionError("event was not cancelled");
        }
        if (cancelledNormal.get() != 0) {
            throw new AssertionError(
                "cancelled event reached normal listener"
            );
        }
        if (cancelledObserver.get() != 1) {
            throw new AssertionError(
                "cancelled observer count=" + cancelledObserver.get()
            );
        }

        AtomicInteger removableHits = new AtomicInteger();
        DomainEventBus.Subscription removable = bus.subscribe(
            TestEvent.class,
            DomainEventBus.Priority.HIGH,
            e -> removableHits.incrementAndGet()
        );
        if (!removable.active()) {
            throw new AssertionError("subscription not active");
        }
        if (!removable.unsubscribe() || removable.active()) {
            throw new AssertionError("unsubscribe failed");
        }
        if (removable.unsubscribe()) {
            throw new AssertionError("unsubscribe was not idempotent");
        }
        bus.publish(new TestEvent());
        if (removableHits.get() != 0) {
            throw new AssertionError(
                "removed listener was invoked"
            );
        }

        worldContext.set(false);
        try {
            bus.publish(new TestEvent());
            throw new AssertionError(
                "off-world publication was accepted"
            );
        } catch (IllegalStateException expected) {
            if (
                !expected.getMessage().contains(
                    "World execution context"
                )
            ) {
                throw expected;
            }
        }

        System.out.println(
            "DOMAIN_EVENT_BUS_PASS priority=true cancellation=true " +
            "removable=true worldContextGuard=true listeners=" +
            bus.listenerCount()
        );
    }

    private static void assertTrace(
        List<String> actual,
        String... expected
    ) {
        List<String> wanted = Arrays.asList(expected);
        if (!actual.equals(wanted)) {
            throw new AssertionError(
                "trace=" + actual + " expected=" + wanted
            );
        }
    }
}
