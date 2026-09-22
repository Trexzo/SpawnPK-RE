# Evidence Package — Exact v308 Sprite Runtime / Namespace Atlas

SYSTEM

Exact-current SpawnPK sprite lookup, retained cache corpus, transparency-key behavior, and interface-builder namespace coverage.

STATUS

LOOKUP-CONTRACT-CLOSED / CACHE-CENSUS-CLOSED / SUBSYSTEM-ATLAS-STRONG-PARTIAL

## AUTHORITY

Exact-current client:

~~~text
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Matched retained cache bundle used for cross-check:

~~~text
.spawnpk-data(1).zip
SHA-256 026336100f5f9a6b1aa89dd5668ee9b17d3a334703e9d6acf017f3eec1600f4c
~~~

Primary exact classes:

~~~text
rs.v.a   cache-root resolution
rs.l.F   named sprite/image loader
rs.n.c.* custom interface builders
~~~

## EXACT RUNTIME LOOKUP ROOT

The exact normal cache-root resolver derives the standard profile directory from:

~~~text
user.home
+ file.separator
+ "." + lowercased product name + optional "-test"
+ file.separator
~~~

For the exact SpawnPK product enum this is the standard:

~~~text
<user.home>/.spawnpk/
~~~

or the test-profile variant:

~~~text
<user.home>/.spawnpk-test/
~~~

The named sprite loader initializes its root as:

~~~text
rs.v.a.f() + "sprites/"
~~~

so the normal exact-current loose sprite path is:

~~~text
<cacheRoot>/sprites/<sprite-name>.png
~~~

## LOWERCASE NAME NORMALIZATION

The ordinary string sprite constructors in `rs.l.F` call:

~~~text
name.toLowerCase()
~~~

before loading the image.

Therefore sprite lookup is canonically lowercased by the client for these paths.

For example an interface literal such as:

~~~text
teleport/SPRITE
~~~

resolves through the ordinary loader as the lowercased namespace:

~~~text
teleport/sprite.png
~~~

where that exact file exists.

## PATH-SEPARATOR NORMALIZATION

The file-backed reload path `rs.l.F#a(String)` builds from the same sprite root and normalizes:

~~~text
/
//
\
~~~

to the host platform's `File.separator` before opening the image.

This makes nested namespaces first-class filesystem paths rather than an archive-only naming convention.

## PNG / LEGACY RETAINED SPRITE CORPUS

The matched retained sprite tree contains:

~~~text
1,582 retained non-directory sprite files
1,578 PNG files
4 non-PNG retained files
~~~

The four non-PNG retained files are:

~~~text
fountain/sprite 18.pdn
index.dat
p12_full.dat
teleport/sprite 26.pdn
~~~

The normal custom-interface named-sprite path recovered here is the loose PNG path.

Do not infer that the four non-PNG files are loaded by the same constructor.

## TOP-LEVEL PNG NAMESPACE CENSUS

Exact retained PNG counts:

| Namespace | PNGs |
|---|---:|
| icons | 410 |
| gameframe | 140 |
| lunar | 101 |
| popups | 88 |
| options | 82 |
| prayer | 80 |
| misc | 61 |
| wiki | 60 |
| fountain | 44 |
| bank | 40 |
| hitmarks | 40 |
| orbs | 38 |
| raids | 37 |
| magic | 35 |
| teleport | 35 |
| construction | 34 |
| emotes | 34 |
| equipment | 33 |
| clan | 31 |
| skills | 31 |
| login | 21 |
| attack | 20 |
| pos | 19 |
| drops | 14 |
| achievements | 9 |
| vote | 8 |
| tasks | 7 |
| factory | 6 |
| anim | 4 |
| gambling | 4 |
| list | 4 |
| event | 3 |
| tournament | 3 |
| slayer | 2 |

## TRANSPARENCY / COLOUR-KEY BEHAVIOR

`rs.l.F#c(r,g,b)` walks the loaded pixel array and replaces an exact RGB match with integer zero.

