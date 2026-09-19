# Exact widget-expression VM (`X`)

The current client method `Client.a(widget, scriptIndex)` evaluates the widget `X` expression stream.

Return boundaries:

- missing `X` or out-of-range script index -> `-2`
- exception -> `-1`
- opcode `0` -> return accumulator

The accumulator begins at zero. Values add by default; opcodes 15/16/17 select subtract/divide/multiply for the next value.

The complete opcode table is in `tables/condition_vm_opcodes.csv`.

Important recovered data sources include current/base levels, XP, inventory/container item counts, varps, varbits, combat level, total level, world coordinates and literals.

## Construction correction: `bk` / `bl`

The Construction builder creates `X[0] = [4,3214,995,0]`, which evaluates the count of item `995` (coins) in inventory widget `3214`.

The same builder writes one-element `bk` and `bl` arrays (`bl[0]=10` and a raw value into `bk[0]`). A full class-reference scan of the exact current client found **no read reference to either field anywhere**. Only the Construction builder writes them.

Therefore R2's raw Construction `bk` values must not be promoted as active comparison/price authority. The visible display costs remain valid client-visible strings; the `bk/bl` raw values are dormant fields in this build.
