# ClientPacketProbe R8.5 patch note

The cumulative source overlays do not contain a complete decompiled/reconstructed `ClientPacketProbe.java`. R8.5 therefore promotes the eight remaining framing-only generic interaction packets by a minimal class-level instrumentation of private `consumeFramingOnly(int)` to call `R85GenericC2SBridge.tryConsume(this, opcode)` before the legacy framing-only path.

The patch was generated with JDK internal ASM (`ClassWriter.COMPUTE_FRAMES | COMPUTE_MAXS`) from `tools/PatchClientPacketProbeR85.java`. The candidate JAR's patched class is regression-tested by `R85GenericC2SProbeIntegrationTest`, which feeds all eight exact ISAAC-framed packets sequentially and verifies stream alignment.

This changes decoding/normalization only. Unknown production outcomes remain fail-closed.
