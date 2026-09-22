# Evidence Package — Exact v308 Task Scroll

SYSTEM

SpawnPK Task Scroll objective/progress/reward client application.

STATUS

CLOSED-CLIENT-UI-CONTRACT / TASK-CATALOG-AND-REWARD-POLICY-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.aS
~~~

## ROOT

~~~
18559
~~~

Background:

~~~
55732 -> tasks/SPRITE 0
~~~

Primary labels:

~~~
55733 -> @or1@Task Scroll Title
55734 -> @or1@Task Information
55735 -> @or1@Potential Rewards
55736 -> @or1@Completion Progress
~~~

Shared close family:

~~~
64275 / 64276
~~~

## TASK INFORMATION

Scrollable panel:

~~~
55737
content height: 250
width: 235
visible height: 170
20 child text rows: 55738..55757
~~~

The 20 rows are generated client-side as numbered/default information lines and are intended to be populated with server-authored task details.

## POTENTIAL REWARDS

Scrollable reward panel:

~~~
55758
content height: 750
width: 178
visible height: 170
contains 55759 @ 18,6
~~~

Reward item widget 55759:

- capacity arrays: 100 entries;
- 4 columns;
- 25 rows;
- horizontal spacing 10;
- vertical spacing 10.

The first ten static slots are deliberately initialized as:

~~~
item 1337
quantities 1..10
~~~

This is strong negative evidence: those static values are placeholder/scaffolding, not authoritative Task Scroll rewards.

## COMPLETION PROGRESS

Progress visuals:

~~~
55760 -> tasks/SPRITE 1
55761 -> tasks/SPRITE 2 variant/overlay
~~~

Exact explanatory text:

~~~
55762 -> @yel@This meter indicates your progress for the objective.\nOnce complete, you'll receive a casket.
55763 -> 0% (0/100)
~~~

The client therefore proves an objective-progress model with casket/reward completion presentation. The static 0/100 is a default, not task-authority.

## ACTIONS

### Collect reward

~~~
55764 / 55765
tooltip: Collect reward
hover/paired: 55766
55767 -> @yel@Collect
~~~

### Track progress

~~~
55768 / 55769
tooltip: Track progress
hover/paired: 55770
55771 ->   @yel@Track<img=39>
~~~

Both are ordinary actionable widgets and fit the exact generic C2S185 widget-action route.

No Task-Scroll-specific C2S packet is proven.

## CLIENT-COMPATIBLE S2C PRESENTATION

Generic exact-current presentation primitives are sufficient:

- S2C97 — open interface;
- S2C126 — task title, information rows, progress text/status;
- S2C53/S2C34 — reward-grid contents;
- S2C79 — scroll position;
- S2C171 — optional visibility/state;
- S2C219 — close interfaces.

Current LocalLab/SpawnPK-Src already has reusable publisher capability for S2C97, S2C126, S2C53 and S2C219.

## CURRENT SERVER GAP

A current-main repository search found no dedicated 18559/Task Scroll application adapter at the time of this package.

## SERVER SEMANTICS PROVEN

The client proves:

- Task Scroll is a distinct objective application;
- it supports up to 20 information/detail lines;
- it presents a large potential-reward grid;
- it has objective completion progress;
- completion is associated with receiving a casket in the exact client explanation;
- it exposes Collect reward and Track progress actions.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- Task Scroll item/activation source;
- task catalog;
- primary/secondary requirement semantics;
- objective counts;
- casket item/reward mapping;
- reward tables/chances;
- reroll/cancel behavior;
- Track action server behavior;
- persistence;
- expiration;
- anti-abuse;
- whether multiple scrolls can coexist.

Do not promote placeholder item 1337 or default 0/100 into live mechanics.

## EXISTING SERVER SUBSTRATE

Task Scroll should compose onto ObjectiveProgress/assignment/reward-delivery foundations rather than introduce a parallel progress engine.

## READY FOR CHAT 2

yes for existing generic transport; persistence needs should follow the semantic model

## READY FOR CHAT 3

yes

Chat 3 can implement a protocol-independent TaskAssignment/TaskScroll projection with exact information/reward/progress widgets. Task definitions and rewards remain authority/policy.