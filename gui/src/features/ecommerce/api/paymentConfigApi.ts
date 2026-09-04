import { httpClient } from '@shared/api/httpClient';

export interface PaymentConfig {
  gateway: 'mock' | 'stripe';
  /** Stripe's publishable key (`pk_test_...`) for `loadStripe()` — null when `gateway` is
   * `'mock'`, since there's nothing to initialize against. */
  publishableKey: string | null;
}

/**
 * Fronts `ecommerce-service`'s `PaymentConfigApi` (`GET /api/v1/public/payment-config`), public
 * (no auth) through `gateway`. Tells the checkout GUI at runtime whether to mount a real Stripe
 * `PaymentElement` (Option A) or just trust `orderApi.pay()`'s own synchronous verdict — see
 * `OrderDetailPage.tsx`'s `handlePay` for how the two paths meet back up.
 */
export const paymentConfigApi = {
  get(): Promise<PaymentConfig> {
    return httpClient.get('/api/v1/public/payment-config');
  },
};
