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

## Strict nlink provider capability probe

A standalone `tools/verification/FilesystemLinkCountProbe.java` Java 11 class now checks the **actual hosted Windows provider**, initially observes `unix:nlink` on a private temporary file, creates a second hardlink (if supported), and then checks whether the count changes from **1 to 2** on the original inode. The job uploads its output as a separate `windows-linkcount-capability` artifact. `UNSUPPORTED_UNIX_NLINK_PROVIDER`, `HARDLINK_CREATION_UNAVAILABLE` and `UNSAFE_OR_INCONSISTENT_LINK_COUNT` must be treated as lack of positive evidence for the opt-in strict writer/journal path. It is a capability **observation**, not a production fallback and not a source-only job failure unless the probe itself cannot compile/run. Even observed 1→2 values do not prove hostile-race atomicity, directory durability or original-client GUI functionality.

This addition means a source-only Windows job can be green while `strictWorldHardlinkGateUsable=false` is explicitly reported; operators MUST interpret the artifact rather than treating a green job as a strict feature qualification.

## First hosted Windows failure and correction — actual evidence

First Windows Actions run [#39030023732](https://github.com/Trexzo/SpawnPK-RE/actions/runs/39030023732) **FAILED before compilation**, AFTER downloading Gradle 8.10.2, because the child `powershell.exe` running `server/gradle/bootstrap-gradle.ps1` raised `Get-FileHash : The term 'Get-FileHash' is not recognized`. This is a real Windows bootstrap dependency failure; neither Java11 Windows bytecode nor the two synthetic tests ran in that attempt. The original-client evidence artifact DID report missing pinned client and did not claim GUI/179 acceptance.

To fix this without weakening the cryptographic pinned distribution gate, `bootstrap-gradle.ps1` sources the new pure-.NET `server/gradle/ExactSha256.ps1` helper. It opens the actual file bytes, computes SHA-256 with `System.Security.Cryptography.SHA256`, returns lowercase hexadecimal, and disposes the file stream and crypto instance deterministically. `scripts/Test-GradleBootstrapHash.ps1` proves the established empty-file and `abc` SHA-256 known vectors; the Windows workflow runs this proof BEFORE downloading/bootstrapping Gradle. The original pinned Gradle distribution version **8.10.2** and digest **31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26** are unchanged. Do not label Windows compilation/smoke PASS until the new exact-head job actually completes successfully.

## Linux full-build contract regression found and fixed

The first corrected Windows code branch had hosted Windows source smoke **SUCCESS**, but independent Linux full Gradle run [#39030374941](https://github.com/Trexzo/SpawnPK-RE/actions/runs/39030374941) **FAILED** at `gradleBootstrapContractRegression` because the pre-existing structural contract expected `function Get-ExactSha256` in `bootstrap-gradle.ps1` itself. This is incompatible with the new, deliberate source separation and is *not* Java regression evidence.

The contract is now changed, not deleted: parse BOTH bootstrap and adjacent helper PowerShell, require exactly one literal source of `ExactSha256.ps1` before the FIRST cached ZIP verification, assert direct `System.IO.File::OpenRead` and `System.Security.Cryptography.SHA256::Create` + `ComputeHash`, deterministic `Dispose()`, and refuse `Get-FileHash`/weak digest dependencies. Every original invariant remains for the three file hashes, pinned ZIP digest, invocation-owned copy+extraction, execution and cleanup ordering. The new hosted Linux full build MUST pass before this latest exact head is considered certified.
