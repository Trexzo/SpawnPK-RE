# G21.209 — legacy named-profile writer bypasses G21.208 cooperative RAW gate

## Exact parent

Cumulative [G21.208/G18.7.3 Draft #2665](https://github.com/Trexzo/SpawnPK-RE/pull/2665), exact SHA `1dc29e0612ff3412901959bd3c7e03fea1cb25fd`, 523 focused regressions registered. **Parent exact-head CI required**; do not infer certification from its ancestors.

## Source-backed distinction

`FilePlayerRepository.save(snapshot)` is a participating RAW adapter when `spk.local.experimentalRawWriteJvmLeaseGate=true`; it uses one normalized selected account path and is refused during a held opt-in `LocalJvmAccountSessionLease`. `LocalAccountProfiles.save(username,bank,equipment,movement,pet,player)` is a **separate historical compatibility writer**, and for non-primary named accounts it independently constructs a SchemaV1 core-only snapshot and writes the same `LocalAccountProfiles.accountFile(username)` path through a fixed `.tmp` + atomic move/fallback **without** G21.208 reservation or session owner checks. The primary `opensrc` path delegates to `AccountStore.save` and is not covered by this named-profile test.

The new test holds a real exact WorldPlayer generation + opt-in JVM token with **both opt-in lease and RAW gate properties enabled**, publishes a newer `LATEST_WORLD_EXTENSION` through a true queued World-owned ordinary SaveTask and proves original capture-owner check. It confirms a direct cooperating `FilePlayerRepository.save` is denied without modifying disk. Then the test-only legacy `LocalAccountProfiles.save` succeeds on that exact account path, replaces disk state, removes newer extension data, and leaves the original World JVM lease untouched.

Expected marker `G21209_LEGACY_PROFILE_WRITER_HELD_JVM_LEASE_BYPASS_PASS`. A green result **proves remaining bypass**, not safety or production fix. Does not claim this legacy adapter is on ordinary TCP/session save path; test intentionally invokes it. No production Java/flags changes; new Java test, focused Gradle **523→524** and dedicated task, this research note. `AccountStore.save`, independent strict writers, other RAW/manual/external writers, process restart, cross-JVM epoch, durable failure/intent, ACTIVE marker retirement and G1/credit outbox remain out of scope. Keep Draft/unmerged, require exact-head hosted Java11 major55, **524/524 focused and full Gradle**.

## G21.210 descendant compatibility note

The **original** G21.209 [Draft #2666](https://github.com/Trexzo/SpawnPK-RE/pull/2666) remains the archived exact-head gate-ON bypass witness. In the subsequent G21.210 production-gate branch, the G21.209 Java test has been changed to explicitly turn the RAW gate OFF only for its negative legacy write. That retained test now characterizes **legacy opt-out compatibility**, not continued bypass when the new named-profile guard is enabled. The new independent G21.210 positive test must prove the ON case is denied before any legacy temp is allocated. Never toggle runtime protection in live sessions; this flag flip is a test-only fixture.
