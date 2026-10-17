# Chat 2 — current-generation provenance and concurrent attempt fences (G2021–24)

Parent: [PR #2913](https://github.com/Trexzo/SpawnPK-RE/pull/2913), exact `70ffcd6ddfef51c122d2e326d90869a0713040a6` (817 focused entries; parent's exact-head CI must be checked independently). Draft-only CUSTOM_LOCALLAB experimental terminal attempt fence, **UNWIRED NON-GRANTING**. No changes to account persistence, live observer callbacks, drop settlement, or reward allocation.

## Correctness fix
`LocalLabSettledDeathEnvelope.observe` already classifies origin and records `observedCurrentGeneration`. Prior attempt reservations compared status and *spawn* generation but ignored the **current** generation. Two distinct occupants of the same username could both be `SAME_NAME_REBOUND`, letting stage G1 reserve against occupant A while stage G4 reserves against occupant B for the same death. The attempt fence now pins the observed current generation to each reservation and includes it in the conflict gate. Its immutable diagnostic entry records the pinned generation. This is evidence consistency only, NOT authorization to award either occupant.

## Stronger existing focused gates (manifest remains 817)
- G2021 executes the real canonical G2017 fixture, then retires the spawn recipient and creates two different same-name replacement WorldPlayers. Both observations have status SAME_NAME_REBOUND but different generations. The first token must remain valid for diagnostics, while a new consumer attempt on the second generation must be refused as conflicting evidence. Exactly one ground receipt and no reward replay.
- G2022 executes the foreign-world G2018 controls and verifies that two different consumer stages *are* allowed on the same stable generation. Checks detached snapshot and exception class-only output.
- G2023 executes G2019 and races 12 threads reserving G1 for the **same real settled NPC death**. Exactly one reservation succeeds; 11 duplicate refusals are retained, with no additional drop.
- G2024 executes the 43-death capacity G2020 control, then races two reservations per stage across all six consumers for another real settled death. Exactly six distinct reservations and six duplicate refusals, with 5 RETURNED_UNACKNOWLEDGED and 1 THREW_MUTATION_UNCERTAIN.

All four existing JavaExec task names and critical retention anchors remain intact, so the **817 focused manifest entry count is unchanged** but coverage is materially strengthened.

## Explicit safety and CI gate
No historical SpawnPK server reward policy is claimed. This experiment has no persistent event identity, owner epoch, durable outbox, consumer mutation+ACK atomicity, safe replay or restart recovery. Preserve the #2587 blocker. Certify only on exact-head hosted 817/817 focused suite, G2021–24 markers, Java 11 major-55 and full Gradle. No merge to main without cross-chat review.
## CI correction — 17 October
The first exact-head job failed at G2021 when assuming two newly instantiated WorldPlayers must have different numeric generations. They do not: generation numbers are not a globally unique occupant identity. Corrected the **source** to carry the exact in-process observed WorldPlayer reference from the settled envelope into the attempt token; the conflict check compares both reference identity and generation. The test now asserts object identity rather than assuming monotonically increasing generation. The historical failing run must not be counted as a pass. This still supplies no cross-restart identity or reward authority.
