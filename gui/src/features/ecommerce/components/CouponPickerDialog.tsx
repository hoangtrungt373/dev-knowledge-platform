import { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  IconButton,
  InputAdornment,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import SearchIcon from '@mui/icons-material/Search';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import { AvailableCoupon } from '../types';
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
  /** The caller's current quoted shipping fee (e.g. the checkout preview's own
   * `originalShippingFee`) — passed to `couponApi.listAvailable` for the Free Shipping section
   * only, so the backend can compute (and sort by) what each shipping coupon would actually
   * deduct from this order. */
  shippingFee: number;
  appliedSubtotalCode: string | null;
  appliedShippingCode: string | null;
  onClose: () => void;
  /** Commits both selections at once, in a single `checkoutApi.preview` round trip — `undefined`
   * clears that slot. Resolves on success (the dialog then closes itself) or rejects with the
   * same `Error` shape every other coupon action on `CheckoutPage` throws, which this dialog shows
   * inline instead of closing. */
  onApply: (subtotalCode?: string, shippingCode?: string) => Promise<void>;
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
  subtotal: number;
  selected: string;
  onChange: (value: string) => void;
}

/** One `RadioGroup` per `CouponTarget` — "restrict one coupon per type" is the whole point of a
 * radio (vs. the earlier per-row Apply button, which had no way to express mutual exclusion
 * within a section other than the backend rejecting a second attempt). No "No coupon" option —
 * a radio group can start with nothing selected (`selected === ''`), and once a real coupon is
 * chosen there's deliberately no way to un-choose it from inside this dialog; clearing an already
 * *applied* coupon still works, just via that coupon's own `Chip`'s `onDelete` on `CheckoutPage`
 * (outside this dialog), not by re-opening the picker. */
function CouponSection({ title, coupons, subtotal, selected, onChange }: SectionProps): JSX.Element {
  return (
    <Box>
      <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>{title}</Typography>
      {coupons.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
          No matching coupons.
        </Typography>
      ) : (
        <RadioGroup value={selected} onChange={e => onChange(e.target.value)}>
          <Stack spacing={1.5}>
            {coupons.map(coupon => {
              const isSelected = coupon.code === selected;
              const shortfall = !coupon.eligible && coupon.minSubtotal != null
                ? coupon.minSubtotal - subtotal
                : null;
              return (
                <FormControlLabel
                  key={coupon.code}
                  value={coupon.code}
                  disabled={!coupon.eligible}
                  control={<Radio size="small" sx={{ alignSelf: 'flex-start', pt: 1.5 }} />}
                  sx={{
                    m: 0,
                    alignItems: 'flex-start',
                    border: '1px solid',
                    borderColor: isSelected ? 'primary.main' : 'divider',
                    borderRadius: 1,
                    opacity: coupon.eligible ? 1 : 0.6,
                  }}
                  label={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1, pr: 1, minWidth: 0 }}>
                      <Thumbnail imageUrl={coupon.imageUrl} alt={coupon.code} width={56} height={56} fallbackIconSize={22} />
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                          <Typography variant="body2" fontWeight={700} sx={{ fontFamily: 'monospace' }}>
                            {coupon.code}
                          </Typography>
                          <Typography variant="caption" color="error.main" fontWeight={700}>
                            {formatValue(coupon)}
                          </Typography>
                          {coupon.eligible && coupon.discountAmount > 0 && (
                            <Typography variant="caption" color="success.main" fontWeight={700}>
                              Save {formatPrice(coupon.discountAmount)}
                            </Typography>
                          )}
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
                    </Box>
                  }
                />
              );
            })}
          </Stack>
        </RadioGroup>
      )}
    </Box>
  );
}

/**
 * Shopper-facing "browse available coupons" dialog — a single dialog covering both
 * `CouponTarget`s, split into two sections (Free Shipping shown first, then Discount — matching
 * `CheckoutPage.tsx`'s own Order Summary ordering). Owns the whole selection flow itself now
 * (search + radio-pick + a bottom Apply button), rather than being a pure picker that closes the
 * instant a row is clicked: `onApply` commits both slots in one `checkoutApi.preview` call, and
 * the dialog stays open with an inline error if that call fails, so a shopper can immediately try
 * a different coupon without reopening it. `CheckoutPage.tsx` still owns the actual API call and
 * the applied-coupon state — this dialog only ever proposes a selection.
 *
 * <p>Each section's coupons already arrive sorted by what's actually best for this order —
 * `couponApi.listAvailable` returns them eligible-first, then by real `discountAmount`
 * descending — so this component renders them in list order with no client-side sort of its own;
 * the search box only filters that order, it never reorders it.
 */
