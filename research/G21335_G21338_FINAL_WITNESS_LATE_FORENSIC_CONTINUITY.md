# G21.335–338 — independent late selected final-witness source continuity

**Certified parent:** [Chat 1 cumulative #2895](https://github.com/Trexzo/SpawnPK-RE/pull/2895) exact `af23dda1e126133183a45ba0015986bf604cbf96`, hosted **781/781**, Java11 major55/full Gradle. Chat 2 cumulative [#2896](https://github.com/Trexzo/SpawnPK-RE/pull/2896) independently certified 781/781; the core gameplay production blobs were SHA-identical. Avoid parallel duplicate PRs.

## Production observation gap
The original `LocalFinalSaveWitnessForensicAudit.inspect` reads exact negative witness twice, then independently validates account+ACTIVE and returns `MATCHES_UNTRUSTED_NEGATIVE`. A noncooperating writer can rewrite the source/marker/witness **after its own last observation** using the same inode/size/mtime, leaving a stale operator report. This is a read-only diagnosis gap, NOT admission or original server authority.

## Change
Added a default no-op *package-private* late seam and an independent, bounded final read of the original selected account+ACTIVE using `LocalAccountForensicEvidence.inspect`, then full NOFOLLOW selected object identity and original witness byte re-read+hardlink check. A real changed account or ACTIVE digest throws `G21.335`; object substitution throws `G21.336`; late witness bytes or inode drift throws `G21.337`. The evidence remains strictly **negative even if matching**. This is *not* an atomic multi-leaf snapshot or hostile-writer exclusion. No account/marker creation, deletion, user login, grant, persistence, World or NPC policies modified.

## Four real regression entrypoints
G21335: rewrite selected account on same inode after initial audit, restore file length+mtime; require G21.335.
G21336: same selected ACTIVE marker late rewrite with restored metadata; require G21.335.
G21337: mutate final witness *after* its initial two reads and source validation, same inode/size/restored mtime; require G21.337.
G21338: mutate unrelated account at identical seam; exact selected canonical witness remains MATCHES_UNTRUSTED_NEGATIVE and no authority flags flip.

All tests assert original path isolation, no created files or automatic admission, and execute actual filesystem FileChannel.force mutations. Manifest **781→785 distinct focused**, four individually callable JavaExec tasks, critical class retention **97→101**. Maintain historical return statuses for stable/foreign controls and no automatic recovery. Gate: hosted exact-head 785/785, each new G21335–38 marker, Java11 major55 and full Gradle; draft/unmerged, main untouched. #2540/#2559 authenticated epoch/final receipt and #2587 durable reward outbox remain unresolved.
