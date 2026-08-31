/**
 * Small formatting helpers shared across this feature's pages/components — extracted once
 * `formatPrice` had been copy-pasted verbatim into six separate files (`CartPage.tsx`,
 * `OrderLineRow.tsx`, `AdminOrderListPage.tsx`, `CheckoutPage.tsx`, `OrderDetailPage.tsx`,
 * `OrderHistoryPage.tsx`) over the course of many small edits. `ProductCard.tsx`'s own
 * `formatPrice(min, max)` and `ProductDetailPage.tsx`'s `formatPriceRange` are genuinely
 * different-shaped (a price *range*, not a single value) and deliberately stay local rather than
 * being folded in here.
 */
export function formatPrice(value: number): string {
  return `$${value.toFixed(2)}`;
}

/**
 * Joins a variant's attribute values (e.g. `{ size: '15in', color: 'Black' }` → `"15in Black"`)
 * — values only, no `key:` prefixes, matching the plainer label `CartPage`'s own variation chip
 * settled on. `undefined`/`null`/empty attributes all resolve to `''`, so callers can render the
 * result directly behind a truthy check without a separate null guard.
 */
export function formatVariantLabel(attributes: Record<string, string> | null | undefined): string {
  return attributes ? Object.values(attributes).join(' ') : '';
}
