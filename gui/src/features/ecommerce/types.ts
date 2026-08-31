// ── Product Categories ──────────────────────────────────────────────────────
// Fronts ecommerce-service's ProductCategory, deliberately distinct from content-service's own
// Category (unrelated domain — product taxonomy vs. knowledge-base taxonomy). Supports an optional
// parent/child hierarchy (self-referential parentId), mirroring @content's own Category/
// CategoryTreeNode shape exactly.

export interface ProductCategory {
  id: number;
  name: string;
  slug: string;
  parentId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProductCategoryTreeNode {
  id: number;
  name: string;
  slug: string;
  parentId: number | null;
  children: ProductCategoryTreeNode[];
}

export interface CreateProductCategoryPayload {
  name: string;
  parentId?: number | null;
}

export interface UpdateProductCategoryPayload {
  name: string;
  parentId: number | null;
}

// ── Products ─────────────────────────────────────────────────────────────────

export interface ProductVariant {
  id: number;
  sku: string;
  price: number;
  stockQuantity: number;
  reservedQuantity: number;
  attributes: Record<string, string>;
}

export interface ProductImage {
  id: number;
  storageKey: string;
  sortOrder: number;
  /** Time-limited presigned GET URL, resolved server-side — never construct one client-side. */
  url: string;
}

export interface Product {
  id: number;
  name: string;
  description: string | null;
  slug: string;
  active: boolean;
  productCategoryId: number;
  categoryName: string;
  variants: ProductVariant[];
  images: ProductImage[];
  createdAt: string;
  updatedAt: string;
}

/** Nested variant input for {@link CreateProductPayload} — every variant of one product must share the same attribute keys. */
export interface ProductVariantInput {
  sku: string;
  price: number;
  stockQuantity: number;
  attributes?: Record<string, string>;
}

export interface CreateProductPayload {
  name: string;
  description?: string;
  productCategoryId: number;
  variants: ProductVariantInput[];
}

export interface UpdateProductPayload {
  name: string;
  description?: string;
  productCategoryId: number;
}

// ── Storefront (public browse/search) ───────────────────────────────────────
// Mirrors ProductSearchResponse — a different, denormalized shape from Product above (that one
// backs admin CRUD and the detail page; this one backs the CQRS read model browse/search grid).

export interface ProductSearchResult {
  productId: number;
  name: string;
  slug: string;
  productCategoryId: number;
  categoryName: string;
  minPrice: number;
  maxPrice: number;
  inStock: boolean;
  /** Time-limited presigned GET URL, resolved server-side; null if the product has no images yet. */
  primaryImageUrl: string | null;
  availableAttributes: Record<string, string[]>;
}

// ── Cart & Checkout (Epic 2) ─────────────────────────────────────────────────
// Mirrors ecommerce-service's CartResponse/CartLineResponse/CheckoutPreviewResponse/
// CheckoutConfirmResponse. A CartLine's fields beyond variantId/quantity/available are omitted by
// the backend (@JsonInclude(NON_NULL)) when available is false — optional here for the same
// reason, not because they're ever optional on an available line.

export interface CartLine {
  variantId: number;
  quantity: number;
  /** false when the variant (or its product) no longer exists/is active — US-2.7. */
  available: boolean;
  sku?: string;
  productId?: number;
  productName?: string;
  productSlug?: string;
  attributes?: Record<string, string>;
  unitPrice?: number;
  lineTotal?: number;
  /** Time-limited presigned GET URL for the product's first gallery image; null if it has none yet. */
  primaryImageUrl?: string | null;
  /** Units currently available for this line's variant (stockQuantity - reservedQuantity) — lets
   * the cart cap the quantity stepper and show a low-stock nudge without a second round trip. */
  availableQuantity?: number;
}

export interface Cart {
  lines: CartLine[];
  /** Sum of lineTotal across available lines only. */
  subtotal: number;
  /** Sum of quantity across available lines only. */
  itemCount: number;
}

export interface Address {
  fullName: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export interface OrderLine {
  variantId: number;
  sku: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  /** The purchased variant's current attributes/primary image/product slug, resolved live against
   * today's catalog by variant id (post-Epic-3 follow-up) — unlike every other field here, which
   * is a frozen-at-purchase-time snapshot. All null if that variant (or its product) has since
   * been deleted. `productSlug` links the line back to `/shop/${productSlug}`. */
  attributes?: Record<string, string> | null;
  primaryImageUrl?: string | null;
  productSlug?: string | null;
}

export interface CheckoutPreview {
  /** Every current cart line — some may have available: false (US-2.7). */
  lines: CartLine[];
  subtotal: number;
  shippingFee: number;
  total: number;
}

/** The confirmed order (US-2.6) — mirrors CheckoutConfirmResponse. */
export interface OrderConfirmation {
  orderId: number;
  status: string;
  address: Address;
  lines: OrderLine[];
  subtotal: number;
  shippingFee: number;
  total: number;
  /** Any cart line dropped at this final revalidation — normally empty. */
  droppedLines: CartLine[];
}

// ── Orders (Epic 3) ──────────────────────────────────────────────────────────
// Mirrors ecommerce-service's OrderResponse/OrderStatusHistoryResponse. Reuses OrderLine/Address
// above (Epic 2) as-is — both already field-match the backend's nested shapes exactly, so there's
// nothing Epic-3-specific about a line item or a shipping address to model separately.

export type OrderStatus =
  | 'PENDING'
  | 'PAYMENT_PROCESSING'
  | 'CONFIRMED'
  | 'EXPIRED'
  | 'FAILED'
  | 'CANCELLED'
  | 'SHIPPED'
  | 'DELIVERED';

/** One entry in an order's timeline (US-3.5) — fromStatus is null only for the very first entry. */
export interface OrderStatusHistoryEntry {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  reason: string | null;
  occurredAt: string;
}

export interface Order {
  id: number;
  status: OrderStatus;
  cancelRequested: boolean;
  shippingAddress: Address;
  subtotal: number;
  shippingFee: number;
  total: number;
  lines: OrderLine[];
  /** Oldest first, per the backend's own @OrderBy("id ASC") — read top-to-bottom as a timeline. */
  statusHistory: OrderStatusHistoryEntry[];
}
