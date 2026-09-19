# R5 operation closures

S2C250 remains `VAR_BYTE -> u16_be subtype -> subtype payload`.

R5 promotes eight additional handlers from outer-grammar-only to per-operation authority:

| subtype | handler | closure |
|---:|---|---|
| 1 | `rs.q.a.a.a.d` | Hunger Games/match lobby + live match presentation |
| 2 | `rs.q.a.a.a.k` | dynamic scene object override state |
| 10 | `rs.n.c.b.d` | Event Task UI |
| 12 | `rs.l.e.p` | world tile polygon/highlight overlay |
| 13 | `rs.n.c.P` | active events/hotspot/timer state |
| 14 | `rs.q.a.a.a.h` | item list/search/transfer UI (26 ops) |
| 16 | `rs.n.c.aN` | server selection list 40405 |
| 20 | `rs.q.b.e` | external login/logout receiver thread control |

See `tables/s2c250_operation_grammars_r5_additions.csv` for the exact per-operation payloads.
