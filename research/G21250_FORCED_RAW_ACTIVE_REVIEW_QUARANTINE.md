# G21.250 — G21.188 permanent ACTIVE review marker veto in opt-in forced RAW mode

Parent [G21.249 #2753](https://github.com/Trexzo/SpawnPK-RE/pull/2753) exact `d086f7254334bfe861cd8e1acf15bde954ca73bd` (597 focused candidate, own hosted CI required); separately certified G21.247/G19.0.4 cumulative [#2750](https://github.com/Trexzo/SpawnPK-RE/pull/2750) 595/595.

## Security boundary
The new default-OFF G21.248 forced verified RAW publication mode could force and publish an exact account postimage despite a pre-existing **G21.188 ACTIVE manual-review marker**. Holding the G21.185 OS lock and verifying temp bytes never conferred authority to ignore a failed or unconfirmed earlier World session. G21.250 now calls the existing NOFOLLOW fail-closed `LocalDurableAccountReviewIntentPrimitive.requireNoReviewMarker(exactAccountPath)` under that optional RAW/OS lock immediately before `ATOMIC_MOVE`, with another lock-object continuity check afterward. This covers all three production direct RAW entrypoints through the one shared forced helper; existing default/legacy direct writes remain exactly as before.

The focused test first writes an account using each of AccountStore.save, LocalAccountProfiles.save and FilePlayerRepository.save, durably creates an actual ACTIVE marker on that exact path via the G21.188 primitive, then attempts a second forced RAW save. All three must refuse publication, preserve old account bytes and markers, release RAW reservations and clean only their own unique temps. Manifest **597→598**. This narrows cooperation but does not stop an external actor from racing after the check, nor does it grant raw-mode privileges to retire/replay/reconcile ACTIVE markers.

Never claim authenticated durable owner epoch, final-save ACK, crash-safe relog, original SpawnPK rule, or permission to disable quarantine. Own hosted exact-head Java11 major55/Gradle **598/598** required. DRAFT/unmerged.
