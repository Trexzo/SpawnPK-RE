# G21.341–344 — late final-source integrity of default-off negative final-save publication

**Certified parent:** [#2901](https://github.com/Trexzo/SpawnPK-RE/pull/2901) exact `703365e12dff567a2d0860abe4dae9c835bd4c41`, **791/791** hosted focused, Java11 major55/full Gradle.

## Gap and scope
The already opt-in `LocalFinalSaveNegativeWitness.publishOnce` performs forced witness file+parent directory write, NOFOLLOW byte/identity checks, original selected-account and ACTIVE forensic hashes, hardlink evidence, and a canonical ACTIVE marker validation. It then returns. A foreign/uncollaborative actor can mutate account, ACTIVE or witness **after the last original selected check but before successful return**, including same-inode, same-size edits with restored timestamp, yielding a misleadingly stable operator publication result. This is only read-only **negative** evidence; no atomicity or authenticated receipt is possible here.

## Production
Introduce a default-inert package test seam just after existing canonical review validation. Before returning, independently re-scan exact selected account and ACTIVE bounded fingerprints and observed single-link evidence; recheck original account and ACTIVE file-key identity and hardlink count; re-open the exact witness NOFOLLOW for expected-size+1 bounded byte comparison with original written bytes, and require original fileKey / one observed hardlink. Reject G21.341 selected byte drift, G21.342 source inode/alias drift, G21.343 selected witness byte/object drift. Run final canonical ACTIVE validation again. Never mutate/clean/restore uncertain sidecars, never authorize any admission/recovery/grant.

## Real regressions
A shared genuine filesystem fixture exercises the REAL publisher after complete forced file+directory publication, rewriting one byte of an existing inode and restoring original mtime at the precise last-return seam. Separate G21341 account, G21342 ACTIVE, G21343 witness cases must fail closed with their original negative witness retained, account/ACTION guard intact and no cleanup/positive receipt. G21344 mutates an unrelated account at that same seam and must leave the selected publication successful but still explicitly untrusted and negative; no global contamination. Four dedicated JavaExec tasks, 791→**795 distinct focused** cases, critical retention 107→111.

**Limit:** foreign RAW writes can still race after the last observation. Never call this an atomic multi-leaf snapshot, durable owner epoch/final-save ACK, automatic ACTIVE retirement or safe repeated login. Issue #2540/#2559/#2587 still open; main untouched, draft only. Requires fresh exact-head hosted **795/795**, G21341–44, Java11 major55/full Gradle.
