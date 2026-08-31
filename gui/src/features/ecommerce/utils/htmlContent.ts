/**
 * True if `html` has anything a shopper/admin would actually see — real text, or an image. TipTap's
 * "empty" document serializes as `"<p></p>"` (or similar), never `""`, so a plain truthy/`.trim()`
 * check isn't enough on either side of `Product.description`: `ProductFormPage`'s submit guard
 * needs it to avoid sending that markup instead of omitting the field, and `ProductDetailPage`'s
 * render guard needs it to avoid showing an empty "Product Details" section for a product that was
 * never really given a description.
 */
export function hasVisibleHtmlContent(html: string | null | undefined): boolean {
  if (!html) return false;
  return html.replace(/<[^>]*>/g, '').trim().length > 0 || /<img[\s>]/i.test(html);
}
