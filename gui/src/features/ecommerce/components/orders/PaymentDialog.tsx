import { useEffect, useState } from 'react';
import { loadStripe, Stripe } from '@stripe/stripe-js';
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { paymentConfigApi } from '../../api/paymentConfigApi';

interface PaymentDialogProps {
  open: boolean;
  /** The PaymentIntent's client secret, from `orderApi.pay()`'s own `paymentClientSecret` field —
   * only ever present when the gateway is `stripe` and the charge is still awaiting client-side
   * confirmation (see `types.ts`'s `Order.paymentClientSecret` doc comment). */
  clientSecret: string;
  onClose: () => void;
  /** Called once `stripe.confirmPayment` resolves with no error — the parent should refetch the
   * order to pick up whatever `webhook.StripeWebhookService` (or the confirmation call itself)
   * ends up resolving; this dialog never itself learns the final CONFIRMED/FAILED outcome. */
  onCompleted: () => void;
}

// One shared Stripe.js instance for the app's lifetime — loadStripe() is meant to be called once
// per publishable key, not once per dialog open (it injects/reuses Stripe's own <script> tag).
let stripePromise: Promise<Stripe | null> | null = null;
let stripePromiseKey: string | null = null;
function getStripePromise(publishableKey: string): Promise<Stripe | null> {
  if (!stripePromise || stripePromiseKey !== publishableKey) {
    stripePromise = loadStripe(publishableKey);
    stripePromiseKey = publishableKey;
  }
  return stripePromise;
}

/**
 * Option A (Stripe Elements, client-side confirmation) — mounts a real `PaymentElement` against
 * the order's PaymentIntent and lets the shopper's own browser confirm it via
 * `stripe.confirmPayment`. The card itself never reaches `ecommerce-service` at all; this dialog
 * only ever sees Stripe's own iframe and whatever error message `confirmPayment` returns.
 *
 * `redirect: 'if_required'` keeps this a single dialog instead of a full-page redirect for the
 * common case (no 3-D Secure needed, or a challenge Stripe.js can show as an inline modal) — a
 * `return_url` is still supplied for the rarer payment method that mandates a full redirect
 * regardless.
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
            <PaymentForm onClose={onClose} onCompleted={onCompleted} />
          </Elements>
        )}
      </DialogContent>
    </Dialog>
  );
}

function PaymentForm({ onClose, onCompleted }: { onClose: () => void; onCompleted: () => void }): JSX.Element {
  const stripe = useStripe();
  const elements = useElements();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (): Promise<void> => {
    if (!stripe || !elements) return;
    setSubmitting(true);
    setError(null);
    const { error: confirmError } = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: window.location.href },
      redirect: 'if_required',
    });
    setSubmitting(false);
    if (confirmError) {
      // A validation error (e.g. incomplete card fields) or a genuine decline — either way, the
      // form stays open so the shopper can fix the fields or try a different payment method.
      setError(confirmError.message ?? 'Payment failed. Please try again.');
      return;
    }
    onCompleted();
  };

  return (
    <Box sx={{ mt: 1 }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Card details are sent directly to Stripe — this app never sees or stores them.
      </Typography>
      <PaymentElement />
      {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 3 }}>
        <Button onClick={onClose} disabled={submitting}>Cancel</Button>
        <Button variant="contained" onClick={handleSubmit} disabled={!stripe || submitting}>
          {submitting ? <CircularProgress size={20} color="inherit" /> : 'Pay'}
        </Button>
      </Box>
    </Box>
  );
}
