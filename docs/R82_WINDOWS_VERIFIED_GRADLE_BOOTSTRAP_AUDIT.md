# R82 — Windows PowerShell verified Gradle archive bootstrap

**DRAFT / STACKED ON R81 PR #3126 / OFFLINE-TESTED / UNMERGED**

## Cause and scope

On the November 20, 2026 Windows Server 2025 GitHub runners, the
shared `server/gradlew.bat` launcher entered the pinned Gradle 8.10.2
bootstrap, downloaded and hash-checked the distribution, and then
failed before any Java compilation:

```
Expand-Archive : Cannot find an overload for "ExtractToDirectory"
and the argument count: "3".
server/gradle/bootstrap-gradle.ps1:55
```

The independent R81 Windows job succeeded with the same Java source
when it used `gradle/actions/setup-gradle@v4` to avoid the shared
PowerShell bootstrap. Thus the Windows atomic-persistence CI failure
was in tool bootstrapping, not evidence of a failing account-
persistence assertion.

## Change

- Keep the same `server/gradlew.bat` entrypoint, selected JDK 21,
  `services.gradle.org` URL, Gradle 8.10.2 version, cache folder and
  expected SHA-256:
  `31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26`.
- Move the verified ZIP processing to the small, reusable
  `server/gradle/verified-gradle-archive.ps1` function.
- Verify the SHA-256 of each cached or newly downloaded ZIP **before
  extraction**, using the original .NET streaming SHA-256 algorithm;
  remove the cached ZIP and stop on a mismatch.
- Replace `Expand-Archive` and unavailable `System.IO.Compression.FileSystem`
  assembly loading with Windows 10+'s native `System32\\tar.exe` ZIP reader.
  List the entries first and reject absolute paths or `..` traversal,
  then extract the SHA-verified archive into a temporary folder.
  This avoids utility-cmdlet/ZIP-assembly availability issues in the
  actual `gradlew.bat` PowerShell process.
- Stage extraction in a randomly named temporary directory and
  require `gradle-8.10.2/bin/gradle.bat` before publication.
  Clean temporary extraction on success and failure. Refuse to
  recurse through a reparse-point cache directory.
- Keep the already-installed valid cached executable fast path in the
  original bootstrap. **That cache fast path does not revalidate the
  extracted directory on every invocation**; this change is a cold
  archive-installation repair, not an end-to-end distribution
  attestation mechanism.

## Negative controls

`server/gradle/R82OfflineVerifiedArchiveTest.ps1` creates disposable
tiny ZIP fixtures under the host temporary directory and exercises
the actual production helper in both Windows PowerShell 5.1 and
PowerShell 7, including:

1. Valid archive with matching hash installs the intended executable,
   and reuse keeps the installed executable.
2. Wrong expected hash fails before extraction, removes the cached
   ZIP, and does not publish the destination.
3. A malformed archive with a matching hash still cannot publish.
4. A valid ZIP whose root is not `gradle-8.10.2` cannot publish.
5. A ZIP entry escaping its extraction folder is rejected by the
   explicit native-archive listing preflight, leaving
   no publication and no outside file.
6. Temporary staging directories are cleaned.

Dedicated Windows R82 CI also runs the actual `server/gradlew.bat
--version` path on a fresh host, including the pinned real Gradle
distribution. Inherited Linux full server and Windows atomic-
persistence GitHub checks must be inspected on the R82 head before
marking a cross-platform build verified.

## Boundaries

The archive regression fixtures contain no actual Gradle distribution,
no account persistence, network traffic, game client, or live
session. Only the actual launcher smoke retrieves Gradle from the
already configured official distribution address.

No Java combat, PvP outcome, projection, session, persistent profile,
game engine, client or private asset source is changed.

**R82 DRAFT / STACKED / NO LIVE DEPLOYMENT.**
