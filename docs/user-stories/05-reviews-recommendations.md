# User Stories — Epic 5: Reviews & Recommendations

Part of the `ecommerce-service` module (study project). See [README](README.md) for cross-cutting
decisions and overall epic list.

## Key decisions locked for this epic

- **Review eligibility: verified purchase only** — a shopper must have a `DELIVERED` order
  containing the product to review it. One review per (shopper, product) — not per variant;
  resubmitting edits the existing review.
- **Reviews auto-publish** — no approval queue; admins can reactively remove policy-violating
  reviews after the fact.
- **Recommendations: similar-products widget only for v1** — vector similarity over product
  embeddings, shown on the product detail page. A natural-language shopping-assistant chat is
  deferred.
- **Module wiring:** `ecommerce-service` → `ai-service` is a new one-directional dependency.
  `ecommerce-service` owns its own `ProductEmbedding` entity (FK to its own `Product`) and calls
  `ai-service`'s embedding-generation service directly. This keeps `ai-service`'s own dependency
  count at one (still just `content-service`) rather than adding `ecommerce-service` as a second
  dependency there.

## Epic: Reviews

**US-5.1 — Leave a review for a verified purchase**
As a shopper, I want to rate (1–5 stars) and optionally write a review for a product I've
received, so I can share my experience.
- Requires at least one `DELIVERED` order containing that product
- One review per (shopper, product); resubmitting edits the existing review
- Auto-published immediately

**US-5.2 — View reviews and aggregate rating**
As a shopper, I want to see a product's average rating and individual reviews on its detail page,
so I can judge quality before buying.
- Average rating is denormalized onto Epic 1's catalog read model, alongside `inStock` and price
  range, rather than aggregated from raw reviews on every catalog read
- Paginated, sortable by recency or rating

**US-5.3 — Edit or delete own review**
As a shopper, I want to edit or delete my own review, so I can correct or retract it.
- Author-only (or admin); any change triggers the same read-model rating recompute as US-5.2

**US-5.4 — Admin removes a policy-violating review**
As an admin, I want to remove a spam/abusive review after the fact, so the storefront stays
trustworthy without pre-moderating every submission.
- Reactive-only, consistent with auto-publish — a safety valve, not an approval workflow

## Epic: Recommendations

**US-5.5 — Generate and maintain product embeddings**
As the system, I want each active product's description embedded into a vector, so similarity
search can find related products.
- `ecommerce-service` owns `ProductEmbedding` (FK to its own `Product`), calling `ai-service`'s
  embedding-generation service to produce the vector
- Re-embedding hooks into the same product-changed domain event that drives Epic 1's read-model
  sync — one event, two listeners, rather than duplicated change-detection logic
- Deactivated products are excluded from embedding/search — never recommended

**US-5.6 — View similar products**
As a shopper viewing a product, I want to see a handful of similar products, so I can discover
alternatives or complements.
- Cosine similarity over `ProductEmbedding` (same pgvector mechanism `ai-service` already uses for
  RAG), excluding the current product and inactive products, top-K (e.g. 4–6)
- Explicit degraded-mode fallback (e.g. same-category products) when too few embeddings exist yet

**US-5.7 — Recommendations respect stock and rating**
As a shopper, I don't want to be recommended something out of stock or poorly rated, so results
stay useful, not just textually similar.
- Similarity search returns a candidate pool larger than K, then filters/re-ranks by `inStock` and
  a minimum rating threshold before trimming to top-K
- Worth keeping this re-ranking step as a swappable piece (Strategy again, if a second ranking
  approach shows up later) rather than hardcoding one heuristic alongside the vector query
