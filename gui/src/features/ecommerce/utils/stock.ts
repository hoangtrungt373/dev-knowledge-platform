/** Below this many units left, nudge the shopper with "Only N left" instead of a plain in-stock
 * state — shared by `ProductDetailPage` and `CartPage` so the threshold/wording can't drift
 * between the two places a shopper sees remaining stock. */
export const LOW_STOCK_THRESHOLD = 5;

export function isLowStock(available: number | undefined): boolean {
  return available !== undefined && available > 0 && available <= LOW_STOCK_THRESHOLD;
}

export function lowStockMessage(available: number): string {
  return `Only ${available} left in stock!`;
}
