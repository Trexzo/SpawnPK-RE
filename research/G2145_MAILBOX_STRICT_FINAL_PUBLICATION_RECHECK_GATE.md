# G21.45 — strict World final publication whole-owner recheck after serialization

**Certified parent:** G21.44 exact commit `61e5e551808a33b58ff161c4ae2f4ccfb7470566`, hosted [workflow #37842492124](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37842492124) SUCCESS; **343** focused Java11 tests. Frozen R25 PR #1847 remains untouched.

## Measured remaining lifetime gap

G21.43 checks the complete PREPARED immutable snapshot against its currently registered WorldPlayer when admitting the strict barrier to the single persistence FIFO. G21.44 repeats that check on the actual persistence worker immediately before invoking G21.42's guarded strict writer. But the writer then creates, serializes and forces a full temporary account file before the final no-fallback `ATOMIC_MOVE` and directory `force(true)`. The World thread is deliberately not blocked by this I/O. A live player can move, change inventory, mailbox or accessory state, or unregister **after** the G21.44 check but **during strict temp serialization**. Without a last-minute read-only check, the strict writer could replace the account file with obsolete PREPARED state despite G21.42's correct review-marker publication lock.

## G21.45 bounded production fix

`StrictDurablePlayerSnapshotWriter` now has a package-private `BeforeWorldPublication` callback and a three-argument concrete `saveStrictForWorld(snapshot, expectedRepositoryPath, publicationCheck)` overload. The historical direct `saveStrict(snapshot)` stays unchanged; the two-argument file-backed `saveStrictForWorld` remains source-compatible and delegates to a no-op check for its earlier callers. The normal G21.23 file force, unique temp, no-fallback ATOMIC_MOVE, directory force, strict receipt and post-replacement `UnconfirmedCommitException` semantics remain unchanged.

The **real** `WorldPlayerPersistence.PreparedStrictBarrierTask.run` now passes a callback for concrete `FilePlayerRepository`:
1. G21.44 still verifies the current full WorldPlayer snapshot at worker start, before any file I/O.
2. G21.42's writer serializes and forces the account temp without holding `owner.mutationLock`; no World tick filesystem operations, private schedulers or new workers were introduced.
3. The writer acquires the *same* per-account G21.38–G21.40 JVM/FileLock used by cooperating review-marker publishers, then checks final G21.32 marker absence.
4. **Immediately before the atomic file replacement, inside the publication lock**, the supplied read-only callback acquires the owner mutation lock just long enough to verify that the account/generation are still current and recompute G21.30 SHA of the *entire currently live player snapshot* using the original snapshot's accessory argument. Any G21.44 retired or divergent condition is wrapped in `IOException G21.45 STRICT_PREPARED_FINAL_RECHECK_VETO` and propagates through the existing future, without replacing account bytes, returning a strict receipt or retaining its temp.
5. For an unchanged player, execution continues into the original strict ATOMIC_MOVE and directory force. Markers armed first still block publication using G21.42's existing veto, and markers armed after a completed strict save remain negative review evidence only.

This callback does not alter live state, award items, change a Mailbox claim, remove review markers or create a positive transaction outcome.

## Deterministic integration

`G2145MailboxStrictFinalPublicationRecheckIntegrationTest` exercises a real concrete file-backed World and strict-barrier persistence worker:

- A barrier with an exact matching PREPARED snapshot reaches `StrictDurablePlayerSnapshotWriter.Phase.BEFORE_ATOMIC_REPLACE`, **after temp force and worker-start validation**, then pauses. A World action changes the player’s movement on the real pulse while the worker is held. Once resumed, the G21.45 callback must refuse the now-stale snapshot before account replacement, with original full account bytes unchanged, no strict receipt and no directory force.
- A second strict task reaches that same post-serialization boundary; the player unregisters, making the owner generation stale. Resuming must refuse before replacement, preserve disk account bytes and perform no directory force.
- An unchanged PREPARED owner still produces the historical exact G21.30 strict receipt.
- A competing **G21.41 verified negative review marker** can appear while the strict writer is paused; the G21.42 final marker check retains precedence and rejects the strict write. After restart, that account's normal session load remains fenced.
- Unrelated account stays intact; no strict temp artifacts or lingering JVM leases, no live Mailbox claim, inventory grant, replay or marker release.

The focused Java11 manifest advances **343→344** and adds `g2145MailboxStrictFinalPublicationRecheckRegression`. Exact-head hosted workflow SUCCESS with the full focused build remains mandatory for certification.

## Crucial residual boundary

**This is a last-chance optimistic recheck, NOT a fully atomic live-owner+account-file transaction.** To avoid blocking gameplay on disk I/O, the callback releases `owner.mutationLock` **before** `Files.move` and directory `force(true)`. A World mutation can still race in the small interval **after** this final check. Similarly, filesystems outside the tested provider or uncooperative external writers do not honor all coordination contracts. An actual owner quiescence/epoch pin, crash-durable multi-file COMMIT record, separate reward-ledger authority and restart-time exact-once recovery proof are still required before enabling native C2S185 widget32181 reward granting.

**NO_GRANT remains authoritative.** Bank widget32178 excluded. R25 promotion PR #1847 untouched.
