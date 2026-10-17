# G21.248 — optional forced and verified RAW account publication

Based on [certified G21.247/G19.0.4 #2750](https://github.com/Trexzo/SpawnPK-RE/pull/2750) exact `0cd01a1fb54e99a216be4352f6214243b886cc26`, hosted Java11 full Gradle GREEN **595/595**.

## Real production change
All three `AccountStore.save`, `LocalAccountProfiles.save`, `FilePlayerRepository.save` opt-in RAW code paths can enable `spk.local.experimentalRawForceVerifiedPostimage` **only** when existing G21.246 `experimentalRawUniqueTempNoFollow` is also enabled with JVM RAW gate + G21.185 cross-JVM OS lock + G21.221 lock-object continuity. Normal legacy behavior remains default and all old direct save modes are unchanged.

With this additional flag, after bounded G21.247 SHA256 seal, file identity is checked, temp FileChannel is forced, the seal is re-verified, account NOFOLLOW ancestry + hardlink gate and held OS lock identity are rechecked. Publication requires sibling `ATOMIC_MOVE` with **NO non-atomic fallback**; afterward force the account parent directory, verify the account filename resolves to the **same original temp inode** and **same complete bounded SHA-256 contents**, recheck lock-object continuity. An exception after rename is explicitly classified `RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW`: account bytes MAY ALREADY be replaced, never infer rollback or issue a positive save receipt. The mode DOES NOT write an ACTIVE quarantine sidecar, clear review markers, or provide a durable player ownership generation.

Test uses all three genuine RAW writers in the forced path; separate deterministic after-rename failure injection proves a failure is explicitly UNCONFIRMED **while new bytes are on disk**, and validates temp cleanup, no reservation leak, and fail-closed partial opt-in. Manifest **595→596** with exact one additive class and Gradle task.

## Nonclaims
A successful fsync/rename/directory-force sequence is a filesystem completion property under cooperating storage, not proof against hardware/controller lies or malicious post-check rename. It is **not a captured-owner final-session ACK**, NOFINAL admission, crash-safe epoch/automatic recovery, external uncooperative writer exclusion, original RSPS server behavior or mailbox entitlement. Keep #2540/#2559 and ACTIVE reviews open; remain DRAFT/unmerged until own exact-head Java11 major55 596/596 and full Gradle CI.
