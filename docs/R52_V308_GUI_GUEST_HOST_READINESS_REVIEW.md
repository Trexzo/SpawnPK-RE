# R52 — static v308 GUI guest readiness review

## Scope

R51 is independently CI-verified, but an actual original graphical v308 or airgap-derivative GUI session is **not** verified. The review-only R44-style Windows Sandbox bundle was refreshed locally with the exact R51 server built from commit `8c2163e04063f0dced64521651a5dea6e6ce6c4b`. Its server SHA256 is `51c4c3683b44d9131118c40d28e03b4dc3473e2156219b1e05fca0e0cfdfb9ab`.

Older R44-generated JSON keeps the historical `R43_PINNED` label even when used with another exact source. R52 therefore **does not infer the version from that label**. It crosschecks an operator-provided exact source SHA, exact server SHA, asset pins, receipt, and on-disk binaries. It separately checks whether the Windows Sandbox executable is physically present, without guessing whether the optional Windows feature is enabled.

## Read-only host command

Use from the R52 checkout in a normal Windows PowerShell session. This does **not** start a guest or Java and does **not** modify the OS.

```powershell
& .\tools\verification\r52_review_v308_gui_guest_host.ps1 `
  -ReviewRoot '<private staged R51 package root>' `
  -ExpectedSourceHead '8c2163e04063f0dced64521651a5dea6e6ce6c4b' `
  -ExpectedServerSha256 '51c4c3683b44d9131118c40d28e03b4dc3473e2156219b1e05fca0e0cfdfb9ab'
```

A successful static package audit yields one of two separate states:

- `R52_STATIC_PINS_PASS_SANDBOX_BINARY_MISSING`: package integrity verified, host guest-launch prerequisite missing.
- `R52_STATIC_PINS_PASS_SANDBOX_BINARY_PRESENT_GUEST_UNVERIFIED`: package integrity verified, launcher executable found, **but feature state and actual isolation still unknown**.

Neither is evidence of a running Windows guest, GUI launch, login or walk. Any unexpected file, pin, source hash or WSB mapping fails with exit code 2 and `R52_HOST_READINESS_REVIEW_BLOCKED`, rather than accepting a changed client.

The tool intentionally avoids an elevated `Get-WindowsOptionalFeature` call. It also does not contact GitHub, open sockets, run Java, enable Hyper-V, alter Windows Sandbox or firewall policies, inspect process memory, touch live account files, or delete user files.

## Local Windows acceptance, 2026-11-14

- Executed the script against the staged private R51 package on the connected Windows host; **PowerShell parser 0 errors**.
- Hashes of staged airgap v308 client, private Java 11 executable and exact R51 LocalLab server match their pinned values.
- Network, clipboard, vGPU, audio input, video input and printer redirection disabled in WSB. Exactly two mapped paths, both read-only, no automatic startup.
- Script returned `R52_STATIC_PINS_PASS_SANDBOX_BINARY_MISSING`, **exit 0 for static inspection only**.
- Windows Sandbox feature state `UNKNOWN_NOT_QUERIED`; guest isolation and graphical authentication/walking are **false/unverified**.
- No source branch merge, no guest Java or Windows setting change. The earlier R43 guest package remains intact.

## Further acceptance

After a separate, authorized isolation setup on Windows, run the existing R23–R26 guest preflights followed by the R38 **guest-only** manual GUI sequence. Use a disposable synthetic account, never a real SpawnPK login. Only a guest-owned client socket, correctly ordered widget/login/walk server markers, a reviewed foreground screenshot and clean client/server termination can establish *derivative* graphical-client acceptance.

GitHub CI for R52 separately validates the no-launch source policy and a Windows fail-closed negative case; no GitHub runner receives a proprietary JAR, cache, private Windows profile or credentials. R52 is **draft and stacked on R51**.
