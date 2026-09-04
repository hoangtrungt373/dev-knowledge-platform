// ── Product Categories ──────────────────────────────────────────────────────
// Fronts ecommerce-service's ProductCategory, deliberately distinct from content-service's own
// Category (unrelated domain — product taxonomy vs. knowledge-base taxonomy). Supports an optional
// parent/child hierarchy (self-referential parentId), mirroring @content's own Category/
// CategoryTreeNode shape exactly.

/** One `ProductCategory` → `ProductAttribute` assignment on a category's own attribute schema —
 * ids only, not the full attribute (mirrors the backend's own `CategoryAttributeAssignmentResponse`
 * and `ProductResponse.tagIds`'s "ids only" precedent); the GUI cross-references
 * `ecommerceApi.listProductAttributes` by id to show a name. See `ProductAttribute` below for the
 * full "Option B" global attribute registry this belongs to. */
export interface CategoryAttributeAssignment {
  attributeId: number;
  required: boolean;
  displayOrder: number;
}

/** Same shape as `CategoryAttributeAssignment` above, minus `displayOrder` — an assignment's order
 * is its position in the array sent to `create`/`updateProductCategory`, not a field of its own
 * (mirrors the backend's own `CategoryAttributeAssignmentRequest`). */
export interface CategoryAttributeAssignmentInput {
  attributeId: number;
  required: boolean;
}

export interface ProductCategory {
  id: number;
  name: string;
  slug: string;
  parentId: number | null;
  /** This category's attribute schema, in display order — empty if none assigned (fully
   * free-form, today's pre-"Option B" behavior). */
  attributes: CategoryAttributeAssignment[];
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
  /** `undefined`/omitted means none assigned yet — see `ProductCategoryService.create`'s own
   * Javadoc for why `create` has no "leave unchanged" case the way `update` below does. */
  attributes?: CategoryAttributeAssignmentInput[];
}

