# R27 — exact v308 GUI bootstrap and manually gated guest-only window trial

R26 proved **real LocalLab Java11 localhost server startup** and a clean
test-token JVM shutdown on the authorized Windows host. It did **not**
start the proprietary graphical v308 client or run Windows Sandbox. R27
addresses the next *specific* technical requirement: **which class
really starts v308's GUI?** A mistaken entrypoint could make a "launch
test" exit successfully without rendering an actual game window.

## Findings grounded in actual original and airgap client bytecode

Inspected the following separately SHA-pinned v308 packages **without
executing or extracting their classes**:

- Original v308 `client(6).jar` SHA256
  `854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`
- Certified R11 v308 airgap derivative SHA256
  `024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`

Both contain exactly **10,472 classes**. Their JAR manifest specifies
`Main-Class: rs.gui.Launcher`. The launcher `main(String[])` calls
`rs.gui.M.a()` (Swing initialization) followed by `rs.p.a.a()`.
The latter uses Guice `createInjector`/`getInstance` and invokes a
`rs.ui.f` client UI controller. That controller calls
`SwingUtilities.invokeAndWait`/`invokeLater`, so the actual GUI
bootstrap is **indirectly constructed through dependency injection**.

The GUI-related `rs/gui/Launcher.class`, `rs/p/a.class`,
`rs/ui/f.class` and `rs/Client.class` all have Java class major **53**
(Java 9 classfile level; loadable by the R13 private Java11 runtime).

Both original and airgap packages contain matching, byte-identical
Windows x64 native entry contents at these exact JAR paths:

- `windows/x64/org/lwjgl/lwjgl.dll`
- `windows/x64/org/lwjgl/opengl/lwjgl_opengl.dll`
- `com/sun/jna/win32-x86-64/jnidispatch.dll`
- `net/runelite/rlawt/windows-amd64/rlawt.dll`

**This is only a static bootstrap contract.** It does NOT prove dynamic
execution reaches all stages, Windows Sandbox supports OpenGL, a window
is created, native libraries load successfully, the modified v308
client can authenticate, or any gameplay works. R23 deliberately
disables vGPU, which may stop the client rendering.

The new read-only tool
`tools/verification/r27_v308_gui_bootstrap_audit.py` checks exact
full input hashes, manifest, Java majors, required method references
and native binary hashes against both JARs. It emits
`R27_PINNED_V308_REAL_GUI_BOOTSTRAP_STATIC_AUDIT_PASS` with explicit
`client_gui_started=false` and `opengl_rendering_verified=false`.
Synthetic negative tests deliberately reject a wrong manifest,
bad class major, missing Guice refs, changed native library and
wrong JAR SHA256. No proprietary client bytes are included in CI.

## New guest-only manual GUI trial

`tools/verification/r27_guest_gui_window_smoke.ps1` is **not**
a host launcher. It refuses execution unless the Windows guest account
is `WDAGUtilityAccount`, its HOME is a verified new R25 directory
under the guest's own Temp path, and a successful **real guest-only
R26 server/loopback receipt** exists inside that same HOME. It reruns
the effective guest R23/R24 no-route, localhost loopback, cache and
client/server/private-Java11 SHA preflight before any process can start.
It also checks the staged R27 static bootstrap audit receipt.

The default invocation is a **plan only**, with NO subprocesses.
A separate explicit `-Launch` and exact approval phrase is required
to try any graphical execution. When authorized *inside the guest*,
it starts two **owned child processes** with disposable synthetic data:
one LocalLab Java11 server on local 43594/43595 using a fresh
private R14 stdin-shutdown token, and one exact R11 airgap-v308
`java -jar` client with `-Duser.home` set to the isolated guest
R25 profile.

It checks that the server owns both 127.0.0.1 listeners, AUX serves
`/spk_live/versions.txt`, and the client child exhibits a **real
top-level Windows main-window handle** within a bounded observation
period. It does NOT type a username/password, attempt login, send
combat packets or make a socket to an external IP. It then closes
only these two owned child process objects, using the server's private
stdin token for its normal shutdown. A forced cleanup or missing
window returns BLOCKED, **not PASS**.

This would be a real GUI-window *smoke only*, not game rendering
quality, authenticated login, movement, bank, combat or PvM parity.
No VM-side success is claimed unless actually observed in a guest.

## Review-only offline package and manual instructions

`tools/verification/r27_guest_gui_package.py` reuses R26 and
R12's source-cache SHA pins and creates a **new** temporary Sandbox
package, adding only two files to the existing read-only assets:
`guest-gui-window-smoke.ps1` and
`r27-v308-gui-bootstrap-audit.json`. It preserves R23's exact
two read-only host mapped folders, `Networking=Disable`,
`ProtectedClient=Enable`, disabled vGPU/clipboard/peripherals,
and **no LogonCommand**. It names the resulting guest config
`SpawnPK-R27-NetworkDisabled-GuiWindow-REVIEW-ONLY.wsb`.

After a future separately approved Windows Sandbox installation
and actual guest isolation check, the operator would:

1. Review/open the pinned R27 `.wsb` file, with no automatic startup.
2. Perform R25 guest cache hydration and R26 guest-only server
   loopback smoke, collecting their successful receipts.
3. Run the R27 **plan only**:
   ```powershell
   & 'C:\SPKR23\assets\guest-gui-window-smoke.ps1' -GuestHome '<guest-R25-HOME>'
   ```
4. Only by separate conscious approval, start a **bounded guest GUI
   window trial** (not an authenticated login):
   ```powershell
   & 'C:\SPKR23\assets\guest-gui-window-smoke.ps1' -GuestHome '<guest-R25-HOME>' -Launch -ExactApproval 'LAUNCH R27 GUEST V308 GUI WITH LOCAL SERVER'
   ```

The two script arguments and final outcome are evaluated inside
the isolated guest, never on the Windows host. **No host firewall
or Windows feature modification is part of the code.**

## Evidence/nonclaims and next step

The R27 static GUI/Guice/native audit has been run on exact original
and airgap v308 binaries on the authorized Windows machine, returned
`R27_PINNED_V308_REAL_GUI_BOOTSTRAP_STATIC_AUDIT_PASS`, exit0.
The first seven synthetic static regression tests also passed.
Further exact-head CI and offline artifact acceptance are recorded in
PR review comments, not implied by this document.

**Open gates:** Windows Sandbox feature is not enabled on the
connected host; the actual guest has never run. No authentic v308
GUI window, graphics renderer, native library execution, login,
server connectivity from the client, bank/combat/XP/reconnect
has been observed. No known live client profile has been mapped
or stopped; PR stays stacked, draft and unmerged.
