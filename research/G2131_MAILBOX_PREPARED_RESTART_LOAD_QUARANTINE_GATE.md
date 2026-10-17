# G21.31 — deny uncertain Mailbox PREPARED account files on real login load

**Certified parent:** G21.30 commit `fe55517e561c73d5283805b3b31c3f37f3df01f2`; hosted [37801242844](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37801242844) SUCCESS / 329 focused Java11.

## Why G21.31 is necessary

G21.22 persists `PREPARED_NO_GRANT` intent together with the original UNCLAIMED Mailbox message and original 28 inventory slots. G21.25 constructs a **hypothetical** complete account file with credited items and Mailbox CLAIMED but intentionally retains the PREPARED journal. G21.23 strict file writes may fail after atomic replace, leaving hypothetical bytes visible with no confirmed directory sync receipt. G21.29/30 detect ambiguous evidence in standalone classification, but **before G21.31, the actual persistence `LoadTask` handed every repository snapshot straight back to the session**, allowing the hypothetical account record to hydrate on a fresh login despite no certified settlement protocol.

## Actual load seam

New `MailboxPreparedRestartAdmission.inspect(snapshot)` runs on a **detached** WorldPlayer, never on the live player. It examines the G21.22 namespace `extension.mailbox-claim-intent`. Accounts containing no such namespace retain the existing loader behavior (no additional canonicalization or journal semantics). Marker detection requires the exact `extension.mailbox-claim-intent.` namespace delimiter; similarly named but distinct extensions must never be treated as claim journals.

For marked snapshots, require a complete canonical v2 account snapshot; decode its full journal; verify canonical account/message ID, selected Mailbox UNCLAIMED state, attachment fingerprint and **all 28 inventory slots exactly equal the journal BEFORE image**. In particular, a CLAIMED Mailbox or an inventory changed toward a hypothetical reward must be refused before normal hydration. Missing/recycled selected envelope, corrupted/foreign journal and malformed account bytes must also be refused. The classifier does not infer an authorized transaction from `idempotencyKey`, file contents, a G21.30 receipt, or a message status.

`WorldPlayerPersistence.LoadTask.run` now calls that classifier immediately after its existing same-worker `repository.load` and **before future completion**. Invalid marked accounts produce an explicit `IOException` with `G21.31 MAILBOX_PREPARED_LOAD_QUARANTINE` and the specific reason. This is a denial of login account hydration, not a file quarantine move, a rollback, or an admin repair operation. No disk write, account mutation, reward action, new I/O worker, client packet or catch-and-continue fallback was added.

If the snapshot is still exactly PREPARED+UNCLAIMED, it can load as an inert record, **without any grant, replay or implicit unprepare**. Old accounts without a journal remain untouched. If an operator repairs/reconciles the on-disk record to a valid PREPARED state, the same existing login path can read it again.

## G21.26 read-only recovery observer compatibility (CI-failing edge resolved)

The first hosted build on original head `ec4c937f...` **failed**, correctly refusing a hypothetical CLAIMED disk postimage that old G21.26 regression tried to read via `WorldPlayerPersistence.load()`. This was not evidence that an unsafe file should be admitted to login.

The final implementation splits **two explicit same-worker FIFO load purposes**, not two I/O workers or bypass of the persistence queue:
- `WorldPlayerPersistence.load(username)`: unchanged public session use; G21.31 classifier denies potentially credited/CLAIMED G21.22 records before returning them to `LocalAccountLifecycle.loadSnapshot` and `LocalSessionPlayerInitializer.initialize` (failed account load rejects session).
- `WorldPlayerPersistence.observeUntrustedMailboxAccount(username)`: package-local, read-only forensic disk observation solely for G21.26 `MailboxDiskPostimageObserver.observe`, whose owner/generation/immutable-proposal checks bracket the read and whose output is a non-granting typed classification. **Raw snapshot bytes are never returned to session hydration by this call.** The queue, load rejection/exception propagation and thread affinity remain the same.

The G21.31 test also checks the same exact hypothetical file is **rejected on real login path** and **observable only through the bounded forensic FIFO path**. It separately calls `LocalAccountLifecycle.load(...)` and asserts the normal session initializer returns `LoadStatus.FAILED` before ever applying the claimed record to a fresh player. Original G21.26 recovery coverage remains meaningful. No G21.26 assertion is weakened or removed.

## Regression and remaining boundary

`G2131MailboxPreparedRestartAdmissionIntegrationTest` drives `world.persistence().load` (the actual asynchronous loader), verifying: ordinary account unchanged, PREPARED restoration without reward, hypothetical CLAIMED file rejected and preserved, inventory-only mutation rejected, missing mail rejected, corrupted or foreign journal rejected, unrelated account unaffected, repaired PREPARED accepted again, repeated load idempotent and original live owner untouched.

Focused manifest **329→330 Java11**; task `g2131MailboxPreparedRestartAdmissionRegression`. Hosted exact-head PASS is mandatory before certification.

**Not solved:** restart-reconstructible reservation/transaction authority; a truly crash-durable idempotent inventory+Mailbox commit; once-only live state reconciliation; hardening against deliberately forged account snapshots with the entire journal removed; original SpawnPK reward economics; pinned v308 manual runtime acceptance. Native widget32181 stays **NO_GRANT** and bank widget32178 remains outside scope. Frozen R25 promotion #1847 untouched.
