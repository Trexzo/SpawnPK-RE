# G21.264/265 — post-ATOMIC_MOVE negative review-marker witness

**Exact parent:** cumulative [#2793](https://github.com/Trexzo/SpawnPK-RE/pull/2793) at `7182b8132a5e3781cfde889535779ef232cdbed7`, with 641 distinct focused candidates. This includes core G21.263 and Chat 2 G19.3.4 production changes. Its own hosted exact-head verification is required and must not be inferred from this patch.

## Concrete source gap

After `LocalOptInRawTemp.forcePublish` completed `Files.move(temp, account, REPLACE_EXISTING, ATOMIC_MOVE)`, it invoked a package-private `afterMoveProbeForTest` before directory force and postimage verification. A manual-review/uncertain negative sidecar created *after replacement* could then remain present while the method reported ordinary RAW publication success: previous G21.250/251/262 checks only covered the **pre-move** interval.

This work adds a third, *post-replacement* negative-sidecar observation, before returning normal completion. Both the permanent G21.188 ACTIVE review marker and the three mailbox negatives (G21.32 durable review, G21.47 uncertain, G21.48 write intent) must be absent for the exact current account. All checks remain under the same cooperating G21.39 publication lock. If a marker appears after the actual file replacement, the existing G21.248 wrapper throws `RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW`: account bytes may already be changed. **Never describe this as a rollback or safe final logout**.

## Focused regressions

- G21.264: primary AccountStore RAW + late ACTIVE, named LocalAccountProfiles RAW + late mailbox review, direct FilePlayerRepository RAW + late uncertain, and another direct RAW + late write-intent. Each sidecar is created using the deterministic post-ATOMIC_MOVE fault seam. Each must cause **uncertain** outcome, with the observed account postimage retained exactly, foreign sidecar untouched, and no owned temp/JVM reservation/publication-lock leak.
- G21.265: a late negative review marker for a **different named account** during the selected RAW postmove seam does not veto the selected healthy save. Its serialized postimage changes while the unrelated bytes and marker remain untouched.
- Manifest advances from 641 to **643 unique focused test classes**; two named Gradle JavaExec tasks added. Production scope is only `LocalOptInRawTemp.java`, not NPC/combat, world ownership, packet decoding, rewards, mailbox grants or original SpawnPK policy.

## Evidence and limits

This narrows a deterministic negative-marker observation gap. It does **not** ensure atomicity with external uncooperative sidecar creation after the last check, prevent lock-file replacement, or prove durable cross-process account ownership epochs. A successfully forced RAW postimage is not an authenticated final-session receipt. There is no automatic recovery, marker retirement, replay, rollback, or grant authority.

**Certification gate:** fresh GitHub-hosted exact-head 643/643 with G21264/G21265 markers, Java11 major=55 and complete Gradle. Keep the branch and PR draft; main unchanged.
