# RSPS completion contract — playable LocalLab, not source-count parity

Status: **planning and acceptance criteria only**, 2026-11-01. This document does not certify gameplay, authorize account writes, or turn a draft PR into a release. The [existing playability campaign](https://github.com/Trexzo/SpawnPK-RE/issues/2967) and its draft `docs/playability/PLAYABLE_JOURNEYS.md` remain the detailed journey tracker.

## Definition of done

A **complete playable LocalLab RSPS** is an independently started Java server and a compatible, authorized, locally supplied graphical client in which an ordinary player can perform the journeys below end to end, observe the correct server-authoritative outcome, disconnect, and return without duplication or rollback. The server must survive restart and ordinary failure modes without declaring corrupted or stale account state current.

This is **not** a claim of 100% reproduction of original SpawnPK rules. Where production rules are unknown, implement and visibly mark a `CUSTOM_LOCALLAB` alternative; do not turn inference into `EXACT_CURRENT_CLIENT` or `LOCAL_RUNTIME_PROVEN` evidence. Do not use number of compiled classes, existence of domain APIs, a passing mock, or a green negative-only filesystem test as a completion percentage.

The release is **local/loopback-only by default**, consistent with `spk.local.Main`. Public hosting, external login, donations, and distribution are **out of scope** until an independently reviewed network/security, operations, licensing and privacy plan exists.

## Current confirmed starting point

- Mainline README pins the canonical research/runtime client at **v308** (SHA-256 `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`), while separate recovery tracks have newer clients. Never silently accept newer client bytes or assume v309/v312 protocol equivalence. Build the exact client/cache acceptance before promoting the pin.
- Mainline Java server has loopback game `127.0.0.1:43594` and auxiliary `127.0.0.1:43595` sockets, shared World, Java 11 bytecode, a JDK 21 build toolchain, and a Java 17 preferred runtime. Keep compatibility until an explicit migration has evidence.
- Gameplay drafts exercise linked PvM→loot→bank→snapshot, synthetic TCP movement/banking/equip/Eat/prayer and other survival/economy tests. Some tests explicitly create a checkpoint; they are **not** evidence that a graphical user logged out or that a crashed server committed a save.
- The P5.49–P5.54 Windows FileId and rename experiments are diagnostic. `G21.171` still rejects account publication where Java cannot verify directory identity. Native directory identifiers or an isolated test-only rename must not be reported as a safe production writer.
- Open, independent blockers: [crash durability #2540](https://github.com/Trexzo/SpawnPK-RE/issues/2540), [cross-World/JVM account ownership #2559](https://github.com/Trexzo/SpawnPK-RE/issues/2559), [Windows safe publish #3015](https://github.com/Trexzo/SpawnPK-RE/issues/3015), [actual graphical/content interfaces #427](https://github.com/Trexzo/SpawnPK-RE/issues/427), and [complete playability campaign #2967](https://github.com/Trexzo/SpawnPK-RE/issues/2967).

## Required acceptance gates

| Gate | Player-visible pass condition | Minimum evidence | Initial status |
| --- | --- | --- | --- |
| R0 Exact client/cache | Clean local client boots and negotiates expected version, login, ISAAC and asset paths; unknown SHA denied | Hash manifest, live packet capture, version-specific regression and real GUI launch | **Not release-certified for newer versions** |
| R1 Account/session | Create/login/logout/relogin same account after process restart without stale-state privilege or duplication | Real client journey, final-save write/read, negative IO failure and multi-JVM tests | **Blocked #2540/#2559/#3015** |
| R2 World traversal | HOME spawn, walk/run, collision, objects, NPC menus and target selection work in original graphical UI | Exact client GUI + packet/state trace; blocked scenery cases | **GUI proof outstanding** |
| R3 PvM/survival | NPC aggro/attack/death, server kill credit, owner drop, Take, combat gear, spells/prayer, food, health/death/respawn and save | One connected real-client journey; invalid/stale actions and reconnect | **Source/synthetic coverage; GUI proof outstanding** |
| R4 Economy/progression | Inventory/bank/shop buy/sell, currency/stock atomicity, skills/XP, Slayer, bosses, rewards/objectives, pets and content progression | Normal + duplicate/retry + rollback/abort + restart tests, explicit custom-versus-recovered authority | **Partial foundations; full journeys outstanding** |
| R5 Multiplayer | Two real clients can see each other, chat, duel/PvP, trade safely, interact with clans/parties/instances and rejoin | Concurrent real clients, settlement once, adversarial disconnects and independent World isolation | **Not certified** |
| R6 Persistence/failure | No successful-ACK after failed final save, no stale overwrite from parallel worlds/JVMs, safe Windows and Linux publication | Crash cutpoints, fsync/directory durability, handle-safe no-follow paths, cross-process locks, backup/restore drill | **Blocked #2540/#2559/#3015** |
| R7 Delivery/ops | Fresh machine can set up locally, build, boot, shut down gracefully, recover backup, inspect errors and pass smoke suite | Reproducible clean install + exact-head CI + release manifest/hashes + human graphical sign-off | **Not certified** |

No gate can be marked complete solely on a synthetic client, a test that accepts only a safe refusal, or a custom `PlayerSnapshotCodec` checkpoint. Preserve separate columns in test reports for **source-domain**, **synthetic TCP**, **real graphical client**, **cross-process**, **crash/power-loss**, **Linux**, and **Windows positive World-save**.

## Delivery order (vertical slices, not subsystems in isolation)

1. **Pin one compatible client and get a complete real GUI loop:** boot → account login → HOME walk → NPC kill → owned drop → Take → bank → logout → restart → identical inventory/coins/kills. Start with existing P1/P2 source and socket tests, add a real graphical reproduction; do not claim completion from mock packets.
2. **Make account state trustworthy:** resolve Windows positive save without dropping ancestry/anti-symlink constraints; then cross-JVM ownership/epoch fencing and crash-durable negative intent/recovery. Avoid attaching more valuable gameplay to known stale-save paths.
3. **Complete a small enjoyable PvM loop:** equipment, food, prayer, magic, death/respawn, XP, drops and a few bosses, all wired through actual widgets and persisted. Prioritize coherent behavior over broad stub catalogues.
4. **Complete economy and objectives:** bank, trade/shop settlement, finite stock, buy/sell, skill and task progression, Slayer, achievements and repeatable activities. Every grant/settlement must have exactly-once or explicit idempotent semantics.
5. **Complete multiplayer:** visibility, social, PvP/duel/trade, parties/clans, instances/raids and hostile concurrency/disconnect tests.
6. **Package a reproducible local release:** required external client/cache manifest, setup automation, integrity checks, backup/restore, error reporting and verified graphical acceptance on both supported platforms.

Do not merge unrelated experimental Windows native filesystem API research into gameplay branches or silently enable normal World saving based on a capability probe. Keep those drafts isolated and preserve `G21.171` until positive production-path proof exists.

## Integration architecture and dependencies

Retain the repository's typed boundary: socket decoder → validated semantic request → single World/command owner → domain service → response/presentation adapter. Use version-specific packet evidence at the boundary, not packet IDs embedded throughout gameplay. Keep all player economy mutations authoritative server-side; send acknowledgments only after the appropriate durable/atomic cutpoint. Inventory, dropped objects, mail, trades, and bank cannot each be independently allowed to grant the same item.

External projects are **references**, not drop-in replacements:

- [OpenRS2](https://github.com/openrs2/openrs2) provides open source server/cache tooling, but its documented server targets RuneScape build 550, **not this SpawnPK protocol**. Its own instructions keep nonfree client/cache/key material separate.
- [OpenRS2 Archive/API](https://archive.openrs2.org/api) documents JS5 cache/archive and XTEA key formats. Cache metadata and public tooling can inform validation, but access to an archive does not grant distribution rights.
- [RuneLite](https://github.com/runelite/runelite) exposes useful open-source client/cache APIs under a BSD 2-Clause license; it is an OSRS client, not evidence that the SpawnPK login, UI or gameplay protocol matches.
- [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) and [Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) provide guidance if expanding beyond isolated local accounts (secure storage, rate limiting, auth sessions and server-side expiry).
- [Oracle Java release support](https://www.oracle.com/java/technologies/java-se-support-roadmap.html) informs future Java upgrade decisions. Do not change the proven Java 11 bytecode or JDK 17 runtime contract just because a newer LTS exists.

**Rights boundary:** [Jagex's Terms](https://legal.jagex.com/docs/terms/terms-and-conditions) and [Fan Content Policy](https://legal.jagex.com/docs/policies/fan-content-policy) restrict private servers and reuse of protected client/game assets. Do not commit or redistribute proprietary client JARs, cache, game content, logos or credentials without appropriate rights; verify legal permissions before any public deployment. The LocalLab documentation is not a license or authorization.

## Suggested PR evidence template

For each completion claim, record:

- Exactly pinned source commit, client JAR SHA-256, cache hash/revision and OS/JVM.
- Player action sequence with actual UI steps, decoded wire packets and server-owned state transitions.
- Original-rule provenance (`EXACT_CURRENT_CLIENT`, `LOCAL_RUNTIME_PROVEN`, `CUSTOM_LOCALLAB`, etc.).
- Positive outcome, invalid/duplicate/replayed packet denials, concurrent attempts and storage failure behavior.
- Persisted account postimage and independent World restart/relogin result.
- Real graphical sign-off separately from synthetic TCP tests; CI job URL and whether it executed the *positive* production code path.
- Known untested cases/blockers. Label the PR draft while they matter.

**Release rule:** all R0–R7 are required. Any unknown or negative-only proof is **NOT READY**, regardless of how many focused tests are green. The existing P5.xx snapshots remain valuable evidence but are not a substitute for this complete acceptance.
