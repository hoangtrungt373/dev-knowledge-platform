import { useEffect, useRef } from 'react';
import { useCountdown } from '@shared/hooks/useCountdown';
import { orderApi } from '../api/orderApi';
import { Order } from '../types';

interface UsePaymentCountdownOptions {
  /** Called with the fresh order every time `reconcilePayment` resolves — even in the rare case
   * it's still `PAYMENT_PROCESSING` (e.g. clock skew put the countdown a moment ahead of the
   * backend's own deadline) — so the caller's local state always reflects the latest known truth. */
  onReconciled: (order: Order) => void;
  /** Called only once `reconcilePayment`'s response shows the order has genuinely left
   * `PAYMENT_PROCESSING` — the caller's cue to show the "payment window expired" dialog. */
  onResolved: (order: Order) => void;
  onError: (err: unknown) => void;
}

/**
 * Drives one order's live "time left to pay" countdown (`order.paymentExpiresAt`) and, the instant
 * it reaches zero, calls `orderApi.reconcilePayment` exactly once — instead of waiting for the
 * backend's own `OrderReconciliationJob` to get to it on its next poll tick (see that job's own
 * Javadoc). Deliberately **not** a retry loop, per request: a failure (network blip, the backend's
 * own `PAYMENT_GATEWAY_UNAVAILABLE`) just calls `onError` and leaves the countdown at zero — the
 * scheduled job remains the real safety net regardless of whether this on-demand call ever
 * succeeds, so there's nothing to aggressively retry client-side.
 *
 * `firedForOrderId` (keyed by order id, not a plain boolean) is what makes this "exactly once per
 * order" — switching to watch a *different* order (a new `order.id`) naturally re-arms it, while a
 * still-`PAYMENT_PROCESSING` result for the *same* order (the clock-skew case above) does not
 * trigger a second call, since nothing about `order.id` changed.
 *
 * @returns the ticking `remainingMs` for display (see `components/orders/PaymentCountdown.tsx`) —
 *          `0` whenever `order` isn't currently `PAYMENT_PROCESSING` (nothing to count down to).
 */
export function usePaymentCountdown(
  order: Pick<Order, 'id' | 'status' | 'paymentExpiresAt'> | null | undefined,
  { onReconciled, onResolved, onError }: UsePaymentCountdownOptions,
): number {
  const expiresAt = order?.status === 'PAYMENT_PROCESSING' ? order.paymentExpiresAt : null;
  const { remainingMs, expired } = useCountdown(expiresAt);
  const firedForOrderId = useRef<number | null>(null);

  useEffect(() => {
    if (!expired || !order || firedForOrderId.current === order.id) return;
    firedForOrderId.current = order.id;
    orderApi.reconcilePayment(order.id)
      .then(result => {
        onReconciled(result);
        if (result.status !== 'PAYMENT_PROCESSING') {
          onResolved(result);
        }
      })
      .catch(onError);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [expired, order?.id]);

  return remainingMs;
}
