# Evidence Package — Exact v308 Adventure Book Structured Projection

SYSTEM

SpawnPK Adventure Book / chapter progression exact S2C250 presentation contract.

STATUS

CLOSED-CLIENT-RECORD-AND-OPERATION-CONTRACT / SERVER-TRIGGERS-AND-REWARDS-UNKNOWN

## AUTHORITY

Exact-current client:

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Application transport:

~~~
S2C250 subtype 22
~~~

## EXACT CLIENT RECORD SHAPE

The exact client-side Adventure Book objective record contains:

~~~
type
definitionId
primaryText
secondaryText?
rewardPairs[itemId,amount][]
currentProgress
requiredProgress
claimed/completed flag
~~~

Exact visible subject type vocabulary:

~~~
ITEM
NPC_HEAD
OBJ
~~~

The client can replace a `{prog}` placeholder in display text using current/required progress.

## EXACT SUBTYPE-22 OPERATIONS

Recovered operations:

~~~
op 0 -> reset chapter state
op 2 -> finalize/render accumulated chapter entries
op 3 -> append structured objective/reward record
op 5 -> client-side highlight/decorative state
op 6 -> client-side highlight/decorative state
op 7 -> chapter claim state
op 8 -> two chapter-level integer counters/scalars
~~~

Do not renumber or collapse these wire operations in the exact adapter.

## CLAIM-STATE PRESENTATION

Exact op-7 presentation:

~~~
0 -> incomplete; claim controls hidden
1 -> claimable; claim control shown
other -> already claimed
~~~

This is a client presentation contract.

It does not prove the server's eligibility calculation.

## SEMANTIC DOMAIN SHAPE

A protocol-independent model should contain concepts such as:

~~~
ObjectivePresentationSubject {
  type
  definitionId
}

ObjectiveProgress {
  current
  required
}

ObjectiveRewardPreview[]
ObjectiveClaimState
ChapterState
~~~

Raw subtype/op numbers belong only in the presentation adapter.

## HARD-CODED CLIENT DEFINITIONS ARE NOT SERVER AUTHORITY

The exact client contains shipped/starter Adventure Book objective definitions and reward previews.

Examples include objectives around voting, Trading Post usage, daily activity participation, acquiring items, Blood Fountain unlocks, Blood Revenants and similar progression content.

These embedded definitions are presentation/reference data.

A modified client must not be able to define its own authoritative objectives or rewards.

The server must independently own:

- completion conditions;
- current progress;
- claim state;
- reward eligibility;
- chapter unlocks;
- canonical reward delivery.

Exact client definitions may seed/corroborate server definitions only with preserved provenance.

## SERVER SEMANTICS PROVEN

The exact client proves:

- Adventure Book is a structured milestone/chapter system;
- objectives have subject type + definition id;
- objectives can carry primary/secondary text;
- reward item/amount previews are structured;
- current/required progress is structured;
- claim state is explicitly projected;
- chapter state supports reset/finalize/append and chapter-level counters.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- exact trigger vocabulary;
- canonical objective catalog;
- validation rules;
- reward amounts where only client-side preview exists;
- prerequisite graph;
- chapter unlock criteria;
- whether every shipped client definition was active production content;
- persistence format;
- anti-abuse.

## ARCHITECTURE CONSEQUENCE

Adventure Book should sit on the semantic Objective/Progression substrate.

It is specifically a milestone/chapter aggregate, not the same state shape as Daily Challenges, Slayer assignments or Collection Log discovery.

Recommended separation:

~~~
ProgressionEventStream
MilestoneProgressionService
AdventureBookDefinitionRepository
AdventureBookProgress
AdventureBookClaimService
AdventureBookPresentationAdapter
~~~

## READY FOR CHAT 2

yes — generic exact S2C250 application transport already exists; keep subtype/op details internal

## READY FOR CHAT 3

yes — exact projection schema is closed; gameplay triggers/rewards remain server-owned