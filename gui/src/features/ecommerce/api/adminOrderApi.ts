import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import { Order, OrderStatus } from '../types';

type ShowError = (msg: string) => void;

/**
 * Fronts `ecommerce-service`'s `AdminOrderApi` (`/api/v1/admin/orders`, US-3.7/3.8), admin-gated
 * through `gateway`. A separate file from `orderApi.ts` (shopper-facing), mirroring the backend's
 * own `AdminOrderApi`/`OrderApi` split. `ship`/`deliver` both return the freshly-resolved `Order`,
 * same "mutating endpoint returns the updated resource" contract as every other mutation in this
 * feature.
 */
export const adminOrderApi = {
  list(status: OrderStatus | undefined, page: number, size: number, showError?: ShowError): Promise<PagedResponse<Order>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    return httpClient.get(`/api/v1/admin/orders?${params.toString()}`, showError);
  },

  ship(id: number, showError?: ShowError): Promise<Order> {
    return httpClient.post(`/api/v1/admin/orders/${id}/ship`, undefined, showError);
  },

  deliver(id: number, showError?: ShowError): Promise<Order> {
    return httpClient.post(`/api/v1/admin/orders/${id}/deliver`, undefined, showError);
  },
};
