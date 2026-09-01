import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import { AvailableCoupon, CouponTarget } from '../types';
import { couponApi } from '../api/couponApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import Thumbnail from './Thumbnail';
import { formatDate, formatPrice } from '../utils/format';

interface Props {
  open: boolean;
  target: CouponTarget;
  /** The caller's current cart subtotal — always the cart subtotal, not the shipping fee, even
   * when `target` is SHIPPING_FEE (minSubtotal is checked against the subtotal regardless of
   * target, same as the backend's own resolve() — see CouponRedemptionService's Javadoc). */
  subtotal: number;
  onClose: () => void;
  onSelect: (code: string) => void;
}

function formatValue(coupon: AvailableCoupon): string {
  const base = coupon.type === 'PERCENTAGE' ? `${coupon.value}% OFF` : `${formatPrice(coupon.value)} OFF`;
  if (coupon.type === 'PERCENTAGE' && coupon.maxDiscountAmount != null) {
    return `${base} (up to ${formatPrice(coupon.maxDiscountAmount)})`;
  }
  return base;
}

/**
 * Shopper-facing "browse available coupons" dialog — opened from `CheckoutPage.tsx` next to each
 * of its two coupon-code fields (one per `CouponTarget`). Purely a *picker*: selecting a row calls
 * `onSelect(code)` and closes; it never applies anything itself — `CheckoutPage`'s own
 * `handleApplyCoupon` (the same one the manual code-entry field already uses) does the actual
 * `checkoutApi.preview` round trip, so a picked coupon goes through the identical revalidation a
 * typed one would.
 */
export default function CouponPickerDialog({ open, target, subtotal, onClose, onSelect }: Props): JSX.Element {
  const { showError } = useNotification();
  const [coupons, setCoupons] = useState<AvailableCoupon[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    couponApi.listAvailable(target, subtotal, showError)
      .then(setCoupons)
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, target, subtotal]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {target === 'SUBTOTAL' ? 'Available Coupons' : 'Available Shipping Coupons'}
        <IconButton size="small" onClick={onClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={28} />
          </Box>
        ) : coupons.length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <LocalOfferIcon sx={{ fontSize: 32, color: 'text.disabled', mb: 1 }} />
            <Typography variant="body2" color="text.secondary">
              No coupons available right now.
            </Typography>
          </Box>
        ) : (
          <Stack spacing={1.5}>
            {coupons.map(coupon => {
              const shortfall = !coupon.eligible && coupon.minSubtotal != null
                ? coupon.minSubtotal - subtotal
                : null;
              return (
                <Box
                  key={coupon.code}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 2,
                    p: 1.5,
                    border: '1px solid',
                    borderColor: 'divider',
                    borderRadius: 1,
                    opacity: coupon.eligible ? 1 : 0.6,
                  }}
                >
                  <Thumbnail imageUrl={coupon.imageUrl} alt={coupon.code} width={56} height={56} fallbackIconSize={22} />

                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Stack direction="row" alignItems="center" spacing={1}>
                      <Typography variant="body2" fontWeight={700} sx={{ fontFamily: 'monospace' }}>
                        {coupon.code}
                      </Typography>
                      <Typography variant="caption" color="error.main" fontWeight={700}>
                        {formatValue(coupon)}
                      </Typography>
                    </Stack>
                    {coupon.description && (
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
                        {coupon.description}
                      </Typography>
                    )}
                    <Stack direction="row" spacing={1.5} sx={{ mt: 0.25 }}>
                      {coupon.minSubtotal != null && (
                        <Typography variant="caption" color="text.secondary">
                          Min. spend {formatPrice(coupon.minSubtotal)}
                        </Typography>
                      )}
                      {coupon.endAt && (
                        <Typography variant="caption" color="text.secondary">
                          Expires {formatDate(coupon.endAt)}
                        </Typography>
                      )}
                    </Stack>
                    {shortfall != null && shortfall > 0 && (
                      <Typography variant="caption" color="warning.main" sx={{ display: 'block', mt: 0.25 }}>
                        Spend {formatPrice(shortfall)} more to unlock
                      </Typography>
                    )}
                  </Box>

                  <Tooltip title={coupon.eligible ? '' : 'Your cart doesn\'t meet this coupon\'s minimum spend yet'}>
                    <span>
                      <Button
                        size="small"
                        variant="contained"
                        disabled={!coupon.eligible}
                        onClick={() => { onSelect(coupon.code); onClose(); }}
                      >
                        Apply
                      </Button>
                    </span>
                  </Tooltip>
                </Box>
              );
            })}
          </Stack>
        )}
      </DialogContent>
    </Dialog>
  );
}
