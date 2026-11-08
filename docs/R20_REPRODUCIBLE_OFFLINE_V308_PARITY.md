# R20 — reproducible, SHA-pinned offline v308 compatibility suite

R19 independently passed the inherited 179-test R8.5 offline compatibility
suite against the original v308 client JAR on Windows, but the run was an
ad hoc command loop with manually captured logs. R20 makes that evidence
reproducible from the **actual repository** without committing or
launching the proprietary original client.

## Inputs and admission

`tools/verification/r20_original_v308_offline_suite.py` accepts:

- `--repo`: clean, isolated `Trexzo/SpawnPK-RE` checkout.
- `--source-head`: 40-character exact Git commit SHA; mismatches fail.
- `--server-jar`: canonical `server/build/SpawnPKLocalServer.jar`
  under that checkout.
- `--server-sha256`: independently measured 64-character SHA-256 of
  the compiled server, which must match before any test JVM starts.
- `--original-v308`: unmodified original v308 client JAR.
  SHA-256 must equal
  `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`.
- `--java11`: a separate Java 11 executable. The version check is a
  short Java subprocess and does NOT launch the graphical client.
- `--private-parent`: an existing absolute directory where a new
  invocation-owned temporary workspace can be created (not the real
  `user.home`, not a repository subtree or a symlink).

The tool also checks the Git origin, relevant tracked source modification
status and the sealed, ordered **179 distinct test class names** parsed
from `RUN_V5185_FULL_SELFTEST.ps1`. It refuses to invent any missing test
names or substitute a differently hashed client.

## Command (PowerShell)

Build the repository separately first, then run from the same exact checkout:

```powershell
python tools/verification/r20_original_v308_offline_suite.py `
  --repo 'C:\path\to\isolated\SpawnPK-RE' `
  --source-head '<40-hex Git commit>' `
  --server-jar 'C:\path\to\isolated\SpawnPK-RE\server\build\SpawnPKLocalServer.jar' `
  --server-sha256 '<64-hex measured SHA256>' `
  --original-v308 'C:\path\to\unmodified\client.jar' `
  --java11 'C:\path\to\dedicated\jdk11\bin\java.exe' `
  --private-parent "$env:LOCALAPPDATA\Temp"
```

Use `--plan-only` to validate identities **without starting any test
classes** (a Java-version probe still runs). `--max-tests N` runs an
explicitly PARTIAL smoke suite that cannot print the 179-test success marker.

## Execution boundary and result

- A unique private `user.home` is created for each full suite invocation.
- The tool starts one separate Java11 child process per named
  `spk.local.<SealedTest>Test`, using the exact server JAR, its test
  classes and the original client JAR in a **classpath**. It never
  invokes `java -jar client.jar`, its main class, GUI or login flow.
- Each test's stdout and stderr go to bounded, separate local log files;
  exit code and timeouts decide success, not diagnostic stderr output.
- A JSON receipt records exact source/client/server identities,
  `tests_passed`, `input_unchanged`, elapsed time and output workspace.
- File hashes of BOTH input JARs are rechecked before a success receipt.
- Real existing v312 Java client PIDs, current user accounts, OS firewall
  rules and real client caches are untouched. No broad Java-process kill.
- Negative controls refuse wrong source SHA, wrong original-client SHA,
  wrong server-JAR SHA, missing test classes, altered manifest and unsafe
  private-parent choices.

## CI and evidence scope

GitHub Actions on Linux and Windows runs
`test_r20_original_v308_offline_suite.py`, which uses only
synthetic Python fixtures and the committed 179-test manifest; it does
**not** require or download the proprietary client JAR.

The genuine full 179-JVM verification must be performed on an authorized
machine with a locally available, exact SHA-pinned original client.
Its successful marker is
`R20_ORIGINAL_V308_OFFLINE_179_PASS`, distinct from the CI-only unit
test marker.

The offline tests exercise server Java test classes with client definitions
available on the classpath. They **do not** establish graphical v308 login,
Windows OS egress-block safety, authentic game economy/combat mechanics,
or power-loss persistence durability. The existing three owner-JDK sessions
and missing exact OS firewall egress rule remain separate GUI acceptance
barriers. R20 is a draft PR stacked after R19, not a merged release.
