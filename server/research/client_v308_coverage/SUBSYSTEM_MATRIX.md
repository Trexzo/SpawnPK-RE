# Exact v308 Client/Cache Coverage Matrix

This is Chat 4's live research queue. Priority is based on how much exact client
authority would unblock Chat 2/3 without inventing server mechanics.

| Priority | Subsystem | Current evidence | Status | Main unknown / next proof | Consumer |
|---|---|---|---|---|---|
| P0 | C2S/S2C packet writer/reader census | exact v308 C2S census: 188 writer sites / 86 opcodes / 86 framed; 51 semantically decoded in main, 20 control/telemetry, 11 exact-research semantics not yet current-main, 4 serializer-only semantic unknowns | STRONG-PARTIAL | C2S framing is closed; in-JAR C2S classification closed; keep serializer-only 2/6/78/109 explicit unknowns, then continue S2C reader census | Chat 2 |
| P0 | Combat-facing client contracts | exact 854f attack writers, packet-81 player masks, packet-65 NPC masks, combat-interface roots/style labels | STRONG-PARTIAL | core client transport/presentation package closed; authoritative formulas/timing/spec/death rules remain `UNKNOWN_SERVER_AUTHORITY` | Chat 3 |
| P0 | Prayer + magic presentation | exact 854f C2S185 prayer/direct widgets, local-only spell selection, five target writer families, prayer `bd` headicon channel, 51 prayer/126 spell client census | STRONG-PARTIAL | client-facing contract packaged; drain/formulas/secondary effects/consumption remain `UNKNOWN_SERVER_AUTHORITY` | Chat 3 |
| P0 | Appearance + equipment + ranks/icons | exact v308 worn HEAD→`fE[rs.l.h.b(aC)]` staff-rank gate, packet-81 `aC` + `bs` fields, Override/C2S16, native icon family | STRONG-PARTIAL | client presentation contract closed; LocalLab currently publishes `aC=0`; original named-rank→numeric-`aC` and production Override policy remain `UNKNOWN_SERVER_AUTHORITY` | Chat 3 |
| P0 | Make-over Mage / character design | exact v308 C2S40/C2S101, roots 4882/2459/3559, exact kit/colour domain | PARTIAL-CLOSED-CONTRACT | server fee/restrictions/persistence policy unknown; native designer cannot preload persisted appearance from packet 81 | Chat 3 |
| P1 | Dialogues / chatbox state machines | exact v308 option roots `2459/2469/2480/2492/14170`, Continue roots `4882/4887/4893/4900/30700`, mouse C2S40/C2S185 and keyboard `dialogueoption N`/C2S40(4907) | STRONG-PARTIAL | core standard input state machine closed; normalize remaining player/item/statement roots and close/cancel presentation families | Chat 2/3 |
| P1 | Items / item definitions / equipment models | item authority R5, item catalog, equipment audits, cache extraction corpus | PARTIAL | exact-current definition-field census, model/colour/retexture semantics, action/menu ownership gaps | Chat 3 |
| P1 | NPC definitions / interaction presentation | Home NPC corpus, NPC resolver, packet-65 parity | PARTIAL | exact-current definition fields, head/model/animation/GFX/menu-option presentation and unresolved NPC families | Chat 3 |
| P1 | Teleports / UI navigation | R82 teleport candidates, application/widget research | PARTIAL | exact navigation roots/buttons/configs and which destination semantics are client-visible vs server-only | Chat 3 |
| P1 | Minigame / event UI | exact v308 Tournament contract now packaged: hub root `27400` with `56000+` children, leaderboard root `61011`, Enter/Spectate/Shop + weekly/all-time filters -> C2S185; plus R83/R84 Hunger Games, Clan Wars, gambling, event/task contracts | STRONG-PARTIAL | Tournament client contract is ready for Chat 3; Blood Slayer selector, normal Duel and Monster Spawner client contracts are now exact/ready for Chat 3; normal Duel also proves C2S185 high-widget-id low16 aliasing; recover remaining event/minigame/application state machines without inventing server rules | Chat 3 |
| P1 | Pet presentation | extensive R82/R85 pet mapping, animation/GFX timing, renderer-control tables | STRONG-PARTIAL | unresolved special-renderer families and current-vs-legacy semantic gaps | Chat 3 |
| P1 | Animations | scythe recovery, pet animation timing, upstream remap tables | PARTIAL | global exact-current animation ownership/index/remap coverage | Chat 3 |
| P1 | GFX | packet-65 GFX parity, pet GFX timing, upstream remaps | PARTIAL | global exact-current GFX definition/remap/attachment timing coverage | Chat 3 |
| P1 | Models / textures / skins / hierarchy | Chat 4 R6–R12 Blender→stock-render pipeline | STRONG-RESEARCH | live config/raw lookup root for isolated GUI override; production cache mutation remains out of scope | Chat 3 / tooling |
| P1 | Sprites | stock sprite/texture archive research, UI surfaces | PARTIAL | subsystem-to-sprite atlas and exact runtime lookup/override paths | Chat 3 / tooling |
| P2 | Application packet 250 families | R84 R4/R5/R6 operation grammars | STRONG-PARTIAL | remaining subtype closures and semantic handoff boundaries | Chat 2/3 |
| P2 | S2C126 application/update bus | R84 exact update-bus research | STRONG-PARTIAL | remaining argument/control routes and server-authority interpretation gaps | Chat 2/3 |
| P2 | Settings/config persistence surface | R83/R84 settings maps | PARTIAL | exact-current config ownership and which values are client-only preferences | Chat 3 |
| P2 | Collection/achievement/task UI | R83/R84 catalogs and crosslinks | PARTIAL | consolidate state-machine inputs; keep reward/progression rules server-unknown unless proven elsewhere | Chat 3 |
| P2 | Social/party/clan UI | party/mailbox/clan research plus newer research branches | PARTIAL | exact-current social-list/party/clan state contracts and server membership/permission unknowns | Chat 3 |

