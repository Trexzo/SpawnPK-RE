# G21.40 — real separate-JVM FileLock publication acceptance

**Certified parent:** G21.39 exact `af5ff30425199034f0250c1d1572c0af035538b9`, hosted [workflow 37835328053](https://github.com/Trexzo/SpawnPK-RE/actions/runs/37835328053) SUCCESS, 338 focused Java11. Frozen R25 promotion PR #1847 untouched.

## Why a second JVM matters

G21.38 added a stable sibling `FileChannel.lock()` to serialize G21.34 no-clobber negative review-marker publication with G21.36 concrete file-backed World account replacement. G21.39 fixed the same-JVM global monitor by using ref-counted JVM leases keyed per account. Those Java-only concurrency fixtures proved multiple writer instances in **one JVM**, not actual operating-system FileLock mutual exclusion between two JVM processes. This test supplies a real hosted cross-process measurement without enabling any Mailbox reward grant.

## G21.40 integration

`G2140MailboxCrossJvmPublicationIntegrationTest` is a standalone main-style Java11 test and also its own child-mode entry point (`--hold-lock`). The parent launches child Java executables using `System.getProperty("java.home")` plus the actual test runtime classpath, preserving platform-specific java/java.exe naming. Each child calls the production `MailboxAccountPublicationCoordinator.withExclusivePublication(accountFile,...)`, writes its **own process PID** to a READY sentinel only **after** acquiring the OS-level account lock, and holds that lock until the parent writes its RELEASE sentinel. Child stdout/stderr are redirected to a per-test log, deadlines are finite, and `close` kills any hung child before temporary file cleanup. This is a **real different process**, not merely another Java thread, method invocation, or synthetic FileLock stub.

The parent drives two independent interleavings against actual gameplay infrastructure:

1. **Marker publication:** A child holds the lock for a PREPARED account. The parent starts an independent G21.34 `MailboxDurableReviewFence.arm(proposal)`, waits for the prepublication fault phase, and confirms that no marker or success receipt exists while the child retains the lock. During this time, an unrelated account's actual `WorldPlayerPersistence.captureAndSave` must successfully complete. The child releases, exits with code 0, and the previously blocked parent marker then publishes its no-clobber **negative-only** record bound to the exact G21.30 proposal. No item grant or Mailbox ACK.
2. **World account replacement:** A second child holds another account's publication lock. The parent captures a normal World save on the real persistence worker; a deterministic FilePlayerRepository `beforeWorldReplace` hook confirms that temp-file serialization reached the final publication boundary. The save must remain incomplete, and original account bytes must be unchanged, while the child owns the OS lock. After release and clean child exit, the World save ticket succeeds. This account has no review marker.

A fresh World subsequently refuses the review-marked account's real login and loads unaffected accounts. The test also verifies no orphan account temporary files, no leftover parent JVM lease registry entries, no live inventory credit or Mailbox claim, and child PIDs different from the parent.

**Focused Java11 manifest 338→339**, Gradle `g2140MailboxCrossJvmPublicationRegression`. The hosted exact-head full CI must pass before certification; process startup and lock support are part of that acceptance.

## Authority/scope limitations

The test proves cooperating Java FileLock behavior on its **tested hosted filesystem and JVM configuration** and in two selected publication interleavings. It does not prove safety for all filesystems, network mounts or noncooperating tools; no malicious external file mutation is covered. Account-file and marker-file writes are still not one atomic durable transaction, and hardware crash/power-loss persistence is not asserted. Native C2S185 widget32181 remains NO_GRANT, and this test introduces no automatic release, replay, rollback, recipient credit or positive settlement receipt. Bank widget32178 excluded.
