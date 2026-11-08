# R25 — guest-local writable v308 HOME hydration (no client launch)

**Goal:** R24's full 10,764-file v308 game cache is available from a
**read-only host mapping** in a hypothetical network-disabled Windows
Sandbox guest. The original graphical client could require creating or
updating cache/index files; it must not write through to the host or to
any existing v312 user's HOME. R25 supplies the missing manual,
guest-only *writable copy* step.

This is preparation for future **authentic graphical** v308 admission,
not evidence that graphical admission has occurred.

## Exact host inputs

R25 extends the existing R24 package in a **new invocation-owned**
temporary directory, after performing the identical source/file
integrity checks. The official source remains the SHA-pinned isolated
R12 cache snapshot:

- **10,764 allowlisted static files**, total **209,188,225 bytes**
- sorted path, byte count and per-file SHA-256 inventory
  `37cf52440fb46c44c8671470776e5950b5bdc6a8ecf470bd52bf71c87d5208f3`
- exact v308 airgap JAR SHA-256
  `024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`
- pinned LocalLab server JAR SHA-256
  `b282147d3e74e32c0587ffa04e47183fb39d17a384562b880421a30a918c3a45`
  after independent exact server-source-tree identity confirmation
- dedicated R13 Java 11 JDK with both executable SHA-256 pins.

`tools/verification/r25_guest_home_package.py` uses R24's
host-only staged snapshot and inserts
`tools/verification/r25_guest_writable_home.ps1` as
`C:\SPKR23\assets\guest-private-home.ps1` inside the **same read-only
assets mapping**. The resulting configuration is named
`SpawnPK-R25-NetworkDisabled-WritableGuestHome-REVIEW-ONLY.wsb`.
The tool refuses wrong source SHA, unrelated data, a modified R24
manifest, unverified client/server or extra/writable mapped folders.

The .wsb still configures `Networking=Disable`, disables vGPU,
clipboard/audio/video/printer redirection, enables protected client,
maps only the invocation-owned assets and private JDK **read-only**,
and contains **no LogonCommand**. No third host folder is added.
The original unpatched proprietary client, live account files and
real `C:\Users\...` profile are **not** mapped.

## Separate manual operation INSIDE a future Sandbox guest

R25 **does not run automatically**. If a future Windows Sandbox
guest becomes available and its network/loopback isolation is
independently verified, an operator may explicitly invoke:

```powershell
# First just inspect, no file copies:
& 'C:\SPKR23\assets\guest-private-home.ps1'

# Only with explicit intent to create a guest-local private cache copy:
& 'C:\SPKR23\assets\guest-private-home.ps1' -Hydrate `
  -ExactApproval 'CREATE R25 PRIVATE GUEST HOME'
```

The script refuses any execution outside the guest's
`WDAGUtilityAccount`, runs the full R24 guest SHA/route/loopback
verification before copying anything, validates its 10,764-entry
manifest, and only creates a previously nonexistent, unique
`spawnpk-r25-guesthome-<random-guid>` directory under the guest's
OWN Temp area. It creates a new `.spawnpk` cache and an empty
`.spawnpk-data` directory, copies **only** permitted static assets
inside this newly owned directory, clears the copied cache files'
read-only attributes, then hashes every destination and the complete
R12 inventory. It rejects identity files, changed or missing cache
entries, extra account files and any unsafe relative path.

Only the new invocation-owned guest Temp output is eligible for
failure cleanup. It never deletes, rewrites or maps a live host
profile/cache, opens a remote address, changes OS firewall rules,
starts the server or launches the client.

On a future successful guest run it will print
`R25_GUEST_PRIVATE_WRITABLE_HOME_10764_SHA256_PASS` and write a
JSON receipt to the guest-only profile. **That success marker has NOT
yet been observed**, because Windows Sandbox is not installed on the
connected Windows machine.

## Tested evidence and limitations

R25's synthetic, host-safe regression tests verify that an R24 package
can be extended without any additional host mapping or LogonCommand,
and that a corrupted inherited manifest, network-enabled WSB, missing
guest script and a pre-existing target are rejected. Native Windows
PowerShell parsing and intended host execution denial (exit 2) are
tested separately from static Python unit tests.

No guest has been created or launched. A username check alone is not
a hypervisor security attestation. The full 10,764-file guest hydration
copy, guest TCP loopback reachability and guest network isolation are
**untested until a real Sandbox exists**. The copied cache is opaque
binary data, and R25 does not certify its contents as free from
sensitive data or harmful behavior; source-level filename allowlists
and per-file SHA-256 checks do not establish that.

Authentic v308 graphical GUI rendering, Java runtime startup,
synthetic-only login, movement, bank, combat, XP and reconnect
remain separate follow-up milestones. R25 is a draft PR stacked on R24,
not a release or merge into the default branch.