export interface UpdateProductCategoryPayload {
  name: string;
  parentId: number | null;
  /** `undefined`/omitted leaves the existing schema untouched; an empty array clears it — same
   * three-state semantics as the backend's own `ProductCategoryService.update`. */
  attributes?: CategoryAttributeAssignmentInput[];
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

// ── Product Attributes ("Option B" global attribute registry) ────────────────
// Fronts ecommerce-service's own ProductAttribute/ProductAttributeValue — a reusable attribute
// concept (e.g. "color") with a controlled vocabulary, assigned to whichever ProductCategory rows
// need it via the attributes field above. See ecommerce-service/CLAUDE.md's own note on this
// feature for the full "Option A vs. Option B" design discussion this came out of.

export interface ProductAttributeValue {
  id: number;
  value: string;
  /** This value's position in the list submitted on create/update — not independently editable. */
  displayOrder: number;
}

export interface ProductAttribute {
  id: number;
  /** Matched literally, case-sensitively, against a `ProductVariant.attributes` map key — see the
   * backend entity's own Javadoc. */
  name: string;
  /** In display order. */
  values: ProductAttributeValue[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateProductAttributePayload {
  name: string;
  /** The attribute's controlled vocabulary, in display order — must be non-empty. */
  values: string[];
}

export interface UpdateProductAttributePayload {
  name: string;
  /** The attribute's new, complete controlled vocabulary, in display order — must be non-empty. */
  values: string[];
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
  /** Optional like line2 below for the same reason: a handful of orders/saved addresses placed
   * before this field existed have no phone on file (see ecommerce-service's own PHONE column
   * migration, nullable at the DB level); every fresh write is required to supply one. */
  phone?: string;
  /** The invoice/order-confirmation recipient — deliberately independent of the caller's
   * Keycloak login email, since the two can legitimately differ. Optional for the same
   * "no backfill" reason as phone above. */
  email?: string;
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
  /** Optional for the same "no backfill" reason as Address.phone above. */
  phone?: string;
  /** Optional for the same "no backfill" reason — see Address.email above. */
  email?: string;
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
  phone: string;
  email: string;
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
  phone: string;
  email: string;
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
  phone?: string;
  email?: string;
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
  /** A SUBTOTAL coupon's discount — subtotal itself is never reduced, this is a separate amount
   * (Coupon feature Phase 2). Zero when no subtotal coupon is applied. */
  subtotalDiscountAmount: number;
  shippingFee: number;
  /** What shippingFee would be absent any promotional waiver (e.g. free-shipping-over-threshold,
   * or Phase 2's SHIPPING_FEE coupon) — equal to shippingFee whenever nothing was waived. A value
   * greater than shippingFee means a waiver applied, and the GUI shows this original fee struck
   * through rather than just the final number. */
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

// ── Payments (Epic 4 Phase 7, US-4.7) ────────────────────────────────────────
// Mirrors ecommerce-service's PaymentStatus/PaymentFailureCategory enums — surfaced read-only on
// Order below, resolved server-side from a live lookup of the order's own Payment row.

export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'DECLINED' | 'REFUNDED';
export type PaymentFailureCategory = 'INSUFFICIENT_FUNDS' | 'CARD_DECLINED' | 'GATEWAY_ERROR';

export interface Order {
  id: number;
  status: OrderStatus;
  cancelRequested: boolean;
  shippingAddress: Address;
  subtotal: number;
  /** A SUBTOTAL coupon's discount, persisted at checkout (Coupon feature Phase 2) — zero when
   * none was applied. */
  subtotalDiscountAmount: number;
  shippingFee: number;
  /** What shippingFee would have been absent any promotional waiver — equal to shippingFee
   * whenever nothing was waived; see CheckoutPreview's own note above. */
  originalShippingFee: number;
  total: number;
  /** The redeemed coupon codes, if any (Coupon feature Phase 2) — null when that target had none
   * applied. */
  subtotalCouponCode: string | null;
  shippingCouponCode: string | null;
  /** Null until a payment attempt has actually started (a PENDING order that never called
   * `pay()` has no Payment row on the backend yet). */
  paymentStatus: PaymentStatus | null;
  /** Only ever set alongside a DECLINED paymentStatus. */
  paymentFailureCategory: PaymentFailureCategory | null;
  /** A short, non-technical, server-owned reason for a DECLINED payment (US-4.7) — never the
   * gateway's own raw error string, which this app never receives at all. */
  paymentFailureMessage: string | null;
  /** Only ever present on the response to `POST /orders/{id}/pay`, and only when the charge is
   * still awaiting the shopper's own client-side confirmation (Option A: Stripe Elements) — null
   * on every other response this type backs (list/get-by-id/cancel), and null even from `pay()`
   * once the gateway already resolved the charge synchronously (MockPaymentGateway, or an
   * outright-declined Stripe charge). Never persisted — see backend's OrderResponse Javadoc. */
  paymentClientSecret: string | null;
  lines: OrderLine[];
  /** Oldest first, per the backend's own @OrderBy("id ASC") — read top-to-bottom as a timeline. */
  statusHistory: OrderStatusHistoryEntry[];
}

// ── Coupons ("ProductDiscount" feature) ─────────────────────────────────────
// Mirrors ecommerce-service's Coupon entity / CouponResponse / Create+UpdateCouponRequest.
// Admin-only CRUD (Phase 1); redemption at checkout is Phase 2 (see CheckoutPreview/Order above
// and CheckoutAddressInput below) — this section is only the admin-management shape.

export type CouponTarget = 'SUBTOTAL' | 'SHIPPING_FEE';
export type CouponType = 'PERCENTAGE' | 'FIXED_AMOUNT';

export interface Coupon {
  id: number;
  code: string;
  target: CouponTarget;
  type: CouponType;
  value: number;
  active: boolean;
  startAt: string | null;
  endAt: string | null;
  minSubtotal: number | null;
  maxRedemptions: number | null;
  maxRedemptionsPerUser: number | null;
  /** Caps a single redemption's discount regardless of value/type (e.g. "20% off, up to $20") —
   * null means no cap. Applied uniformly to both CouponType values, not just PERCENTAGE. */
  maxDiscountAmount: number | null;
  /** Shopper-facing summary (e.g. "20% off orders over $100, up to $20") for the future coupon
   * picker dialog — purely presentational, never read by eligibility/discount logic. */
  description: string | null;
  /** Permanent, unsigned promo banner/icon URL for that same future dialog — null if the admin
   * never uploaded one. Set via `ecommerceApi.uploadCouponImage`, never re-uploaded on save. */
  imageUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCouponPayload {
  code: string;
  target: CouponTarget;
  type: CouponType;
  value: number;
  active?: boolean;
  startAt?: string;
  endAt?: string;
  minSubtotal?: number;
  maxRedemptions?: number;
  maxRedemptionsPerUser?: number;
  maxDiscountAmount?: number;
  description?: string;
  imageUrl?: string;
}

/** No `code` field — immutable after creation, mirroring the backend's UpdateCouponRequest. */
export interface UpdateCouponPayload {
  target: CouponTarget;
  type: CouponType;
  value: number;
  active: boolean;
  startAt?: string;
  endAt?: string;
  minSubtotal?: number;
  maxRedemptions?: number;
  maxRedemptionsPerUser?: number;
  maxDiscountAmount?: number;
  description?: string;
  imageUrl?: string;
}

/** One entry in the shopper-facing coupon picker (`couponApi.listAvailable`) — mirrors the
 * backend's `AvailableCouponResponse`, deliberately leaner than `Coupon` above (no id/active/
 * startAt/redemption-limit counters, all irrelevant once a coupon has already been filtered to
 * "currently redeemable" server-side). `eligible`/`discountAmount` are both computed server-side
 * against the caller's own order (subtotal for eligibility always; subtotal or the passed
 * `shippingFee`, depending on `target`, for `discountAmount`) — not derived client-side, so the
 * GUI never duplicates either computation. The list itself already arrives sorted by what's best
 * for this order (eligible first, then `discountAmount` descending) — no client-side sort needed. */
export interface AvailableCoupon {
  code: string;
  target: CouponTarget;
  type: CouponType;
  value: number;
  minSubtotal: number | null;
  maxDiscountAmount: number | null;
  description: string | null;
  imageUrl: string | null;
  endAt: string | null;
  eligible: boolean;
  /** What this coupon would actually deduct from this order right now — the same computation
   * checkout itself uses. Purely informational (never itself applied), and what the list is
   * sorted by. */
  discountAmount: number;
}
