import { httpClient } from '@shared/api/httpClient';
import { buildQueryString } from '@shared/utils/queryString';
import { AvailableCoupon, CouponTarget } from '../types';

type ShowError = (msg: string) => void;

/**
 * Fronts `ecommerce-service`'s `CouponPickerApi` (`/api/v1/coupons`), authenticated-only through
 * `gateway` — a separate file from `ecommerceApi.ts`'s own admin-CRUD Coupon section, mirroring
 * the backend's own `CouponPickerApi`/`CouponApi` split (same underlying resource, genuinely
 * different audience, same reasoning `orderApi.ts`/`adminOrderApi.ts` already split on).
 */
export const couponApi = {
  /** Lists every currently-redeemable coupon for `target`, annotated with `eligible`/
   * `discountAmount` and pre-sorted by what's best for this order — see `AvailableCoupon`'s own
   * doc comment. Never itself applies anything; `checkoutApi.preview`/`.confirm` remain the real
   * source of truth once a shopper picks one.
   *
   * @param shippingFee the caller's current quoted shipping fee (e.g. the checkout preview's own
   *                     `originalShippingFee`) — only meaningful (and only actually used
   *                     server-side) when `target === 'SHIPPING_FEE'`, since that's the base
   *                     amount a shipping coupon's `discountAmount` is computed against; omit for
   *                     a `'SUBTOTAL'` request. */
  listAvailable(
    target: CouponTarget, subtotal: number, shippingFee?: number, showError?: ShowError,
  ): Promise<AvailableCoupon[]> {
    return httpClient.get(`/api/v1/coupons/available${buildQueryString({ target, subtotal, shippingFee })}`, showError);
  },
};
