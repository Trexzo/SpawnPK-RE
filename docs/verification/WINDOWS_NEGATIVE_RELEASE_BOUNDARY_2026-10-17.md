# Windows source-only release boundary (negative gate)

**Scope:** Based on the green main-aligned cumulative G2069 integration, PR #2954 / 884 focused tests. No account saves, NPC journal production logic, protocol, v308 client code, reward granting, or runtime authority are changed. Everything remains draft/unmerged.

## Why an additional gate is needed

A green Windows hosted source-only workflow proves selected Java11-targeting compilation and tests. It does NOT prove the pinned original v308 graphical client ran, historical 179/179 parity passed, a strict inode/nlink provider exists, durable owner epochs or final-save acknowledgments work, or NPC replay/credits are safe. Previously the separate client JSON and nlink text existed but their consistency was not verified together.

## Machine-checkable negative report

Invoke the script from repository root:

    ./scripts/Assert-WindowsSourceReleaseBoundary.ps1 -ClientEvidencePath windows-release-evidence.json -LinkCountEvidencePath windows-linkcount-evidence.txt -OutputPath windows-source-release-boundary.json

Requires ordinary, existing, non-reparse input files. It validates: exact pinned v308 SHA256; all six negative original-client/owner/replay booleans; exact candidate path set; unique observed client paths; lowercase canonical digests and v307/v308 flag consistency; observed-state consistency; and exactly one parseable Java nlink capability line. Positive reported nlink capability without actual 1-to-2 observations is rejected.

On coherent evidence, the output always asserts readyForRelease=false, decision=BLOCKED_SOURCE_ONLY_EVIDENCE, and originalClientGuiCertified=false, historical179Certified=false, finalSaveAckCertified=false, gameplayReplayAuthorized=false. The strictNlinkProviderObserved field is an observed capability only, not release clearance. Deterministic blocker codes name what was not verified.

Malformed, missing, or contradictory input throws and does not emit a new decision. The script refuses overwriting either input. Windows workflow runs the contract test and validator *after* real source tests and the Java link-count probe, then uploads windows-negative-release-boundary.

## Contract tests

The PowerShell test script includes two coherent but BLOCKED cases: absent original client + unsupported nlink, and deliberately synthetic mismatched client + synthetic 1-to-2 probe. It separately rejects 14 falsifications/errors: 6 forged authority booleans; changed pinned checksum; fake client present state; candidate path-set substitution; digest/flag contradiction; claimed nlink support without observation; impossible observed count; two competing probe records; no probe record. No proprietary original client bytes are invented or shipped. The synthetic link-positive case tests the parser only; actual hosted filesystem capability remains the independent Java probe's responsibility.

## Release blockers, still open

The owner Windows machine must validate the exact pinned v308 client and graphical runtime, historical 179 acceptance with raw logs, client/network parity, strict Windows filesystem and dual-JVM crash/recovery behavior, authenticated cross-JVM owner epoch plus durable save ACK, and actual authorized NPC consumer credit+ACK. The journal forensic CLI is unlocked/read-only/non-granting. No source-only report is approval to grant or replay rewards.

## Required exact-head evidence

1. Hosted Windows contract marker, negative-boundary JSON, exact earlier Windows source smoke, real filesystem capability line.
2. Linux 884/884 focused regressions, Java11 major55, full Gradle build.
3. Review and coordinate with independently advancing Chat 1/Chat 2 branches before choosing an eventual main merge. Do not merge duplicate cumulative and one-commit snapshot PRs.
