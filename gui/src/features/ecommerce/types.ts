// ── Product Categories ──────────────────────────────────────────────────────
// Flat taxonomy (no parent tree, unlike @content's Category) — fronts ecommerce-service's
// ProductCategory, deliberately distinct from content-service's own Category (unrelated domain).

export interface ProductCategory {
  id: number;
  name: string;
  slug: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductCategoryPayload {
  name: string;
}

export interface UpdateProductCategoryPayload {
  name: string;
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
