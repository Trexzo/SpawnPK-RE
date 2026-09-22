# Evidence Package — Exact v308 S2C250 Application Bus Closure

SYSTEM

SpawnPK exact-current S2C250 application-bus handler registry and operation grammar coverage.

STATUS

CLOSED-REGISTERED-SUBTYPE-OPERATION-GRAMMARS / PARTIAL-BUSINESS-SEMANTICS

## AUTHORITY

Exact-current client:

~~~text
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

The earlier R84/R85 application-protocol corpus remains exact for v308 because the whole-JAR 307 -> 308 comparison proves all post-login application classes are byte-identical; only `rs/f/a.class` changed for the build/config constant.

Normalized source corpus:

~~~text
server/research/r84/application_protocol_r4/
server/research/r84/application_protocol_r5/
server/research/r85/s2c250_operation_grammars_r6_remaining.csv
~~~

## REGISTRY CLOSURE

The exact client has:

~~~text
43 registered S2C250 application handlers
subtype ids 1..43
~~~

R4/R5 progressively decoded the high-value handlers. R6 then closed the operation grammars for every handler that still had only an outer grammar.

A direct diff of:

~~~text
application_protocol_r5/tables/s2c250_application_index_r5.csv
against
r85/s2c250_operation_grammars_r6_remaining.csv
~~~

leaves:

~~~text
0 registered subtypes without per-operation grammar coverage
~~~

Therefore broad S2C250 operation-grammar archaeology is closed.

## NORMALIZED SUBTYPE INDEX

| ID | Exact/normalized client family |
|---:|---|
| 1 | matchmaking / live match status |
| 2 | dynamic scene-object override state |
| 3 | mail + reward-coffer visibility/control |
| 4 | token-roll / reward presentation |
| 5 | runtime widget scalar update |
| 6 | Event Activity token/limit rows |
| 7 | boss-bar overlay state |
| 8 | combat-metric overlay state |
| 9 | items-kept-on-death auto-keep projection |
| 10 | event-task UI |
| 11 | killcount/drop-rate essence-bonus overlay |
| 12 | world-tile polygon highlight overlay |
| 13 | active-events / hotspot timer state |
| 14 | item-list/search/transfer UI |
| 15 | gambling-session state toggle |
| 16 | server-selection list 40405 state |
| 17 | shop-tab state |
| 18 | killcount/drop-rate ticket-bonus overlay |
| 19 | named/dynamic timed-effect state |
| 20 | external login/log receiver control |
| 21 | actor-attached effect-list clear |
| 22 | Adventure/chapter reward/claim UI |
| 23 | three-line toast notification |
| 24 | flashing/directional attention hint |
| 25 | overlay counter-pair update |
| 26 | overlay pair-state update |
| 27 | multi-field overlay text matrix |
| 28 | confirmation-dialog state |
| 29 | archive-2 client resource request |
| 30 | Trading Post listing state |
| 31 | mail UI state |
| 32 | custom magic spell state |
| 33 | screen/status-panel controller around root 30700 |
| 34 | infobox overlay control |
| 35 | Make-X quantity interface |
| 36 | indexed enum/progress state (0..457 family) |
| 37 | widget action-text / geometry update |
| 38 | named chat message type 6 |
| 39 | client integer-state map update |
| 40 | selection dialog |
| 41 | raid-instance UI state |
| 42 | NPC-definition runtime override state |
| 43 | client integer-flag map update |

## IMPORTANT DISTINCTION

"Operation grammar closed" does not mean every field has a trustworthy business-domain name.

Examples that intentionally remain structural include:

- subtype 4 scalar/progress fields beyond the exact token-roll client arithmetic;
- subtype 5 runtime widget scalar roles;
- subtype 8 some combat-metric scalar labels;
- subtype 11/18 individual scalar labels inside exact essence/ticket bonus families;
- subtype 24 operation-4 helper scalar;
- subtype 25/26 generic overlay pair values;
- subtype 27 text-matrix business ownership;
- subtype 33 some status-panel scalar/model roles;
- subtype 36 the exact 0..457 indexed progress domain identity;
- subtype 39/43 map keys and their server business ownership.

Those are not missing wire grammars. They are `UNKNOWN_SERVER_AUTHORITY` or deliberately structural semantics until another consumer/source names them.

## ARCHITECTURE CONSEQUENCE

Do not open another broad "recover S2C250" task.

The correct architecture is:

~~~text
S2C250 exact subtype/op decoder
 -> typed presentation operation
 -> subsystem-specific adapter
 -> semantic domain state
~~~

Public gameplay/content APIs should never receive raw subtype/op integers.

Existing LocalLab `ApplicationPacket250Writer` is the generic transport substrate; feature work should compose typed adapters over it rather than implement new packet families.

## READY FOR CHAT 2

yes — transport grammar is finite and closed.

## READY FOR CHAT 3

yes where subsystem semantics are already exact; structural fields should remain presentation-only until separately proven.

## REMAINING CHAT 4 WORK

Only targeted semantic naming/correlation when a concrete subsystem needs one of the still-structural fields. No further broad S2C250 census is warranted.
