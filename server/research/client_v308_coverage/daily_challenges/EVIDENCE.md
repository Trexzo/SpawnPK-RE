# Evidence Package — Exact v308 Daily Challenges Projection

SYSTEM

SpawnPK Daily Challenges exact-current server->client projection and compatibility request grammar.

STATUS

CLOSED-CLIENT-PROJECTION-CONTRACT / SERVER-ASSIGNMENT-AND-REWARD-RULES-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Exact application state uses S2C126 special targets:

~~~
55 -> challenge definition/state record
56 -> challenge progress delta
~~~

## TARGET 55 — DEFINITION / STATE SNAPSHOT

The exact parser consumes one semicolon-delimited record containing:

~~~
integer field A
integer field B
string field C
string field D
integer field E
integer field F
~~~

and constructs a client record shaped as:

~~~
String key
String description
int metadataA
int metadataB
int current
int target
~~~

Exact UI use establishes:

- `key` is the stable challenge key;
- `description` is visible objective text;
- `current` and `target` drive progress / percentage;
- the two metadata integers are retained but are not consumed by this renderer.

Do not invent semantic names for `metadataA` or `metadataB` until another exact read site proves them.

## TARGET 56 — PROGRESS DELTA

Exact target 56 is a smaller update:

~~~
key
current
target
~~~

It locates the existing challenge by key and updates progress independently of the original target-55 definition/state record.

This proves a clean exact presentation pattern:

~~~
definition/state snapshot
+
incremental progress projection
~~~

The server should not rebuild the entire challenge list for every progress tick.

## EXACT REQUEST GRAMMAR

The exact client dynamically produces compatibility commands:

~~~
::claimchallenge <key>
::infochallenge <key>
~~~

These are sent through ordinary C2S103 command transport.

Recommended normalization:

~~~
C2S103 ::claimchallenge <key>
 -> DailyChallengeClaimRequest(key)

C2S103 ::infochallenge <key>
 -> DailyChallengeInfoRequest(key)
~~~

The compatibility strings must not become public gameplay/content APIs.

## CAPACITY / EMPTY STATE

The exact Daily Challenges interface renders a maximum of:

~~~
10 challenge records
~~~

Exact empty-state text:

~~~
You don't have any challenges!
~~~

This is client presentation capacity, not necessarily an authoritative server account limit outside this interface.

## TARGET-1 CONTROL NAME CORRECTION

The exact target-1 strings:

~~~
BUILD_ACHIEVEMENT_TAB
CLEAR_ACHIEVEMENT_TAB
~~~

are misleading historical wire names.

In this client generation they apply to the Daily Challenges UI rather than the separate Achievement Diary.

Therefore preserve exact bytes in the compatibility adapter but normalize internally to semantic operations such as:

~~~
DailyChallenges.rebuild()
DailyChallenges.clear()
~~~

Do not leak the legacy misnomer into the domain model.

## SERVER SEMANTICS PROVEN

The exact client proves:

- Daily Challenges are keyed records;
- definitions and progress can be projected separately;
- current/target progress is explicit;
- claim/info requests carry the stable key;
- at most 10 records are rendered by this interface;
- the server can rebuild/clear the presentation.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- assignment algorithm;
- rotation/reset cadence;
- canonical challenge catalog;
- meanings of metadataA/metadataB;
- completion validation;
- reward contents/amounts;
- claim eligibility beyond server-owned state;
- reroll/skip policy;
- persistence;
- anti-abuse.

## ARCHITECTURE CONSEQUENCE

Daily Challenges fit an assignment/objective service, but their state shape should remain distinct from Adventure milestones and Collection Log discovery.

Recommended semantic operations include:

~~~
DefineDailyChallenge
UpdateDailyChallengeProgress
ClaimDailyChallenge
GetDailyChallengeInfo
ClearDailyChallenges
~~~

## READY FOR CHAT 2

yes — exact S2C126/C2S103 transport is known; normalize targets/commands internally

## READY FOR CHAT 3

yes — projection and request contract are closed; assignment/reward mechanics remain server-owned