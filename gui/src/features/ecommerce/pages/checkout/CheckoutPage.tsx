import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { useCart } from '../../context/CartContext';
import { checkoutApi } from '../../api/checkoutApi';
import { Address, CheckoutPreview, OrderConfirmation } from '../../types';

function formatPrice(value: number): string {
  return `$${value.toFixed(2)}`;
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

export default function CheckoutPage(): JSX.Element {
  const navigate = useNavigate();
  const { showError } = useNotification();
  const { refresh: refreshCart } = useCart();
  const { loading: submitting, guard } = useSubmitGuard();

  const [preview, setPreview] = useState<CheckoutPreview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(true);
  const [confirmation, setConfirmation] = useState<OrderConfirmation | null>(null);

  const [address, setAddress] = useState<Address>(EMPTY_ADDRESS);
  const [errors, setErrors] = useState<AddressFormErrors>({});

  useEffect(() => {
    setPreviewLoading(true);
    checkoutApi.preview()
      .then(setPreview)
      .catch((err) => setPreviewError(err instanceof Error ? err.message : 'Could not load your cart.'))
      .finally(() => setPreviewLoading(false));
  }, []);

  const validate = (): boolean => {
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
        const result = await checkoutApi.confirm(address);
        setConfirmation(result);
        refreshCart(); // backend clears the cart on successful confirm — resync the badge/context
      } catch (err) {
        showError(err instanceof Error ? err.message : 'Could not place your order. Please try again.');
      }
    });
  };

  if (confirmation) {
    return <OrderConfirmationView confirmation={confirmation} />;
  }

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

          <Button type="submit" variant="contained" size="large" disabled={submitting}>
            {submitting ? <CircularProgress size={24} color="inherit" /> : `Place Order — ${formatPrice(preview.total)}`}
          </Button>
        </Stack>
      </Paper>
    </Box>
  );
}

function OrderConfirmationView({ confirmation }: { confirmation: OrderConfirmation }): JSX.Element {
  const navigate = useNavigate();
  return (
    <Box sx={{ p: 3, maxWidth: 600, mx: 'auto', mt: 4 }}>
      <Box sx={{ textAlign: 'center', mb: 3 }}>
        <CheckCircleOutlineIcon sx={{ fontSize: 64, color: 'success.main', mb: 1 }} />
        <Typography variant="h5" fontWeight={700}>Order placed!</Typography>
        <Typography variant="body2" color="text.secondary">Order #{confirmation.orderId}</Typography>
      </Box>

      {confirmation.droppedLines.length > 0 && (
        <Box sx={{ mb: 2 }}>
          {confirmation.droppedLines.map(line => (
            <Chip
              key={line.variantId}
              label={`Variant #${line.variantId} became unavailable and was not included`}
              size="small"
              color="warning"
              variant="outlined"
              sx={{ mb: 0.5 }}
            />
          ))}
        </Box>
      )}

      <Paper variant="outlined" sx={{ p: 2.5, mb: 2 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1.5 }}>Items</Typography>
        <Stack spacing={1}>
          {confirmation.lines.map(line => (
            <Stack key={line.variantId} direction="row" justifyContent="space-between">
              <Typography variant="body2">{line.productName} × {line.quantity}</Typography>
              <Typography variant="body2">{formatPrice(line.lineTotal)}</Typography>
            </Stack>
          ))}
        </Stack>
        <Divider sx={{ my: 1.5 }} />
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">Subtotal</Typography>
          <Typography variant="body2">{formatPrice(confirmation.subtotal)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between">
          <Typography variant="body2" color="text.secondary">Shipping</Typography>
          <Typography variant="body2">{formatPrice(confirmation.shippingFee)}</Typography>
        </Stack>
        <Stack direction="row" justifyContent="space-between" sx={{ mt: 0.5 }}>
          <Typography variant="subtitle1" fontWeight={700}>Total</Typography>
          <Typography variant="subtitle1" fontWeight={700}>{formatPrice(confirmation.total)}</Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2.5, mb: 3 }}>
        <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>Shipping To</Typography>
        <Typography variant="body2">{confirmation.address.fullName}</Typography>
        <Typography variant="body2">{confirmation.address.line1}</Typography>
        {confirmation.address.line2 && <Typography variant="body2">{confirmation.address.line2}</Typography>}
        <Typography variant="body2">
          {confirmation.address.city}, {confirmation.address.state} {confirmation.address.postalCode}
        </Typography>
        <Typography variant="body2">{confirmation.address.country}</Typography>
      </Paper>

      <Box sx={{ textAlign: 'center' }}>
        <Button variant="contained" onClick={() => navigate('/shop')}>Continue Shopping</Button>
      </Box>
    </Box>
  );
}
