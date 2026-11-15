# R75 — Offline retirement-safety audit of unguarded writers and stale reconciliation

## Decision

**Do not retire R73 write-intent markers, release quarantine, authorize replay or admit production login on the basis of R74 reconciliation.** R74 can compare one R72 ACK, the canonical account hash, R63 generation, R64 RELEASED owner and R59 trade index under the cooperative R60 OS lock, but this is only a read-only observation at one instant. It does not prove that every account writer uses the same lock or pre-admission intent protocol.

R75 establishes concrete, executable counterexamples using the actual LocalLab persistence classes and separately launched Java processes. It adds a **read-only explanatory retirement audit** and an isolated deterministic regression. The objective is to identify blockers, not introduce a retirement operation.

## Source audit of currently unfenced entrypoints

The R75 test reads the checked-out repository sources and asserts that these known entrypoints still exist and do not themselves refer to `R73OfflinePendingWriteAdmission`. The list is a **non-exhaustive risk inventory**, not a full transitive call graph. Some are compatibility or offline-only helpers.

| Source | Known operation | Missing guarantee |
|---|---|---|
| `WorldPlayerPersistence.java` | Default `repository.save(snapshot)` | Live/default worker does not install R73 test hook |
| `LocalAccountLifecycle.java` | `repository.save(snapshot)` | Direct compatibility save has no R73 preflight |
| `FilePlayerRepository.java` | `AtomicAccountPublisher.publish` | Actual filesystem publisher does not require R60/R73 |
| `AccountStore.java` | `AtomicAccountPublisher.publish` | Historical primary-profile file writer bypasses R73 |
| `LocalAccountProfiles.java` | `AtomicAccountPublisher.publish` | Secondary-profile writer bypasses R73 |
| `PetAccessoryPersistence.java` | `AtomicAccountPublisher.publish` | Direct account-property merge bypasses R73 |
| `R64OfflineSessionOwnership.java` | `replaceOwner(...)` | Existing offline owner replacement checks R64/R63 but not R73 blocker |
| `R63AccountSaveAdmission.java` | `saveIfCurrent(...)` | R63 canonical+generation save holds R60 but does not require R73 intent |

This source check is deliberately a **review alarm**: if one ingress changes, its test fails and the inventory must be manually revisited. Merely adding a string containing R73 would not prove full integration, so this is not an authorization proof.

## New read-only audit

`R75OfflineRetirementSafetyAudit.inspect(username)` calls R74's existing read-only reconciliation. It distinguishes:

- `BLOCK_UNRESOLVED_ACCOUNT_EVIDENCE`: R74 is not even at a matching-ACK/canonical/released-owner state
- `BLOCK_UNFENCED_WRITER_AND_OWNER_PATHS`: R74 evidence matches, but the eight documented bypass routes still invalidate the inference that the R73 marker may safely be retired
- `BLOCK_STALE_RECONCILIATION_OBSERVATION`: `recheck(username, previous)` sees a different R74 status, observation UUID or account generation than the prior review

These are **all blocking statuses**. For every outcome, `retirementAuthorized`, `loginAuthorized`, `recoveryAuthorized`, `replayAuthorized`, `quarantineReleased`, `allWritersQuiescent`, `productionAuthenticated` and `markersModified` remain false. The audit has no filesystem write, delete, marker-cleanup, grant, action or renewal method.

Rechecking can detect a changed outcome, but it cannot detect a transient **A→B→A** account rewrite that restores the original digest, and it is not an atomic snapshot spanning the interval between two observations. Even a repeated matching result must stay quarantined.

## Windows acceptance — November 15, 2026

The isolated test uses a real `World.isolatedForTest`, normal `FilePlayerRepository`, actual `WorldPlayerPersistence` worker and the R71/R72/R73 test-only intent/final-save path to produce a **genuine, fully consistent R74 ACK, generation-2 account and RELEASED R64 owner**. No production profile path or live server is used.

`R75_OFFLINE_RETIREMENT_SAFETY_AUDIT_PASS` proves:

