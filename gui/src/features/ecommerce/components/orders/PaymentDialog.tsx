import { useEffect, useState } from 'react';
import { Elements } from '@stripe/react-stripe-js';
import {
  Alert,
  Box,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { paymentConfigApi } from '../../api/paymentConfigApi';
import { getStripePromise } from '../../utils/stripe';
import PaymentElementForm from './PaymentElementForm';

interface PaymentDialogProps {
  open: boolean;
  /** The PaymentIntent's client secret, from `orderApi.pay()`'s own `paymentClientSecret` field —
   * only ever present when the gateway is `stripe` and the charge is still awaiting client-side
   * confirmation (see `types.ts`'s `Order.paymentClientSecret` doc comment). */
  clientSecret: string;
  onClose: () => void;
  onCompleted: () => void;
}

/**
 * Option A (Stripe Elements, client-side confirmation), modal form — `OrderDetailPage`'s "Pay Now"
 * retry path for an order that's already `PAYMENT_PROCESSING` with an unconfirmed PaymentIntent.
 * The actual `PaymentElement`/`confirmPayment` mechanics live in `PaymentElementForm.tsx`, shared
 * with `CheckoutPage.tsx`'s own inline payment phase — this component only owns the dialog chrome
 * and the config fetch. Its own secondary button just closes the dialog (the order stays
 * `PAYMENT_PROCESSING`, retryable later) — unlike `CheckoutPage`'s, which cancels the order.
 */
export default function PaymentDialog({ open, clientSecret, onClose, onCompleted }: PaymentDialogProps): JSX.Element {
  const [publishableKey, setPublishableKey] = useState<string | null>(null);
  const [configError, setConfigError] = useState(false);

  useEffect(() => {
    if (!open) return;
    setConfigError(false);
    paymentConfigApi.get()
      .then(config => setPublishableKey(config.publishableKey))
      .catch(() => setConfigError(true));
  }, [open]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        Complete Payment
        <IconButton onClick={onClose} size="small" aria-label="Close">
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        {configError && (
          <Alert severity="error">Could not load payment configuration. Please try again.</Alert>
        )}
        {!configError && !publishableKey && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {publishableKey && (
          <Elements stripe={getStripePromise(publishableKey)} options={{ clientSecret }}>
            <PaymentElementForm onCompleted={onCompleted} secondaryAction={{ label: 'Cancel', onClick: onClose }} />
          </Elements>
        )}
      </DialogContent>
    </Dialog>
  );
}
