# User Stories — Epic 3: Order Lifecycle & Inventory

Part of the `ecommerce-service` module (study project). See [README](README.md) for cross-cutting
decisions and overall epic list.

## Key decisions locked for this epic

- **This is a modular monolith** — one Spring Boot app, one Postgres database (`product` schema)
  across all modules. Order creation + stock reservation are a single local ACID transaction, not
  a distributed saga. The saga only starts at Epic 4's payment-gateway call, the one step that
  genuinely leaves the local transaction.
- **Reservation model**: variants have `stockQuantity` and `reservedQuantity` columns;
  `available = stockQuantity - reservedQuantity`. Reservation increments `reservedQuantity`; a
  confirmed sale decrements both together; a released reservation decrements `reservedQuantity`
  only.
- **Price is snapshotted** on the order at creation time — unlike the cart's live lookup (Epic 2),
  an order is a financial record and must freeze price at time of purchase.
- **Abandoned reservations expire**: a scheduled job releases `PENDING` orders whose payment was
  never attempted within a timeout window (e.g. 15 min).
- **Cancellation is allowed only before shipment.** Returns/refunds after delivery are deferred to
  a later phase.
- **Idempotency/reconciliation is in scope for v1** — the hardest real saga problem (crash between
  "payment succeeded" and "order confirmed") gets an explicit story, not a later hardening pass.

## State machine

| From | To | Trigger | Compensating action needed? |
|---|---|---|---|
| — | `PENDING` | checkout confirmed | — (stock reserved at creation) |
| `PENDING` | `PAYMENT_PROCESSING` | payment attempt starts | — |
| `PENDING` | `EXPIRED` | reservation timeout, no payment attempted | release reservation |
| `PAYMENT_PROCESSING` | `CONFIRMED` | payment succeeded | — |
| `PAYMENT_PROCESSING` | `FAILED` | payment declined | release reservation |
| `PENDING`/`PAYMENT_PROCESSING`*/`CONFIRMED` | `CANCELLED` | shopper cancels | release reservation, **or** restock + refund if already `CONFIRMED` |
| `CONFIRMED` | `SHIPPED` | admin ships | — |
| `SHIPPED` | `DELIVERED` | delivery confirmed | — (terminal; returns deferred) |

*Cancelling mid-`PAYMENT_PROCESSING` queues the cancellation to apply once the in-flight payment
resolves — you can't cancel while a gateway call is literally in progress.

**Design note for implementation phase:** cancellation's compensating action genuinely differs by
originating state (`PENDING` → release only; `CONFIRMED` → restock *and* refund). This is a real
candidate for the GoF **State** pattern (Behavioral) — one class per status implementing
`cancel()`, etc., each throwing/no-oping where invalid — over a single service method with an
if/else ladder on status. Not committed yet; revisit at technical design time.

## Stories

**US-3.1 — Create order and reserve stock atomically**
As the system, when checkout is confirmed, I want to create the order and reserve stock for every
line in one transaction, so two shoppers can never oversell the same limited stock.
- `Order` + `OrderLineItem`s created with price snapshotted at this moment
- Each line increments `reservedQuantity` on its variant
- If any line's available stock can't cover the requested amount, the whole transaction rolls
  back — no partial orders

**US-3.2 — Release stock on reservation timeout**
As the system, I want a `PENDING` order whose payment was never attempted within N minutes to
expire and release its stock, so abandoned checkouts don't lock inventory forever.
- Scheduled job; transitions `PENDING` → `EXPIRED`, decrements `reservedQuantity`

**US-3.3 — Idempotent payment handoff**
As the system, I want each order tagged with an idempotency key before calling the payment
gateway, so a crash between "payment succeeded" and "order confirmed" can be recovered safely
instead of double-charging or losing the sale.
- `PENDING` → `PAYMENT_PROCESSING` immediately before the Epic 4 gateway call, carrying a unique
  key (order ID is a reasonable default)
- Success → `CONFIRMED`, converts reservation to a real sale
- Failure → `FAILED`, releases reservation only

**US-3.4 — Reconciliation for stuck payments**
As the system, I want a recovery job to re-check the actual gateway outcome for any order stuck in
`PAYMENT_PROCESSING` beyond a short grace period, so a crash mid-flight resolves correctly instead
of guessing.
- Queries the gateway by idempotency key rather than assuming failure — resolves to `CONFIRMED`
  or `FAILED` based on ground truth (see Epic 4's webhook handling, which feeds this)

**US-3.5 — View order status with history**
As a shopper, I want to see my order's current status and a timeline of how it got there, so I can
answer my own "what happened to my order" question.
- Per-transition audit log (status + timestamp), not just current state

**US-3.6 — Shopper cancels before shipment**
As a shopper, I want to cancel my order any time before it ships, so I can back out of a purchase.
- Allowed from `PENDING`, `CONFIRMED`, or a queued cancel on `PAYMENT_PROCESSING`; blocked once
  `SHIPPED`
- Compensation branches by originating state per the state machine table above

**US-3.7 — Admin marks shipped**
As an admin, I want to mark a `CONFIRMED` order as shipped.
- Only valid from `CONFIRMED`

**US-3.8 — Order marked delivered**
As an admin (or a future carrier webhook), I want to mark a `SHIPPED` order as delivered, reaching
the terminal happy-path state.
- Only valid from `SHIPPED`; returns/refunds-after-delivery stay deferred
