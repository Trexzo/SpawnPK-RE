# Ordinary packets used alongside custom apps

Do not force all application content through S2C250. Existing exact packet families are intended to compose with it.

- S2C34 `CONTAINER_PARTIAL_UPDATE` — VAR_SHORT; partial slot mutations
- S2C53 `CONTAINER_FULL_UPDATE` — VAR_SHORT; full container publication
- S2C72 `CLEAR_WIDGET_ITEM_CONTAINER` — fixed u16_le widget id
- S2C126 — generic keyed string/control update bus
- S2C171 — widget visibility
- S2C70 — widget position offset
- S2C246 — widget item model
- S2C230 — widget model transform

For Mailbox specifically, widget32175 should use the normal container family for attachments while S2C250/31 owns inbox/claim UI state.
