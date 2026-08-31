import { httpClient } from '@shared/api/httpClient';
import { Address, CheckoutPreview, OrderConfirmation } from '../types';

type ShowError = (msg: string) => void;

/**
 * Fronts `ecommerce-service`'s `CheckoutApi` (`/api/v1/checkout`, US-2.5–2.7), authenticated-only
 * through `gateway`. Two-step flow, same as the backend: `preview` revalidates the current cart
 * and shows what confirming right now would produce; `confirm` creates the order. Neither call
 * needs the caller to pass cart contents — both operate on the caller's own server-side cart.
 *
 * `selectedVariantIds` (post-Epic-2 follow-up) restricts either call to a subset of the cart;
 * omitted operates on the whole cart, same as before this parameter existed.
 */
export const checkoutApi = {
  preview(selectedVariantIds?: number[], showError?: ShowError): Promise<CheckoutPreview> {
    // Repeated query param (?selectedVariantIds=1&selectedVariantIds=2) — preview is a GET, so
    // there's no body to carry a selection in; matches CheckoutApi.preview's own @RequestParam.
    const query = selectedVariantIds?.length
      ? `?${selectedVariantIds.map(id => `selectedVariantIds=${id}`).join('&')}`
      : '';
    return httpClient.get(`/api/v1/checkout/preview${query}`, showError);
  },

  confirm(address: Address, selectedVariantIds?: number[], showError?: ShowError): Promise<OrderConfirmation> {
    return httpClient.post('/api/v1/checkout/confirm', { ...address, selectedVariantIds }, showError);
  },
};
