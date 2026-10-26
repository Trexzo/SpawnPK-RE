# Playability: complete gameplay journeys

Goal: make SpawnPK-RE a genuinely playable LocalLab. This file tracks functional acceptance, not a percentage of fidelity to the original SpawnPK server. Never equate source-domain tests, synthetic TCP tests, exact client packet evidence and real-client graphical acceptance.

## First integrated journey — P1

P1 implements a mandatory server Gradle smoke: Monster Spawner session -> canonical NPC attack 72 -> credited NPC death -> owner-only 995x1 drop -> actual Take -> duplicate-Take refusal -> BankState packet-producing Deposit Inventory -> PlayerSnapshotCodec checkpoint -> new World restore of bank balance and kill count.

The test checks the **delta** against the stock 100,000-coin bank preimage; it does not confuse starter coins with earned loot. The restore uses an explicit source-controlled checkpoint, not a successful LocalSession final-save, nor crash-safe atomic cross-World account ownership. It does not test the original graphical client.

## Coverage and next gates

| Playable journey | Current source foundation | Next acceptance needed |
| --- | --- | --- |
| Start/login/reconnect | LocalSession synthetic TCP and World session tests | Original client GUI, durable failed-final-save recovery (#2540), World ownership (#2559) |
| HOME walking and obstacle interaction | movement, collision, client Walk-here fallback | Real v308 human board/minimap acceptance (#1106/#1113) |
| PvM, loot and banking | G1 PvM loop, BankRuntimeIntegrationTest and P1 linked smoke | One real client journey with network attack/take/bank and logout/reconnect |
| Equipment, prayer, magic, food | domain mechanics and protocol adapters | One encounter: equip/consume/pray/cast and persist |
| Shops and economy | item, shop, transaction services | Buy/sell/stock with atomic settlement and reconnect |
| Slayer, bosses, achievements, Adventures | spawner, objectives and adventure systems | Complete tasks, exact UI, reward cycles and provenance (#427) |
| PvP, duel and player death | combat and duel foundations | Real client duel handshake, battle, drops, respawn, account persistence |
| Raids and groups | party, instance and matchmaking foundations | Group entrance, phase/wave completion and settlement |
| Quests, events, tournaments | progression and event foundations | Client entry/selection, timer and reward completion |
| Pets and construction | existing pet, NPC and construction systems | Full UI, object ownership, deploy/store and reconnect |
| Social and clans | packet/domain foundations | Concurrent client sessions and durable state |
| UI/assets and deployment | v308 client protocol, release scripts and models | Exact graphical acceptance (#9/#427), clean-head cumulative release (#1005) |

## Delivery order

P1 source journey; P2 same-account network journey plus real graphical acceptance; P3 equipment/prayer/magic and consumables; P4 shops/bosses/Slayer/reward loops; P5 multiplayer/PvP/raids; P6 interface, content and release completeness. All release candidates need end-to-end actual-client tests and honest CUSTOM_LOCALLAB labeling when original SpawnPK formulas or policy are unknown.

Project coordination: #2526. Gameplay drop-observer retry #2587, kill-credit origin #2558, Daily Money Making lifetime #2547. Account-safety #2540/#2559 remain independent blockers; no replay, cleanup, release, owner handoff or final-save ACK is added here.


## Second journey — P2 TCP bank/checkpoint candidate

P2 is a separate test-only integration built from the exact server packet flow in BankRuntimeIntegrationTest. One LocalSession on a real isolated World accepts the synthetic revision-317 localhost login, walk packet 164, object packet 132, bank overlay S2C248/53, a withdraw then store then second withdraw through opcode 145, and an explicit bank close before real socket logout. It requires LocalSession completion (no ignored Future timeout), then starts a separate fresh World and validates saved bank 995x99,999 and inventory 995x1. This is more than a no-net-mutation bank UI replay and checks actual final-save state from the transport session, but still uses synthetic wire data and does not prove failed-final-save crash recovery, original GUI or cross-JVM ownership.

Do not mark P2 certified until exact-head Linux Gradle and Windows source-only checks pass on this additional commit.


## P3 — real TCP weapon Wield and reconnect candidate

This connected equipment test reuses the existing native bank withdraw and Wield opcode-41 paths, ensures the old Bloodrend item returns to inventory, verifies appearance81 and continued movement, waits for LocalSession final save, then starts a fresh World with the persisted weapon and inventory. No actual original client or failed-final-save certification is claimed. P3 remains unverified until hosted exact-head CI.


## P4 — shop economy and fresh World candidate

Connected CUSTOM_LOCALLAB service path: acquire 100 coins in an isolated test player's inventory, buy two Rocktails at the explicitly custom ten-coin rate, sell one back for four coins, rebuy one for ten, require independently committed transactions and finite stock delta, then write the actual snapshot through FilePlayerRepository. An entirely fresh World rehydrates 74 inventory coins, two Rocktails and the unchanged 100,000 bank coins. This does not prove original SpawnPK prices, real client shop controls, permanent shared market stock across restart, crash recovery or economic anti-dupe in multi-JVM production.

## P5 — real Eat/health/reconnect candidate

The G1 Rocktail CUSTOM_LOCALLAB inventory-action handler (opcode 122) is exercised with two nonstackable food items: 50→60 HP, rejected premature cooldown, eligible second 60→70 HP, rejected duplicate stale-slot Eat, and no accidental bank stock consumption. After a real file-backed source checkpoint, the test boots an entirely new World and requires the healed HP and depleted inventory to remain exact. Source-only integration; it does not certify actual graphical UI, original heal amount, death/revival, failure durability, or production server concurrency.


## P5.1 — TCP Rocktail Eat and healthy LocalSession logout

Stacked playability draft branch playability/p5-1-rocktail-tcp. The integration pre-seeds a source-owned account at 50 current HP, logs in through actual LocalSession loopback TCP, withdraws a Rocktail from bank slot 3, closes the overlay, and sends exact pinned-v308 inventory option opcode 122. It checks normal inventory 53, skill 134 HP 60, stale Eat replay and ISAAC alignment via a later walk packet. The real healthy LocalSession logout must restore HP 60, zero carried Rocktails, and 19 bank Rocktails in a fresh World. This is synthetic TCP input, NOT the original graphical client. Healing numbers remain CUSTOM_LOCALLAB; healthy session persistence is NOT power-loss recovery or cross-JVM ownership proof. Do not mark P5.1 CI-certified until the new exact-head Linux Gradle and Windows source-only jobs finish successfully.


## P5.2 — exact normal Prayer widget TCP acceptance

The dedicated stacked draft `playability/p5-2-prayer-tcp` tests actual LocalSession synthetic TCP opcode 185 prayer-widget clicks at a file-restored prayer level of 10. It proves normal Thick Skin config 83 on/off/on, denied Piety config 608 at insufficient level, continued ISAAC alignment through a movement request, and ordinary healthy logout to an independently restored World. The shared bootstrap verifier now optionally accepts a distinct persisted current Prayer level while preserving its previous API. Prayer widget activation is currently ephemeral on World restart; this does **not** establish original SpawnPK persistence rules. No prayer point drain, combat damage modifiers, magic projectile effects, original graphical client, cross-JVM account ownership or crash-durable final save is claimed. Maintain draft until Windows and Linux exact-head checks pass and the P5.1 parent is certified.


## P5.3 — survival damage, food, death and respawn

Draft branch `playability/p5-3-survival-respawn` adds a mandatory source-domain connected journey: canonical lifecycle damage takes 99 -> 50 HP; Rocktail Eat heals 50 -> 60 and consumes one; lethal damage kills at tick 20; dead Eat and duplicate damage must have zero effect; tick 24 cannot respawn; tick 25 revives at HOME and 99 HP; a further hit and Eat recover 79 -> 89; repeated stale slot Eat is rejected; unrelated bank stock stays unchanged; an explicit healthy account-file checkpoint restores 89 HP and one remaining carried Rocktail in a new World. This is **source-domain integration**, deliberately not a real client interaction, NPC death, full death-item settlement, death UI, or TCP final-save transaction. The five-tick delay, healing and respawn HP are `CUSTOM_LOCALLAB`, not original SpawnPK rules. Death loot/PK death policy retains separate authority; #2540/#2559 crash and cross-World ownership issues remain blockers. Keep draft and preserve failed evidence until exact-head CI passes on Linux and Windows.


## P5.4 — Quick Prayer ON actual widget 5000 (CUSTOM_LOCALLAB)

Draft branch `playability/p5-4-quick-prayer-on` adds the previously missing server-side Quick Prayer ON action. A confirmed current-book selection is mapped only through visible exact-client prayer definitions. The LOCAL_LAB_POLICY_G123 action fails closed on empty selection, open editing draft, dead player, zero points, missing definition, or any selected prayer above the player's level. All outgoing current-book config36 ON values and the recovered `QUICK_PRAYERS_ON` application-control token are admitted as one queue-only recoverable packet batch; in-memory activation flags change only after successful admission. A full queue permits zero-byte retry; nonretractable direct writers are refused. Repeated ON is idempotent. Existing OFF 4999 and Quick selector 5001 remain separate. New mandatory `quickPrayerOnIntegrationRegression` exercises widget 5000, mixed low-level selections, editing, packet parity, repeated ON, OFF, backpressure and direct-writer denial. This is not an original v308 graphical UI acceptance, prayer-conflict resolution, drain, combat effect, account persistence, socket ACK or crash-durable save. Do not mark this production-visible behavior ready until exact-head Linux/Windows tests and original graphical acceptance.


## P5.5 — Quick Prayer 5001→17202→17241→5000→4999 TCP roundtrip

Stacked draft `playability/p5-5-quick-prayer-tcp` extends P5.4 with actual loopback LocalSession synthetic pinned-v308 opcode185 requests: open Quick Prayer selection 5001 (S2C97 NORMAL root), choose Thick Skin via 17202, confirm via 17241, press ON 5000 (config36 varp83=1 and exact S2C126 `QUICK_PRAYERS_ON`), repeat ON idempotently (control token only), press OFF 4999 (config36 varp83=0 and exact `QUICK_PRAYERS_OFF`), press ON again and send a walking opcode164 to verify continued ISAAC alignment. A healthy file-backed disconnect and fresh World restore checks Prayer level 10 and existing account bank, while not attributing persistence to selector/activation state. This tests synthetic TCP only, not original graphical v308 UI, real prayer effects, original 5000 production policy, a socket ACK for atomic packet queueing, nor crash-durable account recovery. Do not certify until exact-head Linux and Windows CI are green.


## P5.6 — dead-caster targeted spell admission guard

Draft `playability/p5-6-dead-caster-fence` replaces the production `LocalSession` targeted-spell handler's unrelated-state composition with an exact `WorldPlayer` binding. It checks canonical lifecycle DEAD and zero current Hitpoints before calling MagicState target validation or dispatching spell effects; rejects target-player and target-NPC requests without emitting packets or changing MagicState.last; restores ordinary routing after the established five-tick CUSTOM_LOCALLAB respawn. `playableDeadCasterSpellFenceSmoke` is a mandatory source gameplay regression. Existing source-only legacy six-argument constructor is retained for isolated call sites, but live LocalSession is owner-bound. This is NOT a proof of fully atomic cast-vs-death concurrency, original-v308 UI acceptance, rune consumption, player/NPC spell damage, prayer-protection rules or crash-durable account safety. No false effect claims are added to normal spells. Keep draft until exact-head Linux and Windows CI pass and stronger combat/ownership models are reviewed.


## P5.7 — direct spells and Home Teleport blocked while dead

Draft branch `playability/p5-7-dead-direct-spell-fence` binds the production `LocalGameplayWidgetHandler` to the canonical session `WorldPlayer`. Direct (non-targeted) spell widget requests are rejected before `MagicState.direct` when the player is DEAD or has zero Hitpoints; accepted Home Teleport is always reset at the beginning of each request. Legacy state-composition constructor is retained for isolated tests, but the actual `LocalSession` now uses the owner-bound constructor. Mandatory `playableDeadDirectSpellFenceSmoke` covers Home Teleport widgets 1195, 12856, 30000, resource-requiring direct spell 1159, no packet or MagicState.last mutation while dead, denial before tick25, restored behavior at respawn, and zero-HP ALIVE inconsistency. Other widget routes still fall through. **CUSTOM_LOCALLAB** lifecycle only: no claim of original client GUI, concurrent cast/death atomicity, rune consumption, server-original teleport policy or durable account final save. Stay draft until both exact-HEAD CI platforms pass.
