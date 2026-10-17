# G21.244 — best-effort prepublication RAW/strict OS lock object veto

Stacked on G21.243 #2740 exact `3b45e51b1f40db50d8a782b69ab96ff4982b3c7b` (585 focused, GitHub Actions success). The existing G21.221 opt-in filesystem object continuity protects genuine World owners, but the four cooperating non-World RAW writers were not checking their own acquired OS lock object's current name at the last pre-move publication point.

Production updates G21.244: expose `RawWriteReservation.requireLockObjectContinuity()`, reusing the existing `LocalCrossJvmAccountSessionLock.Handle` check. Call it directly before account publication for repository raw save, primary AccountStore.save, named LocalAccountProfiles.save and standalone StrictDurablePlayerSnapshotWriter.saveStrict. Legacy fallbacks recheck. World-only callbacks, serialized formats, strict atomic-move requirement and disabled-by-default configuration stay untouched.

G21.244 test injects a real `.session.lck` file object swap in the existing repository RAW pre-move hook and strict-writer phase hook; it asserts both saves fail before replacing the old account bytes, and that no JVM RAW reservation leaks. Manifest 585→586; targeted JavaExec Gradle task.

**Not proven:** atomic exclusion of attacker mutation after the last check, cross-process durable epoch/fencing, final-save ACK, crash-safe restart admission, external uncooperative writers, original SpawnPK game policy, removal of ACTIVE review markers. G21.241 negative cross-JVM filename-replacement scenario remains relevant. All settings opt-in, no main merge. Requires own exact-head hosted Java11 major55/full Gradle and 586 focused regression success.
