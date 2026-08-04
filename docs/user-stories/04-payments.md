# User Stories — Epic 4: Payments

Part of the `ecommerce-service` module (study project). See [README](README.md) for cross-cutting
decisions and overall epic list.

## Key decisions locked for this epic

- **Gateway: Stripe test mode + a Mock implementation**, both behind a `PaymentGateway` interface
  (`charge`, `refund`) — mirrors this repo's existing pattern of swapping real vs fake infra per
  profile (e.g. Mailpit for email locally).
- **Webhooks are in scope for v1** — this is the boundary where the outbox pattern is most
  justified: an inbound webhook that must not be lost, plus idempotent handling of Stripe's
  at-least-once retried delivery.
- **Full refund only** — no partial refunds, since no partial-order-line cancellation is modeled.
- Two GoF patterns apply and compose rather than compete:
  - **Strategy** (Behavioral) — the choice between `StripePaymentGateway` and
    `MockPaymentGateway`, both implementing `PaymentGateway`, selected by profile/config.
  - **Adapter** (Structural) — `StripePaymentGateway` itself, translating Stripe's SDK
    request/response/exception types into this codebase's own `PaymentResult`/`RefundResult`
    vocabulary.

## Stories

**US-4.1 — Charge via pluggable gateway**
As the system, when an order enters `PAYMENT_PROCESSING`, I want to charge the shopper through a
gateway abstraction, so switching providers or testing locally doesn't touch order/saga logic.
- `PaymentGateway.charge(orderId, amount, idempotencyKey)` → `PaymentResult`
- The internal idempotency key (from Epic 3's US-3.3) is passed through as Stripe's native
  `Idempotency-Key` header — retries of the same attempt get deduplicated by Stripe itself, not
  just by our own bookkeeping
- `MockPaymentGateway` simulates outcomes deterministically (e.g. a magic amount triggers decline)
  for `local`/test profiles

**US-4.2 — Record payment attempt before calling the gateway**
As the system, I want a `Payment` row (order, amount, status=`PENDING`, idempotency key) written
before the gateway call, so a crash mid-call still leaves something to reconcile from.
- This is exactly what Epic 3's US-3.4 reconciliation job queries against

**US-4.3 — Synchronous charge confirmation**
As a shopper, I want immediate success/failure feedback when I submit payment, so checkout doesn't
leave me hanging.
- On Stripe's synchronous response, update `Payment` status and drive the order transition
  (`CONFIRMED`/`FAILED`) in one local transaction

**US-4.4 — Reliable event publishing via outbox**
As the system, when a payment outcome is determined, I want the resulting event written to an
outbox table in the same transaction as the `Payment`/`Order` update, so the event can't be lost
even if the app crashes right after commit.
- Addresses the **dual-write problem**: a DB transaction commit and an external publish can't be
  made atomic directly. The outbox table makes "publish" just another row in the same
  transaction; a separate relay/poller reads unpublished rows and dispatches them
- One outbox mechanism serves both this and Epic 1's catalog read-model sync — same table/relay,
  different event types

**US-4.5 — Stripe webhook handling**
As the system, I want to receive and process Stripe webhook events (`payment_intent.succeeded`,
`payment_intent.payment_failed`), so payment outcomes that don't resolve synchronously still get
recorded correctly.
- Verify Stripe's signature before trusting the payload
- Dedupe by Stripe's event ID — Stripe delivers at-least-once, so the handler must be idempotent
  against redelivery, not just against our own retries
- Same transaction shape as US-4.4: write the outbox event + update `Payment`/`Order` atomically
  inside the webhook handler, never emit side effects before persisting
- Feeds Epic 3's US-3.4 reconciliation directly — webhook-derived `Payment` state is the ground
  truth, independent of whether the synchronous call ever got a response

**US-4.6 — Refund on cancellation**
As the system, when a shopper cancels a `CONFIRMED` order (Epic 3's US-3.6), I want to issue a full
refund via the gateway, so they get their money back.
- `PaymentGateway.refund(paymentId)` — full amount only
- Refund outcome recorded and outbox-published the same way as a charge outcome

**US-4.7 — User-facing failure reasons**
As a shopper, when payment fails, I want a clear but non-technical reason, so I know whether to
retry or contact support.
- Map Stripe decline codes to a small internal category set (insufficient funds / card declined /
  gateway error) — never leak raw gateway error strings to the client
