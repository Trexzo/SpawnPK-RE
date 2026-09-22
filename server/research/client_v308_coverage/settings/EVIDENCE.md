# Evidence Package — Exact v308 Client Settings Ownership / Persistence

SYSTEM

Exact-current SpawnPK settings panel routing, local persistence, and server-boundary classification.

STATUS

CLOSED-CURRENT-SETTINGS-MAP / CLIENT-PERSISTENCE-AUTHORITY-CLOSED / SERVER-POLICY-SEPARATE

## AUTHORITY

Exact-current client:

~~~text
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

Normalized exact research:

~~~text
server/research/r84/client_static_r3_1/03_SETTINGS_EXACT_MAP.md
server/research/r84/client_static_r3_1/tables/settings_exact_map.csv
server/research/r84/client_static_r3_1/evidence/settings_persistence_writer.javap.txt
~~~

## EXACT CURRENT SETTINGS RANGE

The current settings builder uses raw config/varp ids:

~~~text
174..195
~~~

Current `Client.B(int)` remaps these to internal indices:

~~~text
10..31
raw - 164
~~~

This yields exactly:

~~~text
22 current settings rows
~~~

## EXACT MAP

| Raw | Internal | Widget | Label | Local property |
|---:|---:|---:|---|---|
| 174 | 10 | 12470 | Toggle/show roofs | show_roofs |
| 175 | 11 | 12480 | Fog distance | show_fog |
| 176 | 12 | 12482 | Left-click attack | left_click_attack |
| 177 | 13 | 39975 | Particle/glow effects | particle_system_1 |
| 178 | 14 | 39973 | Show news broadcasts | show_broadcasts_1 |
| 179 | 15 | 19099 | Show combat overlay | **no exact persisted key** |
| 180 | 16 | 19097 | Shift dropping | shift_drop |
| 181 | 17 | 19095 | Oldschool 07 ticks | oldschool_ticks |
| 182 | 18 | 19093 | Instant switching | instant_switching |
| 183 | 19 | 19091 | Prayer adjustments | prayer_adjustments |
| 184 | 20 | 19089 | Swap rigour/augury | rigour_augury_swapped |
| 185 | 21 | 19087 | Left click attack target only | left_click_target_only |
| 186 | 22 | 12472 | Resizable mode | screen_mode |
| 187 | 23 | 12474 | Lite mode | lite_version |
| 188 | 24 | 19085 | Extended zoom & distance | extended_zoom |
| 189 | 25 | 19083 | Desktop notifications | desktop_notifications |
| 190 | 26 | 19081 | Player lighting | player_lighting |
| 191 | 27 | 19079 | Hide other pets (non-wild) | hide_non_wild_pets |
| 192 | 28 | 19077 | Toggle pets for "bank all" | bank_all_pet |
| 193 | 29 | 19075 | Cosmetic "icons" visibility | show_icon_equip |
| 194 | 30 | 19073 | Left click magic target only | left_click_magic_only **load-only bug** |
| 195 | 31 | 19071 | Lock spawnable item dropping | lock_spawnable_drop |

## LOCAL PERSISTENCE

The exact settings backend writes a temporary properties file and replaces the persistent:

~~~text
settings.properties
~~~

under the client settings directory.

This is **client-profile persistence**.

It is not evidence that these 22 booleans/modes belong in the authoritative server player/account snapshot.

## CLIENT-ONLY / PRESENTATION-INPUT OWNERSHIP

The exact evidence supports client ownership for the settings surface itself.

Examples include:

- roof rendering;
- fog;
- particle/glow effects;
- broadcast visibility;
- shift-drop input behavior;
- resizable mode;
- Lite mode;
- desktop notifications;
- player lighting;
- hide other pets;
- bank-all pet presentation/input;
- cosmetic icon visibility;
- left-click target filtering;
- left-click magic target filtering;
- local combat-overlay visibility.

Some settings affect which request/input the client chooses to send. That still does not make the preference itself authoritative server state.

The server must independently validate the resulting gameplay request.

