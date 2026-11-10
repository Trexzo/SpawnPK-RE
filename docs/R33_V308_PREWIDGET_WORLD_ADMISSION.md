# R33 — no WorldPlayer or account initialization before verified v308 startup widget

## Exact inherited baseline

R32 verified the original v308 initial client packet shape: **ISAAC opcode 185,
big-endian widget 912**, immediately after result 2 / rank / flag.
R32 rejects wrong/truncated initial packets, but its `LocalSession.run()` created
a WorldPlayer and loaded/registers the selected profile **before** calling
`ClientPacketProbe.readFirst185()`. A zero-byte first-packet EOF returned
`-1` and an idle socket timeout took the "keeping local session open" path,
both of which previously allowed World tick attachment without a valid widget.

## R33 implementation

- Move `LocalSessionPlayerInitializer.initialize(...)`, account selection/load,
  and World registration to **after** `readFirst185()` successfully confirms
  the first exact 185/912 packet. Session-specific ciphers and outbound queue
  remain local and available before packet decoding.
- Treat EOF (`-1`) as a hard admission failure; do not attach a world tick target.
- Treat the existing 5-second first-widget socket timeout as a hard admission
  failure rather than an idle accepted session.
- Only emit the `LOGIN_SUCCESS_LOCAL` admission log after packet validation;
  do not emit selected account/alias values in this log line.
- Preserve subsequent generic opcode185 widget actions, valid first-widget
  server bootstrap, and existing world teardown for successfully registered players.

The server still writes the protocol `2,205,0` triplet *before* receiving the
client first widget, as required by the original v308 handshake. This is **not**
authentication: arbitrary synthetic local peers can create a valid 185/912
packet. A client may receive result 2 before an account initialization failure.
The protocol does not have a previously validated mechanism to defer this triplet.

## Reproducible test

`R33FirstWidgetWorldAdmissionTest` uses actual LocalSession, local-only ephemeral
TCP, synthetic v308 login framing, an isolated World and a test-owned repository.
It checks zero active WorldPlayers and tick targets immediately after the
success triplet **before sending any first-widget byte**. It then covers a
correct 185/912 bootstrap, bad widget values, bad opcode, truncated widget,
immediate EOF and silent idle timeout. Every attempted session must leave no
registered player or tick target after closure. This is stronger than checking
only post-disconnect cleanup, which R32 already covered.

Dedicated Linux and Windows workflow steps run the new test alongside all
inherited R32 and preceding tests.

## Limits and operating boundary

No original graphical v308 client, Sandbox guest, user account, live client
session or firewall rule is used or modified by this source change.
World registration remains after a *syntactically* valid first widget even if
later gameplay fails. Do not infer full RSPS compatibility or production account
security from this regression. The PR is stacked on R32 and remains draft and
unmerged until full CI and any future actual guest validation.
