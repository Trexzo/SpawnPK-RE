# Chat 2 G2037–2040: parent-path admission and caller-only UUID authority boundary

Parent exact source: [PR #2931](https://github.com/Trexzo/SpawnPK-RE/pull/2931) `4555b65a2fc7286610f6076d4972c9a029b8fe72` (845 focused candidate, exact-head CI to be checked independently). Main-aligned source ancestor [#2930](https://github.com/Trexzo/SpawnPK-RE/pull/2930) `cfd3e725711afc53b7940e6212eb7d3ae7499b84` is separately hosted 841/841 Java 11/full Gradle certified. This new source is experimental non-granting Chat2 only; all Chat1 account production remains unchanged.

## Newly verified logic gap (from source audit)
`open()` checked only the final journal directory and `scanDirectory()` checked its inode. If a **parent directory** was a symlink pointing at the original journal directory, the journal could follow it even while its root inode and original lock record remained the same. Additionally `reserve()` formerly allowed a newly added stage to an event UUID loaded after reopening, although the initial World/finalization object and consumer authority were not reconstructed. A caller could present the same exact real finalization object with many different UUIDs to mint many diagnostic events.

## Production corrections (NO LIVE WRITES)
- Check each path ancestor for symlinks before any mkdir, after mkdir, and on every disk scan. This is a conservative observed-path boundary, NOT an adversarial atomic directory capability.
- Bind in-memory live `FinalizeResult` **exact object identity** to a single caller-supplied UUID while current journal instance owns that event. Reject another UUID for the same exact finalization: `DISK_ATTEMPT_DUPLICATE_TERMINAL_ALIAS`.
- Reject conservative matching death fingerprints even after reopen under a different supplied UUID: `DISK_ATTEMPT_DUPLICATE_DEATH_FINGERPRINT`. These strings are ONLY heuristic denial evidence, not original SpawnPK event authority.
- Loaded event records after close/open have no live source object authority, so cannot acquire **any additional stage**: `DISK_ATTEMPT_REOPENED_EVENT_APPEND_FORBIDDEN`. Existing duplicate stage remains `DISK_ATTEMPT_DUPLICATE_CONSUMER`. New genuinely finalized different NPC deaths can still reserve fresh IDs.
- Within one open journal, adding a different stage to the *same caller UUID* requires the exact same live finalization object `DISK_ATTEMPT_TERMINAL_IDENTITY_CHANGED` otherwise. Nothing delivers or replays rewards.

## New regression gates
- **G2037** preexisting symlinked ancestor refuses `open()` before creating the proposed journal directory.
- **G2038** after real NPC settlement and initial reserve, rename a live ancestor and replace its pathname with a symlink pointing at the **same exact journal directory inode**; audited read and append both refuse while previous record survives.
- **G2039** real finalized ground-settled NPC: close/reopen, old UUID cannot append a new stage; different UUID cannot alias same fingerprint; exact previous duplicate stage still denied. A genuinely second finalized NPC with new UUID is permitted; no replay.
- **G2040** 12 concurrent callers present **12 distinct UUIDs** for ONE real finalized NPC/consumer; exactly one reserve succeeds, 11 terminal aliases denied, one ground receipt; the winner may append a different stage to that same original event while its live object is pinned.

Four focused test classes, four JavaExec tasks, one critical retention family. Manifest **845→849 unique**.

## Do NOT infer server authority
The client-generated/caller-supplied UUID and hash derived partly from `System.identityHashCode` are not a persistent authenticated event ID or cross-JVM grant identity. Closing/opening the journal makes previous events read-only even in the same JVM. One process's identity map cannot constrain a hostile peer process and symlink scans have TOCTOU windows. No reward grant, credit ACK, replay, directory fsync, hostile cross-JVM lease or original SpawnPK attribution. #2587 remains open; keep draft/unmerged.

## Exact hosted certification
Require **849/849**, G2037–G2040 PASS, retained G2033–36 + Chat1 G21367–70, Java 11 bytecode major55 and Gradle BUILD SUCCESSFUL on THIS commit. Exact-head CI required; source commit alone is not certification.
