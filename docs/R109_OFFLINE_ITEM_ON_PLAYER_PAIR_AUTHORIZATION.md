# R109 — explicit two-player authority for item-on-player callbacks

**DRAFT / STACKED ON R108 #3153 / OFFLINE-ONLY / UNMERGED / UNDEPLOYED**

## Source observation

R108 mapped the public plugin/content API: external plugins do not receive a
typed `WorldPlayer` or `BankState` through `PluginContext`, but content
callbacks receive semantic `ContentPlayer` facades. Specifically
`ContentItemOnPlayerContext.target()` supplies a resolved target player and
the item ID, while `ContentRegistry.dispatchItemOnPlayer(itemId,target)`
constructs that context. The context **does not carry the source's
`EntityId`, the target's expected generation, or a durable authorization
receipt**. Such authority must not be inferred solely from possession of a
target facade.

The actual runtime adapter is more nuanced than the interface alone:
`ContentRegistry.dispatchItemOnPlayer` creates
`ContentRuntimeAdapters.player(target)` **without a packet writer**.
The writerless adapter's `grantItem` (and certain other
packet-dependent operations) throws `UnsupportedOperationException`.
However, `setRunEnergy` delegates directly to
`target.movement().setRunEnergy`, and other semantic mutators do not
all require a writer or independently acquire the target's mutation
lock. The source/target authorization problem is therefore observable
on the **actual semantic facade type**, even though the test-only
pair permit uses a synthetic item grant.

R107 also proved that a Java callback with captured internal
`WorldPlayer`/bank state can bypass even an owner-bound World helper.
R108's owner-only single-player capability does not authorize intended
*two-player* interactions. Legitimate item-on-player behavior must check
both actors and explicit intent, not simply deny every target mutation.

## Narrow opt-in two-player permit

`R109OfflineItemOnPlayerPermit` is a package-private **test-only**
immutable binding for:
- one World instance;
- one exact tagged source `EntityId` and registration generation;
- one exact target `EntityId` and registration generation;
- one explicitly selected item ID;
- **at most one successful attempt**.

The trusted synthetic test fixture issues the permit after checking that
both actors are registered and distinct. It does **not** model a real
validated incoming item-on-player packet, slot identity, selected item
ownership, consent, range, or external authentication, and must never be
described as a production authorization token. The fixture/factory is
package-private, not non-forgeable from arbitrary in-process Java code.

Only the method `grantToTargetForTest(targetId,itemId)` is exposed.
It does not return a raw player, bank, writer, packet or mutable collection.

Each use checks:
1. Requested target ID and selected item exactly match the issued pair.
2. The permit has not been spent.
3. The current R103/R104 test-only plugin callback's World queue, actor ID
   and generation match the **source** exactly; no callback means denial.
4. Code is executing in the real WorldPulse.
5. The World resolves the exact original source and target by entity ID.
6. `World.withOfflineDualPlayerMutationOwnershipForTest` rechecks **both**
   expected generations under World lifecycle ownership and deterministic
   two-actor mutation lock order. This reuses the already existing R79
   offline dual-player lock helper without changing it.
7. The permit is consumed before the synthetic item grant, so an exception
   is fail-closed and cannot result in automatic replay.

The two-actor helper is intentionally an offline test aid. This does not
change the production item-on-player handler or `ContentItemOnPlayerContext`.

## Executable regression

`R109OfflineItemOnPlayerPermitTest` first executes R108, which inherits
R103–R107 (including the deliberate raw-reference cross-player bypass).
It then runs two additional real isolated Worlds.

**Independent actors and approved opposite-direction interactions:**
Three player actors (source A, target B, unrelated C) are current.
A-tagged task is granted one synthetic item-on-player permit A -> B for
item 560. It rejects target C, the wrong item, an attempt to use a separate
B -> A permit in A's context, and a second use of its own consumed permit.
The B-tagged callback separately consumes B -> A for item 565, under
both World player locks. An unrelated explicit nonplayer callback cannot
use a valid unspent A -> C permit. Outside-World callers cannot use
a permit. Final bank inventories verify precisely two intended grants
(A receives 565, B receives 560) and zero grants to C.

**Writerless semantic target negative control:**
A third isolated World puts an A-tagged plugin callback on the real
WorldPulse holding A's mutation lock but not B's. It creates the
writerless `ContentRuntimeAdapters.player(B)` facade—the factory
used by `ContentRegistry.dispatchItemOnPlayer`—and passes it through
the **actual `PluginCallbackScope.itemOnPlayerContext` and
`LeasedPlayer` wrappers** using a synthetic content context.
During the valid callback lease, `grantItem` correctly refuses
the operation without a packet writer, while `setRunEnergy`
**does mutate B's movement state without B's mutation lock**.
After the nested lease closes, the retained `ContentPlayer`
correctly refuses further mutation; lease expiry works, but does not
imply authorization to mutate the target while the lease is valid.
A's own run energy remains unchanged. This is a deliberately
constructed offline context, **not** an authenticated
item-on-player request or actual content handler dispatch.
It is a counterexample, not authorization evidence.

**Target generation and object replacement:**
A remains registered while target B first unregisters and re-registers
as the same object with a higher generation. A's callback rejects the
previous-generation A -> B permit and succeeds with a newly issued
current-generation permit. B then unregisters and a *different* B2 object
registers with the same username and a recycled numeric generation.
A previously issued B-object permit is rejected because that `EntityId`
is no longer registered; a newly issued A -> B2 permit succeeds.
Both stale permits remain unspent; current permits are spent exactly once.
No stale-target inventory mutation occurs.

Dedicated Linux and Windows Java11 CI runs the inherited R108 lexical
source audit, then the complete R103–R109 World regressions.
The independent server build and Windows atomic-persistence jobs must be
checked on the exact head before R109 can be considered fully verified.

## Non-goals and risk boundary

This **does not** implement or audit the actual live
`ContentRegistry.dispatchItemOnPlayer` authorization. External plugin
code can still perform mutation through other exposed semantic facades,
and captured raw state references can still bypass new capabilities.
There is no proof that a plugin may not create or retain a raw reference
through reflection, classloading or internal hooks. No real incoming
packet, inventory slot/selected item validation, interaction range,
combat/consent semantics, or authenticated session is covered.

The next step toward a production design is to trace the real
item-on-player packet dispatcher and `ContentRuntimeAdapters.player`
callers and author a lease-bound, dual-actor-checked target
mutation facade. Its authorization must be based on the actual
source/target intent (and, for item actions, validated selection/
inventory provenance), not merely on being handed a writerless
`ContentPlayer` object. This requires
provide lease-bound target methods and verify both ownership and
item provenance under the same World mutation transaction. Do not
silently add mandatory restrictions to existing public content callback
APIs before their legitimate uses are mapped.

**No public plugin/content API, LocalSession, World.shared, authenticated
login or server deployment changes. DRAFT / UNMERGED / UNDEPLOYED.**
