# G2025–2028 — experimental on-disk terminal attempt witness

## Source / authority
Parent: [PR #2919](https://github.com/Trexzo/SpawnPK-RE/pull/2919), exact `bdeb0937897994087e8f02a47701a800aa9dbce9`, 822-test cumulative source. The new `LocalLabExperimentalDiskAttemptJournal` is standalone, NOT wired to any gameplay/consumer callback, and **never grants, writes player progression, acknowledges grants, or replays**. Real canonical `LocalLabSettledDeathEnvelope.observe` is required for each test-only reservation. Caller supplies an opaque UUID (NOT an authenticated or original SpawnPK event ID). Evidence fingerprint includes process-local NPC and observed player identity; it is NOT reliable across JVM/world restart.

## Production behavior
One cooperating writer per directory via FileChannel exclusive lock. Strict directory record scan refuses unknown temp artifacts, symlinks, malformed records, noncanonical event names, checksum mismatch and cap overrun. Each event has one small checksummed V1 record with SHA256 of exact canonical content; reservation stages are maintained in fixed G1/G4/G14/G15/G16/G18 order. Temp created CREATE_NEW with NOFOLLOW_LINKS, bytes forced before ATOMIC_MOVE replacement. At most 256 reservations total, no eviction. Prior same-stage claims and caller UUIDs with conflicting observed death fingerprints cannot be reused. Snapshot copies are unmodifiable and explicitly say grant ACK, restart replay and original event authority are false.

**Durability caveats:** no directory fsync portability guarantee, no authorization under noncooperating process/file mutations, no global cross-JVM owner epoch. A crash during temp publication leaves an unknown .tmp, which blocks reopen rather than permitting uncertain replay. This is conservative prototype storage, not a proven exactly-once reward journal.

## Real-runtime test gates
- G2025: genuine canonical NPC death and ground receipt; two consumers persisted, close/reopen, deny repeated first stage, detached snapshots.
- G2026: wrong source World denied before disk write; second journal owner denied; caller UUID reused for distinct real death rejected.
- G2027: orphan temp refuses reopening; manually tampered record is rejected, with no duplicate NPC settlement.
- G2028: 43 genuinely finalized ground-settled NPCs, 258 attempted stage reservations, 256 accepted and last two denied; reopen retains earliest duplicate prohibition.
- Four JavaExec gates, focused manifest 822 -> **826 unique**, critical-retention entry. Java11 bytecode/full Gradle/exact-head 826/826 hosted CI required.

## Next architecture / remaining blockers
Never add a real reward callback to this ledger. First establish an authoritative, durable *event identity and recipient authority* plus atomic grant mutation with per-consumer ACK, an ownership epoch, durable retry rules and failure injection across independent JVMs. Unknown original server policy remains UNKNOWN_SERVER_AUTHORITY. Continue coordination in [issue #2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) and [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558). Draft only; main unchanged.
