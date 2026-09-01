import { useEffect, useState } from 'react';
import {
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import { ProductTag } from '../types';
import { ecommerceApi } from '../api/ecommerceApi';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';

interface Props {
  open: boolean;
  tag: ProductTag | null;
  onClose: () => void;
  onSaved: () => void;
}

/** Mirrors @content's TagFormDialog — minus the Status field, since ProductTag has none
 * (per the confirmed "just name + slug" scope, see ecommerce-service/CLAUDE.md). */
export default function ProductTagFormDialog({ open, tag, onClose, onSaved }: Props): JSX.Element {
  const { showError, showSuccess } = useNotification();
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState('');
  const { loading: saving, guard } = useSubmitGuard();

  const isEdit = tag !== null;

  useEffect(() => {
    if (open) {
      setName(tag?.name ?? '');
      setNameError('');
    }
  }, [open, tag]);

  const validate = (): boolean => {
    if (!name.trim()) {
      setNameError('Name is required');
      return false;
    }
    if (name.trim().length > 100) {
      setNameError('Name must not exceed 100 characters');
      return false;
    }
    setNameError('');
    return true;
  };

  const handleSubmit = (): void => {
    if (!validate()) return;
    guard(async () => {
      try {
        if (isEdit) {
          await ecommerceApi.updateProductTag(tag.id, { name: name.trim() }, showError);
          showSuccess('Product tag updated');
        } else {
          await ecommerceApi.createProductTag({ name: name.trim() }, showError);
          showSuccess('Product tag created');
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
      <DialogTitle>{isEdit ? 'Edit Product Tag' : 'New Product Tag'}</DialogTitle>

      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="Name"
            value={name}
            onChange={e => { setName(e.target.value); setNameError(''); }}
            error={!!nameError}
            helperText={nameError || 'Slug is auto-generated from name'}
            fullWidth
            autoFocus
            inputProps={{ maxLength: 100 }}
          />
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
