# G21.36 — file-backed World saves veto durable Mailbox review fences

**Certified parent:** G21.35 exact head `a7cbe6f79178e6ae2c7a90c64c3dbe1739e87cbc`, hosted [workflow 37829086185](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37829086185) SUCCESS, 334 focused Java11. Frozen R25 promotion PR #1847 untouched.

## Production write gap

G21.31 and G21.32 already block normal account **loads** while a PREPARED journal is inconsistent or a persistent **REVIEW_REQUIRED_NO_GRANT** sidecar is present. G21.35 also adds a late login admission recheck. But an already-online player can still have an ordinary, checkpoint/autosave or final disconnect snapshot captured and submitted to `WorldPlayerPersistence.write()`, which previously sent all accepted tickets to `repository.save(snapshot)` without consulting the durable marker. An opt-in G21.34 marker can be armed after the snapshot was captured and before its worker writes. That could overwrite a known PREPARED disk preimage and silently disturb subsequent operator review.

## Narrow guard

For a *concrete `FilePlayerRepository`* owned by `WorldPlayerPersistence`, the existing single-worker `write` now uses `FilePlayerRepository.saveForWorld(snapshot)`, a **package-private, explicitly production-guarded** file save. It leaves existing ordinary `FilePlayerRepository.save` intact for legacy manual setup and read-only recovery regression fixtures. Direct `save` is therefore **not admitted World gameplay persistence** and must not be treated as safe across a review marker.

The guarded file save:
1. Validates the same G21.31 `MailboxPreparedRestartAdmission.inspect(snapshot)` policy against a snapshot with a G21.22 journal. Any claimed/credited/inconsistent journal postimage is rejected without creating a temp or overwriting the original account file.
2. Checks G21.32 marker presence before creating or serializing a file. A present marker or metadata-read error throws, keeping the normal World SaveTicket exceptional (no success).
3. Serializes the account snapshot into the existing temp file, then checks the durable marker **again immediately before `Files.move(...)`**. A marker appearing during serialization causes `IOException G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO action=REJECT_WORLD_SAVE`, deletes the temp in the existing failure path, and leaves the old saved account bytes untouched.
4. Otherwise preserves preexisting FilePlayerRepository serialization, rename/fallback behavior and normal account write semantics. Other `PlayerRepository` implementations are **outside** this specific sidecar safety claim.

This check applies to all ordinary World `SaveTask` paths sharing `WorldPlayerPersistence.write`, including checkpoint drains and reserved final logout saves. It does NOT automatically revoke an already-online account, clear a marker, or settle Mailbox widget32181.

## Deterministic acceptance

`G2136MailboxFencedWorldSaveIntegrationTest` uses a real file-backed World worker and a **package-private test-only hook** in the FilePlayerRepository at the exact last pre-replace boundary. The worker has already serialized a file but is blocked; an independent thread writes the G21.34 no-clobber negative review marker; the worker resumes and must fail **before replacement**. The test checks original full PREPARED bytes preserved, no temp artifact, failed SaveTicket and marker retained. It also verifies a captured deferred save submitted after the marker, a later ordinary capture-and-save, a World tick checkpoint and a reserved final disconnect save all fail with no account replacement; an unfenced other account succeeds; invalid G21.22 postimage is rejected even without a marker; direct repository setup still permits forensic hypothetical snapshots while the marker continues to deny login; no live inventory or Mailbox ACK; and restarted World login remains fenced.

Focused Java11 manifest **334→335**, Gradle task `g2136MailboxFencedWorldSaveRegression`. Exact-head hosted full CI SUCCESS is required before certification.

## Residual boundaries / what is NOT proven

Two marker checks are not a cross-process atomic transaction with the independent writer. A G21.34 fence could still become visible **after the final check and during account-file replacement**, or while a save's replacement was already in progress. The test proves only the bounded within-serialization race it injects. The temporary filename remains the existing FilePlayerRepository convention and does not establish multi-JVM safe concurrent account writers. Malicious external modifications are not protected. A robust next step needs one coordinated admission+write/marker owner and active-session revocation/checkpoint policy, then a durable exact-once positive transaction with recovery proof. No hardware power-loss claim, native grant, automatic replay, automatic rollback, or marker-release API is introduced. Bank widget32178 excluded.
