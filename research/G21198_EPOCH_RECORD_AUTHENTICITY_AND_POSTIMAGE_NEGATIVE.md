# G21.198 — prototype owner-epoch record authenticity and account-postimage negative

## Exact parent

This Chat1 **TEST-ONLY** regression is stacked on G21.197 [Draft PR #2618](https://github.com/Trexzo/SpawnPK-RE/pull/2618) exact SHA `c9adeff430cf467f0e8b575ac6f6d76aec1c7d4e`, [Actions #38597268652](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38597268652) SUCCESS (504 focused, Java11 and full Gradle). It preserves the already certified G21.196/G18.5.8 combined source [#2616](https://github.com/Trexzo/SpawnPK-RE/pull/2616). Chat2 source and existing production classes remain byte-identical.

## Negative: complete record pair is not a trusted owner or save receipt

The UNUSED G21.196 `LocalDurableOwnerEpochPrototype` stores account **path hash**, random correlation UUID and caller-supplied generation. Its binding contains only the reservation record SHA and account path SHA. It does not include the on-disk account snapshot bytes, a signed/locked owner claim, OS-level file lock evidence, authenticated World membership, or a final-save publication receipt. Its inspector intentionally checks structure and consistency, **not provenance**.

G21.198 adds `G21198CoreEpochPrototypeUntrustedRecordNegativeTest` with two disposable filesystem scenarios:

1. Create a forced normal prototype reservation/binding pair. Write two different test-only account-file images independently through raw Files.write. The same pair still structurally inspects as bound to the same generation even though account bytes changed. Corrupt the binding reservation digest with zeros: inspection must fail with `BINDING_MISMATCH_NO_AUTHORITY`. Test-only raw restoration makes it structurally parseable again. None of these operations establish durable ownership or authorize account reuse.
2. Without calling `reserveBeforeWorldRegistration` or registering any player, a test-only raw writer synthesizes the publicly documented canonical reservation and matching binding for an arbitrary UUID and invented generation 37. `inspect` accepts the pair as structurally bound; this is precisely why **inspection cannot confer authority**. The helper must reject a duplicate reservation without overwriting. Independently force a G21.188 ACTIVE review sidecar and prove `requireNoReviewMarker` still refuses; complete epoch structure cannot bypass it. After deleting only the disposable reservation fixture, inspection of the remaining binding must fail with missing reservation rather than silently recovering or cleaning.

The raw fixture writer neither calls force nor owns a session; the inspector's acceptance must **not** be interpreted as proof of forced publication. This test is not evidence of a public exploit because these records are unused by production and test inputs directly alter private temporary files. No hostile writer security guarantee, physical power-loss proof, or original SpawnPK server rule inference is made.

## GitHub and certification

Add one test, one dedicated Gradle task `g21198CoreEpochPrototypeUntrustedRecordRegression`, and this research note. Focused manifest **504→505 unique**. No existing production classes, runtime flags, LocalSession, World, FilePlayerRepository, G21.188 review primitive or Chat2 gameplay changes. Require **fresh exact-head hosted CI**, `G21198_EPOCH_PROTOTYPE_UNTRUSTED_RECORD_PASS`, G21197 and G1858 markers, Java11 bytecode major55, 505/505 focused, full Gradle. Keep Draft/unmerged.

Remaining real design: trusted exact-account pre-membership owner epoch under cooperating locks; durable binding to actual registered generation, fenced save publication and owner-matched final receipt; independently forced fail-closed ACTIVE retirement; process-kill windows throughout and distinct machine-power-loss validation. [#2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540) and [#2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559) remain OPEN; Chat2 outbox [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remains independent.