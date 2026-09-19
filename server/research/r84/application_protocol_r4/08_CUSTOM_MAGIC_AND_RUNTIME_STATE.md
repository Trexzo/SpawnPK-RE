# Custom magic and generic runtime state

Subtype32 exposes a server-controlled presentation/state layer for custom magic. Notably op2 swaps Blood vs Infernal blitz/barrage presentation, op4 enters client text-input mode, and op5 currently reads three bytes but makes no observable state mutation.

Subtype39 is a generic int->u16 client state map with an exact remove-sentinel branch for value -2.

Subtype42 controls runtime NPC-definition override state in three operations. Field meanings P/Q/R remain deliberately structural because their product semantics are not proven.

Subtype43 is a generic int->u8 flag map. Its removal branch compares the unsigned-byte result to -1; therefore the intended removal sentinel is unreachable in this exact client.
