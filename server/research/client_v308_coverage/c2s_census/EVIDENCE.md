# Evidence Package — Exact v308 C2S Writer / Framing Census

SYSTEM
Exact-current client-to-server packet writer/framing coverage and LocalLab
semantic-coverage boundary.

STATUS
CLOSED-EXACT-FRAMING-CENSUS / PARTIAL-SEMANTIC-COVERAGE

## AUTHORITY

Primary exact-current client:

```
client build/config value: 308
SHA-256: 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
Main-Class: rs.gui.Launcher
```

Chat 4 independently rechecked the supplied v308 artifact and the prior pinned
307 artifact byte-for-byte:

```
entries old/new: 10970 / 10970
added: 0
removed: 0
changed: 1
changed entry: rs/f/a.class
```

The only bytecode-semantic delta already documented in R85 is:

```
rs/f/a.c: 307 -> 308
```

No other class or resource changes.

Therefore every post-login writer/framing fact recovered from the 307 binary
carries forward to v308 **only because the implementing classes are
byte-identical**, not because older evidence is being assumed current.

## EXACT v308 C2S CENSUS

The exact client writer census remains:

```
188 writer callsites
86 distinct C2S opcodes
0 dynamic opcode sites
```

Current LocalLab has exact framing for all 86 opcodes.

Authoritative opcode set:

```
0,2,3,4,6,14,16,17,18,21,23,25,35,36,39,40,41,43,53,57,60,70,72,73,
74,75,77,78,79,85,86,87,95,98,101,103,109,117,120,121,122,126,128,
129,130,131,132,133,135,136,139,140,141,145,148,150,152,153,155,156,
164,176,181,183,185,188,189,192,200,202,208,210,214,215,218,226,228,
230,234,236,237,246,248,249,252,253
```

The exact per-opcode framing/status table is:

```
server/research/client_v308_coverage/c2s_census/v308_c2s_coverage.tsv
```

## COVERAGE SPLIT

The 86 exact-current opcodes currently divide into:

```
51  semantically decoded / normalized transports in current main
 7  client control / telemetry families
 2  exact semantics newly closed by Chat 4 research but not current main
26  exact framing only; server semantic still unpromoted/unknown
---
86  exact-current C2S opcodes
```

The two exact research-semantic gaps are:

```
C2S40  dialogue Continue
C2S101 character design / Make-over submission
```

C2S40 is now proven from exact v308 as:
- mouse action-6 Continue -> C2S40(clickedWidgetId:u16_be);
- Space on standard Continue roots -> C2S40(4907).

C2S101 is proven from exact v308 as the 13-byte character-design payload:
- gender u8;
- seven kit selectors u8;
- five colour selectors u8.

These should be integrated through the typed request pipeline, not by restoring
legacy mutable pending slots.

## IMPORTANT C2S103 FINDING

C2S103 is correctly framed and already reaches LocalLab's generic command path.

Exact v308 additionally proves a client-native dialogue use:

```
1..5 / numpad 1..5 on standard option roots
 -> C2S103
 -> "dialogueoption N\n"
```

Current main does not have a semantic `dialogueoption` handler. So this is not
a framing gap; it is a **semantic normalization gap** inside an already-known
transport.

Issue #17 has been notified so keyboard and mouse option paths can converge on
one semantic dialogue intent without exposing raw opcode/widget identity.

## 26 FRAMING-ONLY OPCODES

Current main safely frames but does not promote server semantics for:

```
2,4,6,60,74,78,85,86,95,109,120,126,133,136,148,150,152,183,188,189,
200,210,215,218,230,246
```

These are now the residual C2S archaeology queue.

Chat 4 must inspect exact v308 writer callsites for each family and classify
them as one of:

- client telemetry/control;
- known semantic request suitable for Chat 2 typed transport;
- client-local/application helper traffic;
- exact framing with production semantic still unknown.

No gameplay meaning should be assigned merely from packet length or historical
317 conventions.

## LOCAL LAB BOUNDARY

`ClientPacketFramingAuthorityTest` still names the older pinned artifact in its
comments/test label, but its 86-opcode set remains structurally valid for v308
because the whole-JAR delta proves the post-login writer code is byte-identical.

This research package does **not** claim:
- v308 has passed the inherited 179/179 runtime gate;
- the existing test's provenance text has been updated;
- every framed opcode has domain semantics;
- production server behavior for the 26 residual families.

Those are separate implementation/acceptance tasks.

## NEXT CHAT 4 ORDER

1. Resolve the 26 framing-only opcode writer families directly from v308.
2. Prioritize any writer tied to dialogue/chatbox, item/NPC interaction or
   application UI.
3. Emit typed-transport handoffs to Chat 2 only where exact semantics are proven.
4. Keep telemetry and unknown production behavior fail-closed.
5. After C2S residuals, return to exact item/NPC definition-field census.

## READY FOR CHAT 2

**yes — framing census is closed**

Chat 2 can treat the 86-opcode framing set as exact v308 transport authority.
It should consume semantic handoffs only where this package or a subsystem
package proves them.

## READY FOR CHAT 3

**yes — semantic transports already proven; no for residual unknowns**

Chat 3 should never need to understand the 86 raw packet numbers. Residual
framing-only packets stay below the domain boundary until Chat 4 proves their
meaning.
