# G21.271 / G21.272 — forced World postmove negative-marker handling

**Base:** [#2798](https://github.com/Trexzo/SpawnPK-RE/pull/2798) exact `79221990fe8f18f2a44c937237dbc33533f1b0e2`, 649-test combined Chat 1/Chat 2 candidate with independent hosted CI required. It includes corrected inherited G21.270 foreign-JVM test handshake, Chat2 G19.3.5/36 recursive NPC attack guard, and core G21.264–69 forced RAW postmove defenses.

## Observed missing protection

The opt-in **World-owned forced-postimage** writer in `FilePlayerRepository.saveInternalSelected` checks the existing mailbox negative sidecars (and disk COMMIT) *before* account replacement, and then forces directory metadata/verifies account bytes via G21.192. Unlike the forced RAW writer after G21.264, the World writer had no corresponding **post-replacement** negative sidecar recheck. An uncooperative actor could insert an ACTIVE account review marker, mailbox review, uncertain/write-ahead intent, or immutable disk COMMIT sidecar just after account `ATOMIC_MOVE`; exact postimage bytes could match while a normal successful World SaveTask receipt was still reported.

This is especially important because the caller of a World SaveTask can treat a successful completion as justification to release a cooperating in-JVM session lease. A checked postimage is not sufficient evidence that an account is free of durable negative markers.

## Production change

Only under the already default-OFF `spk.local.experimentalForceWorldAccountPostimage=true` option, after `replaceSnapshotTemp(..., requireAtomic=true)` and before incrementing the existing verified-World counter:
1. Run a **package-private, constructor-injected** after-World-move fault seam, default inert for every existing repository constructor.
2. Force and verify the exact postimage bytes/metadata.
3. Recheck the exact selected account's G21.188 ACTIVE marker with `LocalDurableAccountReviewIntentPrimitive.requireNoReviewMarker`.
4. Recheck existing G21.36 mailbox review/strict uncertain/strict intent and G21.88 disk COMMIT via `requireUnfencedWorldSave`.
5. A failure **after** replacement throws `G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW`, never reports a verified postimage count, and never claims rollback.

The new behavior deliberately retains all legacy default World save paths, existing production constructors, mailbox/terminal grant rules, packet handling and Chat2 NPC combat source unchanged. This is NOT a durable owner epoch nor atomic external filesystem-marker fencing after the last check.

## Real World-backed source regressions

- **G21.271:** five separate named accounts, five genuine `World.isolatedForTest` worlds, registered WorldPlayers, World-owned `captureAndSave` tickets and real on-disk forced account replacements. A test-owned foreign marker is inserted inside the exact post-ATOMIC_MOVE seam for each of G21.188 ACTIVE, G21.32 durable mailbox review, G21.47 strict uncertain, G21.48 strict write-intent, and G21.88 disk COMMIT. Require exceptional save-ticket completion; the observed postmove bytes and corresponding marker persist, no owned World temp remains, no false verified-success counter. This does not authorize deleting any real operator markers.
- **G21.272:** genuine forced World save after which a G21.188 ACTIVE negative marker appears **only for an unrelated named account**. The selected account must still complete normally with exact new serialized phase, unrelated account bytes and marker preserved, one verified postimage count and no SaveTask failure.

The manifest increases **649→651 distinct focused tests**, with two named Gradle JavaExec regressions. No original SpawnPK server semantics or external owner epoch are inferred.

## Limitations and release gate

These are observations, not linearizable external filesystem transactions. An uncooperative marker can still appear **after** the final postmove read. No cross-process durable writer epoch, crash-safe final-save acknowledgment, durable reward outbox, mailbox grant/replay or positive recovery/marker-cleanup authority is established. Exact source head must independently pass hosted **651/651**, G21271/72 markers, Java11 major55 and full Gradle; keep as draft, `main` unchanged.
