# R90 — producer incarnation identity and capture-sequence continuity

**DRAFT / OFFLINE ONLY / STACKED ON R89 PR #3134 / NOT DEPLOYED**

## Source-level finding

The actual `WorldPlayerPersistence` already assigns a local
`captureOrder` sequence on the **World execution thread** and carries
its ordinals through deferred `CapturedSave.ticket.captureOrdinal`.
Its existing R72 offline-only
`seedOfflineCaptureOrderForTest(committedOrdinal)` accepts a
validated persisted highwater **before World starts** and refuses
double-seeding, invalid overflow or seeding after captures.

R89 introduced a durable, PREPARED-first capture watermark
under the real R60 lock and R63/R64 account protocol, but it
does not know whether a new World instance or a stale detached
World produced the next numeric ordinal. An arbitrary greater
number is not genuine event provenance.

## R90 offline producer record

New disposable, per-account
`sessions/opensrc.r90-producer.properties` stores a strict
checksummed record containing:

- Bound R64 owner UUID/epoch, account generation, R63 snapshot
  digest and R59 journal revision;
- Producer **stream UUID** retained across explicitly authorized
  restarts of the same R64 owner;
- Per-startup **incarnation UUID** that changes at each restart;
- Durable monotone **incarnation generation** and last committed
  R89 capture ordinal;
- `COMMITTED` or `PREPARED` state and any pending ordinal/digest.

The UUIDs and checksum are **not authenticated credentials**.
An attacker with writable filesystem access or a noncooperating
save API can bypass the fixture; the test only protects
cooperating R90-equipped admission instances.

## Binding inside the actual save lock

R89 is unchanged for its existing installations unless a new,
explicit test-only `OfflineProducerFence` is installed. R89
calls this hook *inside its existing R60 OS-locked critical
section*, after checking R64/R63/R59 owner state and R89 highwater,
and before touching account files.

For a capture, the order is:

1. R90 verifies the expected **current incarnation**, owner
   and committed ordinal. It durably publishes a
   **PREPARED** producer record with pending capture digest.
2. R89 durably publishes its own PREPARED watermark.
3. The existing R64 code publishes the account, R63 ledger
   and R64 owner record (for a changed snapshot).
4. R89 publishes its COMMITTED watermark.
5. R90 publishes its COMMITTED producer record bound to the
   newly committed account token and capture ordinal.
6. Only then does R89 acknowledge the write.

Any exception or child death between (1) and (5) leaves a
PREPARED marker, forcing fail-closed refusal on subsequent
R90 startup/admission, regardless of whether the R89/R64
files otherwise appear consistent.

This is a **five-file conservative intent protocol**, **not**
a truly atomic commit across all files or a general recovery
mechanism. The R90 and R89 stages also use distinct atomic
staging files and R51's existing orphan-stage preflight.

## Explicit restart and owner transfer

`R90OfflineProducerContinuity.restart` requires the current
R64 owner and a fully **COMMITTED** matching R89 watermark
and R90 producer record under R60. It atomically replaces
the R90 producer record with a fresh incarnation UUID,
increments producer generation and retains both the
producer stream UUID and committed capture highwater.
An old producer's subsequent save is rejected **inside R60**
even if its R64 owner UUID/epoch/account token remains valid.

A fresh isolated World, not yet started, is seeded using
the existing R72 test hook with that persisted highwater.
Its first snapshot capture receives the **next ordinal**.
The seeding step is expressly test-owned; none of this
installs a live session or modifies the production
WorldPlayerPersistence capture path.

For an explicitly executed R64 owner takeover, R89 requires
an explicit rebind. R90 separately requires a matching
previous owner record and a new R64 owner epoch. It
allocates a fresh producer stream/incarnation and starts a
new ordinal domain from zero *only after* R89's explicit
new-owner rebind. These tokens and rebinding operations
are not authentication or automatic login.

## R90 cross-platform regression matrix

`R90OfflineProducerContinuityTest` covers:

1. A real `World.isolatedForTest` captures ordinal 1 and 2
   on its actor, commits 2 via the dedicated IO worker,
   and refuses deferred 1 with no canonical byte changes.
2. After a producer restart, the same R64 owner remains
   valid, but the old R90 incarnation is denied by
   `R90_STALE_PRODUCER`. The new World is seeded to 2,
   captures ordinal 3, commits, and preserves highwater
   over another explicit restart.
3. Identical-snapshot admission persists the ordinal even
   without account-generation change; a superseded
   incarnation is refused on an otherwise current R64 token.
4. Explicit R64 takeover and R89 rebind require an
   additional explicit R90 rebind to allocate a new
   producer stream for the successor.
5. Three independent **real child JVMs** forcibly die
   while holding the actual R60 lock:
   - after producer PREPARED but before R89;
   - after R89 COMMITTED but before R90 COMMITTED;
   - after both COMMITTED markers but before acknowledgment.
   The first two refuse every ordinary restart; the
   last permits an explicit restart and next ordinal
   while rejecting the already-completed ordinal.
6. Orphan `AtomicAccountPublisher` R51 staging evidence
   and modified checksummed producer records deny restart
   without changing the test-owned account files.

Dedicated Linux/Windows CI includes inherited R89–R63
regressions. Independent full Linux server and Windows
atomic-persistence workflows also run against the exact
commit.

## Limitations and next research

R90 does **not** cryptographically bind the World actor or
individual capture to a unique authenticated producer.
`WorldPlayerPersistence.OfflineOrderedSaveAdmission`
receives an ordinal and snapshot, **not** a producer identity
or per-ticket signature. This fixture binds one *installed
save-gate instance* to a durable incarnation, which is a
narrow offline simulation, not a proof about arbitrary
network/gameplay producers.

The extra R90 file increases the number of independently
published records and preserves crash ambiguity rather
than repairing it. It cannot prove storage durability
after power loss, directory fsync, or exactly-once combat
HP/reward/outbox delivery. Non-R90/R89 writers bypass
the fence and are therefore outside its safety guarantees.
Real `LocalSession`, production World, account paths,
packet handling, PvP and loot remain untouched.

**R90 remains draft, unmerged, offline-only, and not deployed.**
