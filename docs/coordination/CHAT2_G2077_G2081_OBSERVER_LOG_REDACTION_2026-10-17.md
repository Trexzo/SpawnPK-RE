# Chat2 G2077–81 — sanitized post-settlement NPC observer errors

Exact parent is Chat1+Chat2 [#2957](https://github.com/Trexzo/SpawnPK-RE/pull/2957), commit `783cd3b9ab046f8746b5f2899b4894c81eeb4c7a` (891 candidate; CI independently required). This patch changes a concrete canonical CUSTOM_LOCALLAB runtime defect, not another journal parser.

## Actual source risk
After ground-item settlement and permanent canonical NPC removal, `MonsterSpawnerPvmRuntime.settlePending()` caught `Throwable`, then concatenated `failure` in stderr. Java concatenation invokes `Throwable.toString()`; error messages, causes or suppressed values can contain sensitive account/player data and a malicious Throwable subclass can throw from `toString()` INSIDE the nonfatal observer failure handler, breaking the intended FINALIZED boundary.

## Minimal runtime correction
Keep the existing `catch(Throwable)` behavior, exactly one terminal observer invocation, drop receipt, retired NPC, bounded diagnostic history, and no automatic consumer replay. Emit only `failureType=failure.getClass().getName()` along with canonical NPC id and death tick; NEVER call Throwable.toString/getMessage, enumerate causes or suppressed exception values. A returned observer emits no false failure line. The bounded ring already captures immutable type-only failure evidence. Unknown partially-applied G1/G4/G14/G15/G16/G18 consumers remain UNKNOWN, not acknowledgements.

## Real runtime regressions
- G2077: genuine lethal NPC + observer IllegalStateException with private token; stderr has only type, terminal still FINALIZED.
- G2078: genuinely settled NPC + AssertionError subclass with malicious toString throwing if invoked; redaction prevents secondary exception, records type and leaves ground settlement untouched.
- G2079: RuntimeException private message + secret cause and suppressed; no secret leak, ring retains only class.
- G2080: nonfatal Error with secret message; type-only logging and no retryable terminal.
- G2081: normal observer return; no fabricated failure entry or log, one settlement and callback.

Five new focused tests and individual JavaExec gates; critical retention; **891→896 unique**. Existing real World/NPC graph test harness gets a reusable injected observer log capture helper that restores stderr in finally. No account, Windows workflow, original v308 client, drop table, reward stage behavior, or gameplay consumer source touched.

## Nonclaims
No authenticated original server NPC event ID, safe cross-process owner epoch, atomic per-consumer grant+ACK, directory fsync or replay authority. #2587 still open; original SpawnPK credit attribution #2558 remains independent. Hosted exact-head 896/896 Java11 bytecode major 55 and full Gradle, G2077–81 PASS, Windows source-only negative release checker required. Draft/unmerged; main unchanged.
