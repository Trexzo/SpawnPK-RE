# G21.275/276 — exact preexisting ACTIVE marker continuity across forced World publication

**Source parent:** combined [#2803](https://github.com/Trexzo/SpawnPK-RE/pull/2803) corrected head `323a0ef5fb91165e3d7d74ca263bcc0dec90cc14`, 657 focused cumulative candidate (requires own hosted CI). Source includes G21.271/73 forced World postmove review/nlink protections and Chat2 G1937–40 NPC output and lethal callback fixes. All account and NPC production source unchanged by this focused test-only extension.

## Existing regression forced a necessary compatibility correction

The original [#2800](https://github.com/Trexzo/SpawnPK-RE/pull/2800) G21.271 code called `LocalDurableAccountReviewIntentPrimitive.requireNoReviewMarker` *after* World `ATOMIC_MOVE`. Hosted [Actions #38895779768](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38895779768) showed new G21271 and G21272 BOTH passed, then inherited `G21193CoreForcedHealthySaveStillQuarantinedTest` correctly FAILED with `G21.193 healthy World save failed`.

**Explanation:** G21.193 deliberately mints a durable one-shot ACTIVE review marker during the admitted first session and **retains** it across a valid forced World final save. That saved account remains permanently quarantined from subsequent login, even after exact postimage force/readback. Rejecting all existing markers at every World save broke this negative-only opt-in compatibility; it was not a newly discovered positive recovery ability.

**G21.275 production correction already committed to #2800, #2801 and #2803 heads:** capture at the beginning of the pinned World G21.39 publication section a bounded (<=4096 bytes), NOFOLLOW, validated G21.188 existing ACTIVE marker witness with fileKey, creation/mtime, size and exact raw bytes. After actual forced account move and postimage verification, accept an unchanged exact preexisting marker as a **retained NEGATIVE**; refuse a newly created, removed, replaced or byte-altered marker as `G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW`. Pre/post marker identity/data comparison is observational, not atomic external fencing. Nothing is removed or cleared and no new World admission is authorized. The old G21193 remains a mandatory compatibility test.

## New real World-backed focused cases

- **G21.275:** real registered WorldPlayer, `captureAndSave`, opt-in forced World account ATOMIC_MOVE, existing properly formed G21188 ACTIVE marker. Inside an injected postmove hook, modify ONE syntactically-valid hex nibble in `untrustedSessionNonce` on the SAME marker inode, restoring its original length and mtime. Require validated negative marker still present but SaveTicket UNCERTAIN because full captured bytes changed, actual account postimage not rolled back, false verified count zero. Exact marker content must persist as altered by fixture only.
- **G21.276:** identical real World forced snapshot path and valid already-present ACTIVE marker but NO marker tampering. New postimage saves successfully, marker remains exactly unchanged and quarantined, one verified World postimage recorded. No automatic marker retirement and no second-session admission rights.

**Manifest:** previously combined 657 distinct + two new focused test classes = **659 unique** with dedicated Gradle JavaExec tasks; neither World nor Chat2 production files are edited on this incremental branch, no other writer/codec/reward changes.

## Required CI and assurance limit

Exact-head hosted **659/659**, G21275/76, G21193, G21271–74, G1937–40, Java11 major55 and full Gradle. Historical #2800 failed run remains failed; refreshed exact branches must independently pass before certification. Marker observations cannot supply immutable durable owner/generation epochs, external writer atomicity, safe crash recovery or authenticated final logout/settlement receipts. Draft/unmerged, main unchanged.
