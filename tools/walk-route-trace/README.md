# Exact-v308 Walk Route Trace

Diagnostic tooling for Issue #1107 / live acceptance Issue #1106.

This directory is **not release/runtime authority**. It exists only to observe the ordinary exact-v308 scene Walk-here boundary without changing movement semantics.

## Proven static boundary

Exact v308 authority:

`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`

The ordinary scene route site is inside `rs.Client.bz()V` and has this exact structural suffix:

~~~text
ICONST_1
ILOAD 1
INVOKESPECIAL rs/Client.a(IIIIIIIIIZI)Z
ISTORE 3
~~~

The boolean is therefore `fallback=true`.

The transformer inserts exactly one static observer **after** `ISTORE 3`. It does not replace or alter any route argument, return value, branch, collision array or packet writer instruction.

## Compile

Use the same JDK-internal ASM modules already used by `tools/v5131-client-hook`:

~~~powershell
javac `
  --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED `
  --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED `
  -d classes `
  src/WalkRouteTraceClassTool.java `
  src/spk/dev/WalkRouteTrace.java
~~~

## Probe an installed variant class

Extract `rs/Client.class` from the already-built LocalLab client variant and run the class tool in `probe` mode.

Required marker:

`WALK_ROUTE_TRACE_CLASS_PROBE_PASS routeSites=1 hooks=0 fallbackTrue=true postCallOnly=true variantSafe=true`

## Patch / verify

Patch only a disposable copy of the installed LocalLab client variant. Include both the patched `rs/Client.class` and compiled `spk/dev/WalkRouteTrace.class`.

Do not modify the pristine exact-v308 evidence JAR.

After patching, `verify` must emit:

`WALK_ROUTE_TRACE_CLASS_VERIFY_PASS routeSites=1 hooks=1 fallbackTrue=true postCallOnly=true variantSafe=true`

## Runtime marker

Each ordinary scene route attempt should produce:

`LOCALLAB_WALK_ROUTE_TRACE picked=X,Y start=X,Y fallback=true result=<bool> alternate=<bool> resolved=X,Y`

Interpretation:

- `result=false`: route method returned before movement packet publication.
- `result=true alternate=false`: exact picked tile route.
- `result=true alternate=true`: exact target failed and radius-1 fallback was selected.
- `resolved=X,Y`: successful client route endpoint from `gd/ge`.

Capture one open-ground click and one Scoreboard click in the same session.

No movement correction should be proposed until those two traces are compared.
