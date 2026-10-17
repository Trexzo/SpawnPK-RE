# G21.46 — strict World PREPARED post-publication receipt refusal on late owner divergence

**Certified parent:** G21.45 corrected SHA `bd9929003dc53b3e46b55594e6adad4758718665`, [hosted workflow #37844355879](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37844355879) SUCCESS, **344 focused Java11 tests**. Frozen R25 promotion PR #1847 remains untouched.

## Actual residual gap after G21.45

G21.43 validates the complete PREPARED snapshot against the currently owned WorldPlayer when admitted into persistence. G21.44 repeats that whole-snapshot validation at the FIFO worker start. G21.45 repeats it immediately before strict account-file replacement under the same G21.38–G21.40 per-account publication lock used by independent negative review markers. However, this last check releases `owner.mutationLock()` before the physical `Files.move(..., ATOMIC_MOVE)` and parent directory `force(true)`, deliberately avoiding holding a World mutation lock across unpredictable filesystem I/O. A real World command may alter movement, inventory, Mailbox state, or unregister the owner **between G21.45's last check and the strict writer's successful return**. Previously the writer could then return a normal G21.23 `Receipt`, despite the currently owned player no longer matching the persisted PREPARED file.

## G21.46 bounded implementation

- Add package-private `StrictDurablePlayerSnapshotWriter.AfterWorldPublication`, a synchronous read-only callback that is invoked **inside the existing account-local JVM/FileLock** only *after* atomic account-file replacement and parent-directory force have completed but **before** the writer constructs/returns the standard strict `Receipt`.
- Existing historical `saveStrict(snapshot)`, `saveStrictForWorld(snapshot,file)` and three-argument `saveStrictForWorld(snapshot,file,before)` remain source-compatible. They delegate to the new four-argument path with no-op callbacks where earlier explicit semantics were intended. No new filesystem thread, owner-held serialization, World tick I/O or changes to review-marker publication.
- For the **actual concrete FilePlayerRepository-backed World strict PREPARED task only**, the persistence worker passes the existing short G21.45 whole-snapshot owner-and-generation verification callback both before *and after* publication. A mismatch at the post-publication callback is wrapped in `StrictDurablePlayerSnapshotWriter.UnconfirmedCommitException` with the explicit `G21.46 STRICT_PREPARED_POSTPUBLICATION_UNCONFIRMED ... action=MANUAL_REVIEW_NO_GRANT` message. It settles the queued future exceptionally, never returning a normal strict receipt or inferring rollback.
- Crucially, the on-disk account may **already have been replaced**, and directory force may already have succeeded; G21.46 does not revert account bytes, and it does not claim a complete successful live gameplay-state checkpoint. Whether disk is durable on all storage devices and whether restart should accept any positive claim remain outside its scope.
- An unchanged current player still returns the G21.23 strict receipt bound to G21.30 canonical whole-snapshot SHA. Earlier G21.42 review-marker veto and G21.45 prepublication stale-owner veto remain intact.

## Deterministic integrated acceptance

`G2146MailboxStrictPostpublicationUnconfirmedIntegrationTest` exercises the actual WorldPlayerPersistence strict worker on a concrete file-backed World:

1. At the strict writer's `AFTER_DIRECTORY_FORCE` fault seam, *after* the file's atomic replacement and parent directory force but before G21.46's post-publication check, pause the writer. A real WorldPlayer mutation changes movement; after resume the strict future must fail with `UnconfirmedCommitException` instead of a false `Receipt`. Test demonstrates original file bytes **were not rolled back** and the persisted file may still hold the older PREPARED snapshot.
2. Repeat by unregistering the WorldPlayer during that same post-force window; the generation retirement must produce the same unconfirmed outcome rather than a success receipt.
3. A stable, unchanged WorldPlayer continues to return a matching strict receipt and persisted whole snapshot. An independent account remains byte-identical.
4. Check that no items were credited, Mailbox claim statuses remain UNCLAIMED, no negative review marker was invented or cleared, no temp artifacts remain, and account publication JVM leases are all retired.

Focused Java11 manifest **344→345**, task `g2146MailboxStrictPostpublicationUnconfirmedRegression`. Full exact-head hosted CI SUCCESS is required before certification; keep the PR Draft.

## Explicitly unresolved

**This milestone does NOT make the file replacement and live World state atomic.** A late owner mutation may happen after the second check too, or during a complete operation. In an unconfirmed case an obsolete PREPARED account file may already exist on disk. Persistently quarantining that uncertainty (even if the session later disconnects or the JVM crashes) requires a distinct durable negative fence/restart coordination protocol, not merely throwing an exception. No positive inventory+Mailbox COMMIT record, crash-durable exactly-once reward settlement, safe replay, automatic rollback or negative marker release has been established.

Native C2S185 widget32181 stays **NO_GRANT**; bank widget32178 remains excluded. Frozen R25 PR #1847 untouched.
