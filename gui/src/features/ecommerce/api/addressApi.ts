import { httpClient } from '@shared/api/httpClient';
import { CreateSavedAddressPayload, SavedAddress, UpdateSavedAddressPayload } from '../types';

type ShowError = (msg: string) => void;

/**
 * Fronts `ecommerce-service`'s `SavedAddressApi` (`/api/v1/addresses`) — the shopper's own
 * AddressBook. A separate file from `checkoutApi.ts`/`ecommerceApi.ts`, mirroring the backend's
 * own `SavedAddressApi`/`CheckoutApi` split. Never admin-gated — every method operates on the
 * caller's own addresses only.
 */
export const addressApi = {
  list(showError?: ShowError): Promise<SavedAddress[]> {
    return httpClient.get('/api/v1/addresses', showError);
  },

  create(payload: CreateSavedAddressPayload, showError?: ShowError): Promise<SavedAddress> {
    return httpClient.post('/api/v1/addresses', payload, showError);
  },

  update(id: number, payload: UpdateSavedAddressPayload, showError?: ShowError): Promise<SavedAddress> {
    return httpClient.put(`/api/v1/addresses/${id}`, payload, showError);
  },

  remove(id: number, showError?: ShowError): Promise<void> {
    return httpClient.delete(`/api/v1/addresses/${id}`, showError);
  },

  setDefault(id: number, showError?: ShowError): Promise<SavedAddress> {
    return httpClient.post(`/api/v1/addresses/${id}/set-default`, undefined, showError);
  },
};
