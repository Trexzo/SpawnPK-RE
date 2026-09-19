# Event Task UI and Active Events

## Subtype 10 — Event Task UI

Eleven operations are decoded:
- reset task UI and selection state
- append a formatted task row
- set percentage progress graphic (367 px * percent / 100)
- toggle widget 57223 visibility
- toggle root 57220 state and reset client hover state
- update an explicit task row by index
- select a string-keyed task and restore its saved scroll position
- finalize/reflow generated task widgets and clear unused rows
- directly set the paired scroll offsets
- reset scroll offsets and remove the current string-keyed saved position
- replace or append (`old + newline + new`) the current action text and enable it

Task-row strings are parsed by the client's own rich markup language (`{DD}`, `{NPC}`, `{X}`, `{Y}`, `{A}`, `{SIZE}`, `{LABEL}`, `{line}`, etc.). R5 preserves that parser as client authority but does not invent server task definitions.

## Subtype 13 — Active Events / Hotspot

- op1: `i64 delayMs + string hotspotName` -> hotspot expiry/name
- op2: `i64 delayMs + i32 itemId + i32 amount + string label` -> tournament/event card timer, item stack and label
- op3: `i64 delayMs` -> Blood LMS-side timer
- op4: `i64 delayMs` -> Golden HG-side timer
- op5: `string globalBossName + i64 delayMs`
- op6: `string wildyBossName + i64 delayMs`
- op7: `i64 delayMs` -> Event Brawl timer

The rendering layer proves the two named boss rows and the `Event Brawl` row. Event selection and activation logic remain server authority.
