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
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { useCart } from '../../context/CartContext';
import { checkoutApi } from '../../api/checkoutApi';
import { addressApi } from '../../api/addressApi';
import { Address, CheckoutPreview, SavedAddress } from '../../types';
import { formatPrice } from '../../utils/format';

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

  useEffect(() => {
    setPreviewLoading(true);
    checkoutApi.preview(selectedVariantIds)
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
        const result = await checkoutApi.confirm(addressInput, selectedVariantIds);
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
        <Stack spacing={1}>
          {preview.lines.filter(l => l.available).map(line => (
            <Stack key={line.variantId} direction="row" justifyContent="space-between">
              <Typography variant="body2">{line.productName} × {line.quantity}</Typography>
              <Typography variant="body2">{formatPrice(line.lineTotal ?? 0)}</Typography>
            </Stack>
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
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">Subtotal</Typography>
          <Typography variant="body2">{formatPrice(preview.subtotal)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">Shipping</Typography>
          <Typography variant="body2">{formatPrice(preview.shippingFee)}</Typography>
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
                  control={<Radio />}
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
                control={<Radio />}
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
              control={<Checkbox checked={saveAddress} onChange={(e) => setSaveAddress(e.target.checked)} />}
              label="Save this address for future orders"
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
    </Box>
  );
}
