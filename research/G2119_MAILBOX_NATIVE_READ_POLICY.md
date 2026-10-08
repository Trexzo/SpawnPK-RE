# G21.19 — native Mailbox row READ policy boundary

**Predecessor:** exact certified G21.18 `e44e5db9654f4edab20917d4782c1563c9f77cc2` / 317 Java11 focused tests.

## Recovered v308 facts

- C2S185 row-click widget `32026+4*row`, for row 0..34.
- ScriptPacket 31 subtype `operation 7` selects a row, operation `2` updates indexed READ/UNREAD state.
- `READ` maps to 1 and `UNREAD` to 0, per `MailboxPresentationAdapter`.
- Original SpawnPK server behavior or read-acknowledgement *timing* is not recovered by these client-side facts.

## Explicit CUSTOM_LOCALLAB_G2119 policy

When an authenticated, generation-fenced player selects a bound native Mailbox inbox row, the LocalLab server:

1. Resolves the exact-current C2S185 row widget and validates the selected message's immutable identity.
2. Preflights and batches exact `S2C250/31/7 SELECT`, `S2C53/32175 attachment detail`, `S2C126/32168 subject`, and `S2C250/31/4 claim-state visibility`.
3. Calls the previously certified World-owned `publishSelectedReadState` to mark the selected message READ if UNREAD, and appends `S2C250/31/2 row READ=1` within the same outgoing packet batch.
4. Commits that packet batch.

The READ semantic transition is **idempotent**. Every successful row view emits READ=1, including replay, but the domain transition occurs once. The existing snapshot codec persists the read state.

### Failure boundaries

- Stale row/message ID, unsupported item, invalid subject, stale generation or closed scope **before READ**: packet batch is aborted and mailbox state remains unchanged.
- Output stream failure when publishing the final batch: **READ remains committed** if the semantic transition already happened. Wire publication is not a domain transaction. The next explicit `Refresh` republishes the durable READ state. No claim of rollback for partially sent network bytes.
- Bank/inventory deposit and delete widgets still do not settle or delete anything in the live Mailbox scope.
- Native root opening and competing-root lifecycle remain governed by G21.17/G21.18.

This is a LocalLab product policy; it is not presented as exact recovered SpawnPK server read timing. GitHub hosted CI validates byte-level output and World-owned semantics, **not** human-in-the-loop v308 visual gameplay acceptance.
