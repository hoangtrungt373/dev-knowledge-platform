import { httpClient } from '@shared/api/httpClient';
import { Cart } from '../types';

type ShowError = (msg: string) => void;

/**
 * Fronts `ecommerce-service`'s `CartApi` (`/api/v1/cart`, US-2.1–2.4), authenticated-only through
 * `gateway`. Every method returns the freshly-resolved `Cart`, matching the backend's own
 * "every mutating endpoint returns the updated cart" contract — no separate refetch needed after
 * a mutation.
 */
export const cartApi = {
  getCart(showError?: ShowError): Promise<Cart> {
    return httpClient.get('/api/v1/cart', showError);
  },

  addItem(variantId: number, quantity: number, showError?: ShowError): Promise<Cart> {
    return httpClient.post('/api/v1/cart/items', { variantId, quantity }, showError);
  },

  updateItem(variantId: number, quantity: number, showError?: ShowError): Promise<Cart> {
    return httpClient.put(`/api/v1/cart/items/${variantId}`, { quantity }, showError);
  },

  removeItem(variantId: number, showError?: ShowError): Promise<Cart> {
    return httpClient.delete(`/api/v1/cart/items/${variantId}`, showError);
  },
};