1. A matching ACK produces `BLOCK_UNFENCED_WRITER_AND_OWNER_PATHS` from R75, never a release grant. The R73 blocker is byte-for-byte preserved.
2. The test holds the **actual R60 cross-process OS FileLock** in its parent JVM, then launches an independent JVM that invokes `FilePlayerRepository.save` on the same isolated canonical account **without acquiring R60**. The file changes under the held lock. R63 ledger, R64 owner record and R73 intent remain unchanged. R74 subsequently reports a canonical/generation mismatch, and R75 reports a stale review. This demonstrates that R60 protects **cooperative** writers only.
3. The test explicitly restores the previously committed canonical snapshot, producing an **A→B→A** history in which R74 once again reports matching evidence. R75 still denies retirement because it cannot establish the absence of intervening writes or active noncooperative writers.
4. The guarded `R73.replaceOwnerIfClear` refuses the durable registered blocker, even after ACK and RELEASED.
5. A second JVM invokes the original **unguarded offline `R64.replaceOwner`** with the exact prior RELEASED token. It successfully increments the R64 owner epoch despite the R73 blocker. R74 then rejects the now ACTIVE owner, R75 flags the prior observation as stale, and the R73 blocker remains intact. This is not a claim about actual authenticated login; it exposes a bypass in the offline owner-helper API.
6. A third separate-JVM path calls **`R63.saveIfCurrent`**, which uses R60 but not R73, to write a modified snapshot and advance R63 generation. The original R64 owner record is not advanced, so R64 inspection becomes indeterminate and R74 reports mismatched evidence. R75 denies retirement; the R73 marker is unchanged.
7. Read-only R75 inspections are bracketed by byte-for-byte snapshots of the disposable canonical, ledger, owner, journal and R72/R73 evidence files. None are modified by the reviewer.

The Windows JDK21 isolated Gradle `jar testClasses verifyJava11Bytecode --offline --no-daemon` build passed with **2,313 major-55 Java 11 class files**. The new R75 suite passed. Dedicated independent Windows and Linux workflows cover R75 and the inherited R55–R74 session, checkpoint, trade and atomic-write regressions.

## Why R74 + free R60 lock is not enough

1. The lock is voluntary. A noncooperative file writer can complete its atomic replacement while a reviewer owns R60.
2. R63 canonical+generation writes and R64 session-owner replacements have direct offline entrypoints that do not consult R73. They can invalidate a previously consistent R74 snapshot **after its lock has been released**.
3. A digest-matching canonical snapshot is not a history of all I/O operations; ABA writes are observationally indistinguishable from no write at all.
4. ACK is an observation from one worker, not an authenticated proof that every other writer is terminated and will never publish a delayed commit. R72 already demonstrated cancellation-vs-late-write uncertainty.
5. R73 has no durable retirement/grant protocol. Deleting the evidence marker after a read would create an unlocked check-then-admit window, as well as erase important crash evidence.
6. R57/R59 pending trade quarantine, separate-file R63/R64 publication and hardware power-loss recovery require independent treatment.

## Production hardening prerequisites

Before any real marker retirement or login grant is considered, all **actual** account writers and owner issuers must be inventoried and routed through one authenticated, durable, cooperative admission protocol; all delayed callbacks must be fenced at their final commit point; the server must prove a stable quiescent generation or equivalent durable writer lease across reconciliation and permission publication; and multi-file partial publication/trade intent recovery must be separately verified. A future retirement transaction must itself be atomic with new owner admission and must not silently delete ambiguous evidence.

**R75 provides NONE of those retirement permissions.** The R73 blocker remains intentionally non-expiring. Avoid using R75's test-only results to enable live login.

## R76 priority

Implement an **offline inventory and negative-only owner/writer admission review** that accounts for the discovered default, legacy and component-level writers; prove any production-shaped writer not registered with one durable lease blocks a proposed retirement. Keep the first milestone read-only, with independent cross-JVM tests and no automatic marker deletion. Only after the complete writer graph is audited should a durable retirement protocol even be designed.

**R75 DRAFT, stacked on R74, UNMERGED.**
