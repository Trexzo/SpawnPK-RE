# G18.8.4 — verify real installed terminal-isolation property sampling

## Evidence and base

- Base: CI-GREEN opt-in production gameplay donor [#2691](https://github.com/Trexzo/SpawnPK-RE/pull/2691), exact `ddb12ddfddac44ca78f2eba7264e9327fdf0f39e`, [Actions #38764607384](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38764607384) SUCCESS, **540 focused, Java11 major55/full Gradle**.
- The opt-in `-Dspk.locallab.monsterSpawner.isolateTerminalStages=true` is read at `LocalLabMonsterSpawnerProvisioning.create(World)` composition time. Earlier tests G1878–81 exercised the package-private injected diagnostic overload, not this actual public-facing property path.
- New one-class **test-only** G1884 plus doc/Gradle manifest **540→541**, no production source, client, account, original gameplay policy, R25 or main changes. Own exact-head CI remains mandatory.

## G1884 test matrix (two genuine independent isolated Worlds)

| Property before actual `create(World)` | Property flipped AFTER composition | Real NPC and invalid test-only G14 | G15/G16/G18 progress |
| --- | --- | --- | --- |
| clear (default OFF) | changed to `true` | first genuine NPC killed and ground-settled; G1 record1; G14 throws in old sequential callback | each stays **0** |
| `true` (explicit opt-in) | cleared | second genuine NPC in separate World killed and ground-settled; G1 record1; G14 throws but isolated | each becomes **1** |

Both cases activate real G15 Task Scroll, G16 Adventure, G18 Daily Money Making before injecting the malformed in-memory G14 namespace, then use the normal installed observer from **`create(World)`**, normal spawner session, actual `applyDamage` and `finalizeIfOwned` (not a synthetic callback). Both verify one real G1 receipt/ground coin, terminal entry removed, invalid G14 persists and duplicate finalization returns NOT_OWNED. System property is restored in `finally`, and each World is closed. The test's own JavaExec launches a separate JVM, avoiding global process interference with sibling focused tests.

**Marker:** `G1884_TERMINAL_ISOLATION_PROPERTY_COMPOSITION_SNAPSHOT_PASS`.

## Nonclaims / following gates

- This proves composition-time property semantics for a single runtime, not dynamic runtime hot reload or durable state.
- **No replay**, no consumer outbox, no durable `RETURNED` receipt and no original SpawnPK server kill/award rule. Broken G14 remains broken; only later consumers may continue when explicitly opted in.
- Fresh **exact-head hosted Actions SUCCESS**, 541/541 focused/Java11 major55/full Gradle and G1884 marker required before certification. Keep DRAFT/unmerged.
- Original source/kill authority issue [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), durable per-consumer outbox [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) remain open.
