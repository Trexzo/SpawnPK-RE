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
