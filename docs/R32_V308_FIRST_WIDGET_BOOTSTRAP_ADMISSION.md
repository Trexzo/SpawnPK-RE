# R32 — exact v308 first post-login ISAAC185 widget912 admission

## Original client evidence and concrete LocalLab defect

Read-only bytecode inspection of the SHA-pinned original v308 JAR
`client(6).jar` SHA256
`854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6`
and separately certified R11 airgap client SHA256
`024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7`
shows the exact successful login path in `rs.Client.a(String,String,boolean)`:

- Server login result **2** is followed by a one-byte `Client.cT`
  local rank/privilege value, then a one-byte Boolean; LocalLab currently
  sends **2, 205, 0**, as confirmed in its inherited handshake tests.
- After successful response, `rs.Client` writes **opcode 185** through
  its ISAAC packet writer and then **big-endian short 912** using
  `rs/x/e.d(int)`, before proceeding with normal startup actions.
- This first `185 / widget912` is a distinct startup step. **Later**
  `opcode185` packets are regular widget actions; their IDs need not
  equal 912 (for example widget152 is a supported later action).

LocalLab's `ClientPacketProbe.readFirst185()` previously checked
opcode185 and read the two widget bytes, but only **printed**
`expectedWidget=912` without enforcing it. It returned 185 for
**any** widget ID; `LocalSession` therefore ran
`bootstrapPublisher.publish(...)`, registered a region load, then
attached a tick target, even for a malformed initial widget. A wrong
first opcode previously paused decoding rather than immediately
closing, allowing a subsequent tick attach to be attempted.

## R32 changes

- Introduce named `ClientPacketProbe.LOGIN_BOOTSTRAP_WIDGET=912`,
  grounded in the real original v308 startup path.
- Refuse a first `opcode185` containing any widget other than 912
  with `IOException`, set probe alignment false and close the
  LocalSession through its existing finally/teardown sequence.
- Refuse an incorrect **first opcode** with `IOException` rather
  than pausing an otherwise invalid login-admission session.
- A truncated 185 payload still fails closed through the existing
  `Binary.readExactly(in,2)` EOF exception. No additional bytes are
  consumed or invented.
- **Later** opcode185 widget events retain their generic existing
  decode/typed-request behavior. No changes to item actions, bank,
  movement, NPC combat, game world, account data, 317 prelogin
  framing, seeds or Java/client binaries.

This is a **protocol-shape gate, not authentication**: a synthetic
client can easily send opcode185/widget912, and the prior LocalLab
synthetic login mechanism remains independent of production
credentials.

## Tests

1. `R32FirstWidgetAdmissionTest` directly tests the real ISAAC packet
   decoder: valid first 185/912 followed by later widget185/152
   remains aligned; six invalid first widget IDs 0/1/152/911/913/65535
   reject; five invalid first opcodes 0/103/184/186/255 reject;
   both 0-byte and 1-byte truncated widget payloads reject.
   **54 assertions passed on authorized Windows/private Java11.**
2. `R32FirstWidgetLocalSessionSocketTest` exercises **real**
   ephemeral `127.0.0.1` TCP sockets against a full LocalSession
   inside a private isolated World and a *test-owned temporary*
   FilePlayerRepository. Valid 185/912 causes the actual server
   first S2C bootstrap packet **249** (ISAAC decoded); malformed
   widget IDs 0/152/911/913/65535, wrong first opcode184 and
   a truncated widget cause EOF with **no bootstrap bytes**.
   Every test checks no surviving WorldPlayer and no remaining tick
   target after the session exits. **112 assertions passed on
   authorized Windows/private Java11.** No real account or owner
   profile is reused or modified.
3. Dedicated R32 Linux and Windows CI steps run both test classes
   before inherited R31–R20 login, world bootstrap and game-regression
   tests.

For the full server test, Java's classpath must include Gradle's
compiled resources (`server/build/resources/main`), or the isolated
World constructor will fail because `equipment_slots.tsv` cannot
be found. The first development invocation uncovered that **test
harness classpath** issue, not a v308 wire mismatch. After including
the compiled resources, the complete socket test passed.

## Important lifecycle and remaining limitations

The current LocalSession **allocates and registers a WorldPlayer
before** reading this first post-login packet. On an invalid
widget/opcode, its existing cleanup removes that temporary
registration. R32 prevents **subsequent bootstrap packet
publication and tick attachment**, not the earlier transient
registration. It does NOT prove a rejected login never initialized
any server-side state. Moving registration later would require a
separate lifecycle review and must not be claimed or silently done
as part of this narrowly scoped fix.

The actual original graphical v308 client has **not** run in a real
Windows Sandbox guest; the synthetic packet fixture tests do not
establish graphical rendering, original client login, or production
RSPS gameplay. Keep R32 stacked as a draft, unmerged PR and do not
modify owner Java sessions or host firewall.
