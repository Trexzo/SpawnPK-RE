# G21.367–370: World-owned temp/new postimage physical hardlink aliases

Parent [#2925](https://github.com/Trexzo/SpawnPK-RE/pull/2925) exact `276c7e490387c80d179347b15cfe551f612de286`, already hosted CI-certified **833/833**, Java11 major 55 and full Gradle. The tests below are NEW and are not certified merely by that parent.

## Source gap

`FilePlayerRepository.saveForWorld` currently seals exact World-owned temp bytes and file object through `requireOwnedWorldTemp` + SHA, and G21.364–66 checks hardlinks of the *old selected account* before replacing the pathname. Neither condition proves that the **new World-owned temp inode** is exclusively named before it becomes the selected account. A second hardlink pointing to a World temp keeps stable fileKey, creationTime, bytes and SHA; the alias then becomes an out-of-band name for the newly published account. This is especially important because G21.230 generic hardlink policy is independently default-OFF.

## Narrow opt-in production correction

Only with BOTH `experimentalForceWorldAccountPostimage` and `experimentalNegativeFinalSaveReviewWitness` enabled, reuse the existing fail-closed `LocalFinalSaveNegativeWitness.requireSingleObservedLink` NOFOLLOW `unix:nlink == 1` evidence on the World-owned temp **immediately before `replaceSnapshotTemp`**. Keep all original temp digest and previous account preimage checks.

After forced account move and `forceAndVerifyWorldPostimage`, recheck **newly published selected account** `nlink == 1`; if added at the existing `afterForcedWorldMoveForTest` cutpoint, throw inside existing `G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW` handler. The account MAY ALREADY be replaced; do not report a positive receipt, delete an out-of-band alias, or claim rollback.

## Genuine regression boundaries

- **G21367:** hardlink temp after serialization, before exclusive publication: preserve old account bytes/fileKey and fail before move.
- **G21368:** introduce same inode alias inside actual publication callback: preserve old account preimage and fail before move.
- **G21369:** add alias to newly published selected account via actual post-ATOMIC_MOVE callback: no false successful postimage; deliberate UNCERTAIN outcome, no rollback claim.
- **G21370:** unrelated foreign file hardlink remains intact during a healthy selected forced World save; do not globally veto account publication.

Focused manifest **833→837** unique classes, four JavaExec tasks and G21.293 critical retention anchor. New CI must pass exact-head 837/837, explicit G21367–70 output, Java11 major55/full Gradle. Not an atomic guard against uncooperative writers after final check; no durable epoch, final ACK, negative marker retirement, account recovery or gameplay grant.
