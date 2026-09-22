# Evidence Package — Exact v308 Blood Pool Store Projection

SYSTEM

SpawnPK Blood Pool Store navigation and server-driven shop-slot projection.

STATUS

CLOSED-NAVIGATION-AND-PROJECTION-CONTRACT / DESTINATION-ROOT-UNKNOWN / SERVER-ECONOMICS-UNKNOWN

## AUTHORITY

Exact-current client:

~~~
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Corroborating exact-current client/cache audit:

- S2C126 special target 37 = Blood Pool shop slot append;
- target-1 control token RESET_BLOOD_POOL_SHOP_SLOTS clears the same client list;
- Blood Fountain hub exposes a distinct Blood pool store navigation entry;
- exact cache contains Blood-economy valuation tables used by adjacent systems.

## HUB NAVIGATION

Exact Blood Fountain hub root:

~~~
3320
~~~

Exact Blood Pool Store entry:

~~~
60003
label: <img=118> Blood pool store
tooltip: Select blood pool shop
~~~

This proves the Blood Pool Store is a distinct application destination.

Generic actionable-widget routing is compatible with C2S185(widgetId). Domain code should receive a semantic OPEN_BLOOD_POOL_STORE intent rather than widget 60003.

## EXACT S2C126 PROJECTION PATTERN

The exact client has a dedicated structured branch:

~~~
target 37 -> Blood Pool shop slot append
~~~

The same client list is cleared by the exact target-1 control token:

~~~
RESET_BLOOD_POOL_SHOP_SLOTS
~~~

Therefore the exact presentation lifecycle is a reset-and-rebuild stream:

1. clear the client Blood Pool shop slot list;
2. publish zero or more target-37 slot records;
3. render the resulting shop list.

This is materially stronger than treating packet 126 as ordinary widget text.

## ARCHITECTURE CONSEQUENCE

The semantic server presentation API should look like:

~~~
BloodPoolShopPresentation.resetSlots()
BloodPoolShopPresentation.appendSlot(...)
~~~

with one exact S2C126 encoder underneath.

Do not expose target number 37 or magic reset-token strings to gameplay/content code.

## DESTINATION ROOT

The exact destination interface root for Blood Pool Store is not closed by the currently normalized evidence package.

Do not guess it from nearby Blood Fountain widget ids.

STATUS:

~~~
UNKNOWN_CLIENT_PRESENTATION_DETAIL
~~~

until a direct exact-client root/consumer proof is recovered.

## EXACT CACHE / ECONOMY CORROBORATION

The exact-current cache audit records Blood-economy valuation corpora including:

~~~
blood_core.yaml
blood_shards.yaml
blood_diamonds.yaml
~~~

Those tables are exact-current cache data and can seed read-only valuation repositories.

They do not by themselves prove Blood Pool Store prices, currencies, buy/sell policy, or stock semantics.

## SERVER SEMANTICS PROVEN

The exact client proves:

- Blood Pool Store is a distinct Blood Fountain destination;
- the server can reset the Blood Pool shop slot projection;
- the server can append structured shop-slot records through S2C126 target 37;
- the UI is intended to be rebuilt from server-provided state.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- destination interface root;
- slot payload field meanings beyond the recovered append-list semantic unless separately normalized;
- exact inventory/catalog;
- item prices;
- currency/currencies;
- buy/sell direction;
- stock limits;
- refresh policy;
- eligibility;
- rank/perk requirements;
- persistence;
- anti-abuse;
- transaction policy.

## EXISTING SERVER SUBSTRATE

Chat 3 should compose this onto the general Shop domain and a typed S2C126 presentation facade.

Do not create a separate raw packet-driven Blood Pool shop engine.

## READY FOR CHAT 2

yes

Chat 2 has an exact requirement for a typed application-state presentation seam for S2C126 target 37 plus the exact reset control token.

## READY FOR CHAT 3

yes for navigation + shop projection lifecycle, no for economics

Chat 3 can model OPEN_BLOOD_POOL_STORE plus server-owned Shop state and project rows through a semantic reset/append adapter.