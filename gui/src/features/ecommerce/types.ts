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

// ── Product Tags ─────────────────────────────────────────────────────────────
// Fronts ecommerce-service's own ProductTag — deliberately distinct from content-service's Tag
// (unrelated domain) and simpler than it: no status lifecycle, admin-only for now (no storefront
// filtering yet — see ecommerce-service/CLAUDE.md's Product Tags note).

export interface ProductTag {
  id: number;
  name: string;
  slug: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductTagPayload {
  name: string;
}

export interface UpdateProductTagPayload {
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
  /** Ids only, matching content-service's own tagIds convention — no name/slug denormalized here. */
  tagIds: number[];
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
  tagIds?: number[];
}

/** tagIds: omit to leave unchanged, [] to clear, non-empty to replace — mirrors the backend's
 * three-state ProductCommands.Update.tagIds semantics (see ecommerce-service/CLAUDE.md). */
export interface UpdateProductPayload {
  name: string;
  description?: string;
  productCategoryId: number;
  tagIds?: number[];
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

// ── AddressBook ──────────────────────────────────────────────────────────────
// Mirrors ecommerce-service's SavedAddress — a full first-class, independently-owned entity
// (create/edit/delete/set-default), deliberately distinct from Address above (still a plain
// frozen-at-checkout-time snapshot on an Order, no lifecycle of its own).

export interface SavedAddress {
  id: number;
  label: string | null;
  fullName: string;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  defaultAddress: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSavedAddressPayload {
  label?: string;
  fullName: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  /** The caller's very first address is auto-defaulted regardless of this flag — see
   * ecommerce-service/CLAUDE.md's own AddressBook note. */
  makeDefault?: boolean;
}

/** No makeDefault here — promoting an address to default is its own dedicated action
 * (addressApi.setDefault), kept separate from a plain field edit, mirroring the backend. */
export interface UpdateSavedAddressPayload {
  label?: string;
  fullName: string;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

/** Checkout's own two-shape address contract (mirrors ecommerce-service's AddressRequest) — either
 * an existing AddressBook entry (savedAddressId, every other field ignored) or a fresh, one-off
 * address, optionally saved into the AddressBook via saveAddress/addressLabel. */
export interface CheckoutAddressInput {
  savedAddressId?: number;
  fullName?: string;
  line1?: string;
  line2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  saveAddress?: boolean;
  addressLabel?: string;
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
  /** What shippingFee would be absent any promotional waiver (e.g. free-shipping-over-threshold)
   * — equal to shippingFee whenever nothing was waived. A value greater than shippingFee means a
   * waiver applied (today: FreeOverThresholdShippingFeeCalculator's own threshold), and the GUI
   * shows this original fee struck through rather than just the final number. */
  originalShippingFee: number;
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
  /** What shippingFee would have been absent any promotional waiver — equal to shippingFee
   * whenever nothing was waived; see CheckoutPreview's own note above. */
  originalShippingFee: number;
  total: number;
  lines: OrderLine[];
  /** Oldest first, per the backend's own @OrderBy("id ASC") — read top-to-bottom as a timeline. */
  statusHistory: OrderStatusHistoryEntry[];
}
