# R8.5 source provenance

- Runtime baseline: exact certified v5.18.4.2 server SHA `417135d2079bd5134017fd74ee48d76a5ca2d09fdcf7d0472e24fce48c56126d`.
- Exact pinned current client SHA `6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662`.
- R8.5 source overlay was compiled with Java 11 target (`--release 11`) against the v5.18.4.2 baseline JAR.
- `ClientPacketProbe` has no complete reconstructed Java source in the cumulative source overlays. The eight remaining generic C2S decoders are inserted by the included minimal ASM instrumentation source and routed into normal Java source `R85GenericC2SBridge` / `GenericInteractionEvent`.
- R8.5 candidate server SHA `589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c`.
