# S2C126 generic update/control bus

The current client parses S2C126 as a string plus integer target key. Before applying the large global string-token dispatcher, it iterates registered interface controllers and invokes their `a(int,String)` hook.

This gives three distinct interface-population patterns:

1. **Controller target-key updates** — a panel overrides `a(targetKey,payload)`.
2. **Global string control tokens** — the S2C126 string itself selects a client behavior, sometimes carrying arguments.
3. **Normal widget/container packets** — a screen has no custom S2C126 payload decoder and receives normal text/item/widget updates instead.

## Collection Log

`rs.n.c.v` is the recovered concrete current controller override:

- target `54315`: payload ignored; clears the text on odd row widgets `54315..54413`.
- target `54421`: payload is a decimal widget id; selects one row across even widgets `54314..54412` and resets scroll widget `54417`.
- target `54422`: payload is a decimal widget id; selects category widget `54302..54306`, resets category scroll `54313`, and clears row selection state.

See `tables/collection_log_s2c126_targets.csv`.

## Marketplace

The global dispatcher provides exact client controls including `clear_exchange`, `add_exchange`, `update_exchange`, `clearsellmarket`, `clearbuymarket`, and `setsellitem,<id>`. These describe client list/update behavior but do not reveal the production marketplace database or prices.

## Item Library / Knowledgebase

`ITEM_GUIDE_SELECTED_<widgetId>` and `WIKI_SELECTED_<widgetId>` are true argument-bearing controls: they parse the suffix as a decimal widget ID and move the corresponding selected-row image marker. `ITEM_GUIDE_BONUS_WIDGET <ON|...>` controls bonus-widget visibility.

See `tables/s2c126_argument_control_routes.csv`.

## Mailbox / Party

Mailbox has a concrete 35-slot list/detail UI builder and attachment/claim-state helpers, but no recovered top-level custom S2C126 payload override. Party/raid member rows likewise have a concrete local updater but no hidden S2C126 membership payload decoder. Those systems therefore remain normal widget/update contracts plus client helper logic; production row/member data is server authority.
