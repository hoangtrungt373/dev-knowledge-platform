import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  IconButton,
  Paper,
  Stack,
  Step,
  StepLabel,
  Stepper,
  Tooltip,
  Typography,
} from '@mui/material';
import { StepIconProps } from '@mui/material/StepIcon';
import StepConnector, { stepConnectorClasses } from '@mui/material/StepConnector';
import { styled } from '@mui/material/styles';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import { useNotification } from '@shared/contexts/NotificationContext';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import FullPageLoader from '@shared/components/FullPageLoader';
import EmptyState from '@shared/components/EmptyState';
import SubmitButton from '@shared/components/SubmitButton';
import { orderApi } from '../../api/orderApi';
import { Order } from '../../types';
import OrderLineRow from '../../components/orders/OrderLineRow';
import PaymentDialog from '../../components/orders/PaymentDialog';
import {
  ORDER_HAPPY_PATH,
  ORDER_HAPPY_PATH_ICONS,
  ORDER_STATUS_COLORS,
  ORDER_STATUS_LABELS,
  formatOrderDateTime,
  isCancellable,
} from '../../utils/orderStatus';
import { formatPrice } from '../../utils/format';

/**
 * Custom `StepIconComponent` for `ORDER_HAPPY_PATH`'s `Stepper` — a bigger, outlined circle (48px,
 * vs. MUI's default numbered ~24px) carrying that step's own semantic icon from
 * `ORDER_HAPPY_PATH_ICONS` instead of a plain number, per request. `icon` arrives as MUI's default
 * 1-based step number (no per-`Step` `icon` override set), so `Number(icon) - 1` recovers the
 * `ORDER_HAPPY_PATH` index. `error` (an off-path order's terminal step) swaps in a red error icon
 * regardless of position, taking priority over the step's own semantic icon.
 *
 * <p>Outlined, not filled, per request — a stroked circle (`border` + `background.paper`, both
 * colored by state) with the icon itself carrying the state color, rather than a solid-color disc
 * with a white icon.
 */
function HappyPathStepIcon({ active, completed, error, icon }: StepIconProps): JSX.Element {
  const index = Number(icon) - 1;
  const StepIconGraphic = error ? ErrorOutlineIcon : ORDER_HAPPY_PATH_ICONS[index];
  const filled = active || completed || error;
  const stateColor = error ? 'error.main' : filled ? 'primary.main' : 'text.disabled';
  return (
    <Box
      sx={{
        width: 48,
        height: 48,
        borderRadius: '50%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        border: '3px solid',
        borderColor: stateColor,
        bgcolor: 'background.paper',
        color: stateColor,
      }}
    >
      <StepIconGraphic sx={{ fontSize: 26 }} />
    </Box>
  );
}

/**
 * Custom `Stepper` connector for `ORDER_HAPPY_PATH` — a fix, two problems with MUI's default
 * `StepConnector` once `HappyPathStepIcon` grew to a 48px outlined circle: (1) its line color
 * comes from its own `active`/`completed` palette defaults, which don't reuse the exact
 * `text.disabled`/`primary.main` tokens `HappyPathStepIcon` colors itself with, so the two visibly
 * drifted; (2) its horizontal `left`/`right` offset (`calc(±50% + 20px)`) is calibrated for MUI's
 * own much-smaller default icon, so the line reached in under the bigger circle's edge instead of
 * stopping at it. Both fixed by explicitly matching the same state-color tokens and widening the
 * offset past the icon's own 24px radius (plus its 3px border) with a small gap to spare.
 */
