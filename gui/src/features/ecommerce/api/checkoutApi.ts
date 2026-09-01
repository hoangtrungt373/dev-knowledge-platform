import { httpClient } from '@shared/api/httpClient';
import { buildQueryString } from '@shared/utils/queryString';
import { CheckoutAddressInput, CheckoutPreview, OrderConfirmation } from '../types';

type ShowError = (msg: string) => void;

/**
 * Fronts `ecommerce-service`'s `CheckoutApi` (`/api/v1/checkout`, US-2.5–2.7), authenticated-only
 * through `gateway`. Two-step flow, same as the backend: `preview` revalidates the current cart
 * and shows what confirming right now would produce; `confirm` creates the order. Neither call
 * needs the caller to pass cart contents — both operate on the caller's own server-side cart.
 *
 * `selectedVariantIds` (post-Epic-2 follow-up) restricts either call to a subset of the cart;
 * omitted operates on the whole cart, same as before this parameter existed.
 *
 * `subtotalCouponCode`/`shippingCouponCode` (Coupon feature Phase 4) mirror `CheckoutApi`'s own
 * optional params — one code per `CouponTarget`, `undefined` applies none. `preview` never
 * redeems (safe to call repeatedly while a shopper tries different codes); `confirm` is what
 * actually consumes a redemption slot, only for whichever codes are passed at that moment.
 */
export const checkoutApi = {
  preview(
    selectedVariantIds?: number[],
    subtotalCouponCode?: string,
    shippingCouponCode?: string,
    showError?: ShowError,
  ): Promise<CheckoutPreview> {
    // Repeated query param (?selectedVariantIds=1&selectedVariantIds=2) — preview is a GET, so
    // there's no body to carry a selection in; matches CheckoutApi.preview's own @RequestParam.
    return httpClient.get(
      `/api/v1/checkout/preview${buildQueryString({ selectedVariantIds, subtotalCouponCode, shippingCouponCode })}`,
      showError,
    );
  },

  /** AddressBook follow-up: `address` carries either an existing saved address
   * (`savedAddressId`, every other field ignored) or a fresh, one-off address — see
   * `CheckoutAddressInput`'s own doc comment. */
  confirm(
    address: CheckoutAddressInput,
    selectedVariantIds?: number[],
    subtotalCouponCode?: string,
    shippingCouponCode?: string,
    showError?: ShowError,
  ): Promise<OrderConfirmation> {
    return httpClient.post(
      '/api/v1/checkout/confirm',
      { ...address, selectedVariantIds, subtotalCouponCode, shippingCouponCode },
      showError,
    );
  },
};
