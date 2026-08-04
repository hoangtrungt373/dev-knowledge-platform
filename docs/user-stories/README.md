# User Stories — E-Commerce Module

Study-project scope: a new `ecommerce-service` module (own entities, REST/DTO/mapper layer,
depending on `common`+`infra` and, for Epic 5 only, `ai-service`) built as a vertical slice
mirroring `content-service`/`social-service`. See each module's `CLAUDE.md` for the pattern being
copied. These documents capture user stories only — no entities/endpoints exist yet, so
`docs/PROJECT_STRUCTURE.md` and the root `CLAUDE.md` are not updated until implementation starts.

## Epics

| # | Epic | Pattern showcased | Depends on |
|---|---|---|---|
| 1 | [Catalog & Search](01-catalog-search.md) | CQRS (write model vs. read/search model) | — |
| 2 | [Cart & Checkout](02-cart-checkout.md) | Redis as primary store (TTL-based) | Catalog |
| 3 | [Order Lifecycle & Inventory](03-order-lifecycle-inventory.md) | Local-transaction reservation + state machine | Catalog, Cart |
| 4 | [Payments](04-payments.md) | Saga (external step only), Strategy/Adapter gateway, Outbox | Orders |
| 5 | [Reviews & Recommendations](05-reviews-recommendations.md) | Vector similarity via `ai-service` | Catalog, Orders, `ai-service` |

## Cross-cutting decisions (apply across all epics)

- **Seller model:** single storefront, not a multi-vendor marketplace.
- **Categories:** flat, not a nested tree.
- **Variants:** products have one or more variants (SKU, attributes, price, stock); no
  variant-less products.
- **Search:** Postgres `tsvector` + `pg_trgm`, not a dedicated search engine (for v1).
- **Auth:** cart/checkout require an authenticated user — no guest cart/merge-on-login.
- **Admin role:** reuses `common`'s existing `UserRole.ADMIN` — no new role needed.
- **Module wiring:** `ecommerce-service` → `ai-service` is a new one-directional dependency
  (Epic 5 only), keeping `ai-service`'s own dependency count at one (still just
  `content-service`).

## Deferred across all epics (explicitly out of scope for v1)

- Multi-vendor marketplace / per-seller inventory
- Nested/hierarchical categories
- Promo codes / discounts (Epic 2)
- Address-based shipping cost calculation (flat rate only for v1) (Epic 2)
- Guest cart / cart-merge-on-login (Epic 2)
- Returns and refunds *after delivery* (Epic 3) — cancellation before shipment only
- Partial refunds (Epic 4) — full refund only
- Natural-language shopping-assistant chat (Epic 5) — similar-products widget only for v1
- Review moderation queue (Epic 5) — auto-published, with reactive admin removal only
