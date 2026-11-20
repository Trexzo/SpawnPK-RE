# R100 — Windows pinned-Gradle bootstrap retry hardening

**DRAFT / STACKED ON R99 #3144 / BUILD TOOLING ONLY / UNMERGED / NOT DEPLOYED**

## Observed failure

During R99's exact-commit GitHub verification, the independent
Windows atomic-persistence build failed before reaching the test suite:
`server/gradle/bootstrap-gradle.ps1` made one
`Invoke-WebRequest` request for the pinned Gradle 8.10.2
distribution, and Windows reported the transport connection was
forcibly closed. The failure occurred in downloader setup, not
in the changed gameplay code.

This has also affected inherited earlier CI jobs. R100 addresses
the deterministic cause: a single transient network reset should not
automatically fail the build, and a partially written ZIP must never
be mistaken for a valid cached distribution.

## Bounded and independently verified download

The helper `server/gradle/pinned-gradle-download.ps1` exports
`Save-PinnedGradleDistribution`, used by the existing
`bootstrap-gradle.ps1` only when the extracted Gradle launcher
is not present. It preserves the pinned SHA-256, the same Gradle
8.10.2 URL and the existing cache/extractor paths.

- A valid checksum-matching cached ZIP is reused without download.
- A corrupt cached ZIP is refused and fetched anew rather than
  silently passed to the existing R82 verified extraction helper.
- At most **three** attempts are made, with deterministic,
  small, bounded backoffs between attempts.
- Every attempt has a unique adjacent `.r100-*.download` path.
  On IO exception, partial download, wrong hash, or failed move,
  that attempt's temporary file is cleaned in `finally`.
- Downloaded bytes must match the full **pinned SHA-256** BEFORE
  promotion into the canonical cache ZIP. The existing R82 archive
  extractor independently revalidates the same hash.
- A repeated transport failure or corrupt payload produces an
  explicit `R100_GRADLE_DOWNLOAD_DENIED` error. No checksum
  bypass, unpinned mirror, downgrade or silent non-atomic
  extraction path was added.
- No production server gameplay, player session, World actor or
  protocol code changes.

## Executable regression

`test-pinned-gradle-download.ps1` creates a private temporary
directory and injects local fake downloads; **it never requests
external network services** and does not touch the runner's real
Gradle installation/cache. It proves:

1. First attempt writes a partial ZIP and throws; second attempt
   succeeds and promotes only the verified bytes.
2. Reuse of a valid cached file invokes no downloader.
3. A poisoned cached ZIP is replaced by a verified new fetch.
4. Repeated transient errors respect the requested retry limit,
   emit a failure, and leave neither a canonical ZIP nor partials.
5. A completed download with mismatching SHA-256 never promotes.
6. Cleanup finds no leftover `.r100-*.download` files.

The dedicated GitHub Actions workflow repeats the same offline
regression using **Windows PowerShell 5.1 and PowerShell 7**.
The regular server build and atomic-persistence jobs remain
independent and additionally exercise the real bootstrap.

## Explicit boundaries

An unreachable Gradle service or permanently invalid payload
will still fail after bounded attempts. Revalidation does not
make unrelated Gradle plugins/dependencies available and does
not guarantee supply-chain trust beyond the pinned distribution
hash. Concurrent external actors or malicious cache mutation
are not fully modeled; hash validation, not the name of a file,
is the source of acceptance. This is build tooling, not a
filesystem crash-atomicity mechanism.

**Keep draft, unmerged and undeployed until exact-head CI
and both Windows shell tests pass.**
