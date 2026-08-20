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
