# R8.5 certified server artifact

This directory preserves the exact sealed LocalLab v5.18.5 / Engine R8.5
server artifact for provenance and strict baseline verification.

SHA-256:

`
589635cef6244f1282aee487fdb0649150ded5b60bdc7e3fefd28bba8a86372c
`

The Git source tree is the development authority going forward.

Compiler-matrix reconstruction established:

- JDK 11: 0/499 exact certified class entries.
- JDK 17: 142/499 exact.
- JDK 21.0.12.1: 494/499 exact.
- JDK 25: 483/499 exact.

The sealed artifact contains four class entries that are not emitted from the
current byte-identical source by any installed compiler tested, plus one
ClientPacketProbe.class bytecode difference under JDK 21. These are retained as
historical artifact provenance rather than silently injected into development
builds.

Development builds therefore use JDK 21 and are regression-certified rather
than required to reproduce the sealed outer JAR SHA-256.