export default function CouponPickerDialog({
  open, subtotal, shippingFee, appliedSubtotalCode, appliedShippingCode, onClose, onApply,
}: Props): JSX.Element {
  const { showError } = useNotification();
  const [subtotalCoupons, setSubtotalCoupons] = useState<AvailableCoupon[]>([]);
  const [shippingCoupons, setShippingCoupons] = useState<AvailableCoupon[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  // Seeded from whatever's already applied every time the dialog opens ('' when nothing is) —
  // reselecting the exact same coupon and hitting Apply is a harmless no-op (see the
  // disabled-Apply check below), and reopening never loses track of what's actually applied even
  // if the shopper closes without committing a change.
  const [selectedSubtotal, setSelectedSubtotal] = useState(appliedSubtotalCode ?? '');
  const [selectedShipping, setSelectedShipping] = useState(appliedShippingCode ?? '');
  const [applying, setApplying] = useState(false);
  const [applyError, setApplyError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setSearch('');
    setApplyError(null);
    setSelectedSubtotal(appliedSubtotalCode ?? '');
    setSelectedShipping(appliedShippingCode ?? '');
    setLoading(true);
    Promise.all([
      couponApi.listAvailable('SUBTOTAL', subtotal, undefined, showError),
      couponApi.listAvailable('SHIPPING_FEE', subtotal, shippingFee, showError),
    ])
      .then(([subtotalResult, shippingResult]) => {
        setSubtotalCoupons(subtotalResult);
        setShippingCoupons(shippingResult);
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, subtotal, shippingFee]);

  const query = search.trim().toLowerCase();
  const filteredSubtotal = useMemo(
    () => (query ? subtotalCoupons.filter(c => c.code.toLowerCase().includes(query)) : subtotalCoupons),
    [subtotalCoupons, query],
  );
  const filteredShipping = useMemo(
    () => (query ? shippingCoupons.filter(c => c.code.toLowerCase().includes(query)) : shippingCoupons),
    [shippingCoupons, query],
  );

  const hasChanges = selectedSubtotal !== (appliedSubtotalCode ?? '')
    || selectedShipping !== (appliedShippingCode ?? '');

  const handleApply = (): void => {
    setApplying(true);
    setApplyError(null);
    const subtotalCode = selectedSubtotal || undefined;
    const shippingCode = selectedShipping || undefined;
    onApply(subtotalCode, shippingCode)
      .then(() => onClose())
      .catch(err => setApplyError(err instanceof Error ? err.message : 'Could not apply your coupon selection.'))
      .finally(() => setApplying(false));
  };

  return (
    <Dialog open={open} onClose={applying ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        Available Coupons
        <IconButton size="small" onClick={onClose} disabled={applying}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>

      <DialogContent dividers>
        <TextField
          size="small"
          fullWidth
          placeholder="Search by coupon code…"
          value={search}
          onChange={e => setSearch(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" color="action" />
              </InputAdornment>
            ),
          }}
          sx={{ mb: 2 }}
        />

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
              title="Free Shipping"
              coupons={filteredShipping}
              subtotal={subtotal}
              selected={selectedShipping}
              onChange={setSelectedShipping}
            />
            <Divider />
            <CouponSection
              title="Discount"
              coupons={filteredSubtotal}
              subtotal={subtotal}
              selected={selectedSubtotal}
              onChange={setSelectedSubtotal}
            />
          </Stack>
        )}

        {applyError && (
          <Typography variant="body2" color="error.main" sx={{ mt: 2 }}>
            {applyError}
          </Typography>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose} disabled={applying}>Cancel</Button>
        <Button variant="contained" onClick={handleApply} disabled={applying || !hasChanges}>
          {applying ? <CircularProgress size={18} color="inherit" /> : 'Apply'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
