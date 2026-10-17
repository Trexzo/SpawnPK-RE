# Chat1 G21.203 + Chat2 G18.6.4 cumulative — autosave owner witness and valid G4 World scope

## Exact lineage

Certified parent: [G21.202/G18.6.3 #2636](https://github.com/Trexzo/SpawnPK-RE/pull/2636) exact SHA `ec0fe92516acf6aa1d740f643a0f49769baf812a`, hosted Actions #38714817951 SUCCESS (509 focused, Java11/full Gradle). G21.203 [Draft #2638](https://github.com/Trexzo/SpawnPK-RE/pull/2638), SHA `d65d04f30326874b54ab6775e9f90fc76605d19d`, introduces checkpoint capture-time opt-in JVM Token identity into `PendingCheckpoint`, coalesced worker pre-publication check and an actual TCP/World checkpoint regression. Core focused **509→510**, its own hosted CI must be green.

Chat2 donor [G18.6.4 cumulative #2637](https://github.com/Trexzo/SpawnPK-RE/pull/2637) exact SHA `bec6a157fd6b4b62d11267683afaec87871ad17f`, on the SAME certified #2636 ancestor. Imports exact existing G1847 test Git blob `7505d614268f9a6c4df874dc5c0bd22b31e7be3a`, donor G18.6.4 note blob `7f86727005c4446c5c5ae1f5e344028a0b26383d` and G21.202/G18.6.4 coordination note blob `9137c8267cac27ae4f88c05e63b5f0f1422781c7`. Donor's own CI is pending at composition. This joint branch adds only those three imported files and this note vs Chat1 #2638: no additional production/Gradle changes, manifest remains **510 unique**.

## Non-overlapping evidence

Chat1 G21.203: World tick-owned autosave snapshot captures exact original in-JVM lease/token alongside player/generation, retained as one object through checkpoint coalescing and checked in FilePlayerRepository checkpoint write. Test requires a **real World TCP session** with `checkpointDue(100)` to increase checkpointWritten and original Token guard counter, persist checkpoint-only value and later force a healthy final postimage with a distinct value; G21.190 ACTIVE remains unretired. This is not durable cross-process account fencing; previously unleased G21.184 unsafe late-toggle remains unchanged. Strict/mailbox/terminal, raw writers, cross-JVM crash and ACTIVE retirement not covered.

Chat2 G18.6.4: in an existing World, a codec-valid older G4 ACTIVE snapshot collides with an already completed name-keyed World task and is refused; in a separate new isolated World the same exact bytes restore cleanly and can be completed via the installed real NPC terminal observer. This is private LocalLab in-memory fixture, not original SpawnPK Slayer authority, file account read, TCP relog, automatic recovery or exploit.

## Acceptance

Diff compared to #2638 exactly **4 files**: permanent G1847 test overlay, 2 original Chat2 docs, and this coordination note. Fresh exact-head hosted CI required: G21203, G1864/G1863, G21184, **510/510 focused**, Java11 major55, full Gradle. Core #2638 and Chat2 #2637 must also pass their own independent runs. All PRs DRAFT/unmerged, defaults OFF, no main or client changes, grants/replay/repair, raw-writer proof, machine power-loss or ACTIVE marker retirement. Chat1 #2540/#2559 and Chat2 #2587 stay OPEN.