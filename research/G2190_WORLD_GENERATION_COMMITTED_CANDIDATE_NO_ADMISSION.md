# G21.90 — World generation checkpoint for committed recovery (NO ADMISSION)

**Parent:** G21.89 certified commit `c2356ddad090b721c13d495c7e8c34d5caf901fe` / [Actions #38053415268](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38053415268), 389 Java 11 focused tests. Tracks [#2359](https://github.com/Trexzo/SpawnPK-RE/issues/2359).

This adds `MailboxCommittedWorldGenerationCandidate.inspect`, a real **World lifecycle/generation check** for the exact, previously verified, inert snapshot returned by `MailboxCommittedDetachedRestartRecovery.recoverDetached`. It runs inside the existing `World.withOpenPlayerMutationOwnershipIfCurrent` critical section. It rejects retired owner generations, owners attached to another World, foreign accounts, already-hydrated Mailboxes, nonempty inventory, and any changed gameplay state compared with a brand-new default owner.

A successful result is intentionally named `CANDIDATE_ONLY_NO_ADMISSION`; it is **not** a reservation, transaction authority, permission to apply recovered state, grant rewards, load a session, save a committed account, release a claim or send client ACK. The G21.89 disk observation is **not** revalidated under the World mutation lock. No files are read while holding that lock, and no live owner state is modified. Any later live adoption must recheck disk identity, define race-free handoff and save semantics, and preserve generation ownership.

The G21.90 real-filesystem integration creates an authentic PREPARED intent + confirmed strict terminal receipt + G21.86 disk COMMIT, independently restores the exact terminal snapshot, verifies current/old/new World generations, cross-World ownership, wrong-account and dirty-receiver rejection, and proves unchanged original live inventory/claim state and disk bytes. No native widget32181 reward activation. G21.87–88 vetoes and frozen R25 #1847 remain untouched.

Standalone Gradle: `g2190MailboxWorldGenerationCandidateRegression`. Focused manifest 389 → 390 (Java 11). PR remains Draft/unmerged until hosted exact-head validation.
