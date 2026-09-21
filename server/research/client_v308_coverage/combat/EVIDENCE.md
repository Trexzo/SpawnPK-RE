# Evidence Package — Combat-Facing Client Contracts

SYSTEM
Combat-facing exact-current client transport and presentation

STATUS
STRONG-PARTIAL

## AUTHORITY

- EXACT_CURRENT_CLIENT
- EXISTING_LOCAL_RUNTIME_PARITY (supporting only)

Exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
revision 308
```

This package deliberately distinguishes client-visible combat contracts from
authoritative server combat mechanics.

## CLIENT CONTRACT — PLAYER ATTACK

Exact `rs.Client` menu dispatch:

```
menu action 561
    -> C2S128
    -> player scene/index via rs.x.e.d(int)
```

Exact `rs.x.e.d(int)` writer is ordinary big-endian unsigned 16-bit:

```
u16_be playerIndex
```

The client also performs local path/menu state and can emit unrelated telemetry
side packets. Those side effects are not separate gameplay requests.

Example:

```
playerIndex = 513 = 0x0201
C2S128 body = 02 01
```

## CLIENT CONTRACT — NPC ATTACK

Exact `rs.Client` menu dispatch:

```
menu action 412
    -> C2S72
    -> NPC scene/index via rs.x.e.o(int)
```

Exact `rs.x.e.o(int)` writer:

```
u16_be_low_add128
```

Example:

```
npcSceneIndex = 131 = 0x0083
C2S72 body    = 00 03
```

because the low byte is encoded as `0x83 + 0x80 -> 0x03` modulo 256.

The exact branch also posts a client-local RuneLite `EntityInteraction` event
with `combat=true`. That event is client instrumentation, not an extra server
combat request.

## CLIENT CONTRACT — PLAYER PACKET 81 MASKS

The exact player synchronization reader first reads a low mask byte. If low bit
`0x40` is set, it consumes a second byte and computes:

```
mask = low + (high << 8)
```

So `0x40` is the exact player's **mask-extension sentinel**.

Directly recovered mask blocks:

| Mask | Exact client behavior |
|---:|---|
| `0x400` | force-movement block |
| `0x100` | GFX / spot-animation block |
| `0x008` | animation block |
| `0x004` | forced-chat string |
| `0x080` | public-chat block |
| `0x001` | interaction target |
| `0x010` | appearance block |
| `0x002` | turn-to-tile / Q,R presentation fields |
| `0x020` | hit/status update block |

### Player animation `0x08`

Exact reads:

```
animationId = S()   // unsigned LE16, 65535 -> -1
arg1        = O()   // negated unsigned byte transform
arg2        = O()
```

The exact actor state stores the animation id and the two animation-control
values. LocalLab commonly emits zero for both control values.

Example block fragment for animation 808:

```
08 28 03 00 00
```

where `28 03` is LE16 808.

### Player GFX `0x100`

Exact reads:

```
gfxId  = S()   // unsigned LE16, 65535 -> -1
packed = D()   // ordinary BE32
```

The exact client interprets:

```
height/state = packed >> 16
startCycle   = clientCycle + (packed & 0xffff)
```

Example for GFX 1310, high16 7, low16 12:

```
mask bytes: 40 01
GFX block : 1E 05 00 07 00 0C
```

The low mask byte `40` is extension signalling; the actual content bit is
`0x100`.

### Player interaction `0x01`

Exact read:

```
interactionTarget = S()  // unsigned LE16, 65535 -> -1
```

### Player appearance `0x10`

Exact read:

```
length = O()
bytes[length]
rs.a.k.a(rs.x.e)
```

This is the same exact appearance parser used by the separate Make-over and
extra-`bs` evidence packages.

### Player hits/status `0x20`

Exact structural schema:

```
count:u8
repeat count:
    valueA:u16_be
    valueB:u8
    valueC:u8
trailingM:u16_be
trailingN:u16_be
```

Each tuple is passed into the actor's hit-update method together with the current
client cycle. The exact client sets a 300-cycle presentation timeout and stores
the two trailing values into actor fields `M` and `N`.

Do not infer the original server's damage formula or hit-roll semantics merely
from this rendering structure.

## CLIENT CONTRACT — NPC PACKET 65 MASKS

The exact NPC mask is read as an unsigned big-endian 16-bit value using
`rs.x.e.A()`.

Directly recovered masks:

| Mask | Exact client behavior |
|---:|---|
| `0x100` | force-movement block |
| `0x010` | animation |
| `0x008` | repeated/multi-hit presentation block |
| `0x040` | single-hit/status block |
| `0x080` | GFX / spot-animation |
| `0x020` | interaction target |
| `0x001` | forced text |
| `0x002` | definition/state replacement branch |
| `0x004` | two `S()` actor presentation fields |

### NPC animation `0x10`

Exact reads:

```
animationId = S()  // LE16, 65535 -> -1
delay       = y()  // u8
```

### NPC repeated-hit block `0x08`

Exact structure:

```
count:u8
repeat count:
    valueA:u16_be
    valueB:u8
    valueC:u8