Example:

~~~text
client shift-drop preference
 -> chooses a drop action faster
 -> server still validates whether item drop is legal
~~~

## DUAL-SURFACE COMPATIBILITY SETTINGS

A few local settings also emit legacy commands because the production server/client pair coordinated behavior.

### Oldschool 07 ticks

Exact local property:

~~~text
oldschool_ticks
~~~

Exact compatibility route:

~~~text
disabled -> ::newticks
enabled  -> ::oldticks
~~~

The local preference remains client-persisted.

If LocalLab needs server behavior to change with tick style, normalize the command into an explicit session/gameplay preference request rather than copying the local properties file into account persistence.

### Instant switching

Exact local property:

~~~text
instant_switching
~~~

Exact compatibility route:

~~~text
disabled -> ::instantswitching
enabled  -> ::queuedswitching
~~~

Again: local preference persistence and server behavior synchronization are separate concerns.

### Extended zoom & distance

Exact local property:

~~~text
extended_zoom
~~~

Exact local effect:

~~~text
rs.V.a(boolean)
~~~

An exact control route also exposes:

~~~text
::extended
~~~

Do not assume the command means the server owns the preference. The exact client already applies a local rendering/view-distance effect.

## IMPORTANT PERSISTENCE ASYMMETRIES

### Show combat overlay

State field:

~~~text
aB
default = true
~~~

No matching exact load/save property was found in the current settings backend.

Therefore this control is current-client state but not proven persistent across client restart.

### Left click magic target only

Exact property:

~~~text
left_click_magic_only
~~~

The current client **reads** this property on settings load.

The exact current settings writer does **not** write it back.

This is a real persistence asymmetry/bug in the current client.

Do not "correct" the archaeology by pretending the exact writer persists it.

A LocalLab-specific client fix could repair the asymmetry, but that would be:

~~~text
CUSTOM_LOCALLAB
~~~

not recovered SpawnPK behavior.

## LITE-MODE COUPLING

Lite mode:

~~~text
lite_version
~~~

has direct graphics/reload behavior and forces particle effects off.

That is client presentation coupling.

Do not model Lite mode as a server gameplay flag.

## SERVER-OWNED POLICY MUST REMAIN SEPARATE

Several labels sound gameplay-sensitive:

- Prayer adjustments;
- Swap rigour/augury;
- Lock spawnable item dropping;
- Left-click attack;
- target-only modes.

The exact settings panel proves client behavior/configuration, not server trust.

If the server has an authoritative rule for:

- item dropping;
- combat targeting;
- prayer availability;
- spell targeting;
- action timing;

that rule must live in the semantic gameplay/domain layer and be validated independently of the client's local toggle.

## LEGACY / OTHER SETTING SURFACES

Earlier client-discovery lists contain labels such as:

- New hit marks;
- New HP bar;
- Multiplied 10x hits;
- Oldschool 07 graphics;
- Split private chat;
- Toggle-run.

Those are valid discovered client setting/control labels, but they are not part of the normalized current 22-row raw-174..195 settings map above.

Do not merge every historical/adjacent toggle into the exact current settings.properties schema without a separate exact routing proof.

## ARCHITECTURE CONSEQUENCE

Recommended boundary:

~~~text
ClientPreferenceStore
  -> local settings.properties
  -> rendering/input/menu behavior

CompatibilityPreferenceAdapter
  -> parses exact legacy commands where needed
  -> semantic session/gameplay preference request

AuthoritativeGameplayPolicy
  -> independently validates actions
  -> never trusts a client-side preference as permission
~~~

There is no reason to persist the entire current settings map in server account snapshots.

## READY FOR CHAT 2

yes — commands that coordinate server behavior can normalize into typed session requests.

## READY FOR CHAT 3

yes — gameplay policy should treat local settings as hints/input configuration, never authority.

## REMAINING CHAT 4 WORK

Only targeted settings routes outside the current 22-row panel when a specific subsystem requires them. The current settings map and local persistence mechanism are closed.
