package spk.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Synchronous domain-event dispatcher.
 *
 * <p>This bus is intentionally transport-agnostic. Raw packet ids/payloads do not
 * belong here; callers publish validated domain events from the authoritative world
 * execution context.</p>
 *
 * <p>Priority order is HIGH, NORMAL, LOW. Equal-priority listeners run in
 * registration order. Cancellation is monotonic for the duration of a publication:
 * listeners that do not opt into cancelled events are skipped once an event is
 * cancelled.</p>
 */
public final class DomainEventBus implements AutoCloseable {
    /** Marker for validated domain events. */
    public interface Event {}

    /** Optional event contract for domain actions that can be cancelled. */
    public interface Cancellable extends Event {
        boolean isCancelled();
        void cancel();
    }

    public enum Priority {
        LOW(0),
        NORMAL(100),
        HIGH(200);

        private final int weight;

        Priority(int weight) {
            this.weight = weight;
        }

        int weight() {
            return weight;
        }
    }

    @FunctionalInterface
    public interface Listener<E extends Event> {
        void onEvent(E event) throws Exception;
    }

    /** Explicit lifecycle handle; later plugin/module layers can own these handles. */
    public interface Subscription extends AutoCloseable {
        boolean active();
        boolean unsubscribe();

        @Override
        default void close() {
            unsubscribe();
        }
    }

    private static final class Binding<E extends Event> {
        final long sequence;
        final Class<E> type;
        final Priority priority;
        final boolean receiveCancelled;
        final Listener<? super E> listener;
        volatile boolean active = true;

        Binding(
            long sequence,
            Class<E> type,
            Priority priority,
            boolean receiveCancelled,
            Listener<? super E> listener
        ) {
            this.sequence = sequence;
            this.type = type;
            this.priority = priority;
            this.receiveCancelled = receiveCancelled;
            this.listener = listener;
        }
    }

    private static final Comparator<Binding<?>> ORDER =
        new Comparator<Binding<?>>() {
            @Override
            public int compare(Binding<?> left, Binding<?> right) {
                int priority = Integer.compare(
                    right.priority.weight(),
                    left.priority.weight()
                );
                return priority != 0
                    ? priority
                    : Long.compare(left.sequence, right.sequence);
            }
        };

    private final BooleanSupplier executionContext;
    private final ArrayList<Binding<?>> bindings = new ArrayList<>();
    private long sequence;
    private boolean closed;

    public DomainEventBus(BooleanSupplier executionContext) {
        this.executionContext = Objects.requireNonNull(
            executionContext,
            "executionContext"
        );
    }

    public <E extends Event> Subscription subscribe(
        Class<E> type,
        Priority priority,
        Listener<? super E> listener
    ) {
        return subscribe(type, priority, false, listener);
    }

    public synchronized <E extends Event> Subscription subscribe(
        Class<E> type,
        Priority priority,
        boolean receiveCancelled,
        Listener<? super E> listener
    ) {
        requireOpen();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(listener, "listener");

        final Binding<E> binding = new Binding<>(
            ++sequence,
            type,
            priority,
            receiveCancelled,
            listener
        );
        bindings.add(binding);

        return new Subscription() {
            @Override
            public boolean active() {
                return binding.active;
            }

            @Override
            public boolean unsubscribe() {
                return remove(binding);
            }
        };
    }

    /**
     * Publish synchronously on the authoritative world execution context.
     *
     * <p>A publication snapshots registrations at its start. New listeners registered
     * by a callback participate only in a subsequent publication. Unsubscription is
     * effective immediately because inactive snapshot entries are skipped.</p>
     */
    public void publish(Event event) throws Exception {
        Objects.requireNonNull(event, "event");

        List<Binding<?>> snapshot;
        synchronized (this) {
            requireOpen();
            snapshot = new ArrayList<>(bindings);
        }

        requireExecutionContext();
        snapshot.sort(ORDER);

        for (Binding<?> binding : snapshot) {
            if (!binding.active || !binding.type.isInstance(event)) {
                continue;
            }
            if (
                event instanceof Cancellable &&
                ((Cancellable) event).isCancelled() &&
                !binding.receiveCancelled
            ) {
                continue;
            }
            invoke(binding, event);
        }
    }

    public synchronized int listenerCount() {
        return bindings.size();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;

        for (Binding<?> binding : bindings) {
            binding.active = false;
        }

        bindings.clear();
    }

    private synchronized boolean remove(Binding<?> binding) {
        if (!binding.active) {
            return false;
        }
        binding.active = false;
        return bindings.remove(binding);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(
                "domain event bus closed"
            );
        }
    }

    private void requireExecutionContext() {
        if (!executionContext.getAsBoolean()) {
            throw new IllegalStateException(
                "domain events must publish on World execution context"
            );
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void invoke(
        Binding binding,
        Event event
    ) throws Exception {
        binding.listener.onEvent(event);
    }
}
