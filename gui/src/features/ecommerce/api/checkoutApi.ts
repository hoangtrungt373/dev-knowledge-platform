import { httpClient } from '@shared/api/httpClient';
import { Address, CheckoutPreview, OrderConfirmation } from '../types';

type ShowError = (msg: string) => void;

/**
 * Fronts `ecommerce-service`'s `CheckoutApi` (`/api/v1/checkout`, US-2.5–2.7), authenticated-only
 * through `gateway`. Two-step flow, same as the backend: `preview` revalidates the current cart
 * and shows what confirming right now would produce; `confirm` creates the order. Neither call
 * needs the caller to pass cart contents — both operate on the caller's own server-side cart.
 */
export const checkoutApi = {
  preview(showError?: ShowError): Promise<CheckoutPreview> {
    return httpClient.get('/api/v1/checkout/preview', showError);
  },

  confirm(address: Address, showError?: ShowError): Promise<OrderConfirmation> {
    return httpClient.post('/api/v1/checkout/confirm', address, showError);
  },
};
