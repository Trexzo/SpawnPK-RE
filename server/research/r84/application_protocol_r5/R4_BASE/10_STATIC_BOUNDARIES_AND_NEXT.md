# Static boundaries after R4

## Now exact enough to implement on the client-facing side
- generic ScriptPacket250 framing and typed subtype dispatch
- mailbox row/read/claim/selection UI state
- own Tradepost listing add/remove presentation
- Make-X dynamic interface
- shop tab labels/selection
- chapter reward-card and claim presentation
- generic confirmation/selection dialogs
- dynamic widget action strings
- raid party/action/timer/points presentation
- custom magic presentation toggles
- generic client state maps and structural NPC runtime override fields

## Still server-owned
Mail persistence/expiry/reward ownership; marketplace matching/taxes/economy; shop stock/prices/currencies; chapter reward eligibility; Make-X recipes/outputs; raid encounters/loot/eligibility; custom magic combat effects; NPC override business meaning where not evidenced.

## Highest-value next static lanes
1. Per-operation decode subtype14 Item List/Search/Transfer (26 operations).
2. Event Task UI subtype10 and active-event/hotspot subtype13.
3. Dynamic scene object override subtype2 and world polygon highlight subtype12.
4. Matchmaking/report subtypes1/20 and server selection subtype16.
5. Correlate exact C2S185 widget controls with the now-decoded application states.
