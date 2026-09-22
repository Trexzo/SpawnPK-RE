# Evidence Package — Exact v308 Special Pet Presentation / Renderer Controls

SYSTEM

Exact-current SpawnPK special pet renderer families, hidden presentation-state channels, and current-vs-legacy authority boundary.

STATUS

CLOSED-SPECIAL-RENDERER-CONTROLS / PARTIAL-SERVER-PROC-SEMANTICS

## AUTHORITY

Exact-current client:

~~~text
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Normalized existing exact-current research:

~~~text
server/src/spk/local/data/research_r82/pet_special_renderer_authority_r82.tsv
server/src/spk/local/data/research_r82/r30_exact_client_pet_renderer_controls_r82.tsv
server/src/spk/local/data/research_r82/r30_pet_current_vs_legacy_semantics_r82.tsv
server/src/spk/local/data/research_r82/r30_pet_static_ceiling_r82.tsv
~~~

## IMPORTANT BOUNDARY

The exact client can prove pet item/NPC identity, special renderer branches, server-fed presentation-control vocabulary, hidden client state, and sprite/owner-copy/model effects.

It does not automatically prove proc chance, damage threshold, cooldown, combat effect, server state accumulation/reset, or whether a legacy renderer channel is still actively emitted by the current production server.

Those remain server/runtime authority unless separate evidence closes them.

## OWNER-PLAYER-COPY RENDERERS

### Yoshiganger

item 22825; NPC 1334.

Exact renderer:
- owner-player-copy presentation;
- alpha 150;
- temporary model/render value aI=319770;
- intrinsic selector 3;
- client exposes a Switch-effect action.

The renderer is exact. The semantic meaning/timing of Switch-effect state remains server authority.

### Regular Doppelganger

item 3241; NPC 1335.

Exact renderer:
- owner-player-copy;
- alpha 100;
- intrinsic selector 6.

### Wondrous / Dyed Doppelganger

item 28807; NPC 8210.

Exact renderer:
- special owner-copy branch;
- intrinsic selector 3;
- dynamic body-colour treatment;
- Remove-dye action.

The visual branch is exact. Normal gameplay dye/selector state remains separately server-owned.

## SERVER-FED HIDDEN STATE VIA FORCED NPC TEXT

Several pet families reuse the forced-overhead-text channel as an internal renderer-control message.

The client consumes recognized control payloads and clears the visible text rather than displaying it normally.

### Evil Wolper / Resvano Evil Wolper

Items: 24238, 27340, 27341, 27342.
NPCs: 6991, 8124, 8125, 8126.

Exact control:
0 / 1 / 2 / 3 -> hidden short state.

Exact renderer:
state > 0 -> native icon sprite 53 above pet.

The non-zero state value does not select different sprites in this branch.

### Krampmare / Evil Krampmare

Items: 24210, 24211.
NPCs: 3962, 3965.

Exact control: 0 / 1 / 2 / 3 -> hidden state.
Exact renderer: state > 0 -> native icon sprite 53, with its own projection offset.

### Solar / Unholy Behemoth family

Items include 25415 and 25425.
Exact client NPC range includes 5159..5164 and related Solar-behemoth identity 6650 appears in the shared exact renderer branch.

Exact hidden state:
1 -> one icon[22]
2 -> two icon[22]
3 -> three icon[22]

Important current-definition caution:
NPC 5164 is Sheep in the current merged definition authority even though the client renderer range still includes it.

Therefore 5164 is historical/range residue for this special branch and must not be promoted into a current Behemoth server binding merely from the range check.

### Ancient Hydra

item 22947; NPC 6049.

The exact current client still retains the same state 1/2/3 -> 1/2/3 icon[22] charge renderer.

This proves current client capability. Historical/current server usage is a separate question; do not assume the production server still emits the old charge state solely because the v308 renderer remains.

### Tidal / Tsunami / Infernal Tempoross

Items: 27690, 27691, 28692.
NPCs: 8184, 8185, 8186.

Exact state mapping:
state 1 -> icons[59]
state 2 -> icons[58]
state 3 -> icons[369]

The exact client retains this multi-state visual capability. Do not assign gameplay debuff meanings to 59/58/369 from the renderer alone.

## SCOPESIGHT VASA

item 28888; NPC 8330.

Exact forced-text trigger: SNIPE.

The client consumes SNIPE into bespoke hidden renderer state and clears the text.

Recovered exact state setup includes aA=0, aB=0, aC=20, aD=8, aE=3, after which the main NPC renderer enters the native Scopesight presentation path.

No guessed GFX id is required to represent this exact client behavior. Proc chance/timing remains server authority.

## GENERIC FOUR-BYTE RENDER-CONTROL CHANNEL

The exact NPC forced-text parser also recognizes a generic control form beginning with the ] separator.

The client replaces ] separators and parses four byte values into hidden actor fields ao, ap, aq, ar.

During NPC model construction those values are copied into model render fields S, T, U, V.

This is a real server-driven four-byte model/render channel.

The semantics of the four model fields are intentionally unnamed here. Do not label them RGB/tint/alpha/etc. until another exact model consumer proves those meanings.

This channel is a plausible substrate for special pet visual variation, but that architectural observation is not permission to invent per-pet values.

## CURRENT-VS-LEGACY RULE

A renderer branch surviving in v308 means EXACT_CURRENT_CLIENT_CAPABILITY.

It does not necessarily mean CURRENT_PRODUCTION_SERVER_BEHAVIOR.

This matters especially for Ancient Hydra charge visuals, Tempoross legacy multi-state visuals, and owner/staff/custom variants whose item/NPC mapping survives but whose gameplay effect is not independently documented.

Chat 3 should separate PetPresentationProfile from PetProcDefinition / PetCombatEffect / PetChargeState and attach provenance independently.

## STATIC CEILING / UNKNOWN PETS

The broad current pet corpus contains many mappings where item/NPC identity is exact but no proc tuple/effect text or special renderer branch is recovered.

Correct classifications include FOLLOWER_EFFECT_UNKNOWN_OR_UNDOCUMENTED or, where appropriate, LIKELY_COSMETIC_OR_VANILLA_FOLLOWER_BUT_NOT_PROVEN_NO_EFFECT.

Absence of a recovered effect is not evidence that the pet definitively has no server mechanic.

Do not manufacture combat/proc behavior to fill those gaps.

## ARCHITECTURE CONSEQUENCE

Recommended split:

~~~text
PetDefinition / identity
PetPresentationProfile
  -> owner-copy renderer
  -> hidden state/icon renderer
  -> generic render-control bytes
  -> animations/GFX where exact

PetGameplayProfile
  -> proc chance
  -> cooldown
  -> thresholds
  -> effects
  -> charge accumulation/reset
~~~

Only the first group is broadly recoverable from exact current client presentation.

## READY FOR CHAT 3

yes — special renderer/presentation controls are exact enough to implement without guessed visual effects.

Gameplay/proc semantics remain evidence-gated per pet family.

## REMAINING CHAT 4 WORK

Only specific pet families where a new exact runtime/cache source can close a currently unknown proc/presentation correlation; semantic interpretation of the generic four-byte model fields if downstream exact model reads are recovered; and current production usage of legacy-capability branches, which requires runtime/server authority rather than more static inference.