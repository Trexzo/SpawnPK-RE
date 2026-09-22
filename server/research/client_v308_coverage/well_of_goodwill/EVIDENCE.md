# Evidence Package — Exact v308 Well of Good Will Interface

SYSTEM

SpawnPK Well of Good Will client interface and server-wide progress presentation.

STATUS

PARTIAL-CLOSED-CLIENT-UI / SERVER-MECHANICS-UNKNOWN

## AUTHORITY

Exact-current client:

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.aZ
~~~

## ROOT

~~~
51150
~~~

Background:

~~~
51151 -> fountain/sprite 23
~~~

The root installs 18 children.

## EXACT STATIC PRESENTATION

~~~
51152 -> Well of Good Will
51153 -> The well of goodwill seeks..
51154 -> @yel@Divine spirit shields
51158 -> 0% (0/100)
51160 -> <u=16776960>Server wide progress</u>
51161 -> Once the server-wide goal is reached, ALL players will receive..
51162 -> @yel@Double chance for artifacts & statuettes @or1@(2 hrs)
51166 -> <img=9> Contribute
51167 -> For each @yel@Divine spirit shield<col=FF9B00> that you
51168 -> contribute, you will individually receive..
51169 -> @yel@5x Goodwill Points
~~~

These strings prove the shipped client presentation/defaults, not current production policy.

## CONTRIBUTION ITEM SLOT

Widget 51155 is an item/container-style widget with four columns and item id 13741 present in slot 0 with quantity 1 in its static client definition.

Combined with the surrounding exact text, this proves the client presents Divine spirit shield as the example/required contribution item for this shipped interface revision.

It does not prove whether production accepted variants, alternate items, or later event rotations.

## PROGRESS PRESENTATION

Sprites:

~~~
51156 -> teleport/sprite 12
51157 -> teleport/sprite 13
~~~

51157 also installs a custom sprite object using teleport/sprite 13.

Progress text:

~~~
51158 -> 0% (0/100)
~~~

Container/root 51159 holds:

~~~
51156 @ 96,6
51157 @ 96,6
51158 @ 325,8
~~~

This is exact client presentation for a server-wide progress bar/count.

## CONFIRMED CLICKABLE CONTROL

~~~
51163
tooltip: Donate to well of goodwill
hover/paired widget: 51164
~~~

51166 is the visible Contribute label positioned over the contribution area.

The ordinary actionable-widget path is compatible with native C2S185 widget-action transport. This package does not claim a separate Well-specific request packet.

## ROOT CHILD PLACEMENT

High-value exact placements:

~~~
51151 @ 55,36
51152 @ 257,44
51153 @ 259,75
51154 @ 258,93
51155 @ 122,74
51155 @ 357,74
51159 @ 17,137
51160 @ 260,122
51161 @ 257,164
51162 @ 257,182
51163 @ 84,237
51164 @ 84,237
51166 @ 141,246
51167 @ 326,230
51168 @ 326,245
51169 @ 326,260
65418 @ 433,44
65419 @ 433,44
~~~

## CLIENT-COMPATIBLE S2C PRESENTATION

Exact global S2C authority provides generic mechanisms suitable for this interface:

- S2C97 main-interface open;
- S2C53/S2C34 item-container updates;
- S2C126 widget text/control updates;
- S2C171 visibility;
- S2C219 close interfaces.

Current LocalLab already has reusable S2C97, S2C53, S2C126 and S2C219 publisher capability.

## SERVER SEMANTICS PROVEN

The client proves:

- a Well of Good Will application exists;
- it presents a server-wide progress goal;
- the shipped interface names Divine spirit shields;
- it presents a progress percentage/count;
- it presents a server-wide reward description;
- it presents an individual contribution reward description;
- it exposes a Donate to well of goodwill control.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- actual active contribution item policy;
- whether item 13741 is the sole accepted item;
- server goal denominator / target calculation;
- whether 0/100 is literal item count, points, or a normalized default;
- contribution conversion ratio;
- Goodwill Point economy;
- duration semantics for the displayed 2 hours;
- artifact/statuette chance formula;
- reward activation timing;
- stacking behavior;
- persistence;
- global reset timing;
- anti-abuse;
- eligibility.

Do not turn the static 0/100, 5x, or 2-hour strings into authoritative mechanics without separate evidence.

## FILES / METHODS

~~~
rs.n.c.aZ#a()
server/research/client_v308_coverage/well_of_goodwill/evidence/interface_builder_v308.txt
~~~

## READY FOR CHAT 2

partial

No dedicated transport requirement is proven. Generic interface/text/container publishers are the correct substrate.

## READY FOR CHAT 3

yes for semantic application/UI composition, no for original mechanics

Chat 3 can model a protocol-independent server-wide contribution/progress application and project it to this exact client surface, while keeping goal/reward policy explicitly evidence-gated.