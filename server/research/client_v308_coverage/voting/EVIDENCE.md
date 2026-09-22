# Evidence Package — Exact v308 Voting Interface

SYSTEM

SpawnPK native voting-site interface and visible site-selection surface.

STATUS

CLOSED-CLIENT-UI-CONTRACT / BUTTON-TRANSPORT-PARTIAL / SERVER-VOTE-POLICY-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.M
~~~

## ROOT

~~~
60050
~~~

Background:

~~~
60051
~~~

Title:

~~~
60061 -> @or1@Vote for us and receive rewards!
~~~

## CONSTRUCTED VOTE-SITE CONTROLS

Exact client construction defines:

~~~
60052 / 60053 -> Vote for us on TopG
60055 / 60056 -> Vote for us on RuneLocus
60058 / 60059 -> Vote for us on RSPS-List
60164 / 60165 -> Vote for us on Moparscape
~~~

Each family also has an associated auxiliary widget.

## IMPORTANT VISIBLE-LAYOUT CORRECTION

The live scroll/container is:

~~~
60163
~~~

It has exactly six children and places only:

~~~
60055 / 60056  RuneLocus
60058 / 60059  RSPS-List
60052 / 60053  TopG
~~~

The Moparscape pair 60164/60165 is constructed in the class but is **not attached to the visible scroll/root by this builder**.

Therefore exact-current client presentation proves three active visible site controls in this interface:

~~~
TopG
RuneLocus
RSPS-List
~~~

Do not expose Moparscape as an active exact-current option merely because dormant widgets are constructed.

## ROOT COMPOSITION

The root installs five children:

~~~
60051  background
60163  vote-site scroll
63740  shared close control
63741  shared close hover
60061  title
~~~

## INPUT TRANSPORT

The controls are exact clickable client widgets, but this package does not yet promote a site-click packet schema.

The global client has a generic C2S185 widget-action route and no dedicated voting opcode is known. However, vote-site controls may be subject to client-local browser/application interception, so exact click-to-wire behavior remains intentionally unpromoted here until that path is traced.

## SERVER SEMANTICS PROVEN

The client proves:

- a native voting interface exists;
- TopG, RuneLocus and RSPS-List are visible voting destinations in this exact builder;
- Moparscape widgets exist but are not attached to the visible interface;
- voting is presented as reward-bearing activity.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- outbound URLs;
- whether navigation is browser-local or server-mediated;
- vote verification provider/API;
- cooldowns;
- per-site vote windows;
- rewards;
- streaks;
- claim flow;
- account/IP/device anti-abuse;
- persistence;
- whether dormant Moparscape support was ever active in this build.

## READY FOR CHAT 2

not for a dedicated packet — transport trace remains partial

## READY FOR CHAT 3

yes for a semantic voting-site catalog/UI projection

Chat 3 should model voting providers and reward claims independently of widget ids. External vote verification/reward rules require separate server authority or explicit policy.