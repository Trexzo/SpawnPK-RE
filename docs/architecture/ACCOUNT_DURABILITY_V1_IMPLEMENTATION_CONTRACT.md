# Account durability v1 — bounded implementation contract

Baseline: canonical draft PR #2962 at `20b703d8702c4a437945ee0cede868c97fac61bc`.
Status: **design and implementation gate only; no durable protocol has been implemented**.
Issues: #2540 (failed final World save), #2559 (cross-World account ownership).
Do not change `main`, auto-remove ACTIVE/review witnesses, replay gameplay rewards, or claim original SpawnPK server behavior.

## Verified existing mechanisms

- `LocalJvmAccountSessionLease`: optional in-process lease plus negative retained lease after an unconfirmed final save; **not restart-durable**.
- `LocalCrossJvmAccountSessionLock`: optional cooperative session `.session.lck` OS lock, stable name, optional file identity continuity; **not a persistent owner epoch**, and hostile/uncooperative raw writes are outside its guarantee.
- `FilePlayerRepository`: extensive pre-/post-move inode, hardlink and preimage observations in experimental forced-World-save path; observation is **not an atomic multi-file transaction**.
- `LocalFinalSaveNegativeWitness`: negative-only forced final-review evidence; existence vetoes admission and is **never an ACK**.

## Acceptance protocol before enabling new semantics

1. **Choose a single named-account owner before hydration.** Lease must cover World construction, WorldPlayer registration, all participating writer entrypoints and completed final-save outcome; compare normalized exact account path and immutable session generation.
2. **Declare writer participation explicitly.** `FilePlayerRepository.save`, World-owned saves, socket-session save and bootstrap import must either participate or be refused while strict ownership is enabled. A raw writer bypass is a blocker, not an implicit exception.
3. **Persist negative intent before account publication.** Versioned bounded canonical record should bind account path, owner generation, expected preimage digest/file identity, pending snapshot digest and write sequence. Never interpret unknown/malformed intent as positive authorization. Publish via NOFOLLOW temporary file, force file, atomic move, force parent directory where supported; unsupported durability fails closed.
4. **Validate before replacing account bytes.** Under participating writer exclusion, verify original owner epoch/lease, exact preimage and named-account leaf/marker object continuity. On failure preserve old account and negative operator state. No unsafe best-effort override or automatic retry.
5. **Postimage acknowledgement is not equivalent to durable owner release.** Verify canonical account bytes and file identity, record authenticated-by-local-protocol commit outcome with crash-safe ordering, and only then transition owner. Any uncertain state remains denied until operator-reviewed recovery. A single atomic account rename does not atomically update an independent intent file.
6. **Avoid irreversible side effects during retries.** Strictly no item grants, reward replay, account rollback, witness unlink or extra client packet emission in this implementation phase.

## Implementation batches and deterministic negative tests

| Batch | Deliverable | Required regression |
|---|---|---|
| A | Admission-only policy boundary and participant inventory | Two Worlds same named account: exactly one admission; rejected World never hydrates or emits a positive login |
| B | Cross-process lease lifecycle and lock-object identity | Two JVMs overlap; lock-path replacement, hardlink alias, abrupt owner kill; no simultaneous successful ownership |
| C | Before-publication durable negative intent | Existing and absent account; crash before/after file force and atomic intent rename; corrupt/unknown intent refuses login |
| D | Account postimage and uncertain final-save result | IOException before and after account atomic move, directory-force failure; never report a successful final save from partial evidence |
| E | Conservative restart admission | Reopen after process kill at every phase; no stale successful relog or automatic marker deletion |
| F | Wire-in to LocalSession/World and RAW save paths | Real TCP sessions across World boundaries, normal logout and forced disconnect; no stale generation publication |

## Red/green criteria

A test that reproduces stale login or simultaneous ownership is a **RED characterization**, not a fix. A green gate must assert the unsafe state is refused while a healthy single-owner save, close, restart and reconnect still work. Run focused regression plus full Gradle on the **new exact commit**, Java 11 bytecode major 55, Windows source-only and separate actual v308 owner-machine acceptance. Neither source-only Windows nor synthetic TCP tests imply v308 graphical acceptance.

## Explicit exclusions

- No inference of exact original SpawnPK server persistence semantics.
- No authenticated external service, crash-durable grant ACK, cross-domain ACID transaction or automatic reward recovery claimed.
- No promotion of negative final-review witness or forensic record into permission to retire ACTIVE.

## Existing proof inventory (checked 2026-10-18)

- `G21185CoreCrossJvmSessionFileLockTest` already verifies cooperating OS-lock exclusion across independent Java processes and subsequent release. Do **not** recreate that as novel coverage.
- `G21186CoreCrossJvmRealSocketAccountSessionTest` already exercises competing cross-process synthetic TCP logins and successful later hydration/final save. This is a **cooperating-process positive case only**, not crash-durable epoch proof.
- `G21377TwoWorldAccountAdmissionTest` is a small negative test of an existing opt-in lease, with no WorldPlayer registration attempted. It verifies no accidental registration during rejection; its marker deliberately says `noWorldRegistration`, **not** `noHydration`.
- Distinct missing end-to-end coverage is a failed/uncertain final World save followed by actual reconnect while a durable negative state survives process restart, plus stale competing writers that bypass or outlive cooperating lease authority. These must be designed as fail-closed policy, not implicit replay.

**Next implementation choice:** target a durable *negative admission intent* under a strict opt-in protocol and verify the write ordering with real crash cutpoints, while leaving the existing default behavior and frozen release path unchanged.
