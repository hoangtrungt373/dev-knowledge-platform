import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import { buildQueryString, QueryParams } from '@shared/utils/queryString';
import {
  ProductCategory, ProductCategoryTreeNode, CreateProductCategoryPayload, UpdateProductCategoryPayload,
  ProductTag, CreateProductTagPayload, UpdateProductTagPayload,
  Product, CreateProductPayload, UpdateProductPayload,
  ProductVariant, ProductVariantInput, ProductImage,
  Coupon, CreateCouponPayload, UpdateCouponPayload, CouponTarget,
} from '../types';

type ShowError = (msg: string) => void;

export interface ProductListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  productCategoryId?: number;
  active?: boolean;
  q?: string;
  tagIds?: number[];
}

export interface ProductTagListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  q?: string;
}

export interface CouponListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  q?: string;
  active?: boolean;
  target?: CouponTarget;
}

export const ecommerceApi = {
  // ── Product Categories ──────────────────────────────────────────────────────
  // No delete endpoint — ProductCategoryApi doesn't expose one (see ecommerce-service/CLAUDE.md).

  listProductCategories(q?: string, showError?: ShowError): Promise<ProductCategory[]> {
    return httpClient.get(`/api/v1/admin/product-categories${buildQueryString({ q })}`, showError);
  },

  getProductCategoryTree(showError?: ShowError): Promise<ProductCategoryTreeNode[]> {
    return httpClient.get('/api/v1/admin/product-categories/tree', showError);
  },

  createProductCategory(
    payload: CreateProductCategoryPayload, showError?: ShowError,
  ): Promise<ProductCategory> {
    return httpClient.post('/api/v1/admin/product-categories', payload, showError);
  },

  updateProductCategory(
    id: number, payload: UpdateProductCategoryPayload, showError?: ShowError,
  ): Promise<ProductCategory> {
    return httpClient.put(`/api/v1/admin/product-categories/${id}`, payload, showError);
  },

  // ── Product Tags ─────────────────────────────────────────────────────────────
  // No status field/filter — see ProductTag's own Javadoc-equivalent note in types.ts.

  listProductTags(
    params: ProductTagListParams, showError?: ShowError,
  ): Promise<PagedResponse<ProductTag>> {
    // params is typed via a named `interface` (no implicit index signature) — see QueryParams's
    // own doc comment for why that needs an explicit cast here where an inline object literal
    // wouldn't.
    return httpClient.get(`/api/v1/admin/product-tags${buildQueryString(params as QueryParams)}`, showError);
  },

  createProductTag(payload: CreateProductTagPayload, showError?: ShowError): Promise<ProductTag> {
    return httpClient.post('/api/v1/admin/product-tags', payload, showError);
  },

  updateProductTag(
    id: number, payload: UpdateProductTagPayload, showError?: ShowError,
  ): Promise<ProductTag> {
    return httpClient.put(`/api/v1/admin/product-tags/${id}`, payload, showError);
  },

  deleteProductTag(id: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/product-tags/${id}`, showError);
  },

  // ── Products ─────────────────────────────────────────────────────────────────

  listProducts(params: ProductListParams, showError?: ShowError): Promise<PagedResponse<Product>> {
    // tagIds is a repeated query param (?tagIds=1&tagIds=2), matching ProductApi's own
    // @RequestParam Set<Integer> tagIds binding — buildQueryString appends an array value as
    // repeated keys, so it's passed straight through alongside the scalar params (cast needed —
    // see QueryParams's own doc comment).
    return httpClient.get(`/api/v1/admin/products${buildQueryString(params as QueryParams)}`, showError);
  },

  getProduct(id: number, showError?: ShowError): Promise<Product> {
    return httpClient.get(`/api/v1/admin/products/${id}`, showError);
  },

  createProduct(payload: CreateProductPayload, showError?: ShowError): Promise<Product> {
    return httpClient.post('/api/v1/admin/products', payload, showError);
  },

  updateProduct(id: number, payload: UpdateProductPayload, showError?: ShowError): Promise<Product> {
    return httpClient.put(`/api/v1/admin/products/${id}`, payload, showError);
  },

  deactivateProduct(id: number, showError?: ShowError): Promise<Product> {
    return httpClient.patch(`/api/v1/admin/products/${id}/deactivate`, undefined, showError);
  },

  // ── Variants ─────────────────────────────────────────────────────────────────
  // No update endpoint — ProductApi only supports add/remove; changing a variant means removing
  // and re-adding it (matches the backend's own surface, see ecommerce-service/CLAUDE.md).

  addVariant(
    productId: number, input: ProductVariantInput, showError?: ShowError,
  ): Promise<ProductVariant> {
    return httpClient.post(`/api/v1/admin/products/${productId}/variants`, input, showError);
  },

  removeVariant(productId: number, variantId: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/products/${productId}/variants/${variantId}`, showError);
  },

  // ── Images ───────────────────────────────────────────────────────────────────
  // uploadImage is the only image-adding call the GUI uses — the raw-storageKey addImage
  // endpoint exists server-side but has no GUI caller (nothing in this app already knows a MinIO
  // key ahead of time).

  uploadImage(
    productId: number, file: File, sortOrder: number, showError?: ShowError,
  ): Promise<ProductImage> {
    const form = new FormData();
    form.append('file', file);
    form.append('sortOrder', String(sortOrder));
    return httpClient.postForm(`/api/v1/admin/products/${productId}/images/upload`, form, showError);
  },

  removeImage(productId: number, imageId: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/products/${productId}/images/${imageId}`, showError);
  },

  updateImageSortOrder(
    productId: number, imageId: number, sortOrder: number, showError?: ShowError,
  ): Promise<ProductImage> {
    return httpClient.patch(`/api/v1/admin/products/${productId}/images/${imageId}`, { sortOrder }, showError);
  },

  // ── Description images ──────────────────────────────────────────────────────
  // A separate resource from the gallery uploadImage above — no productId, since an image
  // embedded inline in a description isn't a ProductImage row at all (see
  // ecommerce-service/CLAUDE.md's ProductDescriptionImageService note); the returned url is
  // permanent (never expires), unlike ProductImage.url's presigned one, which is why this can't
  // just reuse uploadImage's endpoint.

  uploadDescriptionImage(file: File, showError?: ShowError): Promise<{ url: string }> {
    const form = new FormData();
    form.append('file', file);
    return httpClient.postForm('/api/v1/admin/products/description-images/upload', form, showError);
  },

  // ── Coupons ("ProductDiscount" feature, Phase 4 admin GUI) ────────────────────
  // Admin CRUD only — redemption itself happens through checkoutApi's preview/confirm, not here.

  listCoupons(params: CouponListParams, showError?: ShowError): Promise<PagedResponse<Coupon>> {
    return httpClient.get(`/api/v1/admin/coupons${buildQueryString(params as QueryParams)}`, showError);
  },

  createCoupon(payload: CreateCouponPayload, showError?: ShowError): Promise<Coupon> {
    return httpClient.post('/api/v1/admin/coupons', payload, showError);
  },

  updateCoupon(id: number, payload: UpdateCouponPayload, showError?: ShowError): Promise<Coupon> {
    return httpClient.put(`/api/v1/admin/coupons/${id}`, payload, showError);
  },

  deleteCoupon(id: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/admin/coupons/${id}`, showError);
  },

  // Usable in create mode too (no couponId needed) — same shape as uploadDescriptionImage above,
  // returns a permanent URL rather than a presigned one (see CouponImageService's own Javadoc).
  uploadCouponImage(file: File, showError?: ShowError): Promise<{ url: string }> {
    const form = new FormData();
    form.append('file', file);
    return httpClient.postForm('/api/v1/admin/coupons/images/upload', form, showError);
  },
};
