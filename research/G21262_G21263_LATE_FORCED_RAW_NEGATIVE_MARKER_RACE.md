# G21.262 / G21.263 — late negative marker admission in forced RAW publication

**Parent:** Chat 2's independently certified [#2789](https://github.com/Trexzo/SpawnPK-RE/pull/2789), exact head `8c68e23e8baa0ba2fcc6c2ee2993c561f1638936`, 635/635 focused, Java 11 major=55 and complete Gradle (GitHub Actions #38888810801). That branch itself contains core G21.261 / Chat 2 gameplay through G19.3.0. **No changes to server gameplay, NPC ownership, packets, original SpawnPK authority, or main**.

## Narrow observed RAW bug

`LocalOptInRawTemp.forcePublish` held the account's G21.39 marker-publication lock, checked all four NEGATIVE marker types (G21.188 ACTIVE review; mailbox durable review, strict uncertain and strict write-intent), then called a package-only `beforeMoveProbeForTest`. An uncooperative actor could create a marker in this final gap. The subsequent G21.257 publication lock, G21.258 account-preimage witness, G21.260 nlink gate and G21.185 RAW session lock checks do **not** check these sidecar names. The prior code could therefore atomically replace an account while a newly present manual-review marker remained.

## Change

Under the same held publication lock and before the account file's atomic replacement, recheck **the exact target account's** G21.188 and all three mailbox NEGATIVE sidecars after the test race seam. Retain the existing error classes/markers and prior preflight. Denial does not remove, repair, replay, grant, unlink or clear anything. The opt-in forced RAW path alone is changed. Default legacy RAW remains unchanged.

## Source-level regressions

- **G21.262:** Four distinct exact account files and all three writer families; ACTIVE inserted late during primary legacy RAW, G21.32 mailbox review during named legacy RAW, uncertain during direct repository RAW, and strict write-intent during another direct repository RAW. Every attempted write must be denied *after* the late insertion, with exact prior account bytes, foreign marker, filesystem temporary cleanup, JVM reservations and publication lock cleanup verified.
- **G21.263:** When a review sidecar appears for an unrelated named account during the selected direct RAW writer's final seam, the target account's actual serialized postimage must still change; the unrelated account bytes and new negative marker must remain untouched. Ensures account-path scoping rather than global review lockout.
- Manifest: **635 → 637 distinct focused Java classes**, with two named Gradle JavaExec tasks plus the cumulative test manifest.

## Unproven boundaries

This is a *deterministic after-preflight/before-move check* against an observable race. It is **not** a linearizable filesystem transaction with uncooperative external marker creators. A foreign process can still mutate paths after the last check. Nor is a cooperating G21.39 OS lock an exclusive durable session-ownership epoch across crashes or lock-object replacement. The operation lacks a crash-safe authenticated final-save receipt; uncertain writes require conservative manual review. No original SpawnPK server gameplay semantics are inferred.

**Release gate:** The exact branch head must independently pass hosted **637/637**, `G21262_LATE_FORCED_RAW_NEGATIVE_MARKER_VETO_PASS`, `G21263_UNRELATED_LATE_FORCED_RAW_MARKER_CONTROL_PASS`, Java11 major=55 and full Gradle before labeling it certified. Remain draft/unmerged.
