# R29 — local v308 login alias admission before World registration

R28 corrected the actual LOCAL LAB login-frame length boundary and
redacted untrusted credentials, identifiers and ISAAC seeds in
`LoginFrame.toString()`. The follow-up inspection of the server's real
`LocalSession.run` flow identified a separate, concrete issue:
**the incoming `LoginFrame.username` had no local transport-level name
validation and was later used as a WorldPlayer name, command-routing
alias, profile selection argument, and unredacted diagnostic field**.

This permits a malformed **synthetic localhost login** containing
embedded CR or terminal ESC bytes to reach account/session logic; it
also allows arbitrary client-supplied names to be printed as raw
structured console output. Only prelogin/revision/frame structure
was checked before profile selection.

## Narrow R29 implementation

- New `LocalLoginAliasPolicy.requireValid` is called in the
  **existing** `LocalLoginTransport.readLogin` immediately after
  parsing the v308 login frame and confirming outer protocol revision
  **317**, *before* LocalSession selects an account, registers a
  WorldPlayer, creates ciphers or emits login success.
- The **LocalLab-only** alias policy accepts 1–24 characters from the
  ASCII subset `A-Z a-z 0-9`, space, underscore, hyphen; rejects
  leading/trailing spaces, ASCII controls (including CR, ESC, NUL),
  embedded newline, period/path separators, non-ASCII data and
  oversized values. It preserves the original valid string byte for
  byte (including case). It does **not** normalize a login to a
  different profile, check a password or attempt to reproduce the
  entire production SpawnPK naming system.
- Alias errors are fixed diagnostic phrases and never echo the
  rejected text. The frame is rejected with `IOException`, and the
  socket is disposed by the existing session lifecycle. There is no
  fallback to a persistent account for an invalid wire alias.
- `LocalSession` LOGIN_SUCCESS_LOCAL, `LocalAccountLifecycle` profile
  alias routing, and `LocalSessionPlayerInitializer` World register
  messages now redact the client-facing login alias/player username
  from those particular console lines, while keeping all actual
  AccountStore and WorldPlayer semantics unchanged.

## Tests

`R29LocalLoginAliasAdmissionTest` explicitly exercises:
valid LocalLab aliases `local`, `localtest`, `opensrc`, `src`,
`testprofile`, and synthetic mixed-case/space/hyphen/underscore
aliases; malformed controls/oversized/empty/punctuated aliases;
non-echoing exception text; and multiple actual new
`ServerSocket(0, ..., 127.0.0.1)` TCP logins parsed by
`LocalLoginTransport.readLogin`, accepting valid and denying invalid
names. A separate isolated `World` account-selection check confirms
that an arbitrary *valid* alias still maps to a nonpersistent profile,
while its raw identifier is absent from the profile-routing log.

The CI R29 job runs on both Linux and Windows, including legacy
`HandshakeIntegrationTest` and
`ClientBuildRevision308CompatibilityTest`. These are **synthetic
LOCAL LAB socket sessions**. They do not launch or authenticate the
proprietary v308 graphical client.

## Explicit limitations

This is a *local transport admission* and targeted diagnostic
redaction. It is **not** a comprehensive privacy review of all server
logs, an authentication/password implementation, a production RSPS
security audit, proof of native graphics or actual v308 GUI login.

The connected Windows desktop became unavailable while developing
R29. No new personal-PC Windows acceptance can be claimed until it
reconnects. GitHub Actions provides independent OS-level validation;
actual source/PR evidence is recorded in review comments only after
the workflows finish. The branch stays stacked on R28, draft and
unmerged; existing live Java processes and original client binaries
are not touched.
