# G21.291/292 — comparable read-only operator forensic witnesses

Certified cumulative parent: [#2842](https://github.com/Trexzo/SpawnPK-RE/pull/2842) exact `90b1f47a0aa292141b71f8fac3d6e3d5e39e9ce3`, GitHub Actions #38918013153, **699/699 focused**, Java11 major55, full Gradle.

## Why
Prior G21.289/290 code already computes a bounded SHA-256 of each regular selected negative sidecar twice to catch mutation **within** one forensic scan, but discards those digests in `Evidence`. A subsequent independent operator report therefore says only `PRESENT_REGULAR_NEGATIVE`, even if the same inode was rewritten with original length and mtime restored BETWEEN successful scans. Likewise G21.287/288 compares exact `unix:nlink` counts *within* a scan but displays only `MULTIPLE_ACCOUNT_LINKS_NEGATIVE`, losing the 2-versus-3 observation across scans.

## Source changes
- **G21.291** expose a detached, immutable `negativeSidecarContentWitnesses` map in `Evidence`, containing the already-bounded matched `SHA256:<64 hex>` witness for regular markers, `ABSENT` or explicit opaque-negative status for other selected markers. Report emits five exact `*Content` fields, never raw marker contents. These are untrusted digests, **not a MAC or owner attestation**.
- **G21.292** include a stable `accountObservedLinkCount` in Evidence/report: exact observed positive number when `unix:nlink` is supported; `ABSENT`/`UNAVAILABLE_UNPROVEN` otherwise. Preserve qualitative accountHardlinks negative state; no admission interpretation.
- No extra I/O or probe outside existing read-only bounded scan. No account, marker, grant, world, session or persistence writer modification.

## Regression coverage
- G21291: two independently successful exact selected account forensic scans with activeReview marker changed in-place and mtime/size/inode restored between the scans; the qualitative negative inventory and exact account SHA stay unchanged while marker SHA differs. The copy is immutable and neither scan creates a lock/sidecar or positive receipt.
- G21292: actual selected-account hardlink nlink=2 then nlink=3 across stable scans; two reports retain multiple-link-negative classification but show distinct numeric counts; unrelated account still shows its own count. Unsupported filesystem remains explicitly unproven.
- Cumulative **699→701** distinct manifest cases; two independent Gradle JavaExec gates.

## Explicit limitations
Scans are not atomic over separate filesystem leaves or proof against ABA, hidden external alias paths, hostile process writes, or successful crash-safe final saves. A digest can be compared by an operator but never authorize login, final-save ACK, marker deletion, replay, reward grant, or cross-process owner epoch. The durable account ownership and final-save blockers remain [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) and [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540).

**Gate:** independently hosted exact-head 701/701, G21279–92, Java11 major55, complete Gradle. Draft/unmerged, main untouched.
