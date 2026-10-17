# G21.210 — opt-in JVM account reservation for legacy named profile writer

## Provenance

Certified G21.208/G18.7.3 [Draft #2665](https://github.com/Trexzo/SpawnPK-RE/pull/2665), exact `1dc29e0612ff3412901959bd3c7e03fea1cb25fd`, 523 focused/Java11/full Gradle. G21.209 [Draft #2666](https://github.com/Trexzo/SpawnPK-RE/pull/2666), exact `2fbc24c8d9660595d7d8d165c276d148654d95f8`, first proves the previously unguarded legacy named-profile writer could replace a newer World account **even with both experimental flags ON**. G21.209's hosted exact-head CI must be separately checked.

## Small production fix

`LocalAccountProfiles.save` (non-primary named account branch only) now pins `accountFile(u)` and acquires the existing G21.208 `LocalJvmAccountSessionLease.reserveRawWrite(file,u)` *before* any profile directory creation or historical fixed `.tmp` I/O, releasing the reservation in try-with-resources after the complete write and cleanup. No new lock monitor; actual file I/O remains outside the in-memory admission monitor. As with G21.208, same-path held opted-in JVM lease denies profile writer; in-flight cooperating profile writer denies new opted-in leases and other cooperating same-path RAW writers.

Legacy `AccountStore.save` PRIMARY delegation is **unchanged**, does not participate and remains an open bypass. Normal World `saveForWorld`, raw repository code, properties serializer, original-client, default-OFF flags and frozen R25 remain unchanged.

The inherited G21.209 test is deliberately revised to preserve **default-OFF compatibility gap**: it first proves participating RAW is rejected with the opt-in gate ON, then explicitly turns the gate OFF only around its old legacy-write characterization. Its historical parent #2666 remains the exact original *gate-ON bypass* witness. Do not interpret the legacy test as production permission to toggle properties during live sessions.

## Positive acceptance

New G21.210 test holds a real bound WorldPlayer/JVM lease, publishes a newer World snapshot through actual queued capture-bound SaveTask and then invokes the real `LocalAccountProfiles.save` with BOTH opt-ins enabled. Expect exact `RAW_WRITE_DENIED_ACTIVE_JVM_LEASE` before the legacy temp is allocated; unchanged disk postimage and owner Token. Different account path legacy save remains available; after explicit TEST-ONLY owner cleanup the legacy writer still works using its original core-only serializer. Manifest **524→525**, separate Gradle task and marker `G21210_LEGACY_PROFILE_JVM_RESERVATION_PASS`.

**Not solved:** primary `AccountStore.save`, standalone strict/manual/other writers, external changes, prior-unleased World sessions, live toggle hazards, authenticated cross-process owner epoch, durable intent/final save failure, replay/credit authority and G21.188 ACTIVE marker retirement. Must be Draft/unmerged until exact-head 525/525 focused, Java11 major55/full Gradle CI success.
