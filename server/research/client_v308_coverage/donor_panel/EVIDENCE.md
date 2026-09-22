# Evidence Package — Exact v308 Main Donor Panel

SYSTEM

SpawnPK Main Donor Panel client interface for donor navigation, promotion progress and reward presentation.

STATUS

CLOSED-CLIENT-UI-CONTRACT / PAYMENT-AND-PROMOTION-POLICY-UNKNOWN

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.aI
~~~

## ROOT

~~~
60062
~~~

Primary exact text includes:

~~~
60066 -> @or2@NOTE: @yel@This is only counted from payments, not opening bonds!
60067 -> <img=83> @yel@Reach the total amount in bond purchase, before the promotion expires!
60069 -> @or1@Promotional rewards
60070 -> @or1@Donor options and features
60072 -> @or1@Main Donor Panel
~~~

## DONOR NAVIGATION OPTIONS

Exact selectable text controls use tooltip:

~~~
Select this option
~~~

and include:

~~~
60073 -> <img=9> Donate for rewards
60074 -> <img=6> View donator perks & benefits
60075 -> <img=24> Open donator shop
60076 -> <img=3>  Teleport to donator zone
60077 -> <img=5>  Teleport to elite donator zone
60078 -> <img=7> Teleport to vip donator zone
60079 -> <img=37>  Teleport to sponsor donator zone
~~~

These prove distinct client navigation intents. They do not prove destination coordinates, eligibility thresholds, rank entitlements or shop inventory.

## PROMOTION THRESHOLDS / REWARD PRESENTATION

Exact static threshold labels:

~~~
60081 -> @or2@$150
60084 -> @or2@$300
60087 -> @or2@$500
~~~

Exact static item displays:

~~~
60082 -> item 23172, quantity 2
60085 -> item 11695, quantity 1
60088 -> item 13741, quantity 1
60098 -> item 22466, quantity 2
~~~

These are exact shipped presentation values. They are not by themselves proof that the production server's current promotion thresholds/rewards remained these values.

## PROMOTION MODEL WIDGET

Widget 60089 is a 100x100 model widget with:

~~~
animation/model field af = 1335
zoom aT = 1250
rotations = 0 / 0
model source = rs.d.d.c(808).w
~~~

This proves a dedicated model preview in the promotion area. The exact semantic identity of NPC/definition 808 and the role of animation 1335 should remain unpromoted here unless separately recovered.

## PROMOTION PROGRESS / NEXT PROMO

Exact static widgets include:

~~~
60091 -> @yel@$0
60092 -> <img=25>
60093 -> <img=25>
60094 -> <img=25>
60099 -> @yel@Next promo
60100 -> @yel@will be @gre@10% OFF
~~~

The `$0` and `10% OFF` strings are client defaults/presentation. A live server is expected to project account/promotion state into this surface.

## PAYMENT-VS-BOND ACCOUNTING INTENT

The strongest semantic client text is:

~~~
This is only counted from payments, not opening bonds!
~~~

Combined with the nearby text about reaching a total amount in bond purchase before promotion expiry, the exact client presentation distinguishes **payment-derived promotion progress** from merely opening/consuming bonds.

This proves client-visible accounting intent, not the authoritative server ledger implementation or fraud/payment verification logic.

## INPUT TRANSPORT

The donor options are exact actionable text widgets. No donor-specific C2S opcode is proven by this builder.

The exact client has a generic C2S185 widget-action route; the donor adapter should normalize clicks into semantic intents rather than expose raw widget ids.

External payment initiation/checkout must remain outside a generic gameplay packet handler unless separately proven.

## CLIENT-COMPATIBLE S2C PRESENTATION

Generic exact-current presentation primitives are sufficient for the panel:

- S2C97 — open interface;
- S2C126 — promotion/account text updates;
- S2C53/S2C34 — item reward previews where needed;
- widget model publisher families for the model preview if dynamic replacement is required;
- S2C219 — close interfaces.

Current server code already has S2C97/S2C126/S2C53/S2C219 foundations; the global S2C publisher-parity audit still tracks generic widget-model publisher gaps.

## SERVER SEMANTICS PROVEN

The client proves:

- donor functionality is presented as a first-class application;
- Donate, perks, shop and four donor-zone navigation intents exist;
- promotional reward thresholds/progress are a dedicated UI concern;
- promotion progress explicitly distinguishes payments from opening bonds;
- a future/next-promotion discount is presented.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- current promotion thresholds;
- current reward item mapping;
- donation rank thresholds;
- donor-zone coordinates/requirements;
- donor shop contents/prices;
- promotion expiry schedule;
- discount eligibility/application;
- payment provider verification;
- chargeback/refund/fraud handling;
- whether bond purchases map 1:1 to real-money amounts;
- persistence/account scope;
- historical/current policy for item/model previews.

Do not implement real-money accounting from static client strings alone.

## READY FOR CHAT 2

yes for generic presentation transport only

## READY FOR CHAT 3

yes for semantic donor/promotion state projection, not payment processing

Chat 3 can model donor entitlements, promotion progress and navigation intents independently of raw widgets. External commerce/payment verification is a separate trust boundary and must not be inferred from the client.