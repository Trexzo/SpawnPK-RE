# G21.47 — negative, write-once quarantine for an unconfirmed strict PREPARED account publication

**Certified parent:** G21.46 exact SHA `2a992f4681489c6b5fde16436f1b74b92a92528d`, hosted [workflow #37899632233](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37899632233) SUCCESS, 345 focused Java11. Frozen R25 #1847 untouched.

## Remaining safety gap

G21.46 correctly reports `UnconfirmedCommitException` instead of a strict durability receipt if an owner changes or unregisters after the account-file `ATOMIC_MOVE` and directory force. But the old file-backed World admission, ongoing session and World-save checks only inspected the G21.32 independent negative sidecar. A **G21.46 uncertain account might still load or be overwritten after restart**, even if the file itself looks like a valid PREPARED account, because an exception object does not survive JVM restart.

## G21.47 narrowly bounded implementation

New file-backed `MailboxStrictUncertainFence` publishes a distinct, write-once `.g2147-strict-uncertain` sibling file containing a negative-only account identity, canonical G21.30 PREPARED digest, and `MANUAL_REVIEW_NO_GRANT` status. A sidecar is **never parsed to justify positive credit**. Its mere presence, including malformed content or symlink metadata, blocks admission. Publication creates a unique file-backed temp, fully writes and `force(true)`s it, creates a no-clobber atomic hard link for the final name, and forces the parent directory; it never overwrites or automatically deletes a published marker. Hard-link or metadata errors do not fall back to unsafe copy/rename.

The **real concrete file-backed strict World writer** now catches `UnconfirmedCommitException` resulting from post-replacement owner drift or post-rename file/directory-force uncertainty **inside the existing G21.38–40 account-local JVM/FileLock critical section**. Before that account file lock releases, it attempts to publish the negative sidecar bound to the immutable prepared digest. It always propagates the original uncertain outcome; if marker publication fails, the marker failure is preserved as a suppressed cause. It never incorrectly turns failed/quarantined publication into a positive grant or normal strict receipt. There is no nested account lock acquisition.

`FilePlayerRepository.hasUnresolvedMailboxReviewFence` now checks **both** historical G21.32 and new G21.47 negative sidecar presence. This existing unified gate is already consulted before and after actual WorldPlayerPersistence session admission/load; before and inside the cooperating `saveForWorld` publication lock; and by G21.37 socket-boundary/periodic active-session review checks. The concrete strict writer's own early and last-minute review-fence checks also deny either marker type. Manual/forensic `FilePlayerRepository.load` and historical direct `save` remain deliberately outside live admission authority.

The sidecar does **not** change the historical G21.32 record/format nor require fabricating a nonexistent hypothetical CLAIMED postimage. The two markers remain independent negative-only reasons for requiring operator review.

## Deterministic Java11 regression

`G2147MailboxStrictUncertainFenceIntegrationTest` exercises the real World strict PREPARED queue and G21.46 post-directory-force race. It observes a file already replaced, mutates the live player, allows the strict writer to resume and expects an unconfirmed outcome with a write-once negative sidecar. It verifies the marker's presence in the actual file-backed persistence presence API; normal World-save refusal; fresh World session/load refusal; active-session boundary refusal; independent-account usability; duplicate no-clobber semantics; corrupted marker still vetoing; no inventory/Mailbox positive credit, replay or automatic marker clear; no temp or JVM lease leaks.

The historical G21.46 test still tests its original distinction that the separate G21.32 negative review marker is **not** fabricated. The new G21.47 sidecar is an **additional different marker**, not a change to the G21.32 format.

Java11 focused manifest **345→346**; new task `g2147MailboxStrictUncertainFenceRegression`. Full exact-head hosted CI SUCCESS required before certification; PR stays Draft.

## Explicit residual limitations

- **Not a write-ahead journal.** A sudden JVM crash, power loss or machine reset between account replacement and negative marker creation may leave the account file changed **without** the G21.47 sidecar. If hard-link or force fails, only an unconfirmed exception with suppressed evidence is available. G21.47 is therefore not a guaranteed crash-consistent quarantine for every power-loss ordering.
- A marker being present does not imply its data and parent entry survive every hardware/controller failure; proof is limited to the hosted filesystem API operations and provider.
- Only cooperating World file-backed writers and the existing session checks honor this marker. Manual/forensic `FilePlayerRepository.save`, custom persistence and uncooperative external editors are excluded.
- The strict PREPARED account and its marker are not one atomic multi-file COMMIT. An operator must review any uncertainty; no automatic release, replay, rollback, item grant or CLAIMED transition is authorized.

Native C2S185 widget32181 remains **NO_GRANT**. Bank widget32178 excluded; R25 #1847 untouched.
