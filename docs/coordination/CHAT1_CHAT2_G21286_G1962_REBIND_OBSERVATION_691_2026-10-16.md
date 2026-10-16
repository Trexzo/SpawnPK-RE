# Cumulative G21.286 + G19.6.2 — 691 read-only same-name rebind diagnostics

**Exact core parent:** [#2830](https://github.com/Trexzo/SpawnPK-RE/pull/2830) `3b66d4f6b070d0f8b05bf52a15b5bd22b058014b`, 689 focused candidate with Chat1 account-hardlink forensic G21285/86 and cumulative G1951–60 NPC terminal controls.

**Exact gameplay donor:** [#2833](https://github.com/Trexzo/SpawnPK-RE/pull/2833), `6ce40acdcf27ebad531c24af184f1442bf38d314`. Imports the modified `LocalLabSequentialTerminalStageTrace`, G1961/G1962 tests and source note by exact Git blob SHA. Its production preimage on this core equals the certified 687-test gameplay baseline byte-for-byte. No G1, G4/G14/G15/G16/G18, World, account, file forensic, network, respawn, storage, persistence or original reward policy production file changed. Core 689 + G1961/62 = **691 unique focused tests**, all G21285/86 preserved.

## Preserved legacy LocalLab semantics

The earlier blocking drafts [#2831](https://github.com/Trexzo/SpawnPK-RE/pull/2831) and [#2832](https://github.com/Trexzo/SpawnPK-RE/pull/2832) were closed (unmerged) after exact-head #2831 CI failed historical G1847/G1863: a newly registered WorldPlayer B of the same username is expected to complete A's active World-local G4 task and receive current-name G1/progression. Do not break this established test or imply known original SpawnPK policy. This integration only adds **observed original spawn-to-terminal player mismatch** to default-stage tracing, mirroring existing opt-in isolation evidence.

G1961 and G1962 execute actual spawned/settled NPCs with A→B same-name rebinding, B's active G15/G16/G18, one drop, one G1 receipt and five returned stages; report exactly `spawnOriginMatchedAtDispatch=false` and old/new generation evidence. Both are diagnostic-only; no grant replay or dedupe is enabled.

**Mandatory CI:** exact-head hosted **691/691**, G1847, G1961, G1962 and G21285/86 markers, Java 11 major55 and full Gradle. Superseded failed and independent donor runs do not certify this combined commit. Draft, main untouched.

**Unresolved:** authenticated account-session authority vs same-name World-local continuity, original SpawnPK kill-credit [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), and durable idempotent per-consumer grant/ACK [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587). Observing an identity mismatch never authorizes compensating awards or silent policy changes.
