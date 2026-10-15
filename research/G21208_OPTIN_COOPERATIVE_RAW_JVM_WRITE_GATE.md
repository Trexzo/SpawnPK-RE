# G21.208 — opt-in cooperating RAW FilePlayerRepository JVM write reservation

## Certified base and motivation

Based on cumulative G21.207/G18.7.2 [Draft #2662](https://github.com/Trexzo/SpawnPK-RE/pull/2662) exact `d0a75bb9a9c9fb05838c89fe4e1fb5b99642c1fc` (521 focused registered, own exact-head hosted run may still be in progress). Its core G21.207 demonstrates a **real stale RAW overwrite under a live JVM account lease** when RAW calls use historical defaults. A green G21.207 is negative gap evidence, not a fix.

## Narrow G21.208 production experiment

Introduce default-OFF system property `spk.local.experimentalRawWriteJvmLeaseGate=true`:
- Cooperating direct `FilePlayerRepository.save(snapshot)` resolves the account path **exactly once**, then obtains an in-memory RAW write reservation **before** creating any historical `.tmp` file; the reservation is released only after success/exception/cleanup. With an exact-path active opt-in `LocalJvmAccountSessionLease`, RAW is refused with `G21.208 RAW_WRITE_DENIED_ACTIVE_JVM_LEASE`.
- A second same-path cooperating RAW write is also refused before the legacy fixed `.tmp` is touched; otherwise simultaneous RAW writers could corrupt that shared temp leaf. A new *opt-in* JVM session lease is also refused while a pre-existing cooperating RAW write reservation is active (`G21.208 JVM_LEASE_DENIED_RAW_WRITE_IN_FLIGHT`). Both admission decisions use a single synchronized in-JVM reservation map, independent of filesystem operation duration; no global account lock is held while RAW bytes are written.
- Existing guarded `saveForWorld`, opt-in capture-bound World `SaveTask`, checkpoints, and test-only G21.207 historical default behavior remain unchanged. Individual account paths are scoped independently.
- The focused regression demonstrates both reservation orderings, a second RAW reservation denial, reject-before-temp, disk unchanged under denied RAW, healthy World save, unrelated account, and legacy RAW after test release. **521→522 focused**.

## Explicit limits (not solved)

This is **NOT** a general raw writer fence. Historical `LocalAccountProfiles.save`, `AccountStore.save`, standalone `StrictDurablePlayerSnapshotWriter`, arbitrary manual file changes, cross-JVM writers and external tools **do not participate**. Prior-unleased real World sessions and dynamic runtime flag toggles remain unsafe. It is a cooperating same-process gate only, not durable epoch/intent, startup proof, cross-process account recovery, physical-crash guarantee, source parity with original SpawnPK or permission to retire ACTIVE review markers / replay or grant items. If both experimental properties are not enabled at process startup before admissions, it must not be treated as protection.

**Acceptance:** new test marker `G21208_COOPERATIVE_RAW_JVM_RESERVATION_PASS`; independent exact-head hosted Java11 major55, **522/522 focused**, full Gradle. Draft/unmerged until green and reviewed. Only `LocalJvmAccountSessionLease.java`, `FilePlayerRepository.java`, new Java regression, Gradle manifest/task, and this research note should change.
