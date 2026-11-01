# Receiver reference

`RelayReceiver` verifies the exact UTF-8 bytes of a Relay delivery, enforces a five-minute timestamp window and deduplicates `X-Relay-Delivery-Id`. Persist the delivery IDs in production; the in-memory set is deliberately a reference implementation only.

Map headers into `deliveryId`, `timestamp` and `signature`, call `verify`, process only `accepted`, return a 2xx response for `duplicate`, and return 401/400 for `rejected`.
