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
  /** Lists every currently-redeemable coupon for `target`, annotated with `eligible` against
   * `subtotal` — see `AvailableCoupon`'s own doc comment. Never itself applies anything;
   * `checkoutApi.preview`/`.confirm` remain the real source of truth once a shopper picks one. */
  listAvailable(target: CouponTarget, subtotal: number, showError?: ShowError): Promise<AvailableCoupon[]> {
    return httpClient.get(`/api/v1/coupons/available${buildQueryString({ target, subtotal })}`, showError);
  },
};
