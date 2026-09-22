# Evidence Package — Exact v308 Collection Log Projection

SYSTEM

SpawnPK Collection Log exact-current client presentation model.

STATUS

STRONG-CLOSED-PROJECTION / OUTER-ROOT-NOT-NORMALIZED / REWARD-ECONOMICS-UNKNOWN

## AUTHORITY

Exact-current client:

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.v
~~~

## EXACT WIDGET ROLES

| Widget | Exact client role |
|---:|---|
| 54414 | collection name |
| 54415 | obtained-count text |
| 54416 | kill count |
| 54418 | 120-slot item-result container |
| 54419 | completion-reward heading |
| 54420 | completion-reward description |
| 54421 | hidden integer collection-row selection control |
| 54422 | hidden integer category/tab selection control |

Known exact category presentation includes:

- Bosses;
- Boxes;
- Minigames;
- Other;
- N/A placeholder.

## ITEM GRID

Widget 54418 is the native result/item grid and has capacity:

~~~
120 slots
~~~

It is a normal item container, not a bespoke Collection-Log wire format.

Therefore generic exact S2C53 container projection is the correct presentation primitive.

## TEXT / SELECTION PROJECTION

The remaining labels and selection state fit ordinary widget text/application-state publication.

No Collection-Log-specific packet family is required by the exact client contract.

Recommended semantic presentation shape:

~~~
CollectionLogPresentation.showCollectionName(...)
CollectionLogPresentation.showObtainedCount(...)
CollectionLogPresentation.showKillCount(...)
CollectionLogPresentation.showItems(...)
CollectionLogPresentation.showCompletionReward(...)
CollectionLogPresentation.selectCollection(...)
CollectionLogPresentation.selectCategory(...)
~~~

Raw widget ids remain adapter details.

## DOMAIN SHAPE SUPPORTED BY CLIENT

The exact presentation strongly supports a semantic aggregate containing at least:

~~~
CollectionLogAggregate {
  collectionId
  category
  obtainedEntries
  totalEntries
  killCount
  completionState
  rewardClaimState
}
~~~

This is a discovery/collection aggregate, not a short-lived assignment/task.

## EVENT OWNERSHIP CONSEQUENCE

Collection progress should be updated from authoritative loot/reward/kill events.

It must not infer completion by scanning client-visible inventory/container state.

The client is presentation only.

## SERVER SEMANTICS PROVEN

The exact client proves:

- named collections exist;
- collections are categorized;
- obtained count is displayed;
- kill count is displayed;
- up to 120 result items can be projected;
- a completion reward heading/description exists;
- collection/category selection state is server-addressable through generic presentation paths.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- exact outer interface root if not separately normalized;
- canonical collection IDs/catalog;
- which drops count toward which collection;
- reward item definitions;
- milestone thresholds;
- claim rules;
- duplicate-item semantics;
- persistence format;
- reset semantics, if any;
- anti-abuse.

The reward UI does not prove production reward economics.

## EXISTING SERVER SUBSTRATE

LocalLab already has CollectionLogService-style foundations.

This package should be used to build an exact presentation adapter over that semantic domain, not a second Collection Log implementation.

## READY FOR CHAT 2

yes — generic S2C53/text/application presentation is sufficient

## READY FOR CHAT 3

yes — projection contract is strong; catalog/reward mechanics remain server authority