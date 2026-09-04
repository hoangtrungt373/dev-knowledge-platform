import { loadStripe, Stripe } from '@stripe/stripe-js';

// One shared Stripe.js instance for the app's lifetime — loadStripe() is meant to be called once
// per publishable key, not once per mount (it injects/reuses Stripe's own <script> tag). Shared by
// PaymentDialog.tsx (OrderDetailPage's "Pay Now") and CheckoutPage.tsx's inline payment phase, so
// switching between the two never re-loads the script a second time.
let stripePromise: Promise<Stripe | null> | null = null;
let stripePromiseKey: string | null = null;

export function getStripePromise(publishableKey: string): Promise<Stripe | null> {
  if (!stripePromise || stripePromiseKey !== publishableKey) {
    stripePromise = loadStripe(publishableKey);
    stripePromiseKey = publishableKey;
  }
  return stripePromise;
}
