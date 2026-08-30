import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import { Order } from '../types';

type ShowError = (msg: string) => void;

/**
 * Fronts `ecommerce-service`'s `OrderApi` (`/api/v1/orders`, US-3.3/3.5/3.6), authenticated-only
 * through `gateway`. `cancel`/`pay` both return the freshly-resolved `Order`, matching this app's
 * existing "a mutating endpoint returns the updated resource" convention from `cartApi.ts` — no
 * separate refetch needed after either action.
 */
export const orderApi = {
  list(page: number, size: number, showError?: ShowError): Promise<PagedResponse<Order>> {
    return httpClient.get(`/api/v1/orders?page=${page}&size=${size}`, showError);
  },

  getById(id: number, showError?: ShowError): Promise<Order> {
    return httpClient.get(`/api/v1/orders/${id}`, showError);
  },

  cancel(id: number, showError?: ShowError): Promise<Order> {
    return httpClient.post(`/api/v1/orders/${id}/cancel`, undefined, showError);
  },

  pay(id: number, showError?: ShowError): Promise<Order> {
    return httpClient.post(`/api/v1/orders/${id}/pay`, undefined, showError);
  },
};
