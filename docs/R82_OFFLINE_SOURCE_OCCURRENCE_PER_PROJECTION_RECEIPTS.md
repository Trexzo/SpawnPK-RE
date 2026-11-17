# R82 — Offline producer-scoped occurrence keys and independent projection receipts

**DRAFT / STACKED ON R81 / OFF BY DEFAULT / UNMERGED**

## Source audit and the identity gap

`PlayerCombatResolutionService.resolveImmediateOwned` creates two
fresh immutable `CombatOutcome` objects only after a real lethal PvP
resolution: `PLAYER_KILL` and `PLAYER_DEATH`. Each exposes attacker
and victim reference strings, type, context, tick and authority.
**No existing CombatOutcome field is an authenticated logical event
identity or a cross-JVM replay token.** The two outcome objects from
one death must be distinguishable by type; independent lethal
resolutions against the same actors must never be conflated just
because some public fields match.

R81's first-delivery receipt is keyed by exact Java object identity
and stops an entire fanout after a partial attempt. This safely
prevents duplicate additive writes but means a later, unstarted
projection cannot be completed by a trusted in-memory reissue.
Using only attacker/victim/tick/type strings as a replacement key
would incorrectly trust a fabricated fact and risk merging distinct
same-tick source operations.

## Explicitly offline source keys

`R82OfflineCombatOccurrenceKeys` allocates a new monotonically
increasing ordinal **when the existing real R79 test adapter invokes
the actual PvP resolver**. The key contains a random isolated-test
scope UUID, exact attacker and victim World IDs and generations, the
resolver's ordinal, and the outcome type. It does not infer identity
from the client or packet strings.

The original `CombatOutcome` stays unchanged. In the isolated R79
adapter, each source-emitted immutable fact is associated with its
key by object identity *before* the real DomainEventBus publication.
The key is exposed to the projection receipt only while the current
World execution context and R79's exact live-in-scope producer proof
are held. This preserves R79's original rejection of arbitrary fake
same-field domain events and does not make string identities
authoritative.

A clearly named **test-only** `reissueRecordedFactForTest(original)`
path can construct a new immutable Java object from a *previously
recorded original source fact*, attach the original producer's
existing key, and pass it back through R79's authenticated-in-test
DomainEventBus callback. It does **not** call the combat resolver
again, consume HP, award kills or invent a live network retransmit.
Unknown originals, nested/reentrant reissues, invalid World
generations and quiesced actor pairs are rejected. The producer's
recorded original is required; arbitrary cloned strings are
insufficient.

All source mapping and sequence state live **in process memory**.
The random fixture scope cannot survive reconstruction by another
JVM. This is not a recovered SpawnPK operation-number protocol.

## Independent per-projection claims

`CombatOutcomeObserverGroup.installOfflinePerObserverReceiptForTest`
adds an alternative to R81's whole-fanout claim; the two options
are mutually exclusive. The R80 current World owner check always
executes before this admission.

`R82OfflinePerProjectionReceiptLedger` tracks a synchronized
bounded map from **trusted source key** to up to 64 independent
observer-index bits. Each bit is consumed before its observer starts.

- A trusted first delivery tries each observer separately.
- A duplicate trusted new Java object with the *same producer key*
  skips already claimed observers, but any previously unstarted
  observer may still execute if both World generations remain
  open. This allows safely finishing a fanout stopped between
  objective, match and Collection Log consumers.
- An observer that starts then throws, partially mutates its own
  multiple bindings, or fails in its caller-owned authoritative
  Collection Log resolver has already consumed that observer's
  receipt. A later trusted reissue will **not** replay it.
- A new combat fact with no current R79 producer proof cannot
  claim a receipt even if all its public string fields equal a
  real outcome. Previously consumed source-key/index pairs remain
  consumed.
- A receipt-capacity limit is fail-closed: never evict old source
  keys and permit a prior update to be applied again.
- Synchronized claims prevent two concurrent attempts at the
  same source key/observer index from both winning.

The optional hook is never installed by production
`LocalSession`, actual account persistence, gameplay routing or
network packets. The uninstalled group retains its original direct
delivery and failure-isolation behavior.

## Real-path offline regression

`R82OfflineProducerOccurrenceProjectionTest` exercises a real
isolated WorldPulse and the existing
`PlayerCombatResolutionService.resolveImmediateOwned` through R79.
It uses the original synchronous DomainEventBus, R80 generation
fences, and genuine ObjectiveProgressService, MatchSessionService and
CollectionLogService projection observers.

The Collection Log count is an explicitly synthetic caller-provided
fixture (7, then 8), **not a claimed real PvP count formula**.

Tests include:

1. Two real domain subscribers deliver the SAME kill and death
   source objects to one group. Each objective, score and Collection
   Log projection runs once. A later reissue of the kill fact as
   a **new immutable Java object** retains its producer key and
   cannot double count.
2. If the original fanout stops after objective advancement but
   before match-scoring begins, a trusted later reissue skips
   objective and finishes match + collection. No old score is
   replayed.
3. When the Collection Log absolute-count resolver fails after
   objective and match writes, that observer remains claimed
   and is not retried on a reissue.
4. If the objective observer fails on its second binding, the
   already attempted objective observer is not replayed. Partial
   intra-observer state is **not** repaired by this design.
5. After genuine PlayerLifecycleService respawn, a **second real
   lethal PvP resolution** creates a distinct ordinal and new
   typed source keys even for the same original actor pair.
6. A fresh object with identical public fields is refused both
   by R79's domain proof and R82's direct group receipt.
7. Full bounded receipt storage refuses a distinct new source
   fact without evicting an older receipt.
8. Duplicate installation attempts are refused and a
   synchronized eight-thread claim permits only one winner
   per key and observer index.

Inherited R63–R81 safety and projection regressions are run on
Linux and Windows with Java 11-compatible output. The separate full
server/Windows atomic build is also required; only an exact-head
green result supports verified status.

## Nonclaims / future work

**This is an in-memory offline proof, not a durable replay protocol.**

The runtime has no recovered stable producer-issued ID in its
public CombatOutcome contract. The R82 ordinal and UUID are
generated solely by the opt-in isolated test adapter and cannot
be reconstructed across a game-server restart. Production player
sessions do not use them.

A true cross-process/exactly-once objective/match/collection
transaction needs trustworthy source-issued operation IDs that
survive restarts, atomic per-projection commit receipts, and
recovery tests against interrupted writes. Per-observer receipts
do not repair partial writes *within* an individual observer,
nor undo already committed HP, objective or score mutations.

R63/R64/R72 account-ledger-owner-file publication remains
non-atomic across power loss; R74 cannot cancel arbitrary
noncooperative in-flight disk calls. No gameplay speed, frame
rate, CPU or server latency gains can be inferred.

**R82 OFFLINE ONLY / DRAFT / UNMERGED / NO LIVE ACTIVATION.**
