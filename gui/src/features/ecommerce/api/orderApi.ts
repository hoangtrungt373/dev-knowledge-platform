import { httpClient } from '@shared/api/httpClient';
import { PagedResponse } from '@shared/types';
import { buildQueryString } from '@shared/utils/queryString';
import { Order, OrderStatus } from '../types';

type ShowError = (msg: string) => void;

/**
 * Fronts `ecommerce-service`'s `OrderApi` (`/api/v1/orders`, US-3.3/3.5/3.6), authenticated-only
 * through `gateway`. `cancel`/`pay` both return the freshly-resolved `Order`, matching this app's
 * existing "a mutating endpoint returns the updated resource" convention from `cartApi.ts` — no
 * separate refetch needed after either action.
 */
export const orderApi = {
  /** `statuses` (post-Epic-3 follow-up, `OrderHistoryPage`'s status tabs) narrows the list to one
   * or more statuses via a repeated query param, matching `OrderApi.list`'s own
   * `@RequestParam List<OrderStatus>`; omitted (or empty) returns every status ("All"). */
  list(page: number, size: number, statuses?: OrderStatus[], showError?: ShowError): Promise<PagedResponse<Order>> {
    return httpClient.get(`/api/v1/orders${buildQueryString({ page, size, statuses })}`, showError);
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

  /** Auto-expire follow-up: re-checks this order's real gateway status right now, instead of
   * waiting for the backend's own scheduled reconciliation job — called by
   * `usePaymentCountdown` the instant its live countdown reaches zero. Deliberately no `showError`
   * default here — a failure is handled by the caller's own `onError`, not a generic toast (see
   * that hook's own note on why this call is never retried automatically). */
  reconcilePayment(id: number): Promise<Order> {
    return httpClient.post(`/api/v1/orders/${id}/reconcile`, undefined);
  },
};
