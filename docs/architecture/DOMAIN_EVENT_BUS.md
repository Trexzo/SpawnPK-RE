# Domain Event Bus

Issue #3 originally proposed publishing packet-handler activity directly onto an event bus. That is no longer the intended architecture.

The R8.5 architecture now keeps protocol and gameplay boundaries separate:

```text
socket bytes
  -> exact packet decoder/schema
  -> typed ClientRequest
  -> validation / semantic routing
  -> World / WorldCommandInbox
  -> domain service
  -> DomainEventBus
  -> presentation/content observers
```

## Contract

`spk.event.DomainEventBus` is synchronous. Publishing an event does not create another gameplay thread and does not bypass `WorldCommandInbox`.

Publication is allowed only while the authoritative `WorldPulse` execution context is active. The `World` instance supplies that execution-context guard.

Listeners are deterministic:

1. `HIGH`
2. `NORMAL`
3. `LOW`

Listeners at the same priority run in registration order.

A cancellable event can be cancelled by an earlier listener. Later listeners are skipped unless they explicitly subscribe with `receiveCancelled=true`.

Subscriptions are explicit removable handles. Removing a subscription is idempotent, and a removed listener cannot be invoked by later publications.

## Boundary

The event bus carries validated domain events, not:

- raw opcodes;
- socket payloads;
- ISAAC state;
- packet-writer internals;
- client scene-index authority by itself.

Wire schema/provenance stays with the typed request/validation layers. Event types may carry domain-level authority metadata where needed, but the bus itself does not reinterpret evidence.

This foundation deliberately does not refactor `ClientPacketProbe`, `ApplicationUiService`, or the active Issue #17 request queue. Domain publication should be integrated only after the corresponding request has been validated and routed onto the world execution model.
