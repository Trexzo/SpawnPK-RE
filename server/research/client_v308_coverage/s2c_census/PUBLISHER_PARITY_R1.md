# Exact v308 S2C Publisher Parity R1

SYSTEM

Current-main LocalLab publisher capability compared with the exact v308
75-opcode handled S2C client surface.

STATUS

PARTIAL / VERIFIED-CAPABILITY-LOWER-BOUND

## AUTHORITY

Exact-current client:

```
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
```

Exact handled S2C surface:

```
75 opcodes
63 fixed
8 VAR_SHORT
4 VAR_BYTE
```

This R1 compares that client capability with concrete current-`main` server
emitters. It deliberately distinguishes:

- **publisher capability exists** — LocalLab has an exact encoder/emitter path;
- **feature composition exists** — gameplay/content actually invokes it correctly;
- **original server policy recovered** — why/when SpawnPK production emitted it.

Only the first is being asserted here unless separately stated.

## VERIFIED CURRENT-MAIN PUBLISHER CAPABILITY

### SceneUpdatePublisher

Current `main` directly emits exact client packets for:

```
S2C4    local tile spot graphic
S2C44   ground item add
S2C64   clear local 8x8 zone
S2C84   ground item quantity update
S2C85   local-zone base
S2C101  local object remove
S2C105  positional sound
S2C117  projectile
S2C151  local object spawn
S2C156  ground item remove
S2C160  local object animation
S2C174  sound effect
```

Source:

`server/src/spk/local/SceneUpdatePublisher.java`

These are not merely framing helpers. The publisher owns exact field transforms
and scene-coordinate handling, including local-zone base publication where
required.

### Bootstrap / core presentation

Current `main` contains exact encoder/publisher capability for:

```
S2C27   open amount input
S2C36   varp small update
S2C53   full widget item-container update
S2C65   NPC update
S2C71   sidebar interface assignment
S2C73   static region change
S2C81   player update
S2C87   varp large update
S2C97   main interface open
S2C106  selected sidebar tab
S2C109  logout
S2C110  run energy
S2C126  widget text / SpawnPK control bus
S2C134  skill update
S2C164  chatbox interface open
S2C208  walkable interface
S2C219  close interfaces
S2C248  main + sidebar interface open
S2C249  local-session flag + player index
```

Primary sources:

- `server/src/spk/local/BootstrapPackets.java`
- `server/src/spk/local/LocalSessionBootstrapPublisher.java`
- `server/src/spk/local/ApplicationBus126Publisher.java`
- `server/src/spk/local/AuthorityR16R25Publisher.java`
- `server/src/spk/local/BankState.java`
- `server/src/spk/local/LocalSessionUiActionHandler.java`

Several are actively used by bootstrap/runtime flows; others are exact reusable
encoders available to semantic adapters.

### SpawnPK application bus

Current `main` has a generic exact S2C250 writer:

```
S2C250  VAR_BYTE -> u16_be subtype -> application payload
```

Source:

`server/src/spk/local/ApplicationPacket250Writer.java`

The existence of the generic writer does not mean every v308 application
subtype has been recovered or composed.

## VERIFIED LOWER BOUND

The union above is:

```
32 / 75 exact handled S2C families
```

with concrete current-main publisher/encoder capability verified directly.

This is a **lower bound**, not a claim that the other 43 are absent. Additional
emitters may exist in feature-specific classes and must be audited before a
missing-publisher claim is made.

## HIGH-VALUE UNVERIFIED FAMILIES

The next parity pass should focus on families that unlock user-visible
application/content work:

### Interface/widget presentation

```
8    widget static model
24   flashing sidebar tab
34   partial item-container update   partial item-container update
68   reset varps to defaults
70   widget position
72   clear widget item slots
75   widget NPC model
79   widget scroll position
122  widget color  widget color
142  sidebar-overlay open
171  widget visibility
185  widget local-player model
187  name-input open
200  widget animation
218  dialog/chat-area root
230  widget model rotation/zoom  widget model rotation/zoom
246  widget item model
```

### Social/chat

```
50   friend presence update
104  player interaction option
196  private message receive
206  chat-mode state
214  full ignore list
221  friend-server connection status
253  server message/control text
```

### Player/world status

```
1    reset actor animations
61   multicombat state
68   reset varps
74   music track
78   destination reset
99   minimap state
107  camera reset
114  system-update timer  system-update timer
121  queued music
166  forced camera position
176  welcome/login metadata
177  forced camera look-at
240  weight
254  hint icon
255  hit/block-drop popup event
```

These are not being called missing yet. They are the next exact-current parity
audit queue.

## APPLICATION/DOMAIN BOUNDARY

A verified packet emitter is not a gameplay implementation.

Examples:

- having S2C117 projectile support does not prove combat projectile timing;
- having S2C50 friend presence support would not prove friend persistence/privacy;
- having S2C218 dialog-root support would not prove dialogue branch semantics;
- having S2C250 transport does not prove Tournament/Slayer/etc. application state.

Chat 4 supplies presentation authority.
Chat 2 supplies safe transport/runtime plumbing.
Chat 3 supplies semantic gameplay/domain behavior.

## NEXT

1. Locate current-main emitters for the high-value interface/widget families.
2. Then social/chat.
3. Mark exact verified support in `v308_s2c_coverage.tsv`.
4. For genuinely absent publishers, hand exact field schemas to Chat 2 rather
   than inventing one-off feature packet code.
5. Link application-specific packet requirements into Issue #427.


# R2 Update — Exact Widget Publisher Gap Closure

Current exact-v308 handled S2C parity ledger is now finite:

```
75 total handled client S2C opcodes

32 IMPLEMENTED_OR_AUTHORITY_PRESENT_CURRENT_MAIN
16 CURRENT_MAIN_GENERIC_PUBLISHER_GAP_R2
27 NO_COMPLETE_MAIN_PARITY_CLAIM
```

The 16 exact generic publisher gaps are:

```
8,24,34,70,72,75,79,122,142,171,185,187,200,218,230,246
```

Their exact v308 field order/transforms are closed in:

```
WIDGET_PUBLISHER_GAP_R2.md
widget_publisher_reader_map_v308.txt
```

Chat 2 implementation is tracked by Issue #513.

Important: the remaining 27 rows are **not** being called missing. They remain
unresolved parity until their current-main publisher/use surfaces are audited.

R2 exact-client schema recovery used the real Library `client(6).jar` and
reverified SHA-256:

```
854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
```
