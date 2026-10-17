# Chat 1 G21.198 + Chat 2 G18.5.9 — exact cumulative candidate (505 focused)

## Exact source

Core G21.198 [Draft #2620](https://github.com/Trexzo/SpawnPK-RE/pull/2620), exact parent SHA `fd47187f0dafcaeec52bb5463eb9b64216e300bb`, stacked on certified G21.197 [Draft #2618](https://github.com/Trexzo/SpawnPK-RE/pull/2618), SHA `c9adeff430cf467f0e8b575ac6f6d76aec1c7d4e` / hosted Actions #38597268652 SUCCESS. G21.198 adds standalone TEST-ONLY untrusted owner-epoch record negative, focused manifest 504→505. It does not change any existing production Java.

Chat 2 donor [G18.5.9 #2619](https://github.com/Trexzo/SpawnPK-RE/pull/2619) exact SHA `e3fe46182ffbde402482d20c564ff5c2a5f842dc`, on earlier certified G21.196/G18.5.8 parent, with extra G18.5.9 installed observer failure test and source note. Imported existing `G1847MonsterSpawnerNameReuseTerminalAttributionTest.java` **exact donor Git blob `765f92c4701e107bcd8fcd199cc57de394d52967`** and original G18.5.9 handoff note **exact blob `6c1566db155787d4918bff443c49225331f9047f`**. Do not additionally re-import G18.5.8 or earlier tests.

## Orthogonal negative claims

G21.198: unused G21.196 epoch reservation/binding structural parser accepts caller-manufactured matching records without a real owner or forced write; full pair does NOT seal on-disk account image and cannot bypass separately forced ACTIVE review. Test-only private filesystem mutations; not a public exploit or real session authority. No production wiring, retirement, grants, recovery or process-level power loss.

G18.5.9: actual installed LocalLab Monster Spawner terminal observer delivers G1, then a controlled TEST-ONLY corrupt G4 active-task/mode mismatch throws, causing later G18 DMM consumer not to execute. G1 receipt/one ground settlement/real respawn still happen; terminal returns FINALIZED and cannot be retried. Restoring the exact original G4 mode does not replay missed objective progression. Does not establish historical SpawnPK reward semantics or a public reachability claim.

## Diff and gate

Exactly **3 changed files relative to G21.198**: one existing Chat2 regression overlay, Chat2 donor handoff, this integration note. ZERO production source or Gradle changes; focused manifest stays **505 unique** from Chat1 G21.198. Both source branches need their own exact-head CI, and this joint tree requires a fresh separate job: markers `G21198_EPOCH_PROTOTYPE_UNTRUSTED_RECORD_PASS` and `G1859_INSTALLED_G4_EARLY_FAILURE_BLOCKS_G18_PASS`, Java11 bytecode major55, 505/505 focused and full Gradle. Keep DRAFT/unmerged until exact-head success. No main, client, runtime flags, replay/reward/retirement or uncooperative writer policy changes.

Account design #2540/#2559 and independent gameplay outbox #2587 remain OPEN.