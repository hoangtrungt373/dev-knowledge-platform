# User Stories — Epic 2: Cart & Checkout

Part of the `ecommerce-service` module (study project). See [README](README.md) for cross-cutting
decisions and overall epic list.

## Key decisions locked for this epic

- **Authenticated only** — no guest cart, no merge-on-login.
- **Cart storage: Redis**, keyed by `userId` (`cart:{userId}` → hash of `variantId → quantity`),
  not Postgres. Redis is the primary store here, not a cache — a cache-as-primary-store pattern,
  distinct from its existing use for OAuth2 state.
- **Cart TTL**: sliding expiry, refreshed on every mutation (e.g. 30 days from last activity).
  Expiry is silent and by design (abandoned-cart cleanup), not a bug.
- **Price model: live lookup**, not a snapshot. Cart lines store only `variantId` + `quantity`;
  price/availability is resolved from the catalog read model (Epic 1) at read time. (Orders, by
  contrast, *do* snapshot price at creation — see Epic 3.)
- **No stock reservation at add-to-cart.** Only a soft existence/`active` check. Real reservation
  happens at order creation (Epic 3), so idle carts never lock up inventory.
- **Promo codes/discounts and address-based shipping are deferred** — flat-rate shipping only for
  v1.

## Epic: Cart

**US-2.1 — Add a variant to the cart**
As a shopper, I want to add a product variant to my cart, so I can purchase it later.
- Adding an already-present variant increments quantity rather than creating a duplicate line
- Only a soft existence/`active` check is performed — no stock reservation

**US-2.2 — Update quantity / remove a line**
As a shopper, I want to change quantity or remove an item, so my cart reflects what I actually
want to buy.
- Setting quantity to 0 removes the line entirely

**US-2.3 — View cart with live totals**
As a shopper, I want to see my cart with current prices and a running total, so I know what I'll
pay before checkout.
- Each line's price/availability is resolved live from the catalog read model at read time

**US-2.4 — Cart expiry (abandoned cart)**
As a shopper, I want my cart to persist for a reasonable time across sessions, so I don't lose it
if I close the browser.
- Redis key TTL, sliding, refreshed on every mutation

## Epic: Checkout

**US-2.5 — Select shipping address**
As a shopper, I want to provide a shipping address at checkout, so my order can be delivered.
- `Address` is owned by `ecommerce-service` itself, keyed by `userId` as a plain field — not a
  cross-module FK into `identity-service`'s `User`

**US-2.6 — Review and confirm order**
As a shopper, I want to review my cart, address, and total (items + flat shipping fee) before
confirming, so I can catch mistakes before paying.
- Empty cart cannot proceed to checkout
- Confirming creates an `Order` in `PENDING` state and hands off to Epic 3's reservation step —
  this epic's responsibility ends at order creation
- The Redis cart key is deleted only *after* the order is successfully created, never before

**US-2.7 — Stale cart line at checkout**
As a shopper, if a variant became inactive or was deleted since I added it, I want to be warned at
checkout rather than have it silently fail later.
- Each line is re-validated for existence/`active` status at checkout time (not stock level —
  that's Epic 3's concern)
- Invalid lines are flagged and dropped from the checkout attempt; shopper must confirm the
  adjusted cart before proceeding
