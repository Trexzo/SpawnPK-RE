package spk.plugin.api;

import spk.event.DomainEventBus;

/** Typed validated-domain-event subscription capability for plugins. */
public interface PluginEvents {
    <E extends DomainEventBus.Event>
        DomainEventBus.Subscription subscribe(
            Class<E> type,
            DomainEventBus.Priority priority,
            DomainEventBus.Listener<? super E> listener
        );

    <E extends DomainEventBus.Event>
        DomainEventBus.Subscription subscribe(
            Class<E> type,
            DomainEventBus.Priority priority,
            boolean receiveCancelled,
            DomainEventBus.Listener<? super E> listener
        );
}
