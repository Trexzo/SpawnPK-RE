# R22 — exact original v308 native/process egress surface inventory

**Status:** independent authorized Windows bytecode audit PASS. **No original
graphical client launched**, no Java JAR bytes modified, no OS firewall changes.

## Why this matters

R21 requires outbound Windows Firewall **program rules** for both a private
`java.exe` and `javaw.exe`. This protects those two executable paths.
It does not necessarily cover a subprocess with a different executable path,
nor native components with their own process/network behavior. R22 examines
actual binary call-site declarations in the exact original v308 client and
the separately certified deterministic airgap derivative to ground the risk.

## Source and method

`tools/verification/r22_v308_classfile_native_egress_inventory.py` is a
read-only Python classfile/ZIP parser. It checks the **complete SHA256** for
both v308 inputs, verifies classfile constant pools structurally, enumerates
CONSTANT_Methodref/CONSTANT_InterfaceMethodref owners and method names,
counts Java `ACC_NATIVE` method declarations and native library archive
entries, and checks the original and patched JAR contain the same ordered
class inventory. Neither JAR is ever started or extracted.

A method reference in a constant pool means a potential call site is
represented by the compiled class. It does **not** prove the invocation
executes during startup or that an attacker can reach it. Conversely,
reflection/JNI/dynamic code may provide other call paths that are invisible
to a straightforward method-reference scan. Treat these findings as
**static evidence of potential capability**, not a malware diagnosis,
not runtime tracing and not a complete traffic inventory.

## Independent Windows measurements

| Metric | Original and certified v308 airgap |
|---|---:|
| Java classes compared | 10,472 |
| Static ProcessBuilder launch references in airgap | 1 |
| Static Runtime.exec references in airgap | 8 |
| Native library loader references in airgap | 18 |
| Network Java API method references in airgap | 133 |
| Native Java method declarations in airgap | 4,524 |
| Bundled native-library entries in airgap | 41 |
| Additional process-launch references introduced by airgap | 0 |

Exact original v308 SHA256:
`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`

Certified R11 private airgap v308 SHA256:
`024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`

A disassembly of unchanged `rs/s/n/c.class` specifically showed
`java/lang/ProcessBuilder.start()` and
`java/lang/Runtime.exec(String[])` instructions, alongside a
`sh -lc terminal-notifier -help` command argument. These are not
proof that Windows v308 startup executes a subprocess.

The audited airgap derivative had no **new** process-launch
method references versus original v308. Static analysis result:
`R22_STATIC_PROCESS_NATIVE_SURFACE_INVENTORY_PASS`.
Its JSON receipt deliberately contains
`complete_process_tree_network_isolation_proven=false`.
Source/report was run on authorized Windows in a new isolated checkout;
the local report SHA256 was
`9c417b24a9fae21bc53b1613063307c2967454f0ec21d900b03e3afbe4b60e28`.

## Automated tests and constraints

`tools/verification/test_r22_v308_classfile_native_egress_inventory.py`
uses small, manually constructed synthetic Java classfile fixtures to test
exact method-reference parsing, native declaration detection, malformed/
truncated class rejection, digest denial, class risk grouping, and an
explicit `complete_process_tree_network_isolation_proven=false` result.
CI on Linux and Windows runs these synthetic tests **without obtaining
the proprietary original or patched client JARs**.

The authentic original/airgap static inventory must be rerun on an
authorized machine that already has both SHA-pinned JARs. The source
repository contains only the inspection logic, tests and audit metadata,
not the client binaries.

## Exact meaning for graphical acceptance

- R10 continues to block GUI start while three existing v312 clients run.
- R13/R21 ActiveStore firewall rules are still not installed on the
  non-elevated connected Windows session.
- Even after the dedicated Java program rules are independently installed
  and verified, a **complete** process-tree/native network isolation proof
  remains open. A program rule does not apply automatically to arbitrary
  child executable paths. Do not turn R21 `READY` into an unqualified
  `GRAPHICAL_CLIENT_ISOLATED` result.
- No GUI launch, external network test, game login/movement/bank/combat,
  process kill, or firewall mutation was performed by R22.

This is a source-only, draft, stacked PR. It does not claim that any
native library or process API use is malicious, nor does it certify
original SpawnPK client gameplay or network security.
