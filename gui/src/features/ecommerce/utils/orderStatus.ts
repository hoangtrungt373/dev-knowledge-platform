import { OrderStatus } from '../types';

/** Human-readable label per status — `OrderHistoryPage`/`OrderDetailPage` share these so a status
 * never reads differently between the list and the detail view. */
export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Pending',
  PAYMENT_PROCESSING: 'Payment Processing',
  CONFIRMED: 'Confirmed',
  EXPIRED: 'Expired',
  FAILED: 'Payment Failed',
  CANCELLED: 'Cancelled',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
};

/** MUI Chip `color` per status — same shape as `Chip`'s own color prop, so callers can spread this
 * straight in (`<Chip color={ORDER_STATUS_COLORS[status]} />`). */
export const ORDER_STATUS_COLORS: Record<OrderStatus, 'default' | 'info' | 'primary' | 'secondary' | 'success' | 'error' | 'warning'> = {
  PENDING: 'default',
  PAYMENT_PROCESSING: 'info',
  CONFIRMED: 'primary',
  EXPIRED: 'default',
  FAILED: 'error',
  CANCELLED: 'default',
  SHIPPED: 'secondary',
  DELIVERED: 'success',
};

/** Statuses from which a shopper can still request a cancel (US-3.6) — SHIPPED/DELIVERED and every
 * other terminal status are excluded, matching the backend's own state machine. */
const CANCELLABLE_STATUSES: readonly OrderStatus[] = ['PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED'];

export function isCancellable(status: OrderStatus): boolean {
  return CANCELLABLE_STATUSES.includes(status);
}

export function formatOrderDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatOrderDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
  });
}
