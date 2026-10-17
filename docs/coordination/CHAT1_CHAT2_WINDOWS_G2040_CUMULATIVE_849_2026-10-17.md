# G20.40 / Windows preflight — cumulative main-aligned 849 candidate

## Exact provenance

- Parent [Windows verification #2932](https://github.com/Trexzo/SpawnPK-RE/pull/2932) head `d71bdc7a3c1e29e4e1945752037e30a5ae4389a6` is based on already certified single-commit main-aligned [#2930](https://github.com/Trexzo/SpawnPK-RE/pull/2930) `cfd3e725711afc53b7940e6212eb7d3ae7499b84` (hosted 841/841).
- Chat 2 donor [#2933](https://github.com/Trexzo/SpawnPK-RE/pull/2933) exact `1364e53d314ec79abb168f2e16bf1bb187dac817`, based on [#2931](https://github.com/Trexzo/SpawnPK-RE/pull/2931) 845/845 independently hosted-certified. Changes G2033–40 only to the standalone NON-GRANTING, UNWIRED disk attempt journal, eight authentic tests, manifest 841→849 and two documented source notes. Twelve donor Git blobs imported **unchanged** including exact `server/build.gradle`. No competing modification to the Java/gameplay source in the Windows branch.
- The integration also inherits the independent Windows source-only JDK21→Java11 bytecode/timeout/NPC attempt smoke and the SHA-256-verified Gradle bootstrap. First Windows build [#39030023732](https://github.com/Trexzo/SpawnPK-RE/actions/runs/39030023732) had failed before compile due to Get-FileHash unavailability; corrected hosted Windows [#39030375104](https://github.com/Trexzo/SpawnPK-RE/actions/runs/39030375104) **PASSED**, emitted `SPK_WINDOWS_SOURCE_SMOKE_PASS`.
- That Windows run **also proved** `unix:nlink` is **UNSUPPORTED** on hosted Windows JVM: `SPK_NLINK_PROBE_STATUS=UNSUPPORTED_UNIX_NLINK_PROVIDER strictWorldHardlinkGateUsable=false`. Strict World/account and disk journal hardlink-dependent capabilities cannot be claimed as supported on Windows; the refusal/negative semantics must remain. Pinned v308 client absent: no 179/179 acceptance or GUI verification occurred.
- Subsequent Linux full Gradle [#39030374941](https://github.com/Trexzo/SpawnPK-RE/actions/runs/39030374941) **FAILED** at the old `gradleBootstrapContractRegression` structural assertion expecting inline `Get-ExactSha256`. The corrected parent moved that assertion to the exact sourced .NET helper, verifying PowerShell parse, SHA256 stream hash, disposal and source-before-use ordering while retaining all hardcoded pinned distribution hash/verified temp execution gates. Parent's NEW exact-head CI still must be certified.

## Release/merge gate

Require **849/849** on this exact integration commit, Java11 major 55/full Gradle, all G2033–40 plus original account G21367–70, a successful Windows *source-only* preflight+named artifacts and explicit unsupported nlink capability status. Linux source tests do not qualify strict provider behavior on Windows. This is a draft against #2932 only; main remains unchanged. Do not accidentally merge both this cumulative PR and superseded alternatives.

## Boundaries

No authenticated durable owner epoch, cross-process final-save receipt, crash-durable directory journal fsync, event identity/credit attribution authority, reward+consumer ACK, automatic marker retirement, replay, or historical exact v308 UI/walk/parity acceptance. Java class success is not these guarantees. Preserve original developer provenance of Chat 2's imported Git objects.
