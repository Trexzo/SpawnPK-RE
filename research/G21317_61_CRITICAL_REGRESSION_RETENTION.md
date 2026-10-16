# G21.317 — cumulative 61-class critical regression retention guard

Exact source parent [#2874](https://github.com/Trexzo/SpawnPK-RE/pull/2874) `4700f1413d01f5e6a380ec6b3433267dfae3f012`: 741 focused candidate combining independently hosted certified [#2872](https://github.com/Trexzo/SpawnPK-RE/pull/2872) `4802322b02b25a11c1ec79e033c0dbbe851655cd` **737/737**, plus two account inode G21313–14 tests and two genuine TCP final SaveTask G21315–16 tests. No changes to original 49 G21.308 safety anchors.

## Actual release hazard
The manifest verified exact count and uniqueness, and G21.308 separately required 49 named critical safety tests. New production/failure scenarios G21309–G21316 and Chat2 G1981–G1984 are in the 741 class manifest but **not in any named MUST-RETAIN family**. A concurrent integration that replaces any with an unrelated test without changing total count could get a superficially green train that never reruns that regression.

## Correction
Keep the exact existing 49 unchanged. Add two named Gradle configuration-phase MUST-RETAIN families:
- 8 guarded World/final-review negative witness tests G21309..G21316 (before temp allocation, before rename, actual postrename SaveTicket, selected foreign control, afterforce inode/link drift, real TCP final worker).
- 4 real canonical settled NPC origin × six-consumer regression tests G1981..G1984 (eviction, same-name rebind/disconnect, G1/G14 failures, collector fence).
All **61/61** independently verified present and unique in the actual exact 741-test manifest prior to commit. Any future integration dropping an anchor fails Gradle configuration with its class name. The focused list remains 741 unique; no test production or gameplay behavior changed.

**Gate:** independent hosted exact-head 741/741 with Gradle 61 critical anchors, Java11 major55, complete Gradle. Keep draft/unmerged and main unchanged. Neither a green build, negative final-review witness, nor callback summary grants durable owner epoch, final-save ACK, safe recovery or replay. #2540/#2559/#2587 open.
