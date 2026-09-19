# Mailbox exact client protocol

Mailbox list/state is S2C250 subtype 31 with operations 0..7. The exact operation grammar is in the CSV.

Read state enum:
- 0 `UNREAD` (client color 3145498)
- 1 `READ` (client color 12171349)

Attachment claim enum:
- 0 `EMPTY`
- 1 `UNCLAIMED`
- 2 `CLAIMED`

Claim-state behavior is concrete: EMPTY hides claim controls; UNCLAIMED exposes the attachment region and bank/inventory deposit controls; CLAIMED replaces the actionable state with `Items have been claimed!`.

Mailbox is therefore a mixed but coherent protocol:
- S2C250/31: inbox rows, row status, selection, attachment claim presentation
- ordinary keyed/widget text: subject / sent-expiry / body presentation where used
- normal item-container publication: attachment items on widget 32175
- C2S185 widget actions: client-facing delete/deposit/refresh controls

This is enough to implement the **client protocol** faithfully. Production mail storage, expiry policy, attachment ownership and reward generation remain server authority.

Exact-current quirk: operation 7 reads the row index with the unsigned-byte primitive and then compares it with `-1`; that sentinel cannot be produced by that reader.
