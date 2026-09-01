import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
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
  /** The caller's current cart subtotal — always the cart subtotal, not the shipping fee, even
   * for the shipping section below (minSubtotal is checked against the subtotal regardless of
   * target, same as the backend's own resolve() — see CouponRedemptionService's Javadoc). */
  subtotal: number;
  /** Already-applied codes, if any — the matching row (in whichever section it belongs to) shows
   * an "Applied" state instead of an Apply button. Picking a *different* row in that same section
   * still works and simply replaces it (`CheckoutPage`'s own single subtotal/shipping slot each
   * only ever hold one code at a time — same "at most 2 coupons, 1 per target" rule the backend
   * enforces, this dialog just can't violate it structurally). */
  appliedSubtotalCode: string | null;
  appliedShippingCode: string | null;
  onClose: () => void;
  onSelect: (code: string, target: CouponTarget) => void;
}

function formatValue(coupon: AvailableCoupon): string {
  const base = coupon.type === 'PERCENTAGE' ? `${coupon.value}% OFF` : `${formatPrice(coupon.value)} OFF`;
  if (coupon.type === 'PERCENTAGE' && coupon.maxDiscountAmount != null) {
    return `${base} (up to ${formatPrice(coupon.maxDiscountAmount)})`;
  }
  return base;
}

interface SectionProps {
  title: string;
  coupons: AvailableCoupon[];
  target: CouponTarget;
  subtotal: number;
  appliedCode: string | null;
  onSelect: (code: string, target: CouponTarget) => void;
}

function CouponSection({ title, coupons, target, subtotal, appliedCode, onSelect }: SectionProps): JSX.Element {
  return (
    <Box>
      <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>{title}</Typography>
      {coupons.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
          No coupons available right now.
        </Typography>
      ) : (
        <Stack spacing={1.5}>
          {coupons.map(coupon => {
            const isApplied = coupon.code === appliedCode;
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
                  borderColor: isApplied ? 'success.main' : 'divider',
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

                {isApplied ? (
                  <Chip label="Applied" size="small" color="success" variant="outlined" />
                ) : (
                  <Tooltip title={coupon.eligible ? '' : 'Your cart doesn\'t meet this coupon\'s minimum spend yet'}>
                    <span>
                      <Button
                        size="small"
                        variant="contained"
                        disabled={!coupon.eligible}
                        onClick={() => onSelect(coupon.code, target)}
                      >
                        Apply
                      </Button>
                    </span>
                  </Tooltip>
                )}
              </Box>
            );
          })}
        </Stack>
      )}
    </Box>
  );
}

/**
 * Shopper-facing "browse available coupons" dialog — a single dialog covering both
 * `CouponTarget`s, opened from `CheckoutPage.tsx`'s one shared coupon-code field (per request:
 * one input, not two — the dialog itself is where the SUBTOTAL/SHIPPING_FEE split still lives,
 * shown as two separate sections rather than requiring the shopper to pick a target up front).
 * Purely a *picker*: selecting a row calls `onSelect(code, target)` and closes; it never applies
 * anything itself — `CheckoutPage`'s own `handleApplyCoupon` (the same one manual code entry
 * already uses) does the actual `checkoutApi.preview` round trip, so a picked coupon goes through
 * the identical revalidation a typed one would. Passing `target` back (unlike manual entry, which
 * has to try both slots to discover it) is exactly why picking from here never needs the
 * try-SUBTOTAL-then-SHIPPING_FEE fallback `handleApplyCoupon` uses for a typed code.
 */
export default function CouponPickerDialog({
  open, subtotal, appliedSubtotalCode, appliedShippingCode, onClose, onSelect,
}: Props): JSX.Element {
  const { showError } = useNotification();
  const [subtotalCoupons, setSubtotalCoupons] = useState<AvailableCoupon[]>([]);
  const [shippingCoupons, setShippingCoupons] = useState<AvailableCoupon[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    Promise.all([
      couponApi.listAvailable('SUBTOTAL', subtotal, showError),
      couponApi.listAvailable('SHIPPING_FEE', subtotal, showError),
    ])
      .then(([subtotalResult, shippingResult]) => {
        setSubtotalCoupons(subtotalResult);
        setShippingCoupons(shippingResult);
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, subtotal]);

  const handleSelect = (code: string, target: CouponTarget): void => {
    onSelect(code, target);
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        Available Coupons
        <IconButton size="small" onClick={onClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={28} />
          </Box>
        ) : subtotalCoupons.length === 0 && shippingCoupons.length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <LocalOfferIcon sx={{ fontSize: 32, color: 'text.disabled', mb: 1 }} />
            <Typography variant="body2" color="text.secondary">
              No coupons available right now.
            </Typography>
          </Box>
        ) : (
          <Stack spacing={3}>
            <CouponSection
              title="Discount"
              coupons={subtotalCoupons}
              target="SUBTOTAL"
              subtotal={subtotal}
              appliedCode={appliedSubtotalCode}
              onSelect={handleSelect}
            />
            <Divider />
            <CouponSection
              title="Free Shipping"
              coupons={shippingCoupons}
              target="SHIPPING_FEE"
              subtotal={subtotal}
              appliedCode={appliedShippingCode}
              onSelect={handleSelect}
            />
          </Stack>
        )}
      </DialogContent>
    </Dialog>
  );
}
