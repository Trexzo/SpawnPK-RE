# SpawnPK LocalLab v5.18.5 — implementation exhaustion status

## What R8.5 closes

- Exact pinned current client SHA-256 remains `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`.
- S2C250 application multiplexer: **43/43 subtypes operation-level decoded**.
  - R4 operation rows: 66.
  - R5 operation rows: 69.
  - R6 remaining operation rows: 64.
- Typed S2C250 LocalLab publishers cover the recoverable presentation/state-controller surface.
- Subtype 20 is statically understood but deliberately has no LocalLab emitter because the exact client action starts an external TCP receiver. LocalLab remains loopback-only.
- The previous eight generic C2S interaction packets that were framing-only are now exact decoded/normalized:
  `14, 25, 70, 176, 192, 228, 234, 252`.
- C2S16 option-3 decoding and the definition-driven inventory semantic router from v5.18.4.2 are retained.
- S2C126 generic application publisher remains available.
- Local/dev-only application fixtures can exercise the recovered Mailbox / Item List / Make-X / Event / Shop / Raid / Confirmation / Infobox / Boss / Combat Metric / Attention / Effect / Progress presentation paths without inventing production data.

## What R8.5 does *not* claim

Operation-level client protocol closure is not equivalent to production-server behavior closure.

The following remain server-owned unless separately proven:

- actual mailbox contents and attachment ownership;
- production marketplace listings, prices and economy validation;
- shop stock/prices;
- raid mechanics/rewards/matchmaking;
- reward/RNG formulas;
- item-action eligibility/business rules;
- unknown outcomes for decoded generic interactions;
- arbitrary equipment stat vectors and production combat formulas;
- dynamic production world populations/instance selections.

Unknown outcomes remain fail-closed.

## Item semantic corpus

The exact current item definitions expose a large action corpus (~120 distinct inventory verbs). R8.5 now has the transport/action-resolution architecture to normalize these correctly. It does **not** fabricate every server-side outcome simply because an action label is known.

`Override` is an example of a fully promoted action because its client trigger and existing `bs` cosmetic channel are independently proven and were live-tested. `Defuse` remains correctly decoded but non-destructive because its production transformation is still unknown.

## Remaining research yield

The highest-value static implementation lanes are now substantially narrower. Further useful evidence may still come from:

- exact widget/container producer-consumer cross-links not yet attached to a LocalLab feature;
- command/request producer maps;
- historical-client differentials where current-client evidence has a gap;
- additional item-action result evidence from old artifacts/forums/runtime observation;
- true custom cache asset/model injection.

Low-level lanes already blocked by absent launcher/server/archive bytes should not be repeatedly rescanned without new bytes.

## Certification

Candidate server JAR SHA-256: `589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c`.

Binary seal:

- 499/499 classes are Java 11 class major 55;
- every R8.5-owned compiled class matches its JAR entry byte-for-byte;
- exact R8.5 resources inside the JAR verify item 29999, pet mapping 29999 -> 12000, retirement of old opcode87 non-Drop semantics for 29999, and the 64-row R6 S2C250 table.

Final clean regression was executed against the exact pinned client in segments. Tests 1–90 pass; 91–163 were re-run through 163 with no test failure before the outer execution window cut the runner; tests 164–179 pass. Earlier segmented certification also passed all 179 after the two expected assertion-drift updates (old 29999 corpus count and R8.4 decoded-subtype count). The shipped Windows selftest runs all **179/179** in one contract with a 90-second watchdog and one retry per test.
