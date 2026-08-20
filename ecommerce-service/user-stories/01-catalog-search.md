# User Stories — Epic 1: Catalog & Search

Part of the `ecommerce-service` module (study project). See [README](README.md) for cross-cutting
decisions and overall epic list.

## Key decisions locked for this epic

- Categories are **flat**, not a nested tree.
- Products always have **one or more variants** (SKU, attribute values, price, stock) — no
  variant-less products.
- Out-of-stock products/variants **stay visible**, marked unavailable — never hidden by stock
  level.
- Products have an **image gallery** (ordered list), not a single image; files live in MinIO via
  `infra`'s existing `StorageService`, with a `ProductImage` entity storing the key + sort order.
- Search is Postgres `tsvector` (relevance ranking via `ts_rank`) + `pg_trgm` (typo tolerance),
  not a dedicated search engine, for v1.
- Read path is **CQRS**: browse/search/filter hit a denormalized read model, not the normalized
  write-side tables — kept in sync asynchronously via domain events (see Epic 4's outbox, which
  this reuses rather than inventing a second sync mechanism).

## Epic: Browsing

**US-1.1 — Browse products by category**
As a shopper, I want to view products within a category, so I can narrow down what I'm looking
for.
- Given a category with active products
- When browsing it
- Then only `active = true` products are returned, paginated
- Stock level does not affect visibility — a product with 0 stock across all variants still
  appears, marked `inStock: false`
- Each result shows: name, first gallery image (by sort order), price range across variants,
  `inStock` flag
- A category with zero active products returns an empty result, not a 404

**US-1.2 — View product detail with variants and gallery**
As a shopper, I want to view a product's detail page and select a variant (e.g. size/color), so I
can see the exact price and availability for what I want to buy.
- Given a product with N variants
- Then the detail response includes all variants with SKU, attribute values, price, and stock
  quantity in one response (no N+1 calls)
- Out-of-stock variants are shown, marked unavailable, not omitted
- The full ordered image gallery is returned, not just one image

## Epic: Search & Filter

**US-1.3 — Full-text search**
As a shopper, I want to search products by keyword, so I can find items without browsing
categories.
- Matches against product name/description via `tsvector`
- `pg_trgm` trigram similarity catches near-matches/typos that exact token matching would miss
- Ranked by relevance (`ts_rank`), not just recency or price
- Out-of-stock products remain in results, per the same visibility rule as US-1.1

**US-1.4 — Filter search/browse results**
As a shopper, I want to filter results by price range and variant attributes (size, color, etc.),
so I can narrow results to what fits my needs.
- Price filter applies at the variant level — a product with variants from $10–$30 appears if the
  filter range overlaps at all
- Attribute filters are dynamic per category (a "Books" category won't offer a size filter);
  attributes are modeled as flexible key/value pairs on variants, not fixed columns
- Multiple filters combine with AND logic
- Shoppers may optionally filter "in stock only" — this is opt-in, never a default hard hide

**US-1.5 — Search stays fast as the catalog grows**
As a shopper, I want search/browse to stay fast regardless of catalog size, so the experience
doesn't degrade as more products are added.
- Reads hit a denormalized read model (pre-joined product + variant aggregates + `tsvector`
  column), not the normalized write-side tables
- Write-side changes (product/variant create, update, stock change) publish a domain event; the
  read model is patched asynchronously from that event
- This is a deliberate consistency relaxation (read-model staleness bounded, e.g. "eventually
  consistent within N seconds"), not a bug — worth being explicit about with reviewers

## Epic: Catalog Management (Admin)

**US-1.6 — Create/update a product with variants and image gallery**
As an admin, I want to create a product with one or more variants and an image gallery, so
shoppers can purchase specific configurations with accurate stock and see real photos.
- At least one variant required
- Each variant requires a unique SKU, price, and initial stock quantity
- Attribute keys must be consistent across a product's variants (can't mix `{size}` on one
  variant and `{color}` on another within the same product)
- Images are an ordered list, independently addable/removable/reorderable from variant data

**US-1.7 — Deactivate a product (soft delete)**
As an admin, I want to deactivate a product instead of deleting it, so past orders/reviews
referencing it stay intact.
- Sets `active = false`; disappears from browse/search but existing order line items and reviews
  still resolve it by ID
