# G21.371–373 — strict Windows link provider pre-admission fail-close

## Genuine source risk and objective

Hosted Windows source-only Actions [#39030375104](https://github.com/Trexzo/SpawnPK-RE/actions/runs/39030375104) **PASSED** JDK21/Java11, bounded account timeout, and the 12-thread NPC attempt smoke, but the independent capability probe observed `SPK_NLINK_PROBE_STATUS=UNSUPPORTED_UNIX_NLINK_PROVIDER strictWorldHardlinkGateUsable=false`. This is a real limitation, not a test failure or permission for a weakened fallback.

The strict, opt-in negative final-save witness path needs `unix:nlink` on selected ACTIVE/account/temp/witness files. If the provider cannot expose it, the old path might still publish an irreversible one-shot ACTIVE review marker at initial World admission and only discover the unsupported nlink gate when performing the final save. Since ACTIVE is never automatically retired, this unnecessarily strands a review account on an unsupported provider.

## Production

Add `LocalFinalSaveNegativeWitness.requirePreAdmissionLinkCountCapability(exactLockedAccount)`, used **BEFORE** `LocalDurableAccountReviewIntentPrimitive.writeActiveReview` inside `LocalOneShotActiveReviewOnAdmission.forceBeforeWorldRegistration` only when the experimental negative final review witness property is on. It requires a NOFOLLOW ordinary original parent directory, a numeric `unix:nlink >= 1` on that actual directory, plus `nlink == 1` on an existing selected account inode (honest absent first-time account allowed). Unsupported provider gets `G21.371 STRICT_LINK_PROVIDER_UNAVAILABLE_BEFORE_ACTIVE`, selected aliased inode gets `G21.372 STRICT_PRE_ADMISSION_ACCOUNT_ALIAS_NEGATIVE`. No account bytes or ACTIVE marker are created by this preflight. The session lease still controls the broader World admission logic.

The check provides a conservative *early negative veto*, NOT a proof of hostile-writer atomic exclusion, cross-JVM owner epoch, final-save receipt, directory crash durability or on-disk grant. No default-off behavior changed.

## Focused regressions and Windows contract

- G21371: absent selected account, supported provider permits preflight without creating the account/ACTIVE; unsupported provider fails with an explicit diagnostic.
- G21372: selected account with a real same-inode hardlink refuses before ACTIVE on a supported provider; Windows unsupported provider also fails closed.
- G21373: a foreign account hardlink does not block selected account preflight on supported Unix; unsupported Windows provider still fails closed.

Each test runs on Linux and Windows (capability-aware assertions) and belongs to the explicit current-train retention family. Focused count increases **849→852**. Windows source-only workflow runs all three alongside the existing G21294+G2023 smoke; Windows overall *still* does not run original v308 GUI or historical 179 acceptance. Need exact-head hosted Linux 852/852, Java11 major55/full Gradle + Windows new gates before any certification. Do not promote review/marker retirement on these tests.
