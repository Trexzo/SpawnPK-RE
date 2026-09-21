# Exact v308 Client/Cache Coverage Matrix

This is Chat 4's live research queue. Priority is based on how much exact client
authority would unblock Chat 2/3 without inventing server mechanics.

| Priority | Subsystem | Current evidence | Status | Main unknown / next proof | Consumer |
|---|---|---|---|---|---|
| P0 | C2S/S2C packet writer/reader census | R84/R85 protocol research; exact menu-action and application buses | PARTIAL | close remaining exact-current transport gaps and map each to semantic family without exposing raw transport publicly | Chat 2 |
| P0 | Combat-facing client contracts | packet-65/81 parity tests, hit/animation work, combat interface repository | PARTIAL | exact hit/animation/GFX/state presentation contracts, attack option/menu writers, combat-facing config/interface state | Chat 3 |
| P0 | Prayer + magic presentation | R82 prayer/spell tables, spell authority JSON, prayer/magic runtime tests | PARTIAL | exact-current interface/config/filter/state contracts vs server-only activation/drain/effect rules | Chat 3 |
| P0 | Appearance + equipment + ranks/icons | packet-81 parity, equipment appearance tests, native icon family work | PARTIAL | close rank/staff-partyhat/overhead-icon ownership and exact rendering field/source | Chat 3 |
| P0 | Make-over Mage / character design | exact v308 C2S40/C2S101, roots 4882/2459/3559, exact kit/colour domain | PARTIAL-CLOSED-CONTRACT | server fee/restrictions/persistence policy unknown; native designer cannot preload persisted appearance from packet 81 | Chat 3 |
| P1 | Dialogues / chatbox state machines | dialog key state, many interface/widget contracts | PARTIAL | systematic roots, continue/option writers, close/cancel state machines, historical wording separation | Chat 3 |
| P1 | Items / item definitions / equipment models | item authority R5, item catalog, equipment audits, cache extraction corpus | PARTIAL | exact-current definition-field census, model/colour/retexture semantics, action/menu ownership gaps | Chat 3 |
| P1 | NPC definitions / interaction presentation | Home NPC corpus, NPC resolver, packet-65 parity | PARTIAL | exact-current definition fields, head/model/animation/GFX/menu-option presentation and unresolved NPC families | Chat 3 |
| P1 | Teleports / UI navigation | R82 teleport candidates, application/widget research | PARTIAL | exact navigation roots/buttons/configs and which destination semantics are client-visible vs server-only | Chat 3 |
| P1 | Minigame / event UI | R83/R84 Hunger Games, Clan Wars, gambling, event/task contracts | PARTIAL | consolidate per-minigame exact UI state machines and server-authority unknowns | Chat 3 |
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

1. **Rank / overhead-icon / staff-partyhat rendering**
   - high user-visible value;
   - directly adjacent to already-strong packet-81 appearance authority;
   - likely recoverable entirely from client/cache.
2. **Combat-facing client contract package**
   - consolidate attack menus, hit blocks, animations, GFX, combat interfaces,
     special-attack presentation, and client-local state.
3. **Prayer + magic package**
   - consolidate the existing R82/R83 evidence and mark every server mechanic
     that remains unknown.
4. **Dialogue/interface state-machine atlas**
   - normalize root/widget/continue/close/option contracts so Chat 3 can implement
     content without raw opcode coupling.
5. Continue assets/minigames/items/NPCs by dependency demand from Chat 3.

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