const OrderStatusConnector = styled(StepConnector)(({ theme }) => ({
  [`&.${stepConnectorClasses.alternativeLabel}`]: {
    top: 24,
    left: 'calc(-50% + 28px)',
    right: 'calc(50% + 28px)',
  },
  [`& .${stepConnectorClasses.line}`]: {
    borderTopWidth: 3,
    borderColor: theme.palette.text.disabled,
  },
  [`&.${stepConnectorClasses.active} .${stepConnectorClasses.line}`]: {
    borderColor: theme.palette.primary.main,
  },
  [`&.${stepConnectorClasses.completed} .${stepConnectorClasses.line}`]: {
    borderColor: theme.palette.primary.main,
  },
}));

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
  const [paymentClientSecret, setPaymentClientSecret] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    orderApi.getById(orderId)
      .then(setOrder)
      .catch(() => setNotFound(true))
      .finally(() => setLoading(false));
  }, [orderId]);

  const notifyPaymentOutcome = (result: Order): void => {
    if (result.status === 'CONFIRMED') {
      showSuccess('Payment successful! Your order is confirmed.');
    } else if (result.status === 'FAILED') {
      showError(result.paymentFailureMessage ?? 'Payment was declined. Please try again.');
    }
    // Otherwise still PAYMENT_PROCESSING (a real gateway may not resolve instantly) — the page's
    // own status chip already reflects that, no extra notification needed.
  };

  const handlePay = (): void => {
    guardPay(async () => {
      try {
        const result = await orderApi.pay(orderId);
        setOrder(result);
        if (result.paymentClientSecret) {
          // Option A (Stripe Elements): the PaymentIntent isn't confirmed yet — open the dialog
          // and let the shopper's own browser do that; notifyPaymentOutcome runs once the dialog
          // reports back, not now (result.status is still PAYMENT_PROCESSING at this point).
          setPaymentClientSecret(result.paymentClientSecret);
          return;
        }
        // MockPaymentGateway (or an already-declined Stripe charge) — the gateway already
        // resolved synchronously, nothing for the shopper to confirm.
        notifyPaymentOutcome(result);
      } catch (err) {
        showError(err instanceof Error ? err.message : 'Could not process payment. Please try again.');
      }
    });
  };

  const handlePaymentDialogCompleted = async (): Promise<void> => {
    setPaymentClientSecret(null);
    try {
      const refreshed = await orderApi.getById(orderId);
      setOrder(refreshed);
      notifyPaymentOutcome(refreshed);
    } catch {
      // The confirmation itself already succeeded (or the shopper would have seen an inline
      // error inside the dialog) — a failed refetch here just means the status chip is stale
      // until the next reload, not that anything about the payment failed.
    }
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
    return <FullPageLoader />;
  }

  if (notFound || !order) {
    return (
      <EmptyState
        icon={<ReceiptLongOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled' }} />}
        title="Order not found"
        action={{ label: 'Back to Your Orders', onClick: () => navigate('/account/orders') }}
      />
    );
  }

  const canPay = order.status === 'PENDING';
  const canCancel = isCancellable(order.status) && !order.cancelRequested;
  const cancelPending = order.status === 'PAYMENT_PROCESSING' && order.cancelRequested;

  // Horizontal Stepper (below) drives off these two: happyPathIndex is where a normal
  // in-progress/delivered order currently sits; for an off-path (terminal) order, errorStepIndex
  // is instead where it *was* when the terminal transition happened — found via statusHistory,
  // since CANCELLED/EXPIRED/FAILED aren't steps on the happy path themselves (see
  // ORDER_HAPPY_PATH's own doc comment).
  const happyPathIndex = ORDER_HAPPY_PATH.indexOf(order.status);
  const isOffPath = happyPathIndex === -1;
  const terminalEntry = isOffPath
    ? [...order.statusHistory].reverse().find(h => h.toStatus === order.status)
    : undefined;
  const errorStepIndex = terminalEntry?.fromStatus ? ORDER_HAPPY_PATH.indexOf(terminalEntry.fromStatus) : -1;
  const activeStep = isOffPath ? errorStepIndex : happyPathIndex;

  return (
    // Just p: 3, not this page's old width: '80%', mx: 'auto' — same "nested inside AccountLayout's
    // own already-80%-wide content column now" fix as OrderHistoryPage.tsx's own wrapper above.
    <Box sx={{ px: 3 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 3 }}>
        <Tooltip title="Back to Your Orders">
          <IconButton onClick={() => navigate('/account/orders')} size="small">
            <ArrowBackIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Typography variant="h5" fontWeight={700}>Order #{order.id}</Typography>
        <Chip label={ORDER_STATUS_LABELS[order.status]} color={ORDER_STATUS_COLORS[order.status]} size="small" />
      </Stack>

      <Paper variant="outlined" sx={{ p: { xs: 2.5, sm: 4 }, mb: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 3 }}>Order Status</Typography>
        <Stepper
          activeStep={activeStep}
          alternativeLabel
          connector={<OrderStatusConnector />}
          sx={{
            '& .MuiStepLabel-label': { fontSize: '1rem', mt: 1 },
            '& .MuiStepLabel-label.Mui-active': { fontWeight: 700 },
          }}
        >
          {ORDER_HAPPY_PATH.map((status, index) => {
            const reachedEntry = order.statusHistory.find(h => h.toStatus === status);
            const isErrorStep = isOffPath && index === errorStepIndex;
            const isCompleted = index < happyPathIndex || (index === happyPathIndex && order.status === 'DELIVERED');
            return (
              <Step key={status} completed={isCompleted}>
                <StepLabel
                  error={isErrorStep}
                  StepIconComponent={HappyPathStepIcon}
                  optional={
                    isErrorStep ? (
                      <Typography variant="body2" color="error.main">{ORDER_STATUS_LABELS[order.status]}</Typography>
                    ) : reachedEntry ? (
                      <Typography variant="body2" color="text.secondary">{formatOrderDateTime(reachedEntry.occurredAt)}</Typography>
                    ) : undefined
                  }
                >
                  {ORDER_STATUS_LABELS[status]}
                </StepLabel>
              </Step>
            );
          })}
        </Stepper>
      </Paper>

      {/* A payment can decline asynchronously (webhook/reconciliation, Epic 4 Phase 5), not just
          from this page's own Pay Now click — so the reason has to be shown persistently here too,
          not only as the one-time toast handlePay fires. paymentFailureMessage is always the
          server-owned, non-technical category message (US-4.7), never the gateway's raw string. */}
      {order.status === 'FAILED' && order.paymentFailureMessage && (
        <Alert severity="error" sx={{ mb: 3 }}>{order.paymentFailureMessage}</Alert>
      )}

      {order.paymentStatus === 'REFUNDED' && (
        <Alert severity="success" sx={{ mb: 3 }}>
          This order was cancelled and {formatPrice(order.total)} has been refunded.
        </Alert>
      )}

      {cancelPending && (
        <Paper variant="outlined" sx={{ p: 2, mb: 3, borderColor: 'warning.main', bgcolor: 'action.hover' }}>
          <Typography variant="body2">
            Cancellation requested — this will apply once the in-progress payment resolves.
          </Typography>
        </Paper>
      )}

      <Paper variant="outlined" sx={{ p: 2.5, mb: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1.5 }}>Items</Typography>
        <Stack spacing={2} divider={<Divider />} sx={{ mb: 1.5 }}>
          {order.lines.map(line => (
            <OrderLineRow key={line.variantId} line={line} />
          ))}
        </Stack>
        <Divider sx={{ my: 1.5 }} />
        {(order.subtotalCouponCode || order.shippingCouponCode) && (
          <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
            {order.subtotalCouponCode && (
              <Chip
                label={`${order.subtotalCouponCode} (discount)`}
                size="small"
                color="success"
                variant="outlined"
                icon={<LocalOfferIcon fontSize="small" />}
              />
            )}
            {order.shippingCouponCode && (
              <Chip
                label={`${order.shippingCouponCode} (shipping)`}
                size="small"
                color="success"
                variant="outlined"
                icon={<LocalOfferIcon fontSize="small" />}
              />
            )}
          </Stack>
        )}
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">Subtotal</Typography>
          <Typography variant="body2" color="error.main">{formatPrice(order.subtotal)}</Typography>
        </Stack>
        {order.subtotalDiscountAmount > 0 && (
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">Discount</Typography>
            <Typography variant="body2" color="success.main">−{formatPrice(order.subtotalDiscountAmount)}</Typography>
          </Stack>
        )}
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="body2" color="text.secondary">Shipping</Typography>
          {order.originalShippingFee > order.shippingFee ? (
            // A SHIPPING_FEE coupon discounted the fee at checkout — the only mechanism that can
            // do this now (see ecommerce-service's FlatRateShippingFeeCalculator/
            // FreeOverThresholdShippingFeeCalculator Javadoc), can be a partial discount, not
            // necessarily down to zero — same treatment CheckoutPage's own preview uses: only
            // label it "Free" when the actual charge is zero.
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body2" color="text.secondary" sx={{ textDecoration: 'line-through' }}>
                {formatPrice(order.originalShippingFee)}
              </Typography>
              {order.shippingFee === 0 ? (
                <Typography variant="body2" color="success.main" fontWeight={600}>Free</Typography>
              ) : (
                <Typography variant="body2" color="success.main">{formatPrice(order.shippingFee)}</Typography>
              )}
            </Stack>
          ) : (
            <Typography variant="body2" color="error.main">{formatPrice(order.shippingFee)}</Typography>
          )}
        </Stack>
        <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.5 }}>
          <Typography variant="subtitle1" fontWeight={700}>Total</Typography>
          <Typography variant="subtitle1" fontWeight={700} color="error.main">{formatPrice(order.total)}</Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5, mb: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>Shipping To</Typography>
        <Typography variant="body2">{order.shippingAddress.fullName}</Typography>
        {order.shippingAddress.phone && (
          <Typography variant="body2">{order.shippingAddress.phone}</Typography>
        )}
        {order.shippingAddress.email && (
          <Typography variant="body2">{order.shippingAddress.email}</Typography>
        )}
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
            <SubmitButton
              size="large"
              saving={paying}
              onClick={handlePay}
              label={`Pay Now — ${formatPrice(order.total)}`}
            />
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

      {paymentClientSecret && (
        <PaymentDialog
          open
          clientSecret={paymentClientSecret}
          onClose={() => setPaymentClientSecret(null)}
          onCompleted={handlePaymentDialogCompleted}
        />
      )}
    </Box>
  );
}
