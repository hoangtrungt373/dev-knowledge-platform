import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import {
  ProductCategory, CreateProductCategoryPayload, UpdateProductCategoryPayload,
  Product, CreateProductPayload, UpdateProductPayload,
  ProductVariant, ProductVariantInput, ProductImage,
} from '../types';

type ShowError = (msg: string) => void;

function buildQuery(params: Record<string, string | number | boolean | undefined>): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

export interface ProductListParams {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDir?: string;
  productCategoryId?: number;
  active?: boolean;
  q?: string;
}

export const ecommerceApi = {
  // ── Product Categories ──────────────────────────────────────────────────────
  // No delete endpoint — ProductCategoryApi doesn't expose one (see ecommerce-service/CLAUDE.md).

  listProductCategories(q?: string, showError?: ShowError): Promise<ProductCategory[]> {
    return httpClient.get(`/api/v1/admin/product-categories${buildQuery({ q })}`, showError);
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

  // ── Products ─────────────────────────────────────────────────────────────────

  listProducts(params: ProductListParams, showError?: ShowError): Promise<PagedResponse<Product>> {
    return httpClient.get(
      `/api/v1/admin/products${buildQuery(params as Record<string, string | number | boolean | undefined>)}`,
      showError,
    );
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
};
