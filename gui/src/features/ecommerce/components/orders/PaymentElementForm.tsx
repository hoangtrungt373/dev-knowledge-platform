import { useState } from 'react';
import { PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { Alert, Box, Button, Typography } from '@mui/material';
import SubmitButton from '@shared/components/SubmitButton';

interface PaymentElementFormProps {
  /** Called once `stripe.confirmPayment` resolves with no error — the caller should refetch
   * whatever order this payment belongs to; this component never itself learns the definitive
   * CONFIRMED/FAILED outcome (that's `webhook.StripeWebhookService`'s job — see root CLAUDE.md's
   * Stripe discussion). */
  onCompleted: () => void;
  /** The form's own secondary button — deliberately caller-defined rather than a hardcoded
   * "Cancel," since what it should actually *do* differs by context: `PaymentDialog` just closes
   * itself (the order stays PAYMENT_PROCESSING, retryable later via Pay Now); `CheckoutPage`'s
   * inline payment phase cancels the just-created order outright (releases the stock reservation)
   * — see that page's own note on why "go back and edit" isn't supported yet. */
  secondaryAction: { label: string; onClick: () => void; disabled?: boolean };
  payButtonLabel?: string;
}

/**
 * Option A (Stripe Elements, client-side confirmation) — the actual `PaymentElement` + `stripe.
 * confirmPayment` mechanics, shared between `PaymentDialog.tsx` (a modal, for `OrderDetailPage`'s
 * "Pay Now" retry path) and `CheckoutPage.tsx` (inline, for the merged review→payment flow) — see
 * both call sites for how they differ (dialog chrome vs. inline section, and what the secondary
 * button does). Must be rendered inside an `<Elements>` provider — this component itself doesn't
 * own that, since the two callers construct it slightly differently (a `Dialog`'s `DialogContent`
 * vs. a plain `Paper` section).
 *
 * `redirect: 'if_required'` keeps this a same-page confirmation for the common case (no 3-D
 * Secure, or a challenge Stripe.js can show as an inline modal) instead of a full-page redirect —
 * `return_url` is still supplied for the rarer payment method that mandates one regardless.
 */
export default function PaymentElementForm({
  onCompleted,
  secondaryAction,
  payButtonLabel = 'Pay',
}: PaymentElementFormProps): JSX.Element {
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
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Card details are sent directly to Stripe — this app never sees or stores them.
      </Typography>
      <PaymentElement />
      {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 3 }}>
        <Button onClick={secondaryAction.onClick} disabled={submitting || secondaryAction.disabled}>
          {secondaryAction.label}
        </Button>
        <SubmitButton saving={submitting} onClick={handleSubmit} disabled={!stripe} label={payButtonLabel} />
      </Box>
    </Box>
  );
}
