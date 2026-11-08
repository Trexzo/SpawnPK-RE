# R21 — guarded Windows egress preparation for private v308 graphical acceptance

**Status:** read-only audit and installation **plan** verified on the owner's
Windows desktop. No firewall rule installed. No graphical v308 client
launched. Three existing owner-JDK v312 Java processes left running.

## Why R21 exists

R13 checks a single private `java.exe` against a Windows Firewall
ActiveStore outbound block. A GUI can also be launched using
`javaw.exe`; blocking only `java.exe` is not a complete policy for a
dual-entrypoint JDK. R21 requires **two independently scoped exact
program-block rules** and pinned private executable bytes.

Only a separately copied, invocation-owned private R13 Java 11 JDK may
be used. The installed JDK shared with real v312 clients must NEVER be
selected as the firewall rule's `-Program`.

## Read-only audit

`tools/verification/r21_windows_dual_java_egress_gate.py` independently
checks, for *each* Java entrypoint:

- Exact SHA256 of the private `java.exe` or `javaw.exe`.
- R13-owned JDK snapshot path inside system TEMP, without a symlink.
- The selected executable is not used by any currently running Java JVM.
- An exact named Windows Firewall **ActiveStore**, enabled outbound
  **BLOCK** rule matching that executable, every protocol, remote
  address and port, and all enabled Windows Firewall profiles.
- Firewall service remains running.

The exact R13 legacy `java.exe` rule display name is retained;
`javaw.exe` has a separate R21 display name. If either rule is
missing or cannot be verified, the audit returns
`R21_DUAL_PRIVATE_JAVA_OS_EGRESS_BLOCKED`, exit code 2.

## Operator-only firewall installation

`tools/verification/r21_plan_or_install_private_java_firewall.ps1`
has two modes. The **default** is a fully read-only plan. It checks
the dedicated JDK location, exact Java11 binary hashes, running Java
processes, active firewall service/profile state and existing rule names.
It reports `R21_TWO_PRIVATE_JAVA_FIREWALL_PLAN_READY` without
changing any OS rule.

Example, from a normal non-elevated PowerShell:

```powershell
$repo = 'C:\path\to\isolated\SpawnPK-RE'
$privateJdk = Join-Path $env:TEMP 'spawnpk-r13-owned-jdk-<specific-id>\jdk'
& "$repo\tools\verification\r21_plan_or_install_private_java_firewall.ps1" -PrivateJdkRoot $privateJdk
```

**An install is never automatic and never elevates its own privileges.**
If the owner later explicitly authorizes the OS change, the
`-Apply` mode requires an **already elevated** PowerShell session
and the literal `-ExactApproval` phrase
`INSTALL R21 TWO PRIVATE JAVA OUTBOUND BLOCK RULES`.
It refuses any duplicate/conflicting rule name instead of overwriting
rules, and only adds two new, program-specific outbound BLOCK rules.
It does not change firewall profile defaults or enable/disable the
firewall itself.

The script then invokes the independent Python read-only dual audit.
A failed audit does **not** remove a just-created firewall BLOCK rule
automatically; operator review is required. No game process is started.

## Verified owner Windows facts (2026-11-08)

- Owner machine Windows 11 Pro, but Desktop Commander is **not elevated**.
- Three pre-existing Java processes use a separate SpawnPKOwnerDev JDK17.
- Dedicated R13 Java11 snapshot available, private `java.exe` pin
  `b2484c2889365b93fb48434e12c8f9bc21ca6cd5d4eb2eae39a46b17ced3c8ef`.
- Private `javaw.exe` SHA256
  `0a4656c826032ffe28d58ebfa93e85d4c099c50a1857f29416461848f0c6adaa`.
- Both exact program rules absent from the firewall ActiveStore.
- The script returned `R21_TWO_PRIVATE_JAVA_FIREWALL_PLAN_READY`;
  relevant rule count remained zero before/after.
- Independent dual audit found both private binaries valid and unused,
  but returned `R21_DUAL_PRIVATE_JAVA_OS_EGRESS_BLOCKED`.
- The earlier R11 deterministic builder independently reproduced the
  SHA-pinned certified v308 airgap derivative
  `024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`
  from the original v308 client JAR without starting any game client.

## Boundaries and next acceptance gate

**Firewall program rules are not a complete egress sandbox.** Other
processes created by native/client code can have their own executable
identity, and Windows loopback treatment and effective enforcement must
be independently demonstrated. A successful R21 audit is not permission
to launch a GUI or proof that `127.0.0.1:43594/43595` remains
connectable. Windows Sandbox was not installed on this machine.

Even if both firewall rules are later verified, R10 separately refuses
a graphical test while existing SpawnPK client processes are present.
R21 does not relax that fence or terminate existing v312 sessions.
The graphical client, authenticated login, movement, bank, combat, loot,
XP and reconnect remain unverified, as does real SpawnPK production
fidelity. R21 is a source-only draft stacked on R20.

Microsoft documents per-program outbound rule scope in
[New-NetFirewallRule](https://learn.microsoft.com/en-us/powershell/module/netsecurity/new-netfirewallrule).