The ordinary named-string sprite constructor performs:

~~~text
c(255, 0, 255)     // pure magenta
c(255, 255, 255)   // pure white
~~~

after pixel capture.

A string+boolean constructor always removes pure magenta and removes pure white only when the boolean flag is true.

Therefore exact opaque:

~~~text
#FF00FF
#FFFFFF
~~~

can be converted to transparent pixel value zero on these normal loader paths.

This is path/constructor behavior, not a claim that every image-loading path in the client uses both keys.

Custom sprite tooling should warn when authoring these exact opaque colors on a path that uses the keyed constructor.

## INTERFACE-BUILDER SPRITE ATLAS

The exact `rs.n.c.*` interface classes were scanned for slash-based string literals and cross-checked against the retained PNG tree.

Result:

~~~text
81 interface-builder classes with at least one cache-backed sprite literal
213 unique matched sprite/path literals
~~~

High-value namespace ownership visible directly in exact interface builders includes:

~~~text
achievements -> Achievement / daily activity surfaces
bank         -> Bank and reward/coffer-style surfaces
clan         -> Clan / Clan Wars
construction -> POH / room construction
drops        -> drop search, Collection Log, guides
equipment    -> equipment, death, loadout
fountain     -> Blood/event/conversion/application family
gameframe    -> custom tabs, overlays and gameframe
gambling     -> gambling/duel family
magic        -> spellbook, home/filter/bounty icons
misc         -> donor/login/cart/event/bag and miscellaneous applications
options      -> options/keybind/makeover/colour surfaces
pos          -> in-game Marketplace/listing surfaces
prayer       -> prayers/curses/quick-prayer selection
raids        -> raid setup/party surfaces
skills       -> skill/slayer-adjacent surfaces
tasks        -> Task Scroll family
teleport     -> teleport/menu/application chrome
tournament   -> World Tournament presentation
vote         -> voting/redeem surfaces
wiki         -> Item Library / knowledgebase / guides
~~~

Representative exact matched literals include:

~~~text
bank/bank
bank/tab
clan/sprite
construction/sprite
drops/collection
equipment/loadout 1
fountain/sprite
gameframe/tab/ratings
icons/bosstele
magic/bounty
magic/home
misc/bag
pos/sprite
prayer/quick/sprite
raids/sprite
tasks/sprite
teleport/sprite
tournament/sprite
vote/sprite
wiki/guide
wiki/item
~~~

This atlas proves client presentation ownership/namespace reuse.

It does not prove server feature mechanics.

## ARCHITECTURE / TOOLING CONSEQUENCE

Exact loose-sprite support does not require repacking a sprite archive for these custom-interface paths.

A safe isolated LocalLab asset namespace can use:

~~~text
<isolated cache>/sprites/locallab/...
~~~

provided the exact interface/client code refers to the same lowercased name.

Tooling should:

1. normalize authored sprite names to lowercase for collision checks;
2. preserve nested directory structure;
3. reject or warn on collision with the retained 1,578 exact PNG paths;
4. warn on opaque magenta/white where the selected loader constructor colour-keys them;
5. never mutate the user's real `.spawnpk` tree during custom-asset generation.

## BOUNDARY

This package proves:

- normal named loose-sprite lookup root;
- lowercasing;
- separator normalization on the file reload path;
- transparency-key behavior for common constructors;
- retained sprite corpus size and top-level namespace distribution;
- broad exact interface-builder namespace ownership.

Still not fully normalized:

- every individual sprite ID/name -> exact widget mapping;
- desktop `rs.s.*` image resources outside the game-interface namespace;
- all archive-backed legacy sprite/index paths;
- dynamic sprite construction not sourced from named loose PNG files;
- gameplay/server rules associated with any sprite.

## READY FOR CHAT 3 / TOOLING

yes — exact lookup and namespace contract is strong enough for safe client presentation and isolated custom sprite work.
