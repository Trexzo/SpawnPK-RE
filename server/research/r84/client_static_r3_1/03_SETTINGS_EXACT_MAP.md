# Exact settings map

The R2 settings surface is now joined to its actual config routing and persistence.

The settings builder uses raw IDs `174..195`; current `Client.B(int)` remaps these to internal config indices `10..31` (`raw - 164`). The exact control widget and state branch line up with that mapping.

See `tables/settings_exact_map.csv` for all 22 rows.

Notable exact findings:

- Oldschool ticks persists as `oldschool_ticks` and emits `::newticks` when disabled / `::oldticks` when enabled.
- Instant switching persists as `instant_switching` and emits `::instantswitching` when disabled / `::queuedswitching` when enabled.
- Extended zoom persists as `extended_zoom` and calls `rs.V.a(boolean)`; a control route also exposes `::extended`.
- Lite mode persists as `lite_version`, triggers lower/reloaded graphics behavior and forces particle effects off.
- `left_click_magic_only` is read from settings on load but the exact current settings writer does **not** write that property back. This is a current-client persistence asymmetry/bug, not a LocalLab inference.
- `Show combat overlay` maps to state field `aB`, default true; no matching load/save property was found in the exact settings backend.

The settings writer performs a temporary-file write and replacement into `settings.properties` under the client settings directory.
