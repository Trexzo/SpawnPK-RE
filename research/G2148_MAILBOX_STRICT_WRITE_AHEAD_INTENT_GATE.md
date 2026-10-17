# G21.48 — file-backed strict PREPARED write-ahead negative intent

**Certified parent:** G21.47 corrected exact `81106aecedc88cadb9ca1db32964699c5f3e6b14`, [hosted workflow #37906922073](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37906922073) SUCCESS, **346 focused Java11 tests**. Frozen R25 promotion PR #1847 remains untouched.

## G21.47's unresolved crash window

G21.47 produces an independent permanent `.g2147-strict-uncertain` negative marker when a file-backed G21.46 strict PREPARED checkpoint finishes in an **unconfirmed** state after account-file replacement, before relinquishing its G21.38–40 per-account publication FileLock. But a sudden JVM crash or power interruption **after account ATOMIC_MOVE and before detecting the failure/publishing G21.47** can leave a changed account file with no negative marker. An asynchronous exception returned to the caller is not durable and cannot prevent that account from being admitted after restart.

## G21.48 bounded protocol: prewrite negative *in-progress* marker

New `MailboxStrictWriteIntentFence` is an independent, per-account `.g2148-strict-write-intent` sidecar for **concrete file-backed World strict PREPARED checkpoint tasks only**. Its content contains a format tag, account identity, G21.30 canonical complete PREPARED digest and `IN_PROGRESS_NO_GRANT` status. It is **not** a G21.32/G21.47 permanent manual-review sidecar or a positive COMMIT record.

The real `WorldPlayerPersistence.PreparedStrictBarrierTask` opts into the new five-argument `saveStrictForWorld(...,beforeOwnerCheck,afterOwnerCheck,writeAheadIntent=true)`. The historical direct writer and earlier compatibility overloads keep their prior contracts. G21.43 admission-time and G21.44 worker-start complete owner-state checks remain; G21.45 pre-replacement and G21.46 post-replacement checks remain.

Inside the SAME account publication FileLock, after the G21.42 negative review-marker check and G21.45 whole-owner SHA comparison, the strict writer:
1. Publishes and file-forces a uniquely staged negative **write-ahead intent** via a no-clobber hard link; forces the parent directory **before** modifying the account file. A pre-existing, malformed or symlink intent refuses this write. Unsupported hard links/force fail without a fallback.
2. Executes the original strict account temp `Files.move(..., ATOMIC_MOVE)` and parent directory force.
3. Runs G21.46's exact current WorldPlayer/generation post-move verification.
4. Only for a confirmed strict checkpoint, verifies the transient intent's full canonical identity, deletes **that new in-progress marker only** and forces its directory before issuing the historical strict receipt. Permanent G21.32 and G21.47 markers are NEVER cleared here.
5. On uncertain after-move failures, G21.47 separately publishes its permanent negative sidecar before releasing the same account file lock; the transient G21.48 intent remains. A failure during transient-marker cleanup produces an `UnconfirmedCommitException` and invokes the G21.47 permanent negative path. No automatic retry, rollback, replay or positive claim occurs.

Any PRESENT G21.48 sidecar, even unreadable/corrupted, vetoes new World session load, guarded `FilePlayerRepository.saveForWorld` and another guarded strict checkpoint via the existing unified file-backed review-presence checks. It is a **negative in-progress/pending-recovery marker**; not proof that an inventory+Mailbox transaction committed.

**Live session interaction:** G21.37's socket-driven review check always revokes for permanent G21.32/G21.47 markers. A G21.48 intent belonging to a demonstrably active, unfinished strict checkpoint on this World persistence worker does NOT by itself disconnect that owner during normal file I/O. When the task ends and its G21.48 intent remains, or if this World has no matching in-flight strict task, the intent triggers ordinary session termination. New session admission is always denied while the sidecar exists. This avoids normal checkpoint processing spuriously causing a login kick without allowing stranded intents to become trusted.

## Regression and current status

`G2148MailboxStrictWriteIntentIntegrationTest` injects `AFTER_WRITE_AHEAD_INTENT` failure **after the intent's hard-link+directory force but before account ATOMIC_MOVE**: old account bytes remain untouched, the intent remains, an already-connected owner is exempt while its strict task is demonstrably active, task failure later makes the stranded marker an admission/revoke veto, a fresh World refuses the account, and an existing/malformed marker does not bypass the veto. A separate unchanged PREPARED owner completes the strict checkpoint, returns its usual immutable receipt, clears only its transient intent and remains loadable after restart. Unrelated accounts remain accessible, with no inventory grant or Mailbox CLAIMED transition and no temp/lease leaks.

The older `G2147MailboxStrictUncertainFenceIntegrationTest` is strengthened to assert **both G21.47 permanent quarantine and a stranded G21.48 write-ahead intent** after an actual post-move unconfirmed owner divergence.

Focused Java11 suite **346→347**, Gradle task `g2148MailboxStrictWriteAheadIntentRegression`. Full exact-head hosted CI SUCCESS required; Draft PR only.

## Remaining limits

- Tested filesystem API semantics and ordered file/directory forces **do not prove all hardware/controller crash durability**. This protocol is not an atomic cross-file transaction. We do not claim a mathematically guaranteed recovery decision after arbitrary power failure, especially unsupported filesystems, silent disk corruption or uncooperative external writers.
- The transient marker cleanup after confirmed account force is necessary for ordinary gameplay. It is deliberately not an automatic release of **permanent G21.32/G21.47 manual-review** markers.
- There is still an optimistic World-owner lifetime race around physical I/O and the successful final check. A strict checkpoint receipt is **not** a reward grant receipt or a durable exactly-once inventory+Mailbox COMMIT.
- No native C2S185 widget32181 reward granting, replay, automatic rollback or manual-review marker release is enabled. Bank widget32178 excluded. Frozen R25 #1847 untouched.
