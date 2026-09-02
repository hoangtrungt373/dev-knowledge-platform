import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import { ProductAttribute } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';

interface Props {
  open: boolean;
  attribute: ProductAttribute | null;
  onClose: () => void;
  onSaved: () => void;
}

/** Swaps `values[index]`/`values[index + direction]` — same "move by ±1, clamp at the ends via the
 * caller's own disabled state" convention `ImageThumbnailGrid.onMove` already established. */
function move<T>(list: T[], index: number, direction: -1 | 1): T[] {
  const next = [...list];
  const target = index + direction;
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

/** Create/edit dialog for one `ProductAttribute` — a name plus its complete controlled-vocabulary
 * `values` list, edited as one whole list (matches the backend's own `create`/`update` shape,
 * which always clears and rebuilds the list rather than adding/removing one value at a time). List
 * position becomes each value's `displayOrder` server-side — reordering here is purely a local
 * array edit, no separate "set order" action. */
export default function ProductAttributeFormDialog({ open, attribute, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState('');
  const [values, setValues] = useState<string[]>([]);
  const [valuesError, setValuesError] = useState('');
  const [newValue, setNewValue] = useState('');
  const { loading: saving, guard } = useSubmitGuard();

  const isEdit = attribute !== null;

  useEffect(() => {
    if (open) {
      setName(attribute?.name ?? '');
      setValues(attribute ? attribute.values.slice().sort((a, b) => a.displayOrder - b.displayOrder).map(v => v.value) : []);
      setNewValue('');
      setNameError('');
      setValuesError('');
    }
  }, [open, attribute]);

  const addValue = (): void => {
    const trimmed = newValue.trim();
    if (!trimmed) return;
    if (values.some(v => v.toLowerCase() === trimmed.toLowerCase())) {
      setValuesError(`"${trimmed}" is already in the list`);
      return;
    }
    setValues([...values, trimmed]);
    setNewValue('');
    setValuesError('');
  };

  const removeValue = (index: number): void => {
    setValues(values.filter((_, i) => i !== index));
  };

  const validate = (): boolean => {
    let valid = true;
    if (!name.trim()) {
      setNameError('Name is required');
      valid = false;
    } else if (name.trim().length > 50) {
      setNameError('Name must not exceed 50 characters');
      valid = false;
    } else {
      setNameError('');
    }
    if (values.length === 0) {
      setValuesError('At least one value is required');
      valid = false;
    }
    return valid;
  };

  const handleSubmit = (): void => {
    if (!validate()) return;
    guard(async () => {
      try {
        if (isEdit) {
          await ecommerceApi.updateProductAttribute(attribute.id, { name: name.trim(), values }, showError);
          showSuccess('Product attribute updated');
        } else {
          await ecommerceApi.createProductAttribute({ name: name.trim(), values }, showError);
          showSuccess('Product attribute created');
        }
        onSaved();
        onClose();
      } catch {
        // showError already called
      }
    });
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{isEdit ? 'Edit Product Attribute' : 'New Product Attribute'}</DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="Name"
            value={name}
            onChange={e => { setName(e.target.value); setNameError(''); }}
            error={!!nameError}
            helperText={nameError || 'Matched literally against a variant’s attribute key, e.g. "size" or "color"'}
            fullWidth
            autoFocus
            inputProps={{ maxLength: 50 }}
          />

          <Box>
            <Typography variant="body2" fontWeight={600} sx={{ mb: 1 }}>Values</Typography>

            {values.length === 0 ? (
              <Typography variant="body2" color={valuesError ? 'error' : 'text.secondary'} sx={{ mb: 1 }}>
                {valuesError || 'No values yet — add at least one below.'}
              </Typography>
            ) : (
              <Stack spacing={0.5} sx={{ mb: 1 }}>
                {values.map((value, index) => (
                  <Stack key={index} direction="row" alignItems="center" spacing={0.5}>
                    <Typography variant="body2" sx={{ flex: 1 }}>{value}</Typography>
                    <Tooltip title="Move earlier">
                      <span>
                        <IconButton size="small" disabled={index === 0} onClick={() => setValues(move(values, index, -1))}>
                          <ArrowUpwardIcon fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Move later">
                      <span>
                        <IconButton
                          size="small"
                          disabled={index === values.length - 1}
                          onClick={() => setValues(move(values, index, 1))}
                        >
                          <ArrowDownwardIcon fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Remove">
                      <IconButton size="small" color="error" onClick={() => removeValue(index)}>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </Stack>
                ))}
              </Stack>
            )}

            <Stack direction="row" spacing={1}>
              <TextField
                placeholder="Add a value…"
                value={newValue}
                onChange={e => { setNewValue(e.target.value); setValuesError(''); }}
                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addValue(); } }}
                size="small"
                fullWidth
                inputProps={{ maxLength: 50 }}
              />
              <Button variant="outlined" startIcon={<AddIcon />} onClick={addValue} disabled={!newValue.trim()}>
                Add
              </Button>
            </Stack>
          </Box>
        </Stack>
      </DialogContent>

      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Cancel</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={saving}>
          {saving ? <CircularProgress size={16} color="inherit" /> : (isEdit ? 'Save' : 'Create')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
