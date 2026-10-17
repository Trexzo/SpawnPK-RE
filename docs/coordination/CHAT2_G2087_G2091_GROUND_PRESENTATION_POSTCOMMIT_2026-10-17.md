# Chat 2 G2087–91: canonical NPC ground presentation after commit

Parent #2959 `9860d52a136e218dc989412997f0020342a6da23`: hosted 901/901 and full Linux Gradle Java11/Windows source-only workflows passed. This is a **906-test candidate**, not a certification.

## Source-backed defect and correction
`NpcDropGroundSettlementService.settle` mutates real World ground registry and stores its in-process receipt before calling `publishLiveOwnerScene`. An exception from optional presentation previously escaped and became `SETTLEMENT_PENDING` although mutation was already committed. Catch Throwable **only after** ground/receipt commit; preserve FINALIZED status, caller observer, receipt, ground ownership. Type-only log omits Throwable.toString/message/cause, player and account secrets. Do not automatically resend partially published scene events. Test-only fault cutpoint normally null, reached only for an actual registered recipient.

G2087 RuntimeException, G2088 hostile Error with unsafe toString, G2089 healthy control, G2090 independent later actual death publishes new stack after prior failure, G2091 absent recipient never reaches injector. All exercise real canonical NPC, World death, ground receipt and terminal observer. Five new JavaExec tasks and critical retention; focused 901→906 unique.

## Not proven
No power-loss durability, authenticated original death identity, cross-JVM owner epoch, atomic reward/ACK, presentation queue ACK/reconciliation, safe replay, original v308 GUI or historical 179 parity. An exception from `GroundItemRegistry.addBatchDetailed` **before receipt commit** remains a separate possible partial-mutation hazard. Draft/unmerged; main, Chat1 account, World and Windows source unchanged. Require exact-head hosted 906/906, G2087–G2091 markers, Java11/full Gradle and Windows negative-release source-only checks before certification.
