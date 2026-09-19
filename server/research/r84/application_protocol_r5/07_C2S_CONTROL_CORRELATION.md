# Client controls correlated to outbound routing

The R3.1 menu-action audit established generic interface action 315 -> C2S185(widgetId). R5 correlates this with the newly decoded interfaces:

- Item-list ordinary button/tab/search/back controls use their widget IDs through the normal interface-button path where configured. Item-grid actions are different: menu action 632 is remapped under root 36000 to server-supplied action codes `bK` (main 36025) or `bL` (secondary 30074), default 431.
- Server-selection rows created as selectable carry action `Select` and their generated widget IDs begin at 40405.
- Active-event UI includes explicit `Vote to skip hotspot` control 62153 and event-view controls around 40096/40099; these remain ordinary widget controls client-side.
- Event Task generated action text is set dynamically by subtype10 op10; server outcome semantics remain separate.

Do not infer a server business action merely from a widget label. This file records client-facing routing surfaces only.
