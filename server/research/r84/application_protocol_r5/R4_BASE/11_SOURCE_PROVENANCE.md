# Source provenance

Exact current client JAR: `/mnt/data/exactclient/client.jar`

SHA-256: `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`

Outer S2C250 subtype names/flattened grammars are carried from the already-merged R8.2 protocol master and rechecked against the exact client registry. R4 operation semantics are derived from direct `javap -c -p` / `javap -v` inspection of the exact handler/helper classes included under `evidence/`.

No network/runtime behavior was inferred where the handler does not expose it.
