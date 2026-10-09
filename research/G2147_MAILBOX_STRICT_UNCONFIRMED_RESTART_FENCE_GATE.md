# G21.47 — persistent negative restart fence after an unconfirmed strict PREPARED account write

**Certified parent:** G21.46 exact commit `2a992f4681489c6b5fde16436f1b74b92a92528d`, hosted [workflow #37899632233](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37899632233) SUCCESS, **345 focused Java11 tests**. Frozen R25 promotion PR #1847 remains untouched.

## Real restart-safety gap

G21.46 detects when a strict PREPARED account-file `ATOMIC_MOVE` and parent-directory `force(true)` already completed, but the player's complete live state or registration generation no longer matches the submitted snapshot. Instead of a false ordinary G21.23 strict success `Receipt`, the existing worker returns `UnconfirmedCommitException`. However, a Java future's failure is NOT persistent admission authority: if the server restarts, the saved account file may still contain the old, seemingly valid PREPARED bytes and load normally unless a separate review marker exists.

## G21.47 negative-only sidecar

`MailboxStrictUnconfirmedReviewFence` uses a distinct **`.g2147-strict-postpublication-review`** sibling sidecar name and explicit **`SPK-G2147-STRICT-POSTPUBLICATION-UNCERTAIN-V1`** format. It contains the canonical account name, **G21.30 complete strict-snapshot SHA-256**, a fixed `REVIEW_REQUIRED_NO_GRANT` negative state, and its own SHA-256 checksum. Its record exposes **no** grant, replay, rollback, marker-release or session-admission authority. It **does not** invent a G21.32 hypothetical-CLAIMED postimage fingerprint or impersonate an exact reward-settlement receipt.

The sidecar's publication is a concrete, **no-clobber, write-once** local-filesystem operation: serialize to a unique sibling temporary file, `FileChannel.force(true)`, create the final marker pathname **exclusively** using `Files.createLink` (no overwrite or unsafe rename fallback), unlink the unpublished temp and `force(true)` the parent directory. An existing marker, including a corrupt marker, is never unlinked or replaced. The dedicated `inspect` API checks identity and checksum. Presence is fail-closed even when the content is corrupt, unreadable or a symlink.

## Integration into actual strict World persistence and admission

The **concrete file-backed** `StrictDurablePlayerSnapshotWriter.saveStrictForWorld` already holds the G21.38–G21.40 per-account JVM + cross-JVM `FileChannel.lock()` while doing its final G21.42 review-sidecar check, G21.45 prepublication live owner check, ATOMIC_MOVE, directory-force and G21.46 postpublication live owner check. G21.47 acts **inside that same publication critical section** when the postpublication callback signals changed or retired owner. The writer arms the dedicated negative marker **without trying to reacquire the file lock**. If sidecar publication succeeds, the marker is visible to future processes; the strict future still returns the original G21.46 `UnconfirmedCommitException`, never a success receipt.

If sidecar write, exclusive link or force fails, the writer appends that failure as **suppressed** to the uncertain exception and does **not** claim confirmed persistent quarantine. The account file may already have been replaced. This does not rewrite or roll back any account bytes, and the session is not granted items.

`MailboxDurableReviewFence.present(account)` now checks either the original G21.32 marker **or** the independent G21.47 marker. This is the *existing actual* negative gate shared by `FilePlayerRepository.hasUnresolvedMailboxReviewFence`, normal file-backed World saves, strict World saves, session admission and restart loader. An uncertain G21.47 account therefore cannot bypass restart veto just because its underlying account file remains syntactically valid and PREPARED. The original G21.32 `arm` and `armVerifiedAgainstCurrentFile` record format/semantics remain unchanged; their `inspect` method does **not** reinterpret G21.47 content as a G21.32 proposal. G21.47 has its own dedicated inspector.

## Deterministic real World integration

`G2147MailboxStrictUnconfirmedRestartFenceIntegrationTest` runs the actual `WorldPlayerPersistence` strict PREPARED writer against concrete `FilePlayerRepository`. It pauses at the existing G21.46 `AFTER_DIRECTORY_FORCE` test seam, at which time the account file has **already** been atomically replaced. While the worker is paused, a genuine World-owned movement change or player-generation retirement occurs. After release, the strict future must be **unconfirmed**; a checksum-valid account- and exact-digest-bound negative marker must exist. The test then:

- Verifies a real guarded World save of the marked account is refused through the same G21.36 read-only negative review check.
- Refuses a second marker publication with no clobber, and confirms that deliberately corrupted marker bytes still block presence/admission even though `inspect` rejects them.
- Creates a fresh World, proving both marked accounts' normal account loads are denied; an unrelated clean account still loads.
- Proves an unchanged owner can still receive an exact strict snapshot receipt **without** a marker. No live inventory credit or Mailbox CLAIMED transition, no automatic rollback/replay/release, no marker temp artifacts, and no idle JVM lock leak.
- Updates historical G21.46 focused test to expect this **new negative-only state** on precisely the two unconfirmed cases, while keeping that test's original unconfirmed receipt, no-grant and unchanged-account behavior.

Focused Java11 manifest **345→346**, Gradle task `g2147MailboxStrictUnconfirmedRestartFenceRegression`, hosted exact-head full focused CI mandatory before certification.

## Honest remaining limits — important

**This is NOT a crash-proof multi-file transaction.** The strict account file is replaced **before** G21.47 discovers a late live-owner mismatch and publishes its negative sidecar. A JVM crash, OS failure or power loss **between** the account replacement and sidecar publication can leave the file changed with *no* persisted G21.47 marker. Likewise, a marker-directory force failure does not prove the marker survives power loss. The test verifies the actual hosted filesystem contract and normal process restart **after** successful marker publication; it cannot prove all crash/power-loss orderings.

A complete solution still needs a prepublication durable intent/quarantine with a proven cleanup/commit state machine or an atomic per-account storage layout plus owner-epoch coordination. No native C2S185 widget32181 item grant, exactly-once positive inventory+Mailbox settlement, auto marker release, replay or rollback is authorized. The frozen R25 promotion PR #1847 and bank widget32178 are untouched.
