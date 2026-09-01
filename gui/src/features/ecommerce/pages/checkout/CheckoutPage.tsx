import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
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
import { useCart } from '../../context/CartContext';
import { checkoutApi } from '../../api/checkoutApi';
import { addressApi } from '../../api/addressApi';
import { Address, CartLine, CheckoutPreview, OrderLine, SavedAddress } from '../../types';
import { formatPrice } from '../../utils/format';
import OrderLineRow from '../../components/orders/OrderLineRow';
import CouponPickerDialog from '../../components/CouponPickerDialog';

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
  line1?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
}

const EMPTY_ADDRESS: Address = {
  fullName: '', line1: '', line2: '', city: '', state: '', postalCode: '', country: '',
};

/** Sentinel radio value for "enter a new address" — distinct from any real numeric address id. */
const NEW_ADDRESS_OPTION = 'new';

function formatSavedAddress(a: SavedAddress): string {
  return `${a.fullName}, ${a.line1}${a.line2 ? `, ${a.line2}` : ''}, ${a.city}, ${a.state} ${a.postalCode}, ${a.country}`;
}

export default function CheckoutPage(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const { showError } = useNotification();
  const { refresh: refreshCart } = useCart();
  const { loading: submitting, guard } = useSubmitGuard();

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

  // Coupon feature Phase 4: at most one code per target (mirrors the backend's own "1 subtotal, 1
  // shipping" constraint — two separate optional codes, not a list). "Applied" means the last
  // preview call actually accepted it; the raw text input is kept separate so a typo mid-edit
  // doesn't look like an already-applied code.
  const [subtotalCouponInput, setSubtotalCouponInput] = useState('');
  const [shippingCouponInput, setShippingCouponInput] = useState('');
  const [appliedSubtotalCoupon, setAppliedSubtotalCoupon] = useState<string | null>(null);
  const [appliedShippingCoupon, setAppliedShippingCoupon] = useState<string | null>(null);
  const [subtotalCouponError, setSubtotalCouponError] = useState<string | null>(null);
  const [shippingCouponError, setShippingCouponError] = useState<string | null>(null);
  const [applyingSubtotalCoupon, setApplyingSubtotalCoupon] = useState(false);
  const [applyingShippingCoupon, setApplyingShippingCoupon] = useState(false);
  // Which target's CouponPickerDialog is currently open — null when neither is. One shared dialog
  // instance rather than two, since only one can ever be open at a time.
  const [couponPickerTarget, setCouponPickerTarget] = useState<'subtotal' | 'shipping' | null>(null);

  /** Re-fetches the preview with the given coupon codes (undefined = none) — shared by the
   * initial load and every Apply/Remove action below, so they all go through the exact same
   * revalidation the backend itself does. Never toggles the page-level `previewLoading` spinner
   * once the initial load has completed — an Apply/Remove failure should only affect its own
   * field, not blank out the whole Order Summary the shopper is actively looking at. */
  const loadPreview = (subtotalCode?: string, shippingCode?: string): Promise<CheckoutPreview> =>
    checkoutApi.preview(selectedVariantIds, subtotalCode, shippingCode);

  /** `codeOverride` lets `CouponPickerDialog`'s `onSelect` apply a picked code directly, without
   * first round-tripping it through the text field's own local state — same underlying apply flow
   * (and the same field-scoped error handling) either way. */
  const handleApplyCoupon = (target: 'subtotal' | 'shipping', codeOverride?: string): void => {
    const code = (codeOverride ?? (target === 'subtotal' ? subtotalCouponInput : shippingCouponInput)).trim();
    if (!code) return;
    const setInput = target === 'subtotal' ? setSubtotalCouponInput : setShippingCouponInput;
    const setApplying = target === 'subtotal' ? setApplyingSubtotalCoupon : setApplyingShippingCoupon;
    const setFieldError = target === 'subtotal' ? setSubtotalCouponError : setShippingCouponError;
    setInput(code);
    setApplying(true);
    setFieldError(null);
    const subtotalCode = target === 'subtotal' ? code : (appliedSubtotalCoupon ?? undefined);
    const shippingCode = target === 'shipping' ? code : (appliedShippingCoupon ?? undefined);
    loadPreview(subtotalCode, shippingCode)
      .then(result => {
        setPreview(result);
        if (target === 'subtotal') setAppliedSubtotalCoupon(code);
        else setAppliedShippingCoupon(code);
      })
      .catch(err => setFieldError(err instanceof Error ? err.message : 'This coupon can\'t be applied.'))
      .finally(() => setApplying(false));
  };

  const handleRemoveCoupon = (target: 'subtotal' | 'shipping'): void => {
    const subtotalCode = target === 'subtotal' ? undefined : (appliedSubtotalCoupon ?? undefined);
    const shippingCode = target === 'shipping' ? undefined : (appliedShippingCoupon ?? undefined);
    loadPreview(subtotalCode, shippingCode).then(result => {
      setPreview(result);
      if (target === 'subtotal') { setAppliedSubtotalCoupon(null); setSubtotalCouponInput(''); }
      else { setAppliedShippingCoupon(null); setShippingCouponInput(''); }
    }).catch(err => showError(err instanceof Error ? err.message : 'Could not remove this coupon.'));
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
        refreshCart(); // backend removes only the ordered lines on success — resync the badge/context
        // Order Detail (Epic 3) is now the canonical "here's your order" view — it has the real
        // Pay Now button this page's own former inline confirmation never could.
        navigate(`/orders/${result.orderId}`);
      } catch (err) {
        showError(err instanceof Error ? err.message : 'Could not place your order. Please try again.');
      }
    });
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

        {/* Coupon feature Phase 4 — one code per CouponTarget, mirroring the backend's own
            "at most 2 coupons, 1 subtotal + 1 shipping" constraint. */}
        <Stack direction="row" alignItems="center" spacing={0.5} sx={{ mb: 1 }}>
          <LocalOfferIcon fontSize="small" color="action" />
          <Typography variant="body2" fontWeight={600}>Coupons</Typography>
        </Stack>
        <Stack spacing={1.5} sx={{ mb: 1.5 }}>
          {appliedSubtotalCoupon ? (
            <Chip
              label={`${appliedSubtotalCoupon} — subtotal discount applied`}
              color="success"
              variant="outlined"
              onDelete={() => handleRemoveCoupon('subtotal')}
              sx={{ alignSelf: 'flex-start' }}
            />
          ) : (
            <Stack spacing={0.5}>
              <Stack direction="row" spacing={1} alignItems="flex-start">
                <TextField
                  size="small"
                  label="Subtotal coupon code"
                  value={subtotalCouponInput}
                  onChange={e => { setSubtotalCouponInput(e.target.value); setSubtotalCouponError(null); }}
                  error={!!subtotalCouponError}
                  helperText={subtotalCouponError}
                  sx={{ flex: 1 }}
                />
                <Button
                  variant="outlined"
                  onClick={() => handleApplyCoupon('subtotal')}
                  disabled={!subtotalCouponInput.trim() || applyingSubtotalCoupon}
                  sx={{ height: 40 }}
                >
                  {applyingSubtotalCoupon ? <CircularProgress size={18} /> : 'Apply'}
                </Button>
              </Stack>
              <Button
                size="small"
                onClick={() => setCouponPickerTarget('subtotal')}
                sx={{ alignSelf: 'flex-start', textTransform: 'none' }}
              >
                Browse available coupons
              </Button>
            </Stack>
          )}

          {appliedShippingCoupon ? (
            <Chip
              label={`${appliedShippingCoupon} — shipping discount applied`}
              color="success"
              variant="outlined"
              onDelete={() => handleRemoveCoupon('shipping')}
              sx={{ alignSelf: 'flex-start' }}
            />
          ) : (
            <Stack spacing={0.5}>
              <Stack direction="row" spacing={1} alignItems="flex-start">
                <TextField
                  size="small"
                  label="Shipping coupon code"
                  value={shippingCouponInput}
                  onChange={e => { setShippingCouponInput(e.target.value); setShippingCouponError(null); }}
                  error={!!shippingCouponError}
                  helperText={shippingCouponError}
                  sx={{ flex: 1 }}
                />
                <Button
                  variant="outlined"
                  onClick={() => handleApplyCoupon('shipping')}
                  disabled={!shippingCouponInput.trim() || applyingShippingCoupon}
                  sx={{ height: 40 }}
                >
                  {applyingShippingCoupon ? <CircularProgress size={18} /> : 'Apply'}
                </Button>
              </Stack>
              <Button
                size="small"
                onClick={() => setCouponPickerTarget('shipping')}
                sx={{ alignSelf: 'flex-start', textTransform: 'none' }}
              >
                Browse available coupons
              </Button>
            </Stack>
          )}
        </Stack>

        <Divider sx={{ my: 1.5 }} />
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">Subtotal</Typography>
          <Typography variant="body2">{formatPrice(preview.subtotal)}</Typography>
        </Stack>
        {preview.subtotalDiscountAmount > 0 && (
          <Stack direction="row" justifyContent="space-between">
            <Typography variant="body2" color="text.secondary">Discount</Typography>
            <Typography variant="body2" color="success.main">−{formatPrice(preview.subtotalDiscountAmount)}</Typography>
          </Stack>
        )}
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="body2" color="text.secondary">Shipping</Typography>
          {preview.originalShippingFee > preview.shippingFee ? (
            // A waiver applied — either the automatic FreeOverThresholdShippingFeeCalculator
            // threshold (always all-or-nothing) or a SHIPPING_FEE coupon (Phase 2, can be a
            // *partial* percentage/fixed discount, not necessarily down to zero) — show the fee it
            // would have been, struck through, next to what's actually charged now. Only label it
            // "Free" when the charge is genuinely zero; a partial coupon discount still shows its
            // own discounted price, not a misleading "Free".
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
        <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.5 }}>
          <Typography variant="subtitle1" fontWeight={700}>Total</Typography>
          <Typography variant="subtitle1" fontWeight={700}>{formatPrice(preview.total)}</Typography>
        </Stack>
      </Paper>

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
          {submitting ? <CircularProgress size={24} color="inherit" /> : `Place Order — ${formatPrice(preview.total)}`}
        </Button>
      </Paper>

      {couponPickerTarget && (
        <CouponPickerDialog
          open
          target={couponPickerTarget === 'subtotal' ? 'SUBTOTAL' : 'SHIPPING_FEE'}
          subtotal={preview.subtotal}
          onClose={() => setCouponPickerTarget(null)}
          onSelect={code => handleApplyCoupon(couponPickerTarget, code)}
        />
      )}
    </Box>
  );
}
