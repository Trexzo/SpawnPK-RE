# G21.222 — bounded G21.188 ACTIVE review marker inspection (NOFOLLOW, strict UTF-8)

## Threat surface / source evidence

`LocalDurableAccountReviewIntentPrimitive.writeActiveReview` creates a durable, forced G21.188 `.g21188-active-review` **permanent negative** file. The existing `requireReviewMarker` verified an initial NOFOLLOW regular-file attribute and then used **unbounded `Files.readAllBytes(marker)`**. A hostile/truncated/accidentally huge marker could cause unbounded memory consumption or redirect the second read if the marker leaf changed after its first attribute check. Crucially the actual G21.189 admission veto relies on **existence**, not on parsing a trusted “clean” marker; no malformed marker should ever be treated as permission to relog.

**Certified ancestor:** G21.218 [Draft #2687](https://github.com/Trexzo/SpawnPK-RE/pull/2687) head `46e30fe8dc7dbb5a56b883f0a07f0c3307250646`, Actions #38762094896 SUCCESS **536 focused/Java11/full Gradle**. G21.219–G21.221 are still separately stacked experiments that require their own exact-head CI. G21.220's real hard-killed JVM and completed-healthy-final one-shot ACTIVE negative tests are in [#2689](https://github.com/Trexzo/SpawnPK-RE/pull/2689) and remain conservative NO-RETIRE.

## Production hardening

Replace the `requireReviewMarker` unbounded read with a maximum **4096-byte** negative-only inspection:
- Before opening, validate regular non-symlink NOFOLLOW `BasicFileAttributes`, nonnull fileKey and size <=4096; oversized is explicit `G21.222 REVIEW_MARKER_OVERSIZE_NEGATIVE_NO_CLEAR`.
- Read through `FileChannel.open(marker, READ, NOFOLLOW_LINKS)` into one fixed **4097-byte** ByteBuffer. A marker growing between `stat` and read cannot exceed the allocation. When byte 4097 arrives, reject oversized; no allocation proportional to malicious file size. Zero-progress reads reject conservatively.
- Re-read NOFOLLOW attrs after read and require same nonnull fileKey/regular object; changed object is negative identity drift (not a positive success).
- Decode UTF-8 with `CodingErrorAction.REPORT`, not silently replacing malformed sequences. Existing G21.188 header and `ACTIVE_NEEDS_MANUAL_REVIEW` checks remain unchanged. A readable valid marker remains a NEGATIVE, never an allowlist, cleanup authorization, grant or snapshot receipt.
- **No changes to** `writeActiveReview` forced-create/parent-force behavior, `requireNoReviewMarker` presence-based veto, login wiring, final-save/epoch/admission or default experimental flags. No automatic marker removal.

## Deterministic regression

New Java G21222 test generates a real forced G21.188 marker, validates it as format evidence, proves presence alone vetoes. In a **throwaway temporary test directory**, rewrite the fixture marker to 4097 bytes, malformed UTF-8, and wrong-format header: each parse is refused and existence remains a negative. On Unix, replace only the TEST fixture leaf with a symbolic link and prove NOFOLLOW rejects and does not delete the symlink or read the target. Windows symlink creation may require privilege, so that test branch is explicitly platform conditional; hosted Linux must run it.

Marker `G21222_BOUNDED_NOFOLLOW_REVIEW_MARKER_PASS`. Scope: one production primitive changed, one Java regression, one dedicated Gradle task and count **540→541 focused**, this note. Require independently hosted exact-head Java11 major55, 541/541 unique focused/full Gradle. Draft/unmerged.

## Non-authorities

A bounded parse cannot authenticate owner/epoch, prevent all TOCTOU swaps, resolve hostile ancestor changes, prove marker durable under physical power loss beyond stated force operations, validate prior World postimage, or allow safe relog after crash. The permanent review marker remains fail-closed; no automatic retirement, unlink, replay, grant, restore, key rotation or crash admission is supported. G21.221 fileKey continuity is still only diagnostic. Keep #2540/#2559/#2587 open until genuine end-to-end epoch/fencing and operator-reviewed recovery exists.
