# Chat 2 G18.7.7 — finalized partial G14 failure cannot be retried on replacement owner

Date: 2026-10-15.

**Exact hosted-green parent:** G21.215 + G18.7.5 cumulative [#2679](https://github.com/Trexzo/SpawnPK-RE/pull/2679), SHA `86b3f6cec253f1032ab98cda61fd9feed0fa706d`, [Actions #38754862186](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38754862186) SUCCESS, **532 unique focused**, Java11 major55 and full Gradle. Draft/unmerged. Other concurrent gameplay [G18.7.6 #2682](https://github.com/Trexzo/SpawnPK-RE/pull/2682) independently probes HEALTHY all-consumer delivery and duplicate veto; its exact-head CI was IN PROGRESS at this branch's creation and its donor is deliberately NOT imported here.

## New distinction: post-failure retry entrypoints after owner replacement

G18.7.2 checks G14 invalid persistence while two separate canonical NPC deaths finalize and later G15/G16/G18 remain0. G18.7.5 checks no automatic backfill after G16 error across a same-name owner replacement. G18.7.6 healthy control independently checks simple duplicate finalize. **G18.7.7 combines G14 partial-failure + a healthy same-name replacement + explicit RETRY API attempts against the SAME already-settled NPC ID**, proving no public/runtime retry avenue can invoke missed consumers even after the invalid snapshot owner has departed.

1. In isolated World A legitimately activate G4 Monster Hunter, G15 Task Scroll, G16 Adventure and G18 EASY. Inject ONLY TEST-OWNED malformed G14 Daily Challenge namespace before G14 hydrates. The installed Monster Spawner observer finalizes ONE genuine NPC with immutable owner A: G1 receipt1, ground coin1, G4 completes1, G14 throws, G15/G16/G18 stay0, and runtime NPC entry is permanently removed. G14 owner's failure remains explicitly reproducible from the normal status API.
2. Detach A and register clean same-name B with new generation. B legitimately gets a valid fresh G14 task AND explicitly activates its own new G16 Adventure task (G16 is player-owner-local, not automatically transferred). Assert NO historical G14/G15/G16/G18 progression, no new G1 receipt and no extra coin on replacement.
3. Attempt all actual terminal-retry routes for the SAME old dead NPC: `finalizeIfOwned(firstNpc)` returns `NOT_OWNED`, `retrySettlement(firstNpc.id)` returns `NOT_OWNED`, `retryFinalizationIfPending(firstNpc)` returns null and `retryPendingSettlementsOnce()` returns0. Assert every consumer still has progress0 on B and World totals remain G1 receipt1/coin1; no old G14 partial terminal ACK or replay exists.
4. Use genuine G1 due-time respawn to spawn a DIFFERENT NPC with immutable B/generation origin; kill/finalize it once using the unchanged installed observer. B's valid G14, shared G15/G16 and World G18 then progress1, G1 receipts2 and total coin2, while G4 completion remains1. This is forward-only NEW gameplay, not recovery of prior missed G15/G16/G18.

New marker `G1877_G14_FAILED_TERMINAL_DUPLICATE_RETRY_VETO_PASS`.

## Authority, gaps, and integration

STRICTLY `CUSTOM_LOCALLAB` in-memory World execution with a TEST-created invalid G14 namespace; not a real authenticated TCP login, file-account corruption, original SpawnPK credit policy, remote client exploit, or a durable consumer outbox/compensation fix. This test proves **ordinary NPC retry entrypoints cannot compensate for a post-settlement consumer failure**. A future per-stage durable event mechanism must use immutable canonical event identity, per-stage idempotency/ACK and owner generation policy without replaying G1/ground settlement. See [#2587](https://github.com/Trexzo/SpawnPK-RE/issues/2587) and original reward attribution [#2558](https://github.com/Trexzo/SpawnPK-RE/issues/2558).

**Exactly three additive files/3 commits** vs #2679: independent new `G1877G14FailureNoDuplicateRetryAfterOwnerReplacementTest.java`, `server/build.gradle` single focused entry plus both hard guards **532→533 unique**, this note. ZERO production Java, account G21.215/G21.216 OS/RAW reservation, G18.7.6 healthy sibling, old G1847 or G1872, client/default/main code. **Fresh exact-head hosted CI REQUIRED** G1877/G1875/G21215, 533/533 focused, Java11 major55/full Gradle. Parent and donor green do not certify this new head. Keep DRAFT/unmerged, no ACTIVE review retirement, grant/replay or main merge.

To integrate with newer Chat1 G21.216/217 trains or other gameplay, take ONLY this new Java + note and add ONE manifest entry/count increment to the newest individually certified head; DO NOT apply the 533-standalone Gradle file to a different 533-test core branch.
## CI repair note — first failed exact head (2026-10-15)

Initial hosted [Actions #38759609199](https://github.com/Trexzo/SpawnPK-RE/actions/runs/38759609199) FAILED at the G1877 fixture's B-state assertion, with NullPointerException reading `g16.objective(USER).progress` after A unregister/B register. G16 Adventure task is owner-scoped, so B's new `WorldPlayer` has no Adventure objective until `g16.activate(USER)` on B. The G1877 Java test now explicitly activates B's NEW legitimate G16 objective before checking no historical credit and before attempting old-NPC runtime retries. **No production Java, consumer replay or original-server policy changed. Fresh successor hosted CI is required**.
