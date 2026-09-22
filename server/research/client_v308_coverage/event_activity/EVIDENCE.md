# Evidence Package — Exact v308 Event Activity Viewer

SYSTEM

SpawnPK Event Activity Viewer client application, timed token-limit presentation and exact S2C250 application grammar.

STATUS

CLOSED-CLIENT-UI-AND-APPLICATION-GRAMMAR / SERVER-QUOTA-POLICY-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
interface class rs.n.c.J
application decoder rs.n.c.K
timer refresh rs.n.c.L
activity row state rs.n.c.J$a
~~~

## ROOT

~~~
30072
~~~

Opening the interface registers a client-side refresh callback on root 30072 with a 500 ms interval.

Static sprites:

~~~
30330 -> popups/activities 1
30331 -> popups/activities 2
~~~

Main scroll/container:

~~~
30332
content height: 750
width: 449
visible height: 235
~~~

## ACTIVITY ROW CAPACITY / LAYOUT

The builder pre-creates seven six-widget activity groups with bases:

~~~
30333
30339
30345
30351
30357
30363
30369
~~~

Each base group contains:

- activity/title/timer text at base;
- progress-bar widget at base+1;
- quota/status text at base+2;
- up to three detail lines at base+3, base+4, base+5.

The dynamic renderer additionally places shared separator sprite 30331 once per activity, producing seven root children per populated activity row.

This gives an exact shipped client capacity/layout of seven activity groups.

## STATIC EXPLANATION

~~~
30375 -> Event Activity Viewer
30376 -> <img=50> To prevent excessive farming, activities have timed limits on token earnings
30377 -> <img=78> @cya@The limit on an activity resets once its timer reaches <img=37> @whi@0:00
~~~

Shared close family:

~~~
63740 / 63741
~~~

## EXACT S2C250 APPLICATION REGISTRATION

The client application registry `rs.q.a.a.b` registers:

~~~
subtype 6 -> rs.n.c.J.c.getClass() -> decoder rs.n.c.K
~~~

Therefore Event Activity Viewer is an exact **S2C250 subtype 6** application family.

## EXACT SUBTYPE-6 OPERATION GRAMMAR

`rs.n.c.K#a()` begins by reading an operation integer.

### operation 0 — clear/reset

~~~
op = 0
effect:
  clear client activity list
  reset activity count to 0
~~~

### operation 1 — append one activity

Exact decode sequence:

~~~
op = 1
name: string
detailCount: int
details[detailCount]: string...
currentUsage: int
limitStateOrLimit: int
timerDurationMillis: long
~~~

The decoder passes this record into the viewer's activity model.

### operation 2 — rebuild/render

~~~
op = 2
effect:
  rebuild the scroll child layout from the accumulated activity list
~~~

This produces a clear server publication pattern:

~~~
clear
append activity x N
render
~~~

## EXACT LIMIT STATES

For one activity, the fourth semantic field controls three exact client states.

### limit = -1

Client presentation:

~~~
<img=81> Activity locked! <img=81>
~~~

The activity icon changes to `<img=81>` and the progress bar is set full-width.

### limit = 0

Client presentation:

~~~
No token limit!
~~~

The progress bar is set full-width.

### limit > 0

The client treats the preceding integer as `currentUsage` and this value as the usage limit.

If:

~~~
currentUsage >= limit
~~~

the exact status is:

~~~
@whi@<shad=1>Limit reached! Token earnings are locked until the limit timer ends..
~~~

Otherwise the client dynamically renders current-vs-limit progress and sets progress-bar width to:

~~~
int((currentUsage / limit) * 100)
~~~

## EXACT TIMER BEHAVIOR

`rs.n.c.J$a` stores:

- activity name;
- icon prefix, initially `<img=82>`;
- supplied long timer duration;
- client creation timestamp from `System.currentTimeMillis()`.

Every refresh computes:

~~~
remaining = suppliedDuration - (currentTimeMillis - creationTime)
remaining = max(0, remaining)
~~~

It formats a timer suffix using:

~~~
 <img=46> <img=37> @or1@
~~~

followed by hours/minutes/seconds when at least one hour remains, otherwise minutes/seconds.

`rs.n.c.L` refreshes activity title/timer text approximately every 500 ms.

This proves a countdown-duration client model. It should not be rewritten as an absolute server timestamp without an adapter explicitly converting between representations.

## ACTIVITY DETAILS

Each operation-1 record may carry any detail-array length, but the visible row has only three dedicated detail text widgets. The builder/renderer consumes up to three visible detail lines per activity row.

## INPUT / INTERACTION

This application is a viewer. No per-activity action button is defined by this class. User interaction is limited to normal close/navigation behavior.

## CURRENT SERVER GAP

A current-main repository search found no dedicated root 30072 / Event Activity Viewer adapter at the time of this package.

## SERVER SEMANTICS PROVEN

The exact client proves:

- event activities have a dedicated viewer;
- the viewer can display seven activity rows;
- each row carries name, up to three visible detail lines, current usage, quota state and a countdown duration;
- token earning can be locked by a timed activity limit;
- there are exact states for locked, unlimited and finite-limit activities;
- the client updates countdown presentation locally every 500 ms;
- S2C250 subtype 6 has exact clear/append/render operations.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- activity catalog;
- which activities are quota-limited;
- actual per-activity limit values;
- reset durations;
- whether limits are account-, IP-, device-, or character-scoped;
- token item/currency identity;
- earning formula;
- server clock/restart reconciliation;
- persistence;
- bypasses/exemptions;
- anti-abuse.

The explanatory text proves intent to prevent excessive farming, but not the original enforcement implementation.

## EXISTING SERVER SUBSTRATE

This maps directly onto a semantic UsageQuota/timed-limit service plus a projection adapter. Do not create a parallel quota engine merely because the client uses S2C250 subtype 6.

## READY FOR CHAT 2

yes

Chat 2 can treat S2C250 subtype 6 and its operation grammar as exact transport authority. Server-side scheduling/persistence should remain semantic and not expose subtype numbers outside the adapter.

## READY FOR CHAT 3

yes

Chat 3 can compose activity-specific quota definitions onto the existing UsageQuota-style substrate, with exact locked/unlimited/finite presentation. Limit values and reset policy remain server authority or explicit LOCAL_LAB_POLICY.