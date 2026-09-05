import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  Divider,
  Pagination,
  Paper,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import { useNotification } from '@shared/contexts/NotificationContext';
import FullPageLoader from '@shared/components/FullPageLoader';
import EmptyState from '@shared/components/EmptyState';
import MessageDialog from '@shared/components/MessageDialog';
import { orderApi } from '../../api/orderApi';
import { Order } from '../../types';
import { usePaymentCountdown } from '../../hooks/usePaymentCountdown';
import OrderLineRow from '../../components/orders/OrderLineRow';
import PaymentCountdown from '../../components/orders/PaymentCountdown';
import { ORDER_STATUS_COLORS, ORDER_STATUS_LABELS, ORDER_TAB_GROUPS, formatOrderDate } from '../../utils/orderStatus';
import { formatPrice } from '../../utils/format';

const PAGE_SIZE = 10;

export default function OrderHistoryPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0); // zero-based, matches the backend's own Pageable convention
  const [loading, setLoading] = useState(true);
  // Status tabs (post-Epic-3 follow-up) — 'all' sends no status filter at all.
  const [tabKey, setTabKey] = useState<string>('all');
  const activeGroup = ORDER_TAB_GROUPS.find(g => g.key === tabKey) ?? ORDER_TAB_GROUPS[0];
  // Auto-expire follow-up: set once any row's own usePaymentCountdown confirms a payment window
  // has genuinely expired — shared across every row rather than one dialog per card, since only
  // one can meaningfully be shown at a time anyway.
  const [expiredOrder, setExpiredOrder] = useState<Order | null>(null);

  /** Keeps a single row's order fresh in the list after its own countdown reconciles — passed down
   * to every OrderCard as its usePaymentCountdown onReconciled callback. */
  const handleOrderReconciled = (updated: Order): void => {
    setOrders(prev => prev?.map(o => (o.id === updated.id ? updated : o)) ?? prev);
  };

  useEffect(() => {
    setLoading(true);
    orderApi.list(page, PAGE_SIZE, activeGroup.statuses)
      .then((result) => {
        setOrders(result.content);
        setTotalPages(result.totalPages);
      })
      .catch((err) => showError(err instanceof Error ? err.message : 'Could not load your orders.'))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, tabKey]);

  const handleTabChange = (key: string): void => {
    setTabKey(key);
    setPage(0); // a filter change always restarts pagination — the old page number may not exist under the new filter
  };

  if (loading && orders === null) {
    return <FullPageLoader />;
  }

  // Truly no orders at all yet (checked only against the 'all' tab, never a filtered one — a
  // filtered tab with zero matches still has orders elsewhere, so it gets the inline empty state
  // below instead, keeping the tabs themselves visible so the shopper can switch back to All).
  if (tabKey === 'all' && orders !== null && orders.length === 0) {
    return (
      <EmptyState
        icon={<ReceiptLongOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled' }} />}
        title="No orders yet"
        description="Once you place an order, you'll see it here."
        action={{ label: 'Go to Shop', onClick: () => navigate('/shop') }}
      />
    );
  }

  return (
    // Just p: 3, not this page's old width: '80%', mx: 'auto' — this page now lives nested inside
    // AccountLayout's own already-80%-wide content column (moved here from a top-level /orders
    // route per request), and a second 80% would compound into a visibly narrow, off-center block,
    // same fix AddressBookPage.tsx's own outer wrapper already needed for the identical reason.
    <Box sx={{ px: 3 }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>Your Orders</Typography>

      <Tabs
        value={tabKey}
        onChange={(_, value) => handleTabChange(value)}
        sx={{ mb: 3, bgcolor: 'background.paper', borderRadius: 1 }}
        variant="scrollable"
        scrollButtons="auto"
      >
        {ORDER_TAB_GROUPS.map(group => (
          <Tab key={group.key} value={group.key} label={group.label} />
        ))}
      </Tabs>

      {orders !== null && orders.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', mt: 4, mb: 3 }}>
          No orders in this category.
        </Typography>
      ) : (
        <Stack spacing={2} sx={{ mb: 3 }}>
          {orders?.map(order => (
            <OrderCard
              key={order.id}
              order={order}
              onView={() => navigate(`/account/orders/${order.id}`)}
              onReconciled={handleOrderReconciled}
              onExpired={setExpiredOrder}
            />
          ))}
        </Stack>
      )}

      {totalPages > 1 && (
        <Stack direction="row" justifyContent="center">
          <Pagination
            count={totalPages}
            page={page + 1}
            onChange={(_, value) => setPage(value - 1)}
            color="primary"
          />
        </Stack>
      )}

      {expiredOrder && (
        <MessageDialog
          open
          title="Payment window expired"
          message="The time to complete this payment has run out, and the reservation has been released."
          actionLabel="View Order"
          onAction={() => {
            const orderId = expiredOrder.id;
            setExpiredOrder(null);
            navigate(`/account/orders/${orderId}`);
          }}
        />
      )}
    </Box>
  );
}

interface OrderCardProps {
  order: Order;
  onView: () => void;
  onReconciled: (order: Order) => void;
  onExpired: (order: Order) => void;
}

function OrderCard({ order, onView, onReconciled, onExpired }: OrderCardProps): JSX.Element {
  const { showError } = useNotification();
  const remainingMs = usePaymentCountdown(order, {
    onReconciled,
    onResolved: onExpired,
    onError: (err) => showError(err instanceof Error ? err.message : 'Could not confirm this order\'s payment status.'),
  });
  const placedAt = order.statusHistory[0]?.occurredAt;

  // A decline can happen asynchronously (webhook/reconciliation), so this hint has to come from
  // the order's own persisted paymentFailureMessage (US-4.7) — never a one-time toast the shopper
  // may have already missed. Kept to one compact line here; the full Alert lives on the detail page.
  // Deliberately not gated on order.status === 'FAILED' — a retryable decline under Option A leaves
  // the order at PAYMENT_PROCESSING with the reason still attached to a PENDING payment; gating on
  // FAILED would hide it for exactly the still-retryable case. See OrderDetailPage.tsx's own
  // matching note for the full reasoning.
  const showFailureReason = Boolean(order.paymentFailureMessage);

  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2} sx={{ mb: showFailureReason ? 0.5 : 2 }}>
        <Box>
          {placedAt && (
            <Typography variant="body2" color="text.secondary">Placed {formatOrderDate(placedAt)}</Typography>
          )}
        </Box>
        <Stack alignItems="flex-end" spacing={1}>
          <Chip
            label={ORDER_STATUS_LABELS[order.status]}
            color={ORDER_STATUS_COLORS[order.status]}
            size="small"
          />
          {order.paymentExpiresAt && <PaymentCountdown remainingMs={remainingMs} />}
          <Button size="small" onClick={onView}>View Details</Button>
        </Stack>
      </Stack>

      {showFailureReason && (
        <Typography variant="body2" color="error.main" sx={{ mb: 2 }}>
          {order.paymentFailureMessage}
        </Typography>
      )}

      <Stack spacing={2} divider={<Divider />}>
        {order.lines.map(line => (
          <OrderLineRow key={line.variantId} line={line} />
        ))}
      </Stack>

      <Divider sx={{ my: 2 }} />

      <Stack direction="row" justifyContent="flex-end">
        <Typography variant="subtitle1" fontWeight={700} color="error.main">Total: {formatPrice(order.total)}</Typography>
      </Stack>
    </Paper>
  );
}
