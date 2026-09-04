import { useEffect, useRef, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  InputAdornment,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { Coupon, CouponTarget, CouponType } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import SubmitButton from '@shared/components/SubmitButton';
import UploadingOverlay from '@shared/components/UploadingOverlay';
import Thumbnail from './Thumbnail';

interface Props {
  open: boolean;
  coupon: Coupon | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormErrors {
  code?: string;
  value?: string;
  dateRange?: string;
  maxDiscountAmount?: string;
  description?: string;
}

/** `<input type="datetime-local">` has no timezone of its own — `new Date(value)` parses it as
 * local time and `toISOString()` converts to UTC, so this round-trips correctly through the
 * backend's plain `Instant` fields without this component needing to reason about timezones
 * itself. */
function toDatetimeLocal(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number): string => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromDatetimeLocal(value: string): string | undefined {
  return value ? new Date(value).toISOString() : undefined;
}

/** Admin CRUD for the Coupon ("ProductDiscount") feature, Phase 4. Mirrors `ProductTagFormDialog`'s
 * shape but with the richer field set `Coupon` actually carries — target/type/value plus optional
 * date-range/min-subtotal/redemption-limit conditions. `code` is immutable after creation (per
 * `ecommerce-service/CLAUDE.md`'s own Coupon note), so it's disabled, not just omitted, in edit
 * mode — keeping the field visible (read-only) is clearer than hiding it outright. */
export default function CouponFormDialog({ open, coupon, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const { loading: saving, guard } = useSubmitGuard();
  const imageInputRef = useRef<HTMLInputElement>(null);

  const [code, setCode] = useState('');
  const [target, setTarget] = useState<CouponTarget>('SUBTOTAL');
  const [type, setType] = useState<CouponType>('PERCENTAGE');
  const [value, setValue] = useState('');
  const [active, setActive] = useState(true);
  const [startAt, setStartAt] = useState('');
  const [endAt, setEndAt] = useState('');
  const [minSubtotal, setMinSubtotal] = useState('');
  const [maxRedemptions, setMaxRedemptions] = useState('');
  const [maxRedemptionsPerUser, setMaxRedemptionsPerUser] = useState('');
  const [maxDiscountAmount, setMaxDiscountAmount] = useState('');
  const [description, setDescription] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [uploadingImage, setUploadingImage] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});

  const isEdit = coupon !== null;

  useEffect(() => {
    if (!open) return;
    setCode(coupon?.code ?? '');
    setTarget(coupon?.target ?? 'SUBTOTAL');
    setType(coupon?.type ?? 'PERCENTAGE');
    setValue(coupon ? String(coupon.value) : '');
    setActive(coupon?.active ?? true);
    setStartAt(toDatetimeLocal(coupon?.startAt ?? null));
    setEndAt(toDatetimeLocal(coupon?.endAt ?? null));
    setMinSubtotal(coupon?.minSubtotal != null ? String(coupon.minSubtotal) : '');
    setMaxRedemptions(coupon?.maxRedemptions != null ? String(coupon.maxRedemptions) : '');
    setMaxRedemptionsPerUser(coupon?.maxRedemptionsPerUser != null ? String(coupon.maxRedemptionsPerUser) : '');
    setMaxDiscountAmount(coupon?.maxDiscountAmount != null ? String(coupon.maxDiscountAmount) : '');
    setDescription(coupon?.description ?? '');
    setImageUrl(coupon?.imageUrl ?? '');
    setErrors({});
  }, [open, coupon]);

  const validate = (): boolean => {
    const newErrors: FormErrors = {};
    if (!isEdit) {
      if (!code.trim()) newErrors.code = 'Code is required';
      else if (code.trim().length > 50) newErrors.code = 'Code must not exceed 50 characters';
    }
    const numericValue = Number(value);
    if (!value.trim() || Number.isNaN(numericValue) || numericValue <= 0) {
      newErrors.value = 'Value must be greater than zero';
    } else if (type === 'PERCENTAGE' && numericValue > 100) {
      newErrors.value = 'A percentage value can\'t exceed 100';
    }
    if (startAt && endAt && fromDatetimeLocal(endAt)! <= fromDatetimeLocal(startAt)!) {
      newErrors.dateRange = 'End date must be after the start date';
    }
    if (maxDiscountAmount.trim() && Number(maxDiscountAmount) <= 0) {
      newErrors.maxDiscountAmount = 'Must be greater than zero';
    }
    if (description.trim().length > 255) {
      newErrors.description = 'Description must not exceed 255 characters';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleImageFileSelected = async (e: React.ChangeEvent<HTMLInputElement>): Promise<void> => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      showError('Only image files are allowed');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      showError('Image must not exceed 5 MB');
      return;
    }
    setUploadingImage(true);
    try {
      const result = await ecommerceApi.uploadCouponImage(file, showError);
      setImageUrl(result.url);
    } finally {
      setUploadingImage(false);
      // Reset so the same file can be re-selected
      if (imageInputRef.current) imageInputRef.current.value = '';
    }
  };

  const handleSubmit = (): void => {
    if (!validate()) return;
    guard(async () => {
      try {
        const shared = {
          target,
          type,
          value: Number(value),
          active,
          startAt: fromDatetimeLocal(startAt),
          endAt: fromDatetimeLocal(endAt),
          minSubtotal: minSubtotal.trim() ? Number(minSubtotal) : undefined,
          maxRedemptions: maxRedemptions.trim() ? Number(maxRedemptions) : undefined,
          maxRedemptionsPerUser: maxRedemptionsPerUser.trim() ? Number(maxRedemptionsPerUser) : undefined,
          maxDiscountAmount: maxDiscountAmount.trim() ? Number(maxDiscountAmount) : undefined,
          description: description.trim() || undefined,
          imageUrl: imageUrl.trim() || undefined,
        };
        if (isEdit) {
          await ecommerceApi.updateCoupon(coupon.id, shared, showError);
          showSuccess('Coupon updated');
        } else {
          await ecommerceApi.createCoupon({ ...shared, code: code.trim().toUpperCase() }, showError);
          showSuccess('Coupon created');
        }
        onSaved();
        onClose();
      } catch {
        // showError already called
      }
    });
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{isEdit ? 'Edit Coupon' : 'New Coupon'}</DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="Code"
            value={code}
            onChange={e => { setCode(e.target.value); setErrors({ ...errors, code: undefined }); }}
            error={!!errors.code}
            helperText={errors.code || (isEdit ? 'Immutable after creation' : 'Normalized to uppercase')}
            disabled={isEdit}
            fullWidth
            autoFocus={!isEdit}
            inputProps={{ maxLength: 50 }}
          />

          <Stack direction="row" spacing={2}>
            <TextField
              select
              label="Applies to"
              value={target}
              onChange={e => setTarget(e.target.value as CouponTarget)}
              fullWidth
            >
              <MenuItem value="SUBTOTAL">Subtotal</MenuItem>
              <MenuItem value="SHIPPING_FEE">Shipping Fee</MenuItem>
            </TextField>
            <TextField
              select
              label="Discount type"
              value={type}
              onChange={e => setType(e.target.value as CouponType)}
              fullWidth
            >
              <MenuItem value="PERCENTAGE">Percentage</MenuItem>
              <MenuItem value="FIXED_AMOUNT">Fixed Amount</MenuItem>
            </TextField>
          </Stack>

          <Stack direction="row" spacing={2}>
            <TextField
              label="Value"
              type="number"
              value={value}
              onChange={e => { setValue(e.target.value); setErrors({ ...errors, value: undefined }); }}
              error={!!errors.value}
              helperText={errors.value}
              InputProps={{
                endAdornment: type === 'PERCENTAGE' ? <InputAdornment position="end">%</InputAdornment> : undefined,
                startAdornment: type === 'FIXED_AMOUNT' ? <InputAdornment position="start">$</InputAdornment> : undefined,
              }}
              fullWidth
            />
            <TextField
              label="Max discount amount"
              type="number"
              value={maxDiscountAmount}
              onChange={e => { setMaxDiscountAmount(e.target.value); setErrors({ ...errors, maxDiscountAmount: undefined }); }}
              error={!!errors.maxDiscountAmount}
              helperText={errors.maxDiscountAmount || (type === 'PERCENTAGE' ? 'e.g. cap a 20% discount at $20' : 'No cap if left blank')}
              InputProps={{ startAdornment: <InputAdornment position="start">$</InputAdornment> }}
              fullWidth
            />
          </Stack>

          <TextField
            label="Description"
            value={description}
            onChange={e => { setDescription(e.target.value); setErrors({ ...errors, description: undefined }); }}
            error={!!errors.description}
            helperText={errors.description || 'Shown to shoppers in the coupon picker — e.g. "20% off orders over $100, up to $20 off"'}
            multiline
            minRows={2}
            fullWidth
            inputProps={{ maxLength: 255 }}
          />

          <Box>
            <Typography variant="body2" sx={{ mb: 1 }}>Promo image (optional)</Typography>
            <input
              ref={imageInputRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={handleImageFileSelected}
            />
            <Stack direction="row" spacing={2} alignItems="center">
              <Box sx={{ position: 'relative', width: 96, height: 64, flexShrink: 0 }}>
                <Thumbnail imageUrl={imageUrl || null} alt="Coupon promo" width={96} height={64} fallbackIconSize={22} />
                {uploadingImage && (
                  <UploadingOverlay>
                    <CircularProgress size={20} sx={{ color: 'white' }} />
                  </UploadingOverlay>
                )}
              </Box>
              <Stack spacing={0.5}>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => imageInputRef.current?.click()}
                  disabled={uploadingImage}
                >
                  {imageUrl ? 'Replace' : 'Upload'}
                </Button>
                {imageUrl && (
                  <Button size="small" color="error" onClick={() => setImageUrl('')} disabled={uploadingImage}>
                    Remove
                  </Button>
                )}
              </Stack>
            </Stack>
          </Box>

          <FormControlLabel
            control={<Switch checked={active} onChange={e => setActive(e.target.checked)} />}
            label="Active"
          />

          <Typography variant="subtitle2" color="text.secondary">Eligibility conditions (optional)</Typography>

          <Stack direction="row" spacing={2}>
            <TextField
              label="Starts at"
              type="datetime-local"
              value={startAt}
              onChange={e => { setStartAt(e.target.value); setErrors({ ...errors, dateRange: undefined }); }}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
            <TextField
              label="Ends at"
              type="datetime-local"
              value={endAt}
              onChange={e => { setEndAt(e.target.value); setErrors({ ...errors, dateRange: undefined }); }}
              error={!!errors.dateRange}
              helperText={errors.dateRange}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
          </Stack>

          <TextField
            label="Minimum cart subtotal"
            type="number"
            value={minSubtotal}
            onChange={e => setMinSubtotal(e.target.value)}
            InputProps={{ startAdornment: <InputAdornment position="start">$</InputAdornment> }}
            helperText="No minimum if left blank"
            fullWidth
          />

          <Stack direction="row" spacing={2}>
            <TextField
              label="Max total redemptions"
              type="number"
              value={maxRedemptions}
              onChange={e => setMaxRedemptions(e.target.value)}
              helperText="No cap if left blank"
              fullWidth
            />
            <TextField
              label="Max redemptions per user"
              type="number"
              value={maxRedemptionsPerUser}
              onChange={e => setMaxRedemptionsPerUser(e.target.value)}
              helperText="No cap if left blank"
              fullWidth
            />
          </Stack>
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <SubmitButton saving={saving} onClick={handleSubmit} label={isEdit ? 'Save' : 'Create'} />
      </DialogActions>
    </Dialog>
  );
}
