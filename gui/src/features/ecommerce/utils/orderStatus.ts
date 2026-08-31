import { ComponentType } from 'react';
import { SvgIconProps } from '@mui/material';
import PendingActionsIcon from '@mui/icons-material/PendingActions';
import PaymentIcon from '@mui/icons-material/Payment';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import LocalShippingIcon from '@mui/icons-material/LocalShipping';
import Inventory2Icon from '@mui/icons-material/Inventory2';
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

/** The order lifecycle's linear "happy path" — `CANCELLED`/`EXPIRED`/`FAILED` are terminal
 * branches off this path, not steps along it (see `ecommerce-service`'s `OrderStatus` enum
 * Javadoc for the full state machine). Drives `OrderDetailPage`'s horizontal `Stepper`: a
 * happy-path order's `activeStep` is just its index here; an off-path (terminal) order's
 * `activeStep` is the index of whichever happy-path status it was at when the terminal
 * transition happened (found via its `statusHistory`), with that step shown in the `Stepper`'s
 * error state rather than inventing a step slot for "Cancelled" itself. */
export const ORDER_HAPPY_PATH: readonly OrderStatus[] = ['PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED', 'SHIPPED', 'DELIVERED'];

/** One icon per `ORDER_HAPPY_PATH` entry, same index order — `OrderDetailPage`'s `Stepper` gives
 * each step its own semantic icon (a pending clock, a payment card, a confirmed check, a shipping
 * truck, a delivered box) instead of MUI's default numbered circles. */
export const ORDER_HAPPY_PATH_ICONS: ComponentType<SvgIconProps>[] = [
  PendingActionsIcon, PaymentIcon, CheckCircleIcon, LocalShippingIcon, Inventory2Icon,
];

/** One tab in `OrderHistoryPage`'s status filter — `statuses: undefined` ("All") sends no filter
 * at all; every other tab maps to one or more raw `OrderStatus` values, Shopee-style grouping
 * chosen over one tab per raw status (per request) so a shopper filters by "what do I still need
 * to do" rather than by this app's own internal state-machine names. */
export interface OrderTabGroup {
  key: string;
  label: string;
  statuses?: OrderStatus[];
}

export const ORDER_TAB_GROUPS: OrderTabGroup[] = [
  { key: 'all', label: 'All' },
  { key: 'to_pay', label: 'To Pay', statuses: ['PENDING', 'PAYMENT_PROCESSING'] },
  { key: 'processing', label: 'Processing', statuses: ['CONFIRMED'] },
  { key: 'shipped', label: 'Shipped', statuses: ['SHIPPED'] },
  { key: 'delivered', label: 'Delivered', statuses: ['DELIVERED'] },
  { key: 'cancelled', label: 'Cancelled', statuses: ['CANCELLED', 'EXPIRED', 'FAILED'] },
];

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
