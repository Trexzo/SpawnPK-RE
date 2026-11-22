# R108 — plugin player-state inventory and owner-bound bank capability

**DRAFT / STACKED ON VERIFIED R107 #3152 / OFFLINE TEST ONLY / UNMERGED / UNDEPLOYED**

## Source-level reachability distinction

R107's negative control proved that a Java callback tagged to player A
can directly mutate captured player B state through
`B.bank().spawnItem(...)`, without B's World mutation lock.
It used *package-private internal objects* in an `spk.local` fixture.

That does **not** mean ordinary external plugins receive
`WorldPlayer` or `BankState` from the public plugin API.
The existing public `PluginContext` provides
`content()`, `events()` and `scheduler()`; `WorldPlayer` is
package-private, not an exposed public plugin interface.

The public `ContentPlayer` facade **does** offer several semantic
mutators, including `grantItem`, `setRunEnergy`,
`restoreCombatSkillsAndSpecial`, `clearTimedStatuses`,
`syncMaintainedPetEffects`, `switchPrayerBook`,
`switchSpellBook` and `deactivatePrayers`. Plugins can
register content callbacks through `ContentRegistrar`. In
particular, `ContentItemOnPlayerContext.target()` returns a
`ContentPlayer` facade for a different actor in an explicitly
multi-actor interaction. The `PluginCallbackScope.LeasedPlayer`
facade validates callback lease/world execution, but lease
ownership is *not* the same as authorization for arbitrary
cross-actor mutations. Those semantics need separate auditing,
not blanket removal of intended item-on-player actions.

R108 adds a **read-only lexical source inventory**
`server/tools/audit_plugin_mutability_surface.py`. It reports
the known package-private `WorldPlayer` mutable state getters,
public plugin registration methods, known mutator candidates on
`ContentPlayer`, and public content callback contexts returning
`ContentPlayer`. It fails if those known source anchors disappear
or an internal raw `WorldPlayer` / `BankState` type appears
in the public `spk.plugin.api` Java source.

The inventory is intentionally **not** a full Java call graph,
bytecode verification, reflection/native reachability proof, or
guarantee that all mutable state has been discovered.

## Narrow test-only owner-bound bank grant

`R108OfflineOwnerBankCapability` is package-private, accepts
`World`, `EntityId` and positive registration generation,
and exposes **only** `grantSingleItemForTest(itemId)`. It never
returns the player, bank, packet writer or a mutable collection.

Each invocation:

1. Requires a currently active R103/R104 tagged plugin callback
   with the **exact same World event queue, EntityId and generation**.
   Unlike the older R107 compatibility check, an absent callback
   marker **denies** the operation.
2. Requires execution on the actual World pulse.
3. Resolves the current player by ID, then reacquires the
   World lifecycle/player mutation lock via
   `World.withOpenPlayerMutationOwnershipIfCurrent` and
   rechecks the registration generation before touching bank state.
4. Performs only a synthetic single-item bank/inventory grant
   with a throwaway packet writer and returns a result string.

This is a **cooperative capability model**, *not a sandbox*.
The World state remains accessible to internal `spk.local`
code, arbitrary plugin closures may capture other references,
and the tagged callback marker itself is an offline-only
test aid. There is no API migration for external plugins.
Do not expose this implementation as production authority
without a separate authorization and content-context review.

## Executable real-World regression

The dedicated `R108OfflineOwnerBankCapabilityTest` first runs
R107 (which inherits R103–R106), then adds:

**Concurrent players A/B:** Two current actors obtain distinct
test capabilities. Use from an unrelated caller before/after
World execution is refused. A-tagged callback grants an item
only to A; trying B's capability or A with the wrong generation
fails. A separate B-tagged callback grants B's item under B's
mutation lock. An explicitly non-player World event cannot use
A's capability. Final inventory quantities show no cross-owner
grant through the new facade, and all handles retire.

**Same-object generation rollover:** An old-generation plugin
callback is queued, the player is unregistered and re-registered
under a later generation, and a new tagged callback tries both
the old and new capabilities. The old capability is refused;
the new one succeeds. R99 denies the formerly queued event
at due dispatch and retires its handle.

Dedicated Linux and Windows Java11 builds execute both the
read-only source audit and the Java regression. The independent
full server build and Windows atomic-persistence matrix must
also pass at the exact head before R108 is marked verified.

## Remaining gap and proposed next steps

R107's deliberate negative control is **still part of the R108
test suite**: a plugin callback can bypass this new capability
entirely by directly dereferencing another player object that
it already possesses. This is not a general protection for
untrusted Java plugins or reflection.

A production design would need to systematically:
- inventory all publicly exposed semantic `ContentPlayer`
  mutation methods and their actual `spk.local` backing
  implementations;
- specify identity/target authority for commands, actions,
  item-on-player and other multi-actor content callbacks;
- issue non-forgeable, non-escaping owner/target capabilities,
  revalidate them under current World ownership at execution,
  and prevent plugin code from obtaining raw mutable objects;
- retain explicit multi-actor transactions rather than
  silently breaking legitimate target-player interactions;
- verify lifecycle, cancellation, replay and persistence
  effects on Linux and Windows.

No `PluginContext`, `PluginScheduler`, `ContentPlayer`,
`WorldPluginManager`, `World.shared` or live session/socket
implementation is modified in R108.

**DRAFT / UNMERGED / UNDEPLOYED.**
