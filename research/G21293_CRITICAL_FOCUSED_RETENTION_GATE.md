# G21.293 — CI retention guard for critical focused regressions

Parent cumulative [#2846](https://github.com/Trexzo/SpawnPK-RE/pull/2846) at `f01c57ca4fa6f7a59bbd4cd17278648e0d7e2fe6`, 703-test candidate. Certified previous combined [#2844](https://github.com/Trexzo/SpawnPK-RE/pull/2844) at `6d08473bb3f276c2d561bec1c5780f1de6854796` (701/701 Java11 and full Gradle).

## Evidence
`server/build.gradle` currently verifies that focused train has 703 unique class names and executes all names, but **does not assert that a known essential class remains present**. When integration Chat 1/Chat 2 reconciles independent manifests, deleting a critical negative-regression and substituting an unrelated test could accidentally preserve count/uniqueness. Source reviewers may not notice, and the deleted regression would silently stop running.

## Implementation
Before task registration, introduce `currentTrainCriticalCoverage` family requirements in the Gradle configuration phase. Each required class must occur in `currentTrainFocusedTests` or Gradle throws `Current-train safety regression family omitted` naming missing classes.

Explicit anchored groups: actual socket failed-final and cross-World stale-write negative characterizations (G21177/G21180), forced healthy-save quarantine (G21193), real hardkill one-shot review and healthy nonretirement G21220 variants, independent JVM G21253; G21285–92 forensic exact account/marker safety; G1847 compatibility and G1961–68 real NPC terminal provenance/diagnostic boundaries. All **23 required exact entries** were preflight-verified present.

No runtime source, gameplay policy, persistence writer, account data, grant or protocol behavior changes. This is a release/build configuration guard, not a new runtime safety feature. All existing 703 tests still run unchanged.

**Gate:** exact-head GitHub-hosted Gradle configuration + all 703 focused tests, Java11 major55 and full build. Keep draft/unmerged; main unchanged. Do not promote the build guard to original SpawnPK authority or recovered server completeness.
