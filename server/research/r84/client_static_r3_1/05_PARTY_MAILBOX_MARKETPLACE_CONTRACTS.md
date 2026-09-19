# Party, mailbox and marketplace client contracts

## Party / raids

`rs.n.c.d.c.a(int,String,String)` is an exact local member-row updater. Starting from the party row family around widget `32318`, it derives the row by member index, writes member/status strings, disables interaction when status contains `max`, and changes the action label to `Cancel invitation` when the status contains `invit`; otherwise it uses `Kick player`.

This is client UI authority, not proof of the server membership model.

## Mailbox

The mailbox implementation contains a 35-slot inbox UI, expiry strings, detail/claim-state helpers, attachment presentation and bank/inventory deposit affordances. Its list row base is `32026 + index*4`. No dedicated top-level S2C126 payload decoder was found for mailbox row data, so the safe model is client helper + normal widget/container updates.

## Marketplace

The client exposes exact clear/add/update operations through the S2C126 global-token dispatcher. In particular, `add_exchange` parses a pair of integers and appends an `Integer[]{a,b}` row to the client exchange list, while `setsellitem,<id>` selects an item into widget 25342. The actual listing records, stock/ownership, prices and transaction outcomes are still production server data.
