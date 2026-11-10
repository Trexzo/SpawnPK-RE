# R36 — v308-shaped first World tick loopback acceptance

## What this verifies beyond the existing R32–R35 gates

Existing tests prove original-v308-style prelogin, post-login
`ISAAC185/widget912`, admission rejection, profile file safety and
many separately tested gameplay features. The inherited
`BootstrapRuntimeIntegrationTest`, however, creates a shared World
and uses a legacy login config-2 value `307`, rather than the exact
v308 login config value `308`.

R36 adds a separate, isolated *real localhost Java socket* test
spanning the first playable World cycle:

1. A synthetic client sends authentic-shaped prelogin14 and framed
   revision317 login carrying v308 config2=308, local alias, and
   independent ISAAC seed state.
2. It expects the exact login success triplet `2,205,0`, sends
   first ISAAC185/widget912 and verifies a single registered World
   player.
3. It decodes the actual server writer's first S2C249 and S2C73
   bytes against the pinned-v308 packet encoders, then checks
   S2C81 teleport/appearance, run-energy110, orb126 and the
   existing sidebar/inventory/home-NPC bootstrap contract.
4. It checks two separate idle S2C81 world updates, then sends
   authentic-shaped C2S164 var-byte movement one tile east
   (3087,3495 → 3088,3495). Server must publish
   `BootstrapPackets.player81WalkStep(4)` as an ISAAC-decoded
   S2C81 world-tick update.
5. After client socket closes, session worker must exit, unregister
   the player, detach tick target and create no nonpersistent
   `local.properties` file.

An isolated World, a fresh disposable FilePlayerRepository location
and an ephemeral 127.0.0.1 listener ensure no live account, active
client or other server session is involved. The test uses actual
`LocalSession` and `World` and runs on Java11 on Windows and both
GitHub CI systems.

## Limits

**This is still a synthetic protocol client, not the original
graphical v308 Java client.** Matching byte-level server packet
serializers and completing a movement tick do not verify the Swing
GUI, client cache/OpenGL, native libraries, graphical rendering,
actual controller input, game integrity or real production
credentials. The guest-only original v308 GUI acceptance from
R23–R27 remains blocked on actual Windows Sandbox approval/setup.
Do not run proprietary JARs on the host, change firewall state or
terminate the three live owner v312 Java processes.

This PR is stacked on R35, draft and unmerged pending exact-head
CI and a future separately authorized guest trial.
