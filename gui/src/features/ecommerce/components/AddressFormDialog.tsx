import { useEffect, useState } from 'react';
import {
  Button,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Stack,
  TextField,
} from '@mui/material';
import { SavedAddress } from '../types';
import { addressApi } from '../api/addressApi';
import { useNotification } from '@shared/contexts/NotificationContext';

interface FormErrors {
  fullName?: string;
  line1?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
}

interface Props {
  open: boolean;
  address: SavedAddress | null;
  onClose: () => void;
  onSaved: () => void;
}

/** Create/edit dialog for one AddressBook entry. No "set as default" toggle in edit mode — that's
 * its own dedicated action on the list page (mirrors the backend: update() never touches the
 * default flag at all), only "Set as default" on create, matching CreateSavedAddressPayload's
 * own makeDefault field (the caller's very first address is auto-defaulted regardless). */
export default function AddressFormDialog({ open, address, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const isEdit = address !== null;

  const [label, setLabel] = useState('');
  const [fullName, setFullName] = useState('');
  const [line1, setLine1] = useState('');
  const [line2, setLine2] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');
  const [postalCode, setPostalCode] = useState('');
  const [country, setCountry] = useState('');
  const [makeDefault, setMakeDefault] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      setLabel(address?.label ?? '');
      setFullName(address?.fullName ?? '');
      setLine1(address?.line1 ?? '');
      setLine2(address?.line2 ?? '');
      setCity(address?.city ?? '');
      setState(address?.state ?? '');
      setPostalCode(address?.postalCode ?? '');
      setCountry(address?.country ?? '');
      setMakeDefault(false);
      setErrors({});
    }
  }, [open, address]);

  const validate = (): boolean => {
    const e: FormErrors = {};
    if (!fullName.trim()) e.fullName = 'Full name is required';
    if (!line1.trim()) e.line1 = 'Address is required';
    if (!city.trim()) e.city = 'City is required';
    if (!state.trim()) e.state = 'State is required';
    if (!postalCode.trim()) e.postalCode = 'Postal code is required';
    if (!country.trim()) e.country = 'Country is required';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    setSaving(true);
    try {
      const fields = {
        label: label.trim() || undefined,
        fullName: fullName.trim(),
        line1: line1.trim(),
        line2: line2.trim() || undefined,
        city: city.trim(),
        state: state.trim(),
        postalCode: postalCode.trim(),
        country: country.trim(),
      };
      if (isEdit) {
        await addressApi.update(address.id, fields, showError);
        showSuccess('Address updated');
      } else {
        await addressApi.create({ ...fields, makeDefault }, showError);
        showSuccess('Address added');
      }
      onSaved();
      onClose();
    } catch {
      // showError already called
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{isEdit ? 'Edit Address' : 'New Address'}</DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="Label (optional)"
            placeholder="Home, Work…"
            value={label}
            onChange={e => setLabel(e.target.value)}
            fullWidth
            autoFocus
            inputProps={{ maxLength: 50 }}
          />
          <TextField
            label="Full Name"
            value={fullName}
            onChange={e => { setFullName(e.target.value); setErrors(p => ({ ...p, fullName: undefined })); }}
            error={!!errors.fullName}
            helperText={errors.fullName}
            fullWidth
          />
          <TextField
            label="Address Line 1"
            value={line1}
            onChange={e => { setLine1(e.target.value); setErrors(p => ({ ...p, line1: undefined })); }}
            error={!!errors.line1}
            helperText={errors.line1}
            fullWidth
          />
          <TextField
            label="Address Line 2 (optional)"
            value={line2}
            onChange={e => setLine2(e.target.value)}
            fullWidth
          />
          <Stack direction="row" spacing={2}>
            <TextField
              label="City"
              value={city}
              onChange={e => { setCity(e.target.value); setErrors(p => ({ ...p, city: undefined })); }}
              error={!!errors.city}
              helperText={errors.city}
              fullWidth
            />
            <TextField
              label="State"
              value={state}
              onChange={e => { setState(e.target.value); setErrors(p => ({ ...p, state: undefined })); }}
              error={!!errors.state}
              helperText={errors.state}
              fullWidth
            />
          </Stack>
          <Stack direction="row" spacing={2}>
            <TextField
              label="Postal Code"
              value={postalCode}
              onChange={e => { setPostalCode(e.target.value); setErrors(p => ({ ...p, postalCode: undefined })); }}
              error={!!errors.postalCode}
              helperText={errors.postalCode}
              fullWidth
            />
            <TextField
              label="Country"
              value={country}
              onChange={e => { setCountry(e.target.value); setErrors(p => ({ ...p, country: undefined })); }}
              error={!!errors.country}
              helperText={errors.country}
              fullWidth
            />
          </Stack>

          {!isEdit && (
            <FormControlLabel
              control={
                <Checkbox
                  checked={makeDefault}
                  onChange={e => setMakeDefault(e.target.checked)}
                  disableRipple
                  sx={{ p: 0 }}
                />
              }
              label="Set as default address"
              sx={{ ml: 0 }}
            />
          )}
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={saving}>
          {saving ? <CircularProgress size={16} color="inherit" /> : (isEdit ? 'Save' : 'Add Address')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
