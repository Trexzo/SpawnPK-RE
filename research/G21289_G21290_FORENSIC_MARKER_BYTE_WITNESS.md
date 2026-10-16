# G21.289/290 — bounded read-only negative marker byte continuity

**Certified parent:** [Core PR #2838](https://github.com/Trexzo/SpawnPK-RE/pull/2838), exact `5cca4600aed3481284e023b5265ef5332800a2c1`, independently hosted 695/695, Java11 major55, full Gradle.

## Source-identified gap
`LocalAccountForensicEvidence.inspect` previously reobserved five exact negative sidecar names with `BasicFileAttributes` only. An uncooperative actor could rewrite an **existing, regular** selected negative marker in place after the initial observation and restore original inode/size/mtime. `same(before, after)` would consider it stable even when bytes had drifted. A present marker is already negative, but falsely stable evidence is still misleading.

## Changes
- Add a bounded SHA-256 witness of each **regular selected negative sidecar** before the deterministic first-account-digest seam, and compare with independent NOFOLLOW SHA-256 witnesses after the seam, alongside existing stat checks.
- Do not follow or parse symlink/nonregular sidecars; classify those only as opaque negative evidence. Oversize or unidentified negative leaves stay opaque, never proof of safety.
- On different bounded regular bytes with unchanged metadata, throw `G21.289 NEGATIVE_SIDECAR_BYTES_CHANGED_DURING_READ`; do not produce a stable report.
- No file creation, mutation, lock, removal, login admission, grant, auto-recovery, new owner epoch, final-save receipt or positive clean status.

## Real focused coverage
- **G21289:** create exact selected activeReview sidecar, change one byte in-place at the seam, force file, restore original mtime; verify original fileKey and size, scanner refuses with the new error, bytes preserved, subsequent unchanged marker still negative.
- **G21290:** exact unrelated account's ACTIVE sidecar is changed at selected account's seam; verify selected account + own ACTIVE marker remain stable negative, no global veto, no scanner-created artifacts.
- Core 695 + 2 tests = **697 distinct focused**, two individual JavaExec tasks. Previous G21279–88 remain part of manifest.

## Limitations
Even two independent digests are not an atomic snapshot against adversarial ABA or hardlink attacks; opaque nonregular/oversized markers cannot be proven byte-stable. This is read-only operator evidence only. No cross-process owner epoch, crash-safe final save, no automated grant/replay/cleanup or original server authority. Issues [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559), [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) remain open.

Gate: hosted **exact-head 697/697**, G21279–90, Java11 major55 and full Gradle. Draft/unmerged, main unchanged.
