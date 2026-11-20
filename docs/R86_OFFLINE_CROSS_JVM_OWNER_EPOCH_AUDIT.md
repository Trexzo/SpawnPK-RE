# R86 — offline owner-epoch handoff across JVMs

**DRAFT / STACKED ON R85 PR #3130 / OFFLINE ONLY / NOT DEPLOYED**

## Why a new single-file V2 fixture?

R84's disposable event record keeps its original owner epoch in
both the record and its producer-assigned event IDs. R85 introduced
a real cooperating-writer OS `FileLock` and canonical-generation
check, but deliberately did not model *owner takeover*.

A separate sidecar owner file would create a dangerous gap: the
event/checkpoint file and new owner epoch could be independently
published. R86 instead introduces a **separate, single-file V2
experimental record**, leaving R84/R85 code and their negative
controls unchanged.

Each V2 record contains:
- A producer-stream UUID and **immutable origin epoch** used by
  the previously committed kill/death IDs.
- Current owner UUID, current owner epoch and monotone revision.
- Simulated 10HP-to-zero lethal transition, stable kill/death IDs.
- Three independent projection values and checkpoint bits.
- A SHA-256 data-integrity digest; this is *not* tamper-proof
  authentication.

R86 restricts takeover to an already-committed source event.
Pre-source owner transfer is a different design problem and
explicitly rejected by this fixture. Under an OS `FileLock`
permanently associated with the fixture, takeover atomically
replaces the **single** canonical record, incrementing the epoch
and revision and assigning a new owner UUID. Event IDs, HP, and
projection values are preserved verbatim.

Every cooperating write **reopens and validates the canonical
file while holding that same lock**, checks expected stream UUID,
owner UUID, epoch and revision, and rejects stale writers before
mutation. Normal commits also publish the revision increment,
consumer value and checkpoint bit in that single record. Repeated
completed consumer commits are no-ops. A stale same-epoch snapshot
is refused as surely as an old-epoch writer.

## Cross-JVM and failure regressions

The dedicated `R86OfflineOwnerEpochTakeoverTest` checks:

1. Same-JVM explicit owner transfer preserves the already-committed
   `KILL`/`DEATH` event IDs and objective checkpoint; the new
   owner commits remaining match and collection checkpoints.
2. The original owner cannot write or reopen with its stale
   token after takeover. A failed write invalidates the stale
   writer instance until explicit review.
3. A **real second JVM** receives the predecessor's snapshot token,
   performs the owner transfer and commits a match checkpoint.
   The original JVM is rejected and can only proceed using an
   explicitly presented successor token.
4. **Two real contender JVMs** attempt takeover with the same
   predecessor token. The OS lock serializes them; exactly one
   advances the epoch; the other fails its predecessor CAS.
5. Injected failure **before atomic move** leaves the predecessor
   and all checkpoints unchanged. Failure **after move but
   before acknowledgment** may publish the successor. The
   predecessor must fail on revalidation; inspecting the file
   does not automatically authorize a new writer.
6. Two same-epoch handles with different committed revisions
   cannot overwrite one another's checkpoints.
7. Missing or corrupted canonical files are quarantined and
   cannot be silently reconstructed.

All tests use newly-created disposable temporary directories and
JDK child processes. The inherited R85 real OS-lock tests, R84
lost-update *negative control*, R83 diagnostics, and R79–R81 real
offline combat-producer regressions remain in CI.

## Security boundaries

**The predecessor token is not a secret or login credential.**
This exercise assumes all writers follow its cooperative lock
and compare-and-swap protocol. A malicious process with write
access can alter the record or bypass the wrapper. There is no
authenticated owner transfer, trusted lease, account login,
client packet, real World, production JVM/session takeover,
cross-account transaction, outbox, or automatic event replay.

The record is staged and `FileChannel.force(true)` is applied
before `ATOMIC_MOVE`. There is **no directory fsync or power
failure durability proof**. The simulated crash cutpoints throw
exceptions rather than killing the host. On unsupported file
systems the fixture refuses atomic publication and provides no
non-atomic fallback.

Production migration would require an independent audit of real
R64 session authority and R63/R70/R71 coordinated account-save
ordering, authenticated owner-epoch transitions, multi-actor
combat transaction boundaries, and executable process-crash tests.

**R86 DRAFT / UNMERGED / NOT DEPLOYED / NO LIVE CHANGES.**
