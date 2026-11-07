# R16 — LocalLab atomic account snapshot publication

Status: experimental, stacked draft on R15. Not original SpawnPK gameplay or a
power-loss durability certification.

## Change

`AtomicAccountPublisher` is the shared publication mechanism for
`FilePlayerRepository`, `AccountStore`, the secondary `LocalAccountProfiles`
writer, and `PetAccessoryPersistence`. The schema and `saved.at` key remain
unchanged.

1. Encode Java Properties completely in memory without changing the live file.
2. Create an adjacent, uniquely named staging file (never truncate inherited
   `*.tmp` files); force staged file bytes and metadata with `FileChannel.force(true)`.
3. Reject symlink/directory target paths and symlink parent components that can
   be observed through ordinary NIO attribute checks.
4. Replace the live file using `ATOMIC_MOVE`. If the provider does not support
   atomic move, refuse to publish: **no non-atomic fallback**.
5. Clean up the invocation's own stage on normal errors or success.

An abrupt process death before rename leaves the original file in place,
although its orphaned stage may remain. A new process can write using a unique
stage without requiring automatic deletion of old files.

## Evidence requirements

- Exact-head Java 21 Gradle build and Java 11 bytecode checks.
- Linux Java17 invocation of `spk.local.AtomicAccountPublisherTest`.
- Real Windows Java17 invocation after a Windows JDK21 build.
- Unsupported-atomic-move injection must leave prior account bytes untouched.
- Killed *test-only* child JVM between staged-file force and replace must
  leave prior account bytes intact; a subsequent publication must work even
  with abandoned staging evidence.
- Legacy `*.tmp` inputs remain unmodified; non-file targets fail closed.
- Existing R4–R15 synthetic login/PvM/loot/XP/bank/reconnect tests remain green.

## Explicit limitations

- This improves **atomic visibility/torn-write avoidance**, not full
  power-failure persistence. Directory-entry fsync / Windows metadata barriers,
  hardware write caches and unexpected storage faults require separate proof.
- Path checks are best-effort Java NIO checks, not race-free filesystem
  sandboxing against a concurrently malicious local process.
- It does not make legacy writers cooperate with the R15 process lease or
  serialize overlapping read-modify-write operations within one JVM.
- There is no automatic deletion/recovery of unknown abandoned stage files
  because they may be forensic evidence. They cannot block fresh unique stages.
- A failure after a storage provider commits but before its call returns cannot
  be interpreted as proof that the old file remains; callers should not blindly
  replay a gameplay transaction.
- The original graphical v308 client and authentic SpawnPK game content remain
  unverified; original v312 clients and their profiles are not used.
