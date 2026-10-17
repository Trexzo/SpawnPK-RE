# G21.71 — restart recovery inspection with pinned file identity (NO GRANT)

**Parent:** hosted certified G21.70 `5dc90830a3019d282d0890937a66090b0237e6cd`, [Actions #37965622089](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37965622089) SUCCESS, 370 focused Java11. [Issue #2318](https://github.com/Trexzo/SpawnPK-RE/issues/2318).

## Problem and implementation

A G21.66–G21.70 terminal attempt can end with a coherent terminal file but no live owner transition, a PREPARED account, a G21.47 permanent uncertain marker, an abandoned G21.48 write-ahead intent marker, or damaged/partial bytes. A new process has no trustworthy record of the old **in-memory** G21.27 reservation, G21.67 strict receipt or G21.69 freshness observation. None of these bytes independently authorize reward credit or exactly-once replay. Existing restart admission already **rejects terminal and negative marker states**; this milestone provides a single read-only forensic classifier, not a replacement login path.

`FilePlayerRepository.inspectRestartRecoveryReadOnly(account)` holds the G21.39 **cooperating account publication lock** for the full inspection. It binds negative marker names and NOFOLLOW account-file reads to the original pinned account file. Three sidecar families are checked in priority order: permanent G21.32 review (also the G21.49 legacy review marker), G21.47 strict uncertain commit, and G21.48 stranded write-ahead intent. Any marker presence forces review regardless of account bytes; marker mutations while observing fail closed. No marker is removed.

For an unmarked account the inspector uses the G21.60/61 admitted-session-quality primitives: NOFOLLOW regular file attributes before/after decoding, an independent SHA-256 comparison against raw on-disk bytes, another final file object check, and original account resolver and negative-marker rechecks before returning. The inspector never returns a PlayerSnapshot to be accidentally hydrated into World. Uncooperative file rewrites or metadata/bytes changes detected during inspection cause IOException, not a positive state.

Read-only `RestartRecoveryEvidence.State` classifications:

- `DURABLE_REVIEW_MARKER`
- `UNCERTAIN_COMMIT_MARKER`
- `STRANDED_WRITE_INTENT_MARKER`
- `MISSING_ACCOUNT_NO_REPLAY`
- `COHERENT_TERMINAL_QUARANTINE`
- `INVALID_TERMINAL_QUARANTINE`
- `PREPARED_UNCLAIMED_NO_REPLAY`
- `INVALID_PREPARED_QUARANTINE`
- `LEGACY_NO_JOURNAL_NON_ADMITTING`

Every state has `restartAdmissionAuthorized=false`, `durabilityConfirmed=false`, `transactionCommitted=false`, `liveApplied=false`, `grantAuthorized=false`, `replayAuthorized=false`, `rollbackAuthorized=false`, `releaseAuthorized=false`, and `clientAckAuthorized=false`. A legacy no-journal account may continue through the **existing separate** World session admission; the new inspector never grants that admission itself.

The real-filesystem G21.71 regression tests prepared and coherent terminal state, both existing restart paths, malformed terminal and prepared images, missing/legacy accounts, review marker precedence, stranded write-ahead marker, strict terminal post-move uncertainty (both permanent marker and restart refusal), same-metadata content rewriting, marker insertion mid-read, symlink rejection, stable repeat audit, no file mutation and unchanged original live inventory. Focused train 370→371, Java11 bytecode and exact-head hosted full build required before certification.

**Boundaries:** G21.39 locks coordinate cooperating publishers only. This inspection is not a power-loss fsync proof, cannot reconstruct a lost in-memory reservation, does not distinguish historical timeout vs normal coherent terminal purely from account bytes, and cannot authorize restart replay/repair. Native C2S185/widget32181 NO_GRANT; frozen R25 PR #1847 unchanged. Draft/unmerged.