trailingM:u16_be
trailingN:u16_be
```

As with the player hit block, this is presentation structure, not proof of the
authoritative combat calculation.

### NPC single-hit/status `0x40`

Exact reads:

```
valueA = O() // negated unsigned byte
valueB = P() // 128-wire unsigned byte
valueC = y() // raw u8
M      = P()
N      = O()
```

The values are supplied to the NPC actor hit-update method and M/N are stored
afterward.

A known LocalLab parity fixture writes damage-like value 7 and M/N 93/100 as:

```
00 40 F9 80 00 23 9C
```

where `00 40` is the BE16 NPC mask. The byte transform itself is exact current
client authority; the labels “damage/current HP/max HP” should remain tied to
runtime/parity evidence rather than treated as proof of the original server
formula.

### NPC GFX `0x80`

Exact reads:

```
gfxId  = A() // BE16
packed = D() // BE32
```

The high/low 16-bit timing interpretation mirrors the actor spot-animation
state.

Example for GFX 1310, high16 7, low16 12:

```
00 80 05 1E 00 07 00 0C
```

### NPC interaction `0x20`

Exact read:

```
target = A() // BE16, 65535 -> -1
```

### NPC forced text `0x01`

Exact read:

```
F() // newline-terminated string
```

## CLIENT CONTRACT — COMBAT TAB ROOTS

Exact class:

```
rs.n.c.aY
```

constructs the current combat-style interfaces. Exact root IDs include:

| Family | Root | Exact visible style labels |
|---|---:|---|
| Axe | 1698 | Chop / Hack / Smash / Block |
| Dagger | 2276 | Stab / Lunge / Slash / Block |
| Sword | 2423 | Chop / Slash / Lunge / Block |
| Mace | 3796 | Pound / Pummel / Spike / Block |
| Spear | 4679 | Lunge / Swipe / Pound / Block |
| Two-handed | 4705 | Chop / Slash / Smash / Block |
| Pickaxe | 5570 | Spike / Impale / Smash / Block |
| Claws | 7762 | four-option claw family |
| Scythe | 776 | Reap / Chop / Jab / Block |
| Maul | 425 | Pound / Pummel / Block |
| Crossbow | 1749 | Accurate / Rapid / Longrange |
| Bow | 1764 | Accurate / Rapid / Longrange |
| Thrown | 4446 | Accurate / Rapid / Longrange |
| Unarmed | 5855 | Punch / Kick / Block |
| Warhammer | 6103 | Pound / Block |
| Halberd | 8460 | Jab / Swipe / Fend |
| Whip | 12290 | Flick / Lash / Deflect |
| Staff | 328 | Bash / Pound / Focus |

The same class embeds client-visible XP-mode descriptions such as
`Accurate / Slash / Attack XP`, `Aggressive / ... / Strength XP`,
`Controlled / ... / Shared XP`, `Defensive / ... / Defence XP`, and ranged
`Accurate/Rapid/Longrange` descriptions.

These are exact presentation/UI contracts. They do **not** prove authoritative
accuracy modifiers, damage modifiers, attack delays, or XP-award rules.

## SERVER SEMANTICS PROVEN

The exact current client proves:

- the player/NPC attack selection transports above;
- the exact actor synchronization mask topology above;
- animation, GFX, interaction, forced-chat/text, appearance and hit-presentation
  fields consumed by the client;
- combat-tab roots and client-visible style labels/descriptions.

## SERVER SEMANTICS UNKNOWN

Unless separate server/runtime evidence exists:

- UNKNOWN_SERVER_AUTHORITY: attack-speed/cooldown rules
- UNKNOWN_SERVER_AUTHORITY: hit chance / accuracy formulas
- UNKNOWN_SERVER_AUTHORITY: damage/max-hit formulas
- UNKNOWN_SERVER_AUTHORITY: defence formulas
- UNKNOWN_SERVER_AUTHORITY: PID/tie-breaking
- UNKNOWN_SERVER_AUTHORITY: attack-distance validation
- UNKNOWN_SERVER_AUTHORITY: line-of-sight/projectile collision rules
- UNKNOWN_SERVER_AUTHORITY: special-attack energy costs
- UNKNOWN_SERVER_AUTHORITY: special-attack effects/proc chances
- UNKNOWN_SERVER_AUTHORITY: authoritative XP awards
- UNKNOWN_SERVER_AUTHORITY: death/loot consequences
- UNKNOWN_SERVER_AUTHORITY: prayer/magic combat effects

Client-visible “Attack XP”, “Strength XP”, etc. are UI strings, not sufficient
authority for exact server XP logic.

## FILES / METHODS

Exact current v308:
- `rs.Client.a(int,int,int,int,int,String)`
  - menu action 561 -> C2S128 player attack
  - menu action 412 -> C2S72 NPC attack
- `rs.Client.a(rs.x.e)` — player mask byte + extension reader
- `rs.Client.a(int,int,rs.x.e,rs.a.k)` — player mask decoder
- `rs.Client.b(rs.x.e)` — NPC mask decoder
- `rs.x.e.d(int)` — BE16 writer
- `rs.x.e.o(int)` — BE16 low-byte+128 writer
- `rs.x.e.S()` — LE16 reader
- `rs.x.e.D()` — BE32 reader
- `rs.x.e.O()` / `P()` — transformed byte readers
- `rs.n.c.aY.a()` — combat-interface construction

Supporting LocalLab files:
- `CombatSync`
- `NpcSyncEncoder`
- `CombatPlayer81AnimationClientParityTest`
- `CombatNpc65HitClientParityTest`
- `NpcPacket65GfxCodecTest`
- `CombatNpcAttackOpcode72Test`
- `CombatInterfaceRepository`

## READY FOR CHAT 2

**yes — transport/presentation evidence**

Chat 2 can preserve the exact packet schemas while keeping raw transport
identity internal.

## READY FOR CHAT 3

**yes — client-facing combat presentation only**

Chat 3 can build authoritative combat as a protocol-independent domain system
and project results through these client contracts.

Every formula/rule listed under `UNKNOWN_SERVER_AUTHORITY` still requires an
explicit LocalLab design decision or separate recovered server/runtime evidence.
