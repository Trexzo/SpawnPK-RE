# G21.300–301 — forced final-save negative witness crash cutpoints

Parent [#2855](https://github.com/Trexzo/SpawnPK-RE/pull/2855) exact `a64c48f04d622106378f9d2d3e1819c8f4b1915a` introduced the default-OFF, write-once, forced `.g21298-final-review-witness` tied to an exact REAL final World SaveTask, forced account postimage, original World generation/sequence and retained durable G21.188 ACTIVE. The marker remains negative after healthy saves.

**G21.300 actual JVM hard kill:** Package-only deterministic after-write/before-FileChannel.force probe blocks a CHILD JVM inside `LocalFinalSaveNegativeWitness.publishOnce`, after CREATE_NEW wrote bytes but before any forced file or parent directory ACK. A parent JVM observes a readiness sidecar, calls `Process.destroyForcibly`, waits for actual termination, then checks account and ACTIVE remain, the possibly unforced witnessed leaf exists, admission still vetoes, and a second witness creation is refused. This deliberately does NOT assert power-loss durability of the unforced witness. No cleanup is attempted.

**G21.301 post-force failure:** A separate package-only probe fires after **both file and parent directory are forced** but before final postread. Throws IOException and confirms the failed caller cannot report an ordinary final-save success, delete or retry an existing witness, alter account/ACTIVE, or allow new login. The record is still untrusted even though forced.

These two test-only probes are DEFAULT-INERT and cannot bypass the actual marker verification, strict World owner/generation captured source, account consistency, or write-once constraints. No server reward policy, raw writer, authenticated receipt or recovery mutation is implemented. The fact that a file was forced is not evidence that another process cannot forge it or that a durable session epoch exists.

**CI gate:** 713→**715 distinct focused tests**, G21300/G21301 pass, G21298/G21299 real TCP and negative controls, Java11 major55, full Gradle, and both independently verified parent CI runs. Keep draft/unmerged, `main` unchanged. #2540/#2559 remain open.
