# Chat 1 / G21.174 — real session World-mutation semantic account roundtrip

Certified parent G21.173 [Draft PR #2529](https://github.com/Trexzo/SpawnPK-RE/pull/2529), exact `63076d8b1d5c95c4033f9e0dc1b7d8440f38b6aa`, [Actions #38453859951](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38453859951) Java11 major55, 473/473 focused and full Gradle SUCCESS. Coordinating with Chat 2 through [issue #2526](https://github.com/Trexzo/SpawnPK-RE/issues/2526). New issue #2531.

## Source-derived gap

G21.173 proves two real synthetic revision317 TCP/ISAAC/socket sessions against two complete World instances, with a durable account file *present*. It does NOT assert that a specific non-default player property changed during the first World-owned session appears on the second WorldPlayer. A broken save/hydrate flow restoring default values could pass.

## Stronger real-socket acceptance

G21.174 reuses G21.173's exact LocalSession/ISAAC/widget912 `OneSession` harness (changed from private nested class to package-only for tests; no production behavior changes). The first connected player is mutated via **World.submit(player, expectedGeneration, action)** (WorldPulse-owned), writing harmless `extension.core_reconnect.phase=FIRST_WORLD_MUTATION`; disconnect triggers actual final-save, which must persist EXACT first value. Close the entire original World and ensure it refuses stale player commands. New isolated World, new actual socket login to the canonical saved account: freshly hydrated **in-memory WorldPlayer** MUST contain FIRST value, not defaults. World-owned command updates it to SECOND_WORLD_MUTATION; second disconnect/final save must write exact SECOND to disk. Both worlds tick independently; no accidental `src` account, leaks or unowned writes.

This is stronger than mere snapshot-existence proof but deliberately not a GUI original client test, gameplay completeness claim, cross-file atomic crash epoch or permission for Mailbox settlement. It does not alter `World`, `WorldPlayerPersistence`, `LocalSession`, snapshot encoding or the historical raw save behavior. Acceptance: Java 11 major55, focused **474/474**, dedicated Gradle regression and exact-head full hosted Gradle; Draft/unmerged.

No settlement, grant, replay, cleanup, rollover, pruning, unlink, release or ACK; frozen R25 #1847/G21.87/88 and Chat 2 gameplay ownership unchanged.
