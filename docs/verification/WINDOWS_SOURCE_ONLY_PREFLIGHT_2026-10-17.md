# Windows source-only preflight: evidence boundary

This draft branch adds `.github/workflows/windows-source-preflight.yml` and `scripts/Check-WindowsReleaseEvidence.ps1` on top of independently Linux-certified [single-commit snapshot #2930](https://github.com/Trexzo/SpawnPK-RE/pull/2930) (exact tree as Linux certified [#2929](https://github.com/Trexzo/SpawnPK-RE/pull/2929)).

## Intent

Actually exercise Windows CI (JDK 21 compiler targeting Java 11 bytecode) instead of inferring Windows compatibility from Linux hosted tests. The Windows job uses the existing **SHA-256 pinned** Gradle 8.10.2 PowerShell bootstrap, compiles production/test classes, verifies Java 11 bytecode and exercises two real but bounded non-GUI fixtures:

- `g21294FinalSaveWaitTimeoutRegression`: bounded final-save wait/exception/interrupt semantics.
- `g2023AttemptFenceUncertainCompletionEvidenceRegression`: genuine canonical NPC death and 12-way in-process duplicate-consumer attempt race (non-granting).

They are deliberately chosen to avoid claiming broad Windows POSIX `unix:nlink` availability, exact storage durability or full client gameplay parity. This is **not** a full 841/841 Windows regression matrix. It also is not a v308 Windows graphical-client launch.

## Explicit client evidence report

The repository's `RUN_V5185_FULL_SELFTEST.ps1` strictly requires an exact v308 JAR with SHA-256 `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`, verified directly from the current selftest script. The gate checks only the four established candidate paths, computes hashes for files that are present and records a JSON artifact. Missing/mismatched JARs block any claim that 179/179 original-client parity or GUI tests ran; they do **not** make a source-only compilation meaningless. There are no downloaded or spoofed proprietary client bytes and no workaround for that authority requirement.

JSON boolean fields `originalClientGuiExercised=false`, `historical179TestAcceptanceRun=false`, `authenticatedFinalSaveReceiptVerified=false` and `automaticGameplayReplayAuthorized=false` remain false even if the source smoke passes.

## Gates/limits

Require an actual Windows GitHub Actions success before claiming this limited smoke. Any Windows failure needs real logs, not synthetic PASS. Linux full 841/841 certification remains independent. Before merging the large source snapshot: original pinned v308 graphical runtime on the target Windows owner machine, actual 179 acceptance, crash/dual-JVM contention/restart evidence, client/network and build provenance, and operator manual review semantics remain open.

All parent PRs are draft; this branch is verification-only, not changing `main` or the experimental gameplay/account authority.
