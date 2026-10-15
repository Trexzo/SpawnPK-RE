# G21.207 — raw writer bypasses held in-JVM account session lease

**Scope:** deterministic **TEST-ONLY** gap characterization, not a production patch. Baseline: cumulative G21.206/G18.7.1 [Draft #2658](https://github.com/Trexzo/SpawnPK-RE/pull/2658), exact `40f90d2435c268db4f8d3268e65ca6ce3a52f61a` and **519** focused regressions (new exact-head CI pending when branched).

## Source evidence

`FilePlayerRepository.saveForWorld(snapshot)` invokes `saveInternal(snapshot,true)`: guarded World admission, review-fence checks, privately allocated World temp and coordinated publication. `FilePlayerRepository.save(snapshot)` invokes `saveInternal(snapshot,false)`: the historical raw/forensic route, deliberately excluded from guarded World admission and held `LocalJvmAccountSessionLease` checks. G21.206 establishes captured owner checks on ordinary queued World `SaveTask`; that does **not** establish exclusion of the direct raw route.

## Regression

A genuine registered World owner acquires and binds an opt-in JVM session token. The World captures an earlier `OLDER_CAPTURE` `PlayerSnapshot` without publishing it, then mutates to `NEWER_WORLD_POSTIMAGE` and submits a **real queued World-owned** ordinary save. Assert actual capture-bound token worker check advanced, disk contains the newer state, and the original token remains held. Next, a TEST-ONLY direct raw `repository.save(olderSnapshot)` writes the earlier snapshot while the original player/generation and lease are still current. Assert disk now contains `OLDER_CAPTURE` and the same live JVM owner remains held.

Expected marker: `G21207_RAW_WRITER_HELD_SESSION_LEASE_BYPASS_PASS`.

**Meaning:** A PASS is evidence of an unresolved stale-raw-writer gap, not a green recovery/security guarantee. Direct saves may remain intentionally useful as manual/forensic operations, but must never be counted as a session-owner-bound durable commit. G21.207 does not change runtime, raw API, default flags, review-intent/ACTIVE retirement, cross-JVM admission, power-loss policy, Mailbox credit, original SpawnPK protocol, or frozen R25. Exactly one new test, one build manifest/task and this note; **519→520** focused tests. Requires independently hosted exact-head Java11 major55, 520/520 focused/full Gradle, and G21207/G21206/G1871 markers. Draft/unmerged.
