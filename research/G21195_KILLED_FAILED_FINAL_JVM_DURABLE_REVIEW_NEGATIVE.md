# G21.195 — failed final World save across killed holder JVM / fresh JVM ACTIVE review veto

## Why this separate test is needed

G21.194 (#2608/#2609) initially failed CI because it incorrectly asserted the failed World owner's JVM lease was released. G21.181/G21.182 deliberately retain same-JVM ownership after failed or uncertain final saves. The corrected G21.194 checks a second World in the SAME JVM cannot acquire another token; it cannot isolate the durable sidecar as the post-process admission veto.

G21.195 uses two independent child JVMs and a supervisor, not World.close() as a surrogate for process death. This is a test-only negative, never a recovery mechanism.

## Process / file cutpoints

1. Supervisor allocates disposable account settings and starts an external HOLDER JVM with the five normally-default-OFF opt-ins: JVM lease, cross-JVM OS lock, existing-review veto, one-shot ACTIVE-before-membership, and G21.192 forced World postimage.
2. Holder performs real synthetic 317/ISAAC TCP login, waits for fully forced World BOOTSTRAP, mutates its World extension, injects a deterministic test-only BeforeWorldReplace IOException into subsequent final save (after temp preparation, before account replacement), asserts old disk state plus ACTIVE marker and retains the negative JVM lease.
3. Another JVM attempts real TCP login while the holder remains active; supervisor confirms account and marker, then force-kills the holder. Process termination releases its OS lock.
4. A fresh independent JVM with empty local lease map and optional review veto enabled must refuse real TCP admission, create no World member and retain no new JVM token, with account and review marker bytes unchanged.
5. Supervisor removes only private temporary test fixtures after all children have exited; no production account/intent is deleted or retired.

**Important:** This verifies a cross-process refusal with persistent ACTIVE negative intent after a deterministic FAILED-before-replace final save. It is not machine power-loss evidence, post-rename recovery, proof against uncooperative RAW writers, automatic account repair, safe marker retirement, a durable owner-epoch protocol, or evidence of original SpawnPK client/server rules.

## Acceptance

Stacked on corrected G21.194+Chat2 G18.5.6 Draft #2609 at exact SHA f8f6ea69cc5f066e42852d2f55c04e5d67c92c09. That earlier exact-head CI was pending when stacked; do not inherit its certification. New G21195CoreKilledFailedFinalSaveReviewVetoTest + dedicated g21195CoreKilledFailedFinalSaveReviewVetoRegression task; focused manifest 501 to 502 unique. Require fresh Actions: G21195_CROSS_JVM_FAILED_FINAL_REVIEW_PASS, G21194/G1856, Java11 major55, 502/502 focused and full Gradle. Draft/unmerged until green.

Account persistence #2540/#2559 and independent Chat2 gameplay receipts #2587 remain open. No main merge, production Java edit, runtime default toggles, marker retirement, grants/replay/settlement, or Chat3 client changes.