import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import { Product, ProductCategory, ProductSearchResult } from '../types';

type ShowError = (msg: string) => void;

function buildQuery(params: Record<string, string | number | boolean | undefined>): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}

export interface ShopSearchParams {
  page?: number;
  size?: number;
  categoryId?: number;
  q?: string;
  minPrice?: number;
  maxPrice?: number;
  inStockOnly?: boolean;
  /** Dynamic attribute filters (e.g. { size: 'M', color: 'Black' }) — every non-reserved query
   * param the backend receives is treated as one, per ProductSearchApi's own contract. */
  attributes?: Record<string, string>;
}

/**
 * The public storefront surface — `/api/v1/public/products/**`, permit-all on the backend (see
 * ecommerce-service/CLAUDE.md). Deliberately a separate file from `ecommerceApi.ts` (admin CRUD),
 * mirroring the backend's own `ProductSearchApi`/`ProductApi` split — same reasoning as
 * `@content`'s admin-vs-public separation. Goes through `httpClient`/`VITE_BACKEND_URL` like every
 * other feature — no separate origin constant needed, gateway already routes these paths, and
 * `httpClient` simply omits the Authorization header when no one is logged in.
 */
export const shopApi = {
  search(params: ShopSearchParams, showError?: ShowError): Promise<PagedResponse<ProductSearchResult>> {
    const query: Record<string, string | number | boolean | undefined> = {
      page: params.page,
      size: params.size,
      categoryId: params.categoryId,
      q: params.q,
      minPrice: params.minPrice,
      maxPrice: params.maxPrice,
      inStockOnly: params.inStockOnly,
      ...params.attributes,
    };
    return httpClient.get(`/api/v1/public/products${buildQuery(query)}`, showError);
  },

  getBySlug(slug: string, showError?: ShowError): Promise<Product> {
    return httpClient.get(`/api/v1/public/products/${slug}`, showError);
  },

  // ProductCategoryApi's own /api/v1/admin/product-categories requires ROLE_ADMIN — a logged-out
  // or non-admin shopper can't reach it, hence this separate public endpoint for the category
  // filter rail (see ecommerce-service's PublicProductCategoryApi).
  listCategories(showError?: ShowError): Promise<ProductCategory[]> {
    return httpClient.get('/api/v1/public/product-categories', showError);
  },
};
