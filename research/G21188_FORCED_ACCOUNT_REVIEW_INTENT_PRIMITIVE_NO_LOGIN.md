# Chat 1 G21.188 — standalone forced active-review intent primitive

## Certified origin, explicit absence of integration

Exact parent core G21.187 [Draft #2579](https://github.com/Trexzo/SpawnPK-RE/pull/2579) SHA `e76e670a2b1850335e886ee0468136b046ad69f4`, [Actions #38556891212](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38556891212) SUCCESS Java11 major55 **487/487** focused, full Gradle. Its real-TCP, three-JVM killed-holder experiment proved that ordinary OS session file locking does **NOT** leave durable negative intent after process death; a fresh JVM can hydrate a stale BOOTSTRAP account.

G21.188 is an intentionally **standalone, nonadmitting prototype**, not an account-login fix. It has **NO production wiring** into `LocalSession`, `LocalSessionPlayerInitializer`, `WorldPlayerPersistence`, `FilePlayerRepository.saveForWorld`, account read, or grant/settlement logic. Default LocalLab behavior is completely unchanged.

## Primitive API and fail-closed behavior

`LocalDurableAccountReviewIntentPrimitive` has a private fixed sibling `account.g21188-active-review` marker. It validates existing NOFOLLOW ancestry and a regular non-symlink parent directory before creating a marker with exclusive `CREATE_NEW` + NOFOLLOW. It writes a version marker, an account-path SHA-256 fingerprint, a fresh random nonce and an ACTIVE/NEEDS_MANUAL_REVIEW state. It calls `FileChannel.force(true)` on the marker, then opens and forces the parent directory, and checks post-write NOFOLLOW regular file identity. **Any error leaves an uncertain existing marker; there is no automatic unlink/rollback or release.** The primitive deliberately has NO retire/delete API. In the future, a fully specified file+directory forced final-save receipt and exact owner epoch would be required before even designing a safe intent-retirement operation.

`requireNoReviewMarker` checks marker *existence*, not validity. A preexisting or malformed leaf is a negative, never interpreted as clean. Concurrent processes racing on the same leaf cannot both CREATE_NEW. This is a narrow file-system primitive; the pathname can still be adversarially replaced/unlinked, and it cannot fence uncooperative RAW writers.

## G21.188 process-kill test

`G21188CoreDurableReviewIntentProcessKillTest` uses disposable temp files, not a real named user account. A child JVM creates and forces an intent marker plus parent directory, signals ready and remains alive. Parent force-kills that exact test child (nonzero exit), confirms byte-identical marker, and starts a second independent JVM. The new process sees the marker and refuses both a clean-admission check and a second marker allocation, never creates a real account, and leaves the marker intact. Fixture cleanup may delete its own isolated temporary files **only after all spawned test processes have exited**. No production or recovery deletion is authorized.

Expected marker `G21188_DURABLE_PRIMITIVE_PASS markerSurvivesProcessKill=true failClosedExistingMarker=true wiredToLogin=false durableSaveReceipt=false automaticIntentRetirement=false powerLossCutpointsProven=false ...`. PASS is evidence of marker persistence through *one user-space process kill*, **not** system power-loss/disk-controller guarantee, account crash safety, safe session admission, or automatic recovery. On filesystems without directory fsync the primitive must fail closed.

## Required exact-head acceptance and boundaries

Add one helper, one test, dedicated Gradle `g21188CoreDurableReviewIntentProcessKillRegression`; focused **487→488**. Require hosted exact-head Java11 bytecode major55, dedicated test marker, **488/488** unique focused and full Gradle SUCCESS. No main merge, no automatic flag enable or promotion. The G21.187 crash negative remains true for ordinary LocalLab sessions.

A future actual protocol needs a durable ACTIVE epoch *before* login success, a separately auditable account temp-file force and directory force before final success, explicit forced marker retirement, crash cutpoints at every step, comparison of real account digests/generations, defense against hostile path replacement and nonparticipating writers, and manual review of unknown state. **Source audit note:** `FilePlayerRepository.saveForWorld` currently uses `Properties.store` to temp followed by `Files.move(...ATOMIC_MOVE)` / fallback, without an explicit file force + parent directory force in `replaceSnapshotTemp`. Hence do NOT claim the existing save has durable receipt semantics based on atomic rename alone.

Open [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) for actual generic account durability/ownership. Frozen R25 #1847/G21.87/G21.88. No automatic account repair, settlement/grants/replay/cleanup/rollover/pruning/unlink/session release/ACK, original client GUI or server reward behavior. Chat 2 gameplay and Chat 3 client untouched.
