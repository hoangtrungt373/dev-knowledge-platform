import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Elements } from '@stripe/react-stripe-js';
import {
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Paper,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { isValidEmail } from '@shared/utils/validation';
import { useCart } from '../../context/CartContext';
import { checkoutApi } from '../../api/checkoutApi';
import { addressApi } from '../../api/addressApi';
import { orderApi } from '../../api/orderApi';
import { paymentConfigApi } from '../../api/paymentConfigApi';
import { Address, CartLine, CheckoutPreview, CouponTarget, OrderLine, SavedAddress } from '../../types';
import { formatPrice } from '../../utils/format';
import { getStripePromise } from '../../utils/stripe';
import OrderLineRow from '../../components/orders/OrderLineRow';
import CouponPickerDialog from '../../components/CouponPickerDialog';
import PaymentElementForm from '../../components/orders/PaymentElementForm';

/** An available CartLine has every one of these fields populated (see CartLine's own doc comment
 * — they're only optional to model an unavailable line, which is filtered out before this runs) —
 * this just re-shapes it into OrderLine so the Order Summary can render via the same OrderLineRow
 * OrderHistoryPage/OrderDetailPage use, rather than a third, slightly-different inline rendering. */
function toOrderLine(line: CartLine): OrderLine {
  return {
    variantId: line.variantId,
    sku: line.sku ?? '',
    productName: line.productName ?? '',
    unitPrice: line.unitPrice ?? 0,
    quantity: line.quantity,
    lineTotal: line.lineTotal ?? 0,
    attributes: line.attributes ?? null,
    primaryImageUrl: line.primaryImageUrl ?? null,
    productSlug: line.productSlug ?? null,
  };
}

interface AddressFormErrors {
  fullName?: string;
  phone?: string;
  email?: string;
  line1?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
}

const EMPTY_ADDRESS: Address = {
  fullName: '', phone: '', email: '', line1: '', line2: '', city: '', state: '', postalCode: '', country: '',
};

/** Sentinel radio value for "enter a new address" — distinct from any real numeric address id. */
const NEW_ADDRESS_OPTION = 'new';

function formatSavedAddress(a: SavedAddress): string {
  const contact = [a.phone, a.email].filter(Boolean).join(' · ');
  return `${a.fullName}${contact ? ` · ${contact}` : ''}, ${a.line1}${a.line2 ? `, ${a.line2}` : ''}, ${a.city}, ${a.state} ${a.postalCode}, ${a.country}`;
}

/** Same shape as `formatSavedAddress`, for the fresh-address form's own local `Address` state —
 * used by the payment phase's read-only "Shipping To" summary when the shopper typed a new
 * address rather than picking a saved one. */
function formatAddressState(a: Address): string {
  const contact = [a.phone, a.email].filter(Boolean).join(' · ');
  return `${a.fullName}${contact ? ` · ${contact}` : ''}, ${a.line1}${a.line2 ? `, ${a.line2}` : ''}, ${a.city}, ${a.state} ${a.postalCode}, ${a.country}`;
}

export default function CheckoutPage(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const { showError, showSuccess } = useNotification();
  const { refresh: refreshCart } = useCart();
  const { loading: submitting, guard } = useSubmitGuard();

  // Two-phase checkout (per request — merges what used to be a separate "Place Order" ->
  // OrderDetailPage "Pay Now" hop into one page): 'review' is everything below (address/coupons/
  // Place Order button); 'payment' swaps in a read-only summary + inline Stripe Elements once the
  // order's been created and its PaymentIntent is awaiting client-side confirmation. Going back to
  // 'review' to edit address/coupons isn't supported yet (see handleCancelOrder's own note) —
  // deliberately deferred, not an oversight.
  const [phase, setPhase] = useState<'review' | 'payment'>('review');
  const [createdOrderId, setCreatedOrderId] = useState<number | null>(null);
  const [paymentClientSecret, setPaymentClientSecret] = useState<string | null>(null);
  // Fetched synchronously in handleSubmit before phase ever flips to 'payment' — unlike
  // PaymentDialog.tsx (which fetches it after opening, so it needs its own loading/error states),
  // this is always non-null by the time the payment Paper below renders at all.
  const [publishableKey, setPublishableKey] = useState<string | null>(null);
  const [cancellingOrder, setCancellingOrder] = useState(false);

  // Set by CartPage's "Checkout Selected" flow (post-Epic-2 follow-up) — undefined for the
  // ordinary "Proceed to Checkout" flow (whole cart) or a direct navigation to this page.
  const selectedVariantIds = (location.state as { selectedVariantIds?: number[] } | null)?.selectedVariantIds;

  const [preview, setPreview] = useState<CheckoutPreview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(true);

  // AddressBook follow-up: the caller's saved addresses, fetched alongside the preview — a
  // separate, independent request (not gating previewLoading), since a failure here shouldn't
  // block checkout, only fall back to the "enter a new address" form below.
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  // The selected radio value — a SavedAddress id (as a string, to share one RadioGroup value type
  // with the NEW_ADDRESS_OPTION sentinel) or NEW_ADDRESS_OPTION itself.
  const [addressChoice, setAddressChoice] = useState<string>(NEW_ADDRESS_OPTION);

  const [address, setAddress] = useState<Address>(EMPTY_ADDRESS);
  const [errors, setErrors] = useState<AddressFormErrors>({});
  const [saveAddress, setSaveAddress] = useState(false);
  const [addressLabel, setAddressLabel] = useState('');

  // Coupon feature follow-up: both slots are now chosen together inside CouponPickerDialog (a
  // radio per CouponTarget section, applied in one combined call) rather than through a field on
  // this page — "Applied" still just means the last preview call actually accepted a code into
  // that slot; CheckoutPage owns this state and the actual API call, the dialog only ever
  // proposes a selection (see applyCoupons' own doc comment).
  const [appliedSubtotalCoupon, setAppliedSubtotalCoupon] = useState<string | null>(null);
  const [appliedShippingCoupon, setAppliedShippingCoupon] = useState<string | null>(null);
  const [couponPickerOpen, setCouponPickerOpen] = useState(false);

  /** Re-fetches the preview with the given coupon codes (undefined = none) — shared by the
   * initial load and every Apply/Remove action below, so they all go through the exact same
   * revalidation the backend itself does. Never toggles the page-level `previewLoading` spinner
   * once the initial load has completed — an Apply/Remove failure should only affect its own
   * field, not blank out the whole Order Summary the shopper is actively looking at. */
  const loadPreview = (subtotalCode?: string, shippingCode?: string): Promise<CheckoutPreview> =>
    checkoutApi.preview(selectedVariantIds, subtotalCode, shippingCode);

  /** The single entry point for changing either (or both) applied coupon(s) — one
   * `checkoutApi.preview` call carrying the *complete* desired state for both slots at once
   * (never a delta), same shape `checkoutApi.preview`/`.confirm` themselves expect. Passed
   * straight through as `CouponPickerDialog`'s own `onApply` prop (its two radio selections
   * already resolve to exactly these two optional codes) and reused by `handleRemoveCoupon` below
   * for a single-slot clear, so there's exactly one code path that ever calls `loadPreview` with
   * coupon codes attached. */
  const applyCoupons = (subtotalCode?: string, shippingCode?: string): Promise<void> =>
    loadPreview(subtotalCode, shippingCode).then(result => {
      setPreview(result);
      setAppliedSubtotalCoupon(subtotalCode ?? null);
      setAppliedShippingCoupon(shippingCode ?? null);
    });

  const handleRemoveCoupon = (target: CouponTarget): void => {
    const subtotalCode = target === 'SUBTOTAL' ? undefined : (appliedSubtotalCoupon ?? undefined);
    const shippingCode = target === 'SHIPPING_FEE' ? undefined : (appliedShippingCoupon ?? undefined);
    applyCoupons(subtotalCode, shippingCode)
      .catch(err => showError(err instanceof Error ? err.message : 'Could not remove this coupon.'));
  };

  useEffect(() => {
    setPreviewLoading(true);
    loadPreview()
      .then(setPreview)
      .catch((err) => setPreviewError(err instanceof Error ? err.message : 'Could not load your cart.'))
      .finally(() => setPreviewLoading(false));

    addressApi.list().then(addresses => {
      setSavedAddresses(addresses);
      // Pre-select the caller's default address (or the first one, if somehow none is marked
      // default) so the common case needs zero clicks — "enter a new address" stays one click away.
      if (addresses.length > 0) {
        const preferred = addresses.find(a => a.defaultAddress) ?? addresses[0];
        setAddressChoice(String(preferred.id));
      }
    }).catch(() => {
      // Silently falls back to the "enter a new address" form (its own default addressChoice) —
      // a toast here would read as a real checkout error over what's really just a cosmetic
      // convenience failing to load.
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const usingSavedAddress = addressChoice !== NEW_ADDRESS_OPTION;

  const validate = (): boolean => {
    if (usingSavedAddress) return true; // nothing to validate — the picked address already exists
    const newErrors: AddressFormErrors = {};
    if (!address.fullName.trim()) newErrors.fullName = 'Full name is required';
    if (!(address.phone ?? '').trim()) newErrors.phone = 'Phone number is required';
    const emailTrimmed = (address.email ?? '').trim();
    if (!emailTrimmed) newErrors.email = 'Email is required';
    else if (!isValidEmail(emailTrimmed)) newErrors.email = 'Enter a valid email address';
    if (!address.line1.trim()) newErrors.line1 = 'Address is required';
    if (!address.city.trim()) newErrors.city = 'City is required';
    if (!address.state.trim()) newErrors.state = 'State is required';
    if (!address.postalCode.trim()) newErrors.postalCode = 'Postal code is required';
    if (!address.country.trim()) newErrors.country = 'Country is required';
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault();
    if (!validate()) return;
    guard(async () => {
      let orderId: number;
      try {
        const addressInput = usingSavedAddress
          ? { savedAddressId: Number(addressChoice) }
          : {
              ...address,
              saveAddress,
              addressLabel: saveAddress ? (addressLabel.trim() || undefined) : undefined,
            };
        const result = await checkoutApi.confirm(
          addressInput,
          selectedVariantIds,
          appliedSubtotalCoupon ?? undefined,
          appliedShippingCoupon ?? undefined,
        );
        orderId = result.orderId;
        refreshCart(); // backend removes only the ordered lines on success — resync the badge/context
      } catch (err) {
        showError(err instanceof Error ? err.message : 'Could not place your order. Please try again.');
        return;
      }

      // The order now exists — from here on, any failure still leaves a real, recoverable order
      // behind, so the fallback is always "send the shopper to its detail page" (below), never a
      // dead-end error on this one.
      try {
        const paid = await orderApi.pay(orderId);
        if (paid.paymentClientSecret) {
          // Option A: the PaymentIntent still needs the shopper's own client-side confirmation —
          // stay on this page and swap into the payment phase instead of navigating away.
          const config = await paymentConfigApi.get();
          if (!config.publishableKey) {
            throw new Error('Payment is not configured.');
          }
          setCreatedOrderId(orderId);
          setPaymentClientSecret(paid.paymentClientSecret);
          setPublishableKey(config.publishableKey);
          setPhase('payment');
          return;
        }
        // MockPaymentGateway (or an outright-declined Stripe charge) already resolved the charge
        // synchronously — nothing left for the shopper to confirm.
        navigate(`/account/orders/${orderId}`);
      } catch (err) {
        showError(err instanceof Error
          ? err.message
          : 'Your order was placed, but payment could not be started. You can retry from your order.');
        navigate(`/account/orders/${orderId}`);
      }
    });
  };

  /** Cancels the just-created order outright (releases its stock reservation) rather than
   * supporting "go back and edit the coupon/address" — the order/coupon-redemption/cart-removal
   * are all already committed server-side by this point, and there's no backend capability yet to
   * amend a PENDING order in place. See this page's own top-of-state comment. */
  const handleCancelOrder = (): void => {
    if (!createdOrderId) return;
    setCancellingOrder(true);
    orderApi.cancel(createdOrderId)
      .then(() => {
        showSuccess('Order cancelled — nothing was charged.');
        navigate('/cart');
      })
      .catch(err => showError(err instanceof Error ? err.message : 'Could not cancel this order.'))
      .finally(() => setCancellingOrder(false));
  };

  /** `stripe.confirmPayment` resolved with no error — the definitive CONFIRMED/FAILED outcome
   * still comes from `webhook.StripeWebhookService`, not this call, so this deliberately doesn't
   * announce a verdict it doesn't actually know yet; OrderDetailPage's own status chip/alerts pick
   * up whatever the webhook (or a still-in-flight PAYMENT_PROCESSING) ends up showing. */
  const handlePaymentCompleted = (): void => {
    if (!createdOrderId) return;
    navigate(`/account/orders/${createdOrderId}`);
  };

  if (previewLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (previewError || !preview) {
    return (
      <Box sx={{ p: 3, textAlign: 'center', maxWidth: 500, mx: 'auto', mt: 6 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>Can't check out right now</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          {previewError ?? 'Something went wrong.'}
        </Typography>
        <Button variant="contained" onClick={() => navigate('/cart')}>Back to Cart</Button>
      </Box>
    );
  }

  const droppedLines = preview.lines.filter(l => !l.available);

  return (
    <Box sx={{ p: 3, maxWidth: 700, mx: 'auto' }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 3 }}>Checkout</Typography>

      <Paper variant="outlined" sx={{ p: 2.5, mb: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1.5 }}>Order Summary</Typography>
        <Stack spacing={2} divider={<Divider />}>
          {preview.lines.filter(l => l.available).map(line => (
            <OrderLineRow key={line.variantId} line={toOrderLine(line)} />
          ))}
        </Stack>

        {droppedLines.length > 0 && (
          <Box sx={{ mt: 2 }}>
            {droppedLines.map(line => (
              <Chip
                key={line.variantId}
                label={`Variant #${line.variantId} is no longer available and won't be included`}
                size="small"
                color="warning"
                variant="outlined"
                sx={{ mb: 0.5 }}
              />
            ))}
          </Box>
        )}

        <Divider sx={{ my: 1.5 }} />

        {/* Coupon feature follow-up — both slots are chosen together inside CouponPickerDialog
            now (a radio per CouponTarget section); the backend still enforces "at most 2 coupons,
            1 subtotal + 1 shipping" via two independent slots either way. Shipping shown before
            Subtotal throughout this page, matching the dialog's own section order. */}
        <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mb: 1 }}>
          <LocalOfferIcon fontSize="small" color="action" />
          <Typography variant="body2" fontWeight={600}>Coupons</Typography>
        </Stack>
        <Stack spacing={1} sx={{ mb: 1.5 }}>
          {appliedShippingCoupon && (
            <Chip
              label={`${appliedShippingCoupon} — shipping discount applied`}
              color="success"
              variant="outlined"
              onDelete={phase === 'review' ? () => handleRemoveCoupon('SHIPPING_FEE') : undefined}
              sx={{ alignSelf: 'flex-start' }}
            />
          )}
          {appliedSubtotalCoupon && (
            <Chip
              label={`${appliedSubtotalCoupon} — subtotal discount applied`}
              color="success"
              variant="outlined"
              onDelete={phase === 'review' ? () => handleRemoveCoupon('SUBTOTAL') : undefined}
              sx={{ alignSelf: 'flex-start' }}
            />
          )}
          {/* Once payment starts, the order (and its redeemed coupons) is already committed
              server-side — no way to change it in place yet, so this trigger disappears rather
              than opening a picker that can no longer do anything. */}
          {phase === 'review' && (
            <Button
              size="small"
              onClick={() => setCouponPickerOpen(true)}
              sx={{ alignSelf: 'flex-start', textTransform: 'none' }}
            >
              {appliedSubtotalCoupon || appliedShippingCoupon ? 'Manage coupons' : 'Add a coupon'}
            </Button>
          )}
        </Stack>

        <Divider sx={{ my: 1.5 }} />
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">Subtotal</Typography>
          <Typography variant="body2">{formatPrice(preview.subtotal)}</Typography>
        </Stack>
        {/* Shipping shown before the subtotal Discount row, matching the Coupons section/dialog's
            own Shipping-before-Subtotal ordering throughout this page. */}
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="body2" color="text.secondary">Shipping</Typography>
          {preview.originalShippingFee > preview.shippingFee ? (
            // A SHIPPING_FEE coupon discounted the fee — the only mechanism that can do this now
            // (FreeOverThresholdShippingFeeCalculator's automatic threshold waiver was demoted
            // once it turned out to conflict with shipping coupons, see that class's own Javadoc)
            // — which can be *partial* (percentage/fixed, not necessarily down to zero), so show
            // the fee it would have been, struck through, next to what's actually charged now.
            // Only label it "Free" when the charge is genuinely zero; a partial discount still
            // shows its own discounted price, not a misleading "Free".
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="body2" color="text.secondary" sx={{ textDecoration: 'line-through' }}>
                {formatPrice(preview.originalShippingFee)}
              </Typography>
              {preview.shippingFee === 0 ? (
                <Typography variant="body2" color="success.main" fontWeight={600}>Free</Typography>
              ) : (
                <Typography variant="body2" color="success.main">{formatPrice(preview.shippingFee)}</Typography>
              )}
            </Stack>
          ) : (
            <Typography variant="body2">{formatPrice(preview.shippingFee)}</Typography>
          )}
        </Stack>
        {preview.subtotalDiscountAmount > 0 && (
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">Discount</Typography>
            <Typography variant="body2" color="success.main">−{formatPrice(preview.subtotalDiscountAmount)}</Typography>
          </Stack>
        )}
        <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.5 }}>
          <Typography variant="subtitle1" fontWeight={700}>Total</Typography>
          <Typography variant="subtitle1" fontWeight={700}>{formatPrice(preview.total)}</Typography>
        </Stack>
      </Paper>

      {phase === 'review' && (
      <Paper variant="outlined" sx={{ p: 2.5 }} component="form" onSubmit={handleSubmit}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1.5 }}>Shipping Address</Typography>

        {savedAddresses.length > 0 && (
          <RadioGroup
            value={addressChoice}
            onChange={(e) => setAddressChoice(e.target.value)}
            sx={{ mb: 2 }}
          >
            <Stack spacing={1}>
              {savedAddresses.map(saved => (
                <FormControlLabel
                  key={saved.id}
                  value={String(saved.id)}
                  control={<Radio disableRipple />}
                  sx={{
                    m: 0,
                    p: 1,
                    border: '1px solid',
                    borderColor: addressChoice === String(saved.id) ? 'primary.main' : 'divider',
                    borderRadius: 1,
                    alignItems: 'flex-start',
                  }}
                  label={
                    <Box sx={{ pt: 0.5 }}>
                      <Stack direction="row" alignItems="center" spacing={1}>
                        <Typography variant="body2" fontWeight={600}>
                          {saved.label || saved.fullName}
                        </Typography>
                        {saved.defaultAddress && (
                          <Chip label="Default" size="small" color="primary" variant="outlined" />
                        )}
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        {formatSavedAddress(saved)}
                      </Typography>
                    </Box>
                  }
                />
              ))}
              <FormControlLabel
                value={NEW_ADDRESS_OPTION}
                control={<Radio disableRipple />}
                label="Enter a new address"
                sx={{
                  m: 0,
                  p: 1,
                  border: '1px solid',
                  borderColor: addressChoice === NEW_ADDRESS_OPTION ? 'primary.main' : 'divider',
                  borderRadius: 1,
                }}
              />
            </Stack>
          </RadioGroup>
        )}

        {!usingSavedAddress && (
          <Stack spacing={2}>
            <TextField
              label="Full Name"
              fullWidth
              value={address.fullName}
              onChange={(e) => setAddress({ ...address, fullName: e.target.value })}
              error={!!errors.fullName}
              helperText={errors.fullName}
            />
            <TextField
              label="Phone Number"
              fullWidth
              value={address.phone ?? ''}
              onChange={(e) => setAddress({ ...address, phone: e.target.value })}
              error={!!errors.phone}
              helperText={errors.phone}
              inputProps={{ maxLength: 30 }}
            />
            <TextField
              label="Email"
              type="email"
              fullWidth
              value={address.email ?? ''}
              onChange={(e) => setAddress({ ...address, email: e.target.value })}
              error={!!errors.email}
              helperText={errors.email}
              inputProps={{ maxLength: 255 }}
            />
            <TextField
              label="Address Line 1"
              fullWidth
              value={address.line1}
              onChange={(e) => setAddress({ ...address, line1: e.target.value })}
              error={!!errors.line1}
              helperText={errors.line1}
            />
            <TextField
              label="Address Line 2 (optional)"
              fullWidth
              value={address.line2}
              onChange={(e) => setAddress({ ...address, line2: e.target.value })}
            />
            <Stack direction="row" spacing={2}>
              <TextField
                label="City"
                fullWidth
                value={address.city}
                onChange={(e) => setAddress({ ...address, city: e.target.value })}
                error={!!errors.city}
                helperText={errors.city}
              />
              <TextField
                label="State"
                fullWidth
                value={address.state}
                onChange={(e) => setAddress({ ...address, state: e.target.value })}
                error={!!errors.state}
                helperText={errors.state}
              />
            </Stack>
            <Stack direction="row" spacing={2}>
              <TextField
                label="Postal Code"
                fullWidth
                value={address.postalCode}
                onChange={(e) => setAddress({ ...address, postalCode: e.target.value })}
                error={!!errors.postalCode}
                helperText={errors.postalCode}
              />
              <TextField
                label="Country"
                fullWidth
                value={address.country}
                onChange={(e) => setAddress({ ...address, country: e.target.value })}
                error={!!errors.country}
                helperText={errors.country}
              />
            </Stack>

            <FormControlLabel
              control={
                <Checkbox
                  checked={saveAddress}
                  onChange={(e) => setSaveAddress(e.target.checked)}
                  disableRipple
                  sx={{ p: 0 }}
                />
              }
              label="Save this address for future orders"
              sx={{ ml: 0 }}
            />
            {saveAddress && (
              <TextField
                label="Label (optional)"
                placeholder="Home, Work…"
                fullWidth
                value={addressLabel}
                onChange={(e) => setAddressLabel(e.target.value)}
                inputProps={{ maxLength: 50 }}
              />
            )}
          </Stack>
        )}

        <Button type="submit" variant="contained" size="large" fullWidth disabled={submitting} sx={{ mt: 2 }}>
          {submitting ? <CircularProgress size={24} color="inherit" /> : `Place Order & Pay — ${formatPrice(preview.total)}`}
        </Button>
      </Paper>
      )}

      {phase === 'payment' && (
        <>
          <Paper variant="outlined" sx={{ p: 2.5, mb: 3 }}>
            <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>Shipping To</Typography>
            <Typography variant="body2" color="text.secondary">
              {(() => {
                const saved = usingSavedAddress ? savedAddresses.find(a => String(a.id) === addressChoice) : undefined;
                return saved ? formatSavedAddress(saved) : formatAddressState(address);
              })()}
            </Typography>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2.5 }}>
            <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1.5 }}>Payment</Typography>
            {publishableKey && paymentClientSecret && (
              <Elements stripe={getStripePromise(publishableKey)} options={{ clientSecret: paymentClientSecret }}>
                <PaymentElementForm
                  onCompleted={handlePaymentCompleted}
                  secondaryAction={{ label: 'Cancel Order', onClick: handleCancelOrder, disabled: cancellingOrder }}
                  payButtonLabel={`Pay ${formatPrice(preview.total)}`}
                />
              </Elements>
            )}
          </Paper>
        </>
      )}

      {couponPickerOpen && (
        <CouponPickerDialog
          open
          subtotal={preview.subtotal}
          // The pre-coupon quoted fee — Free Shipping coupons' discountAmount/sort is computed
          // against this, never the already-(possibly-)discounted preview.shippingFee, so
          // reopening the dialog with a shipping coupon already applied still ranks options
          // correctly.
          shippingFee={preview.originalShippingFee}
          appliedSubtotalCode={appliedSubtotalCoupon}
          appliedShippingCode={appliedShippingCoupon}
          onClose={() => setCouponPickerOpen(false)}
          onApply={applyCoupons}
        />
      )}
    </Box>
  );
}
