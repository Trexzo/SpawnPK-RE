# Evidence Package — Exact v308 Clan Chat / Clan Wars Presentation Contract

SYSTEM

SpawnPK Clan Chat membership presentation, clan permission UI, Clan Wars rules surface, and live Clan Wars overlay state.

STATUS

STRONG-PARTIAL / PRESENTATION-AND-RULE-VOCABULARY-CLOSED / SERVER-MEMBERSHIP-AND-SCORING-UNKNOWN

## AUTHORITY

~~~
client(6).jar
SHA-256 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6
build/config 308
~~~

## CLAN CHAT PRESENTATION

Exact client surface exposes:

- `Clan Chat (0/100)`;
- `Talking in`;
- `Owner`;
- `Join Chat`;
- `Clan Setup`;
- `Manage clan member`.

Exact member-row ranges:

~~~
18144..18244
25800..25999
~~~

Together these provide 300 server-addressable text rows across the primary and management surfaces.

The rows use generic widget text presentation and are actionable with `Manage clan member` context behavior.

Do not infer that 300 is the clan-size limit. The visible `0/100` string proves a 100-member chat presentation capacity, not necessarily authoritative total-clan membership policy.

## CLAN PERMISSION VOCABULARY

Exact Clan Setup presentation includes server-owned configuration for:

- clan name;
- who can enter chat;
- who can talk in chat;
- who can kick/mute;
- who can ban in chat.

Exact visible permission thresholds:

~~~
Anyone
Any friends
Recruit+
Corporal+
Sergeant+
Lieutenant+
Captain+
General+
Only me
~~~

Exact additional option:

~~~
Add co-owner privileges to the General rank
~~~

This strongly supports semantic concepts such as:

~~~
ClanRank
ClanPermission
ClanPermissionThreshold
CoOwnerPolicy
~~~

but the client does not prove persistence format or every server-side permission check.

## CLAN WARS RULE VOCABULARY

Exact visible rule values include:

### Spell policy

~~~
All spellbooks
Standard spells
Binding only
Disabled
~~~

### Prayer policy

~~~
All allowed
Standard prayers
Disabled
~~~

### Staff-of-the-Dead policy

~~~
Allowed
No Staff of the Dead
Disabled
~~~

### Victory / kill-goal values

~~~
Last team standing
25 kills
50 kills
100 kills
200 kills
500 kills
~~~

Additional exact visible values include:

~~~
Kill 'em all
Ignore 5
Ignore freezing
PJ timer
Single spells
EdgePvP mode
~~~

The exact heading/semantic role for every early toggle is not fully recovered. Preserve any unresolved selector as an unnamed exact option rather than assigning a guessed domain meaning.

## EXACT ARENAS

Ten exact visible Clan Wars arenas:

~~~
Wasteland
Plateau
Sylvan Glade
Forsaken Quarry
Turrets
Clan Cup Arena
Ghastly Swamp
Northleach Quell
Gridlock
Ethereal
~~~

These prove client-visible arena identities, not coordinates/spawn layouts or server admission rules.

## LIVE CLAN WARS OVERLAY

Exact S2C126 target-1 control tokens:

~~~
ENABLE_CLAN_WARS_OVERLAY
DISABLE_CLAN_WARS_OVERLAY
~~~

Structured live-state targets:

### Target 28 — begin deadline

Payload is interpreted as seconds and converted to:

~~~
absoluteDeadline = now + seconds * 1000
~~~

Renderer shows:

~~~
Begin in.. mm:ss
~~~

while active.

### Target 29 — team counters

Two exact counters map to:

~~~
Your clan
Opponents
~~~

### Target 30 — counter mode

~~~
0      -> Fighters:
nonzero -> Kills:
~~~

This proves the live overlay can represent either team-size/fighter counts or kill-score counts.

## ARCHITECTURE CONSEQUENCE

Clan identity and Clan Wars match state should remain separate aggregates:

~~~
Clan
  durable membership / ranks / permissions

MatchRules
MatchTeam
MatchSession
WorldInstance
  active Clan Wars match state
~~~

Do not encode each widget selector as an independent boolean in gameplay code.

## SERVER SEMANTICS PROVEN

The exact client proves:

- a durable-looking Clan Setup surface exists;
- membership/member-management rows are server-addressable;
- explicit permission threshold vocabulary exists;
- General can receive an optional co-owner privilege mode;
- Clan Wars exposes spell/prayer/staff policies, kill-goal/victory choices and named arenas;
- live Clan Wars overlay supports enable/disable, begin deadline, two team counters and fighter-vs-kill display mode.

## SERVER SEMANTICS UNKNOWN

UNKNOWN_SERVER_AUTHORITY:

- authoritative maximum clan size;
- persistence format;
- join/invite/leave rules;
- rank mutation permissions;
- mute/ban duration and enforcement;
- exact mapping of every unresolved setup toggle;
- arena coordinates/spawn layouts;
- admission rules;
- scoring details beyond client-visible counters/modes;
- tie/disconnect/forfeit behavior;
- rewards;
- anti-abuse.

## READY FOR CHAT 3

yes

Chat 3 can map this onto ClanService + MatchRules/MatchSession/WorldInstance foundations without exposing raw widget ids or S2C126 targets.