## Immediate campaign order

1. **Dialogue/interface state-machine atlas — remaining families**
   - core standard option/Continue input routing is now packaged;
   - close player/item/statement dialogue roots plus close/cancel presentation;
   - keep wording and server-authored branch semantics evidence-gated.
2. **S2C publisher parity + field schemas**
   - exact handled S2C set is now finite at 75 opcodes;
   - exact framing is closed: 63 fixed / 8 var-short / 4 var-byte;
   - all 75 have client-presentation family classifications;
   - compare each family against current LocalLab publisher support;
   - deepen exact per-field schemas for missing/high-value publishers;
   - hand only proven transport/presentation facts to Chat 2/3.
3. **Items + NPC definition field census**
   - exact-current model/action/animation/GFX/recolour/retexture ownership;
   - keep server mechanics unknown unless separately proven.
4. **Teleports / UI navigation**
   - close exact roots/buttons/configs and client-visible destination semantics.
5. Continue Issue #427 application contracts: Quick Prayer/Curse -> Pet Fusing/Enchanting -> PK Ratings -> Event Chest/Well/Looting Bag, then remaining minigames/assets/settings/social surfaces by dependency demand.

Completed high-value P0 packages now include the exact v308 worn staff-rank
overhead gate, combat-facing contracts, prayer/magic presentation, Make-over
Mage wire/cache contracts, and the separate staff-partyhat Override/`bs`
channel.

## Existing high-value corpus

- `server/research/r83/client_discovery_r2/`
- `server/research/r84/client_static_r3_1/`
- `server/research/r84/application_protocol_r4/`
- `server/research/r84/application_protocol_r5/`
- `server/research/r85/`
- `server/src/spk/local/data/research_r82/`
- `protocol/`
- `evidence/`

The campaign should link and normalize these sources rather than duplicate them.
