# Outbound startup schemas — v0.3.2

## Opcode 103

Current raw writer path (`rs.n.a.a()` and the normal Client command route):

```text
opcode 103
1 byte length = original command string length - 1
bytes = command substring after leading ::
0x0A newline terminator
```

The length therefore equals `commandWithoutColons.length + 1`.

Observed localhost startup commands decode as:

```text
gpuflagon
queuedswitching
soundon
```

## Opcode 0

Static writer callsites emit opcode 0 with no payload.  The v0.3.1 captured stream remains ISAAC-aligned when it is consumed as fixed length 0.

## Opcode 121

The Client loading-complete path emits opcode 121 with no payload.

## Opcodes 164 / 98 / 248

The movement writer emits a one-byte payload length immediately after the opcode.  164 and 98 use `2*steps+3`.  248 uses that movement body plus 14 additional bytes.

v0.3.2 frames and logs these packets but does not mutate local-world position.
