# Source provenance

Primary static source:
- exact current `client.jar` from the pinned R8 CDN snapshot;
- SHA-256 `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`.

Methods:
- JAR class/resource census;
- printable-string census;
- targeted `javap -c -p` decompilation of current classes;
- enum/static-initializer reconstruction;
- UI array/condition inspection;
- cross-check against prior R1 boundaries.

Important epistemic rule:
- embedded current-client UI/data = static evidence;
- client-side schema = protocol/interface contract;
- server-selected values and outcomes are **not inferred** merely because their display surface exists.
