# G19.0.1–G19.0.2 — accepted attacker witness on canonical NPC click (not original kill credit)

## Authority and exact parent
- Based on [G1899/1900 #2742](https://github.com/Trexzo/SpawnPK-RE/pull/2742) source SHA `4880ec7540cb8b500853b517bcc7b9747f8cc775`, **587 focused**, own hosted CI was pending at initial composition. Underlying independently certified [G21.243 #2740](https://github.com/Trexzo/SpawnPK-RE/pull/2740) SHA `3b45e51b1f40db50d8a782b69ab96ff4982b3c7b`, [Actions #38786775465](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38786775465) GREEN 585/585 Java11/full Gradle.
- This change is `CUSTOM_LOCALLAB_ACCEPTED_CANONICAL_NPC_HIT_WITNESS`, **not** a claim of original SpawnPK attacker reward rules. No loot, XP, G1/G4/G14/G15/G16/G18, money, packet opcode, client, prior server protocol or account writer policy is changed.

## Important distinction discovered in actual source

The earlier anonymous lifecycle source audit G1899/G1900 was correct *only for that API*: `NpcLifecycleService.applyDamage(npcId,amount,tick)` accepts **no player identity**, and its `DamageResult.sourceAuthority` is NPC registration/lifecycle authority. However, there is ALSO an explicit exact-player combat path:
- `LocalCanonicalNpcAttackHandler.handle(NpcAction option2, NpcEntity, ServerPacketWriter)` validates the exact `WorldPlayer` plus `generationSupplier`, scene/definition/canonical NPC identity, legal attack range, tick cadence and packet-65 hitmask representability.
- `NpcCombatResolutionService.resolveImmediateOwnedAdmitted(world,expectedAttackerGeneration,target,weapon,style,tick,admission)` checks exact attacker/target ownership inside the immediate-damage admission path and returns a typed `Result.attackerId`, `targetId`, `lifecycle`, `timing` and `damage`.
- The existing `LocalCanonicalNpcAttackHandler` only exposed damage/status/tick-equivalent data to its caller; a successful click's exact **validated attacker WorldPlayer/generation** was not retained in its `Result`. Moreover its `LethalFinalizer` callback receives only `WorldNpc target`, so Monster Spawner finalization **does not automatically receive** this attacker witness or any contributor history. This is precisely why spawn recipient and later terminal-time name lookup are not original kill-credit proof.

## Narrow production change

Single production file `LocalCanonicalNpcAttackHandler.java`:
- Add nested immutable package-visible `AcceptedHitWitness` with actual producing `World` identity, exact `WorldPlayer attacker` object, captured expected generation, `attackerId` as returned by verified `NpcCombatResolutionService.Result`, `targetNpcId`, world attack tick, applied immediate damage and newly-died flag.
- Attacker ID must equal `attacker.id()`; generation/tick/damage must be nonnegative; target ID/damage/death bit must match enclosing `LocalCanonicalNpcAttackHandler.Result`. The helper `stillCurrentIn(World)` tests only exact same World object and current owner/generation registry; it is **not** proof of entitlement when stale.
- Only `Status.HIT` carries `acceptedHit`; all `STALE_PLAYER`, `OUT_OF_RANGE`, `STALE_CANONICAL`, `TARGET_DEAD`, `CADENCE_BLOCKED`, dummy or other rejected requests return `acceptedHit==null`. All previous status constructors and networking/packet presentation are unchanged.
- Witness is built from the **existing successfully admitted `hit.attackerId` and `hit.targetId`** after actual damage and packet presentation, not from a terminal-time byName lookup. The witness is **read-only**, exposed only on the returned Java handler result; it is not persisted, handed to Monster Spawner finalization or used for consumer reward changes.
- If an already-performed hit's later packet/finalizer throws before returning the handler result, the caller may not receive this witness. Therefore this addition is NOT a durable atomic damage+attacker receipt and cannot resolve crash/retry ambiguity.

## Real packet-click regression tests

- **G1901:** one real WorldPlayer A creates an attackable canonical WorldNpc with 20 HP, packet option 2 opcode72 attack from adjacent tile, actual packet-65 mask and immediate lifecycle damage10, immutable `AcceptedHitWitness` A/player/generation/World/canonical target. A unregisters, new B same normalized username registers; stale A click returns `STALE_PLAYER` with no witness or HP mutation/packet. B's real option2 click to SAME NPC lethally damages remaining 10 HP; its accepted witness records exact B, not the stale A object; old A witness remains A/stale, B current. No reward callback or inference of original kill credit. Marker `G1901_ACCEPTED_CANONICAL_HIT_EXACT_PLAYER_WITNESS_PASS`.
- **G1902:** same name registered simultaneously in TWO actual different World objects; independent canonical WorldNpc(20HP) and NpcRegistry/viewer per World. Successful option2 A hit produces evidence locked to World A only, B remains untouched. B moves out of legal range: packet2 request rejected `OUT_OF_RANGE`, no accepted witness, no hit packet or cadence/HP mutation. B moves in range and its own genuine click gets World B/player B witness, preserving both independent NPC HP and excluding use of A witness as B evidence. Marker `G1902_ACCEPTED_CANONICAL_HIT_TWO_WORLD_REJECTION_PASS`.

Two added focused tests and dedicated Gradle tasks, both hardguards **587→589 unique**, one production file and this exact scope note. All inherited Chat1 G21.243 and G1891–1900 tests retained.

## Nonclaims and next source recovery

- `attackerId` in an admitted immediate LocalLab click is **meaningfully stronger evidence** than a mere spawn recipient or anonymous lifecycle damage, but does **not prove historical SpawnPK's last-hit, most-damage, assist, PvM owner or loot attribution**.
- No per-NPC multi-hit contributor ledger, delayed projectile handling, attack-to-MonsterSpawner terminal evidence publication, authenticated server request origin from a live original client, or persistence exists in this patch. `NpcCombatResolutionService.resolveOwned` can create **scheduled hits** whose attribution requires separate exact resolution-time provenance and tests.
- Original kill policy [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558), durable per-consumer ACK [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587), DMM lifetime [#2547](https://github.com/Trexzo/SpawnPK-RE/issues/2547), account writer epoch #2540/#2559 all remain OPEN.
- Require fresh **own exact-head hosted GitHub Actions** Java11 major55/full Gradle **589/589 focused**, G1901/G1902 PASS; inherited G1899/G1900 and G21.243 green. DRAFT/UNMERGED, main untouched.
