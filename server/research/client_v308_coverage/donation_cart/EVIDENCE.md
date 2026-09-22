# Evidence Package — Exact v308 Donation Shopping Cart

SYSTEM

SpawnPK Donation Shopping Cart client interface, bond catalog/cart presentation and payment-method selection surface.

STATUS

CLOSED-CLIENT-UI-CONTRACT / COMMERCE-TRUST-BOUNDARY-EXPLICIT

## AUTHORITY

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
class rs.n.c.C
~~~

## ROOT

~~~
60200
~~~

Background:

~~~
60201 -> misc/donor sprite 7
configured size: 740 x 480
~~~

Title:

~~~
60269 -> @or1@Donation Shopping Cart Interface
~~~

The root allocates 51 children.

## PRODUCT CATALOG

Catalog/scroll parent:

~~~
60202
content height: 250
width: 239
visible height: 270
~~~

Catalog item widget:

~~~
60204
~~~

Exact layout:

- 2 columns;
- horizontal spacing 47;
- vertical spacing 28.

Exact static product item ids:

~~~
slot 0 -> 16001
slot 1 -> 16002
slot 2 -> 16003
slot 3 -> 16004
slot 4 -> 16005
slot 5 -> 16006
slot 6 -> 16007
slot 7 -> 16008
~~~

Per-product quantity display widgets initialize to `0`:

~~~
60205
60206
60207
60208
60209
60210
60211
60282
~~~

## QUANTITY CONTROLS

Each product row has exact Reduce/Increase controls.

Visible button tooltips:

~~~
Reduce quantity
Increase quantity
~~~

Exact paired control families:

~~~
60227/60228 reduce  | 60230/60231 increase
60233/60234 reduce  | 60236/60237 increase
60239/60240 reduce  | 60242/60243 increase
60245/60246 reduce  | 60248/60249 increase
60251/60252 reduce  | 60254/60255 increase
60257/60258 reduce  | 60260/60261 increase
60263/60264 reduce  | 60266/60267 increase
60284/60285 reduce  | 60287/60288 increase
~~~

This gives one quantity-control pair for each of the eight exact catalog item ids.

## SHOPPING CART / CHECKOUT PRESENTATION

Headings:

~~~
60212 -> @or1@Purchase Options
60213 -> @or1@Shopping Cart
~~~

Checkout/cart control:

~~~
60214
tooltip: Shopping cart
hover/paired: 60215
60217 -> Checkout cart
~~~

Static checkout summary lines:

~~~
60218 -> 1x @whi@$5.00 Bond
60219 -> 1x @whi@$10.00 Bond
60220 -> 1x @whi@$20.00 Bond
60221 -> 1x @whi@$30.00 Bond
60222 -> 1x @whi@$45.00 Bond
60223 -> 1x @whi@$75.00 Bond
60224 -> 1x @whi@$100.00 Bond
60225 -> Subtotal: @yel@$285.00
~~~

Summary parent:

~~~
60226
content height: 150
width: 135
visible height: 177
8 children
~~~

The `$285.00` subtotal is the exact shipped static example/default state. It is not authoritative trusted pricing.

## PAYMENT METHOD SELECTION

Exact selectable controls:

~~~
60273
tooltip: Select PayPal
~~~

and:

~~~
60274 -> <tab=20><img=9> OSRS GP
tooltip: Select OSRS GP
~~~

This proves the exact client offered PayPal and OSRS GP as two presentation/payment-selection modes.

It does **not** prove payment completion, exchange rates, settlement, ownership transfer, fraud prevention or price validation.

## DISPLAYED DENOMINATIONS

Exact static price labels:

~~~
60275 -> <img=84>@yel@$5
60276 -> <img=84>@yel@$10
60277 -> <img=84>@yel@$20
60278 -> <img=84>@yel@$30
60279 -> <img=84>@yel@$45
60280 -> <img=84>@yel@$75
60281 -> <img=84>@yel@$100
60283 -> <img=84>@yel@$500
~~~

These are client-visible denomination labels, not a substitute for authoritative server-side SKU/pricing configuration.

## PROMOTION CROSS-LINK

The cart reuses donor-promotion widgets from the Main Donor Panel and includes exact static promotion text:

~~~
60300 -> @whi@You'll receive a free @gre@$20 bond@whi@ with each
60301 -> @whi@claimed promotional item below!
~~~

This proves promotion presentation is integrated into the shopping-cart surface.

Current/live promotion qualification remains server authority.

## INPUT TRANSPORT

The quantity, checkout and payment-method controls are client action widgets. No dedicated Donation-Cart C2S packet family is proven by this interface builder.

Raw widget actions should be normalized into semantic cart intents if LocalLab models this UI.

Actual PayPal/browser/external-commerce completion is a separate trust boundary and should not be implemented as if a client click itself proves payment.

## CLIENT-COMPATIBLE S2C PRESENTATION

Generic exact-current presentation primitives are sufficient for a server projection:

- S2C97 — open interface;
- S2C126 — quantities, summary, subtotal, selection/status text;
- S2C53/S2C34 — item/product or promotion item displays;
- S2C171 — conditional visibility if required;
- S2C219 — close interfaces.

Current server code already has reusable S2C97/S2C126/S2C53/S2C219 foundations.

## SERVER SEMANTICS PROVEN

The client proves:

- an eight-item donation/bond catalog exists in this shipped interface;
- quantities are independently adjustable;
- a cart/checkout summary is presented;
- $5/$10/$20/$30/$45/$75/$100/$500 denomination labels exist;
- PayPal and OSRS GP selection surfaces exist;
- donor promotion presentation is integrated into the cart.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- authoritative mapping of item ids 16001..16008 to bond denominations/names;
- trusted prices/SKUs;
- OSRS GP exchange rate;
- OSRS GP payment verification;
- PayPal transaction creation/completion;
- callbacks/webhooks;
- settlement/fraud/chargeback/refund rules;
- stock/availability;
- taxes/fees;
- promotion eligibility;
- donation credit ledger;
- fulfillment idempotency;
- security model.

Never treat client-supplied quantity/payment selection or static dollar text as proof of a successful or valid payment.

## READY FOR CHAT 2

yes only for generic local UI transport; external payment integration is outside packet trust

## READY FOR CHAT 3

yes for local semantic cart/promotion projection, not payment authority

Chat 3 can represent cart lines, chosen payment mode and donor-promotion UI state semantically. Any real-money or cross-game-currency payment system must use a separate authoritative commerce boundary.