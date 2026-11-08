# R24 — SHA-verified static game cache for offline Windows Sandbox

R23 prepares a network-disabled Windows Sandbox configuration and only a
pinned server JAR, v308 airgap-client JAR and private JDK. R24 supplies the
previously missing **static game cache assets** inside that same read-only
R23 assets mapping, still without installing Windows Sandbox or launching
any Java client.

## Safety boundary

R24 stages **only an existing R12 isolated static-cache copy**, never the
real user's live `.spawnpk` profile or any `.spawnpk-data` account/session
directory. It requires the R12 copy to be an invocation-owned direct child
of the Windows Temp directory, with its original R12 signed-by-hash
inventory receipt, and an **empty** `.spawnpk-data` directory.

The source R12 cache was previously created by a strict allowlist that
excludes known identity/settings/credentials/log filenames. R24 tightens
this further: **any** unknown file or directory, `uid.dat`, unexpected
user-data entry, symlink, reparse point, or hash mismatch blocks staging.

**Limitation:** these are opaque binary game cache assets. Filename
allowlists and SHA digests do **not** certify that their contents contain no
private data, malware or executable behavior. R24 explicitly reports
`opaque_binary_content_privacy_certified=false`.

## Exact asset identity

The previously accepted independent R12 snapshot has:

- Static game cache entries: **10,764**.
- Total bytes: **209,188,225**.
- SHA256 of the sorted R12 relative-path / byte-count / file-SHA inventory:
  `37cf52440fb46c44c8671470776e5950b5bdc6a8ecf470bd52bf71c87d5208f3`.
- Required `configs/e.bin`, `configs/i.bin`,
  `main_file_cache.dat/idx0/idx1` and
  `main_file_osrs.dat/idx0`.
- No transferred `uid.dat`, personal account settings or `.spawnpk-data`
  content.

`tools/verification/r24_stage_guest_static_cache.py` **independently
rehashes all 10,764 source files before staging**, then verifies each
source/destination digest while copying to a **new** R23-compatible
read-only assets folder and rehashes every source file again afterwards.
It validates the identical LocalLab server Git tree and exact externally
supplied SHA256 for the compiled server JAR through R23's existing
source/JDK/client preflight. It never reuses or overwrites a previously
prepared R23 package.

The R23 generated `.wsb` configuration is unchanged in security scope:

- `Networking=Disable`.
- `VGpu=Disable`, `ProtectedClient=Enable`.
- Clipboard, video/audio input and printer redirection disabled.
- Exactly two mapped folders: a newly owned `assets` directory and the
  SHA-pinned private R13 Java11 JDK; both mappings `ReadOnly=true`.
- **No LogonCommand**, no automatic game, server, Java or guest execution.

The new assets are stored under
`C:\SPKR23\assets\cache\.spawnpk` **inside the guest**, not mounted from
an actual user profile. The package includes a separate
`r24-static-cache-manifest.json` with ordered per-file hashes and sizes.

## Guest-only verification

The `r24_guest_cache_preflight.ps1` script must only be run manually
**inside an actual Windows Sandbox guest**. It refuses to run outside
the `WDAGUtilityAccount` guest. Before checking cache, it runs the
independent R23 guest preflight: effective guest adapter/default route
inspection, 127.0.0.1 loopback probe, and server/client/private-JDK pins.

R24 then validates every file against the manifest, rejects missing,
extra, wrong-sized, linked or altered files, and recomputes the exact
R12 inventory digest. Success will print
`R24_GUEST_R12_STATIC_CACHE_10764_SHA256_PASS`; this marker is **not**
evidence until the script is executed inside a real guest.

The guest script does NOT run Java, start a client, connect to any
external endpoint, modify network or firewall settings, or launch the
game. Passing guest preflight also does not establish client GUI
compatibility: the missing step is a separately reviewed guest-local
client HOME/cache copy and actual game login workflow.

## Current evidence and limitations

On the authorized Windows host, R24's first exact checkout passed
**8/8 synthetic unit tests**, the native PowerShell syntax parser and
the intentional host denial exit 2. The independently staged R12 cache
passed its full 10,764-file read-only input-inventory preflight.

**Not yet executed:** Windows Sandbox itself; Windows Sandbox feature
installation; guest loopback test; actual game/client start; graphical
login, movement, bank, combat or reconnect. The host currently lacks the
enabled Windows Sandbox feature; the three existing v312 client JVM
processes must not be touched.

R24 is a source-only stacked draft PR and no proprietary JAR or game
cache content is committed to the public repository.
