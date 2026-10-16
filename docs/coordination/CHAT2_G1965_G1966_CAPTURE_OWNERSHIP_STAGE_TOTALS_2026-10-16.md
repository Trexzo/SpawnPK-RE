# Chat 2 G19.6.5 / G19.6.6 — stage trace ownership and aggregate outcomes

Parent: integration PR #2837, exact base commit `0e27d11c841b70c0f6b9b875814e14fa497c57ca` (693 focused tests). Source authority: CUSTOM_LOCALLAB only.

## Source-verified gaps

1. `LocalLabSequentialTerminalStageTrace.record` originally checked `FinalizeResult.sourceWorld` but not which trace instance created `Capture`. A different tracer for the **same** World could record or consume a foreign invocation-local capture, contaminating diagnostics.
2. The stage tracer kept only the most recent 128 `Report` objects and an overall `observed/evicted` count. When an older `THREW` or `NOT_REACHED` report was evicted, the per-stage outcome distribution was lost.

## Implementation

- Capture now carries a private exact collector identity. `record` rejects a same-World foreign capture with `SEQUENTIAL_TERMINAL_STAGE_CAPTURE_OWNER_MISMATCH` *before* mutating the capture, counters, or report ring; the earlier different-World fence retains its precedence.
- World-local, in-memory `EnumMap<Stage,EnumMap<Outcome,Long>>` counts all accepted diagnostic observations, with saturation at `Long.MAX_VALUE`.
- `Snapshot.outcomeCounts` is a detached, deeply immutable view; `outcomeCount(stage,outcome)` gives a precise aggregate observation.
- All `G4,G14,G15,G16,G18` and `RETURNED,THREW,NOT_REACHED,SKIPPED_NO_CURRENT_RECIPIENT` outcomes are tracked. Earlier report detail remains bounded at 128.

## Focused tests

- `G1965SameWorldSequentialTraceCaptureOwnerTest`: genuine finalized and settled NPC, two separate same-World trace instances, reject misattribution without consuming original capture, reject double record, no additional settlement.
- `G1966SequentialStageOutcomeEvictionTotalsTest`: 131 unique real canonical NPC deaths in an installed default progression graph; check 128 retained, three evicted, all 131 per-stage RETURNED callback totals, deeply immutable detached early snapshot, no fabricated alternative outcome, one ground drop per NPC. The fixture uses an **unregistered spawner session owner** but a **registered and current recipient**. This prevents unrelated G1 auto-respawn pending-ticket collisions while exercising authentic World NPC lifecycle, ground settlement, G1 recipient record and installed G4/G14/G15/G16/G18 callbacks. It does not change the production policy.

Focused manifest 693 -> **695 unique**, plus exact `g1965...` and `g1966...` JavaExec regressions. Require fresh hosted exact-head 695/695, Java11 major 55, and full Gradle before certification. All existing G21 account/security code is untouched.

## Limits

The counters are **process-local diagnostics**. They are not persisted through World restart. `RETURNED` does not mean reward credited; `THREW` can mean partial mutation. No original SpawnPK killer authority is inferred. There is no consumer grant transaction, persisted death-event outbox, idempotency key, replay loop, durable ACK or reward migration. Issue #2587 remains open; nothing here should justify automatic replay of rewards.
