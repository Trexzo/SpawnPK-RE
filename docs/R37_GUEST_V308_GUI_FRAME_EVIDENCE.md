# R37 — guest-only graphical v308 frame evidence

## Why R37

R27's original-v308 GUI smoke passed if a test-owned Java child exposed a
`MainWindowHandle`. A top-level window can belong to a launcher, updater,
blank Swing container, or native graphics failure. By itself, it does not
constitute visible evidence of what actually rendered.

R37 optionally captures **the visible main-window rectangle of the exact
owned client process** while running in the independently verified,
network-disabled Windows Sandbox guest. It retains all of R27's requirements:
WDAGUtilityAccount, canonical R25 guest HOME, genuine R26 guest-only loopback
receipt, effective R23/R24 disabled-network preflight, SHA-pinned client/JDK
and server, and manual `-Launch` plus the exact approval string.

## Manual invocation *inside a verified guest*

Plan only (no Java or window launched):

```powershell
& 'C:\SPKR23\assets\guest-gui-window-smoke.ps1' -GuestHome '<R25 guest HOME>'
```

The R37 observation mode is opt-in **in addition** to R27's existing
launch approval and must not be invoked on the host:

```powershell
& 'C:\SPKR23\assets\guest-gui-window-smoke.ps1' -GuestHome '<R25 guest HOME>' -Launch -CaptureFrame -ExactApproval 'LAUNCH R27 GUEST V308 GUI WITH LOCAL SERVER'
```

During an already authorized guest client/server trial, R37:
1. Obtains the owned Java child's `MainWindowHandle`; refuses missing,
   invisible or non-child-PID windows using `GetWindowThreadProcessId`.
2. Requires a plausible rectangular window size (360×220 through
   4096×4096 pixels); copies only that guest window's screen rectangle
   to a new bitmap.
3. Checks a 12×12 pixel sample contains at least five distinct colors
   (rejects a uniformly blank window); this is merely a *basic evidence*
   filter and does not recognize any game content.
4. If the owned client/server shutdown is clean, writes a PNG and a
   SHA256-based JSON receipt under the disposable guest-only R25 HOME.
   Files are never written into mapped host folders or live profiles.
5. The receipt explicitly declares that unmodified-original v308,
   authenticated login, game rendering and gameplay were **not** verified.
   The pinned certified derivative is the client being observed.

The existing R27 `-Launch` without `-CaptureFrame` retains its prior
behavior. `-CaptureFrame` without explicit launch approval is refused.

## Important operational limitation

Windows Sandbox is presently not available to the non-elevated connected
Windows host (WindowsSandbox.exe absent; Windows optional feature query
requires elevation). R37 **does not install Sandbox**, modify the firewall,
create an unisolated launch path, or invoke the proprietary JAR on the host.

A GUI screenshot in the isolated guest may show launcher UI or a black
render region. Pixel diversity does not prove functional graphics or actual
gameplay. Because the WSB mappings are read-only and clipboard/network
are disabled, the PNG/receipt remain **inside the ephemeral guest**; the
operator can inspect them there before shutdown. Host-side export would
require a separately reviewed transfer method, not weakening guest
networking or adding a writable host mount by default.

Static CI checks parse the PowerShell source on Windows and verify its
manual approval, process PID attribution, receipt nonclaims and guest-only
policy. They cannot establish a dynamic GUI frame was actually captured.
This remains a stacked draft PR on R36, unmerged pending full CI and
authorized real-guest acceptance.
