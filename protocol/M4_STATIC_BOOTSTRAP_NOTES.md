# M4 bootstrap — static reconstruction, not runtime-certified yet

The packet-81 decoder is fully visible in the current JAR:

- enter bit mode;
- 1 bit local-update flag;
- if local unchanged, continue;
- 8 bits existing-other-player count;
- new-player records use 11-bit player ids and terminate at `2047`;
- return to byte mode;
- parse update masks only for queued ids;
- decoder asserts exactly all packet bytes were consumed.

Therefore a syntactically valid **idle** packet-81 payload is 20 meaningful bits padded to three bytes:

`00 7F F0`

That decodes as local unchanged / zero existing other players / 2047 sentinel.

For initial placement, local movement type `3` consumes plane(2), two 1-bit flags, localX(7), localY(7). The lab includes an experimental five-byte packet-81 teleport builder plus packet 73 region setup. It is disabled by default and enabled only with `--bootstrap` because we have not launched the real client against localhost in this work session.
