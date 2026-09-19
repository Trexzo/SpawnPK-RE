# Tradepost own-listing protocol

S2C250 subtype 30 has two operations.

Operation 1 publishes a listing using:
`itemId, received/completed quantity, amount, price, currency`.

The first/third/fourth fields are independently cross-proven by the exact client and the parallel Trading Post JSON/history parser (`item_id`, `amount`, `price`). The second quantity is displayed against amount and participates in the `Received:` calculation, so R4 labels it `receivedOrCompletedQty` rather than inventing a stronger business term.

Currency enum:
- 0 GOLD, multiplier 1
- 1 CASH_BAGS, multiplier 100000000

Operation 2 removes the listing by item id.

The desktop/history JSON path additionally exposes `id`, `item_id`, `time`, `item_name`, `seller`, `buyer`, `currency`, `price`, `amount`; it is a separate record/search path and is not silently conflated with subtype 30.
