import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  Paper,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { orderApi } from '../../api/orderApi';
import { Order } from '../../types';
import {
  ORDER_STATUS_COLORS,
  ORDER_STATUS_LABELS,
  formatOrderDateTime,
  isCancellable,
} from '../../utils/orderStatus';

function formatPrice(value: number): string {
  return `$${value.toFixed(2)}`;
}

export default function OrderDetailPage(): JSX.Element {
  const { id } = useParams<{ id: string }>();
  const orderId = Number(id);
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();
  const { loading: paying, guard: guardPay } = useSubmitGuard();

  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    setLoading(true);
    orderApi.getById(orderId)
      .then(setOrder)
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
  }, [orderId]);

  const handlePay = (): void => {
    guardPay(async () => {
      try {
        const result = await orderApi.pay(orderId);
        setOrder(result);
        if (result.status === 'CONFIRMED') {
          showSuccess('Payment successful! Your order is confirmed.');
        } else if (result.status === 'FAILED') {
          showError('Payment was declined. Please try again.');
        }
        // Otherwise still PAYMENT_PROCESSING (a real gateway may not resolve instantly) — the
        // page's own status chip already reflects that, no extra notification needed.
      } catch (err) {
        showError(err instanceof Error ? err.message : 'Could not process payment. Please try again.');
      }
    });
  };

  const handleCancel = async (): Promise<void> => {
    setCancelling(true);
    try {
      const result = await orderApi.cancel(orderId);
      setOrder(result);
      setCancelDialogOpen(false);
      showSuccess(result.status === 'CANCELLED' ? 'Order cancelled.' : 'Cancellation requested.');
    } catch (err) {
      showError(err instanceof Error ? err.message : 'Could not cancel this order.');
    } finally {
      setCancelling(false);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (notFound || !order) {
    return (
      <Box sx={{ p: 3, textAlign: 'center', maxWidth: 500, mx: 'auto', mt: 6 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>Order not found</Typography>
        <Button variant="contained" onClick={() => navigate('/orders')}>Back to Your Orders</Button>
      </Box>
    );
  }

  const canPay = order.status === 'PENDING';
  const canCancel = isCancellable(order.status) && !order.cancelRequested;
  const cancelPending = order.status === 'PAYMENT_PROCESSING' && order.cancelRequested;

  return (
    <Box sx={{ p: 3, maxWidth: 700, mx: 'auto' }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 3 }}>
        <Tooltip title="Back to Your Orders">
          <IconButton onClick={() => navigate('/orders')} size="small">
            <ArrowBackIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Typography variant="h5" fontWeight={700}>Order #{order.id}</Typography>
        <Chip label={ORDER_STATUS_LABELS[order.status]} color={ORDER_STATUS_COLORS[order.status]} size="small" />
      </Stack>

      {cancelPending && (
        <Paper variant="outlined" sx={{ p: 2, mb: 3, borderColor: 'warning.main', bgcolor: 'action.hover' }}>
          <Typography variant="body2">
            Cancellation requested — this will apply once the in-progress payment resolves.
          </Typography>
        </Paper>
      )}

      <Paper variant="outlined" sx={{ p: 2.5, mb: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1.5 }}>Items</Typography>
        <Stack spacing={1}>
          {order.lines.map(line => (
            <Stack key={line.variantId} direction="row" justifyContent="space-between">
              <Typography variant="body2">{line.productName} × {line.quantity}</Typography>
              <Typography variant="body2">{formatPrice(line.lineTotal)}</Typography>
            </Stack>
          ))}
        </Stack>
        <Divider sx={{ my: 1.5 }} />
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">Subtotal</Typography>
          <Typography variant="body2">{formatPrice(order.subtotal)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">Shipping</Typography>
          <Typography variant="body2">{formatPrice(order.shippingFee)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.5 }}>
          <Typography variant="subtitle1" fontWeight={700}>Total</Typography>
          <Typography variant="subtitle1" fontWeight={700}>{formatPrice(order.total)}</Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5, mb: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>Shipping To</Typography>
        <Typography variant="body2">{order.shippingAddress.fullName}</Typography>
        <Typography variant="body2">{order.shippingAddress.line1}</Typography>
        {order.shippingAddress.line2 && <Typography variant="body2">{order.shippingAddress.line2}</Typography>}
        <Typography variant="body2">
          {order.shippingAddress.city}, {order.shippingAddress.state} {order.shippingAddress.postalCode}
        </Typography>
        <Typography variant="body2">{order.shippingAddress.country}</Typography>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5, mb: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1.5 }}>Order Timeline</Typography>
        <Stack spacing={1.5}>
          {order.statusHistory.map((entry, i) => (
            <Box key={i}>
              <Typography variant="body2" fontWeight={600}>
                {entry.fromStatus ? `${ORDER_STATUS_LABELS[entry.fromStatus]} → ` : ''}
                {ORDER_STATUS_LABELS[entry.toStatus]}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {formatOrderDateTime(entry.occurredAt)}
                {entry.reason ? ` — ${entry.reason}` : ''}
              </Typography>
            </Box>
          ))}
        </Stack>
      </Paper>

      {(canPay || canCancel) && (
        <Stack direction="row" justifyContent="flex-end" spacing={2}>
          {canCancel && (
            <Button color="error" onClick={() => setCancelDialogOpen(true)}>
              Cancel Order
            </Button>
          )}
          {canPay && (
            <Button variant="contained" size="large" disabled={paying} onClick={handlePay}>
              {paying ? <CircularProgress size={24} color="inherit" /> : `Pay Now — ${formatPrice(order.total)}`}
            </Button>
          )}
        </Stack>
      )}

      <ConfirmDialog
        open={cancelDialogOpen}
        title="Cancel this order?"
        message="This can't be undone. Any payment already captured will be refunded."
        confirmLabel="Cancel Order"
        loading={cancelling}
        onConfirm={handleCancel}
        onCancel={() => setCancelDialogOpen(false)}
      />
    </Box>
  );
}
