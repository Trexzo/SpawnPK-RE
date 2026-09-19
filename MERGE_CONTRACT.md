# v5.8 MAINLINE merge contract

- Required installed base server SHA-256: `78fbf3a673b679c5d5a13c057edc142b8bc743c6dc1c17d06f8a985cb2cdc1c2` (v5.7.1).
- v5.8 server SHA-256: `029654e5657f108166a177bf9316a289b7c1e6e3809efcfeeef844cdabc82a2a`.
- Lane: MAIN only. Never target WORLD-LANE / WORLD-R.
- WORLD-R7 R1.1 owned HOME/collision data remains untouched.
- Existing `opensrc.properties` must be byte-identical across installation.
- No client JAR appears in the payload.
- Java classes are compiled with `--release 11` (class major 55) for the user's Java-17 runtime.
- Combat M2 numeric hit formulas remain fixture-only (100 PvP dummy / 200 PvM dummy); Scopesight combat modifiers are metadata-only until the real formula layer exists